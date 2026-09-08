# Specyfikacja: Feedback-driven RAG Quality Loop

## 1. Cel

Zaprojektowanie i wdrożenie pętli zwrotnej (feedback loop), w której ocena trafności odpowiedzi przez użytkowników wpływa bezpośrednio na jakość przyszłych wyników RAG. System ma się uczyć na feedbacku i automatycznie poprawiać trafność odpowiedzi z bazy wiedzy.

## 2. Problem

Obecnie istnieje `FeedbackService`, który:
- Zbiera głosy (positive/negative) od użytkowników via Slack buttons
- Aktualizuje `quality_score` na wpisach wiedzy za pomocą formuły Bayesowskiej
- Ale `quality_score` **NIE jest uwzględniany** w zapytaniu wektorowym — wyniki wyszukiwania zależą wyłącznie od cosine distance

W efekcie wpisy o niskiej jakości (z鳢e odpowiedzi) nadal pojawiają się wysoko w wynikach, a dobre wpisy nie są premiowane.

## 3. Kontekst

### Istniejąca architektura
- **`KnowledgeService.answerQuestion()`** — główne zapytanie RAG z trzema progami (HIGH/MEDIUM/MIN confidence)
- **`FeedbackService.applyVote()`** — Bayesowska aktualizacja `quality_score` (CONFIDENCE=10, PRIOR=0.5)
- **`FeedbackMapping`** — mapowanie feedbackId → entryIds + votes
- **`SimilarityResultProjection`** — wynik zapytania wektorowego: `{id, content, answer, source, accepted, score}`
- **`KnowledgeEntry.quality_score`** — Float 0.0-1.0, aktualizowany po głosowaniu
- **`KnowledgeEntry.usage_count`** — ile razy wpis był użyty w odpowiedzi
- **`KnowledgeEntry.accepted`** — czy przeszedł ocenę LLM (ingestion) lub został zweryfikowany

### Istniejące zapytanie wektorowe (publiczne: `SpringDataKnowledgeRepository`)
```sql
SELECT id, content, answer, source, accepted,
       1 - (embedding <=> CAST(:embedding AS vector)) AS score
FROM knowledge_entries
WHERE tenant_id = :tenantId
  AND answer IS NOT NULL
  AND (project_id IS NULL OR project_id IN (:projectIds))
  AND 1 - (embedding <=> CAST(:embedding AS vector)) >= :minScore
ORDER BY score DESC
LIMIT :limit
```

**Uwaga:** `quality_score` nie występuje w zapytaniu — nie wpływa na ranking.

## 4. Algorytm: Hybrid Reranking

### 4.1. Idea przewodnia

Zamiast polegać wyłącznie na cosine distance (pure vector search), wprowadzamy **hybrid scoring** łączący:
1. **Semantic similarity** (cosine distance) — jak blisko semantycznie jest treść zapytania do wpisu
2. **Quality score** (Bayesowski, z feedbacku) — jak wysoka jakość tego wpisu według użytkowników
3. **Freshness** (czas ostatniego użycia) — preferowanie aktualnych wpisów
4. **Acceptance signal** — preferowanie zweryfikowanych wpisów

### 4.2. Wzór na_final score

```
final_score = α × semantic_score + β × quality_score + γ × freshness_bonus + δ × acceptance_bonus
```

Gdzie:
- `α` (default: 0.65) — waga podobieństwa semantycznego
- `β` (default: 0.25) - waga jakości z feedbacku
- `γ` (default: 0.05) — waga świeżości
- `δ` (default: 0.05) — waga akceptacji

#### Normalizacja wag
```
α + β + γ + δ = 1.0
```

Wagi są **konfigurowalne** (application.yaml) i mogą być dostrajane w zależności od ilości zebranego feedbacku.

### 4.3. Parametry komponentów

#### Semantic Score (α)
- Zwracane bezpośrednio przez pgvector: `1 - (embedding <=> CAST(:embedding AS vector))`
- Zakres: [0.0, 1.0]

#### Quality Score (β)
- Wartość `quality_score` z `KnowledgeEntry`
- Aktualizowana przez `FeedbackService` na Bayesowskim wzorze:
  ```
  quality = (CONFIDENCE × PRIOR + netVotes) / (CONFIDENCE + totalVotes)
  ```
- Zakres: [0.0, 1.0]
- Domyślna wartość dla nowych wpisów: 0.5 (PRIOR)

#### Freshness Bonus (γ)
- Na podstawie `last_used_at` wpisu
- Wzór:
  ```
  freshness = 1.0 / (1.0 + days_since_last_use / DECAY_DAYS)
  ```
- `DECAY_DAYS` = 90 (konfigurowalne)
- Wartość: 1.0 (użyte dziś) → ~0.01 (użyte 3 lata temu)
- Dla wpisów nigdy nieużywanych (`last_used_at = null`): używamy `created_at`

#### Acceptance Bonus (δ)
- `accepted == true` → 1.0
- `accepted == false` → 0.3 (nie eliminujemy, ale obniżamy ranking)

### 4.4. Adaptacyjne wagi

Wagi `α, β, γ, δ` mogą być **adaptacyjne** w zależności od ilości zebranego feedbacku:

```
IF total_feedback_count < THRESHOLD_LOW (np. 10):
    α = 0.85, β = 0.10, γ = 0.03, δ = 0.02  (mało feedbacku → ufaj vectorom)

IF total_feedback_count >= THRESHOLD_LOW AND < THRESHOLD_HIGH (np. 50):
    α = 0.70, β = 0.20, γ = 0.05, δ = 0.05  (średnio feedbacku)

IF total_feedback_count >= THRESHOLD_HIGH:
    α = 0.60, β = 0.30, γ = 0.05, δ = 0.05  (dużo feedbacku → ufaj feedbackowi)
```

To zapobiega sytuacji, gdzie mała liczba głosów (np. 1-2 negatywne) drastycznie psuje ranking.

## 5. Wymagania funkcjonalne

### 5.1. Hybrid Reranking w zapytaniu wektorowym

- **FR-01:** Zapytanie `findSimilarWithScore` zostaje zastąpione nową wersją uwzględniającą `quality_score`, `last_used_at` i `accepted` w rankingu.
- **FR-02:** Nowy wynik zapytania zawiera dodatkowe pola: `quality_score`, `last_used_at`, `accepted` (już są, ale nie zwracane w projekcji).
- **FR-03:** `SimilarityResultProjection` zostaje rozszerzony o pola: `qualityScore: Double`, `lastUsedAt: Instant?`.
- **FR-04:** Hybrid score jest obliczany **po stronie SQL** (PostgreSQL) w celu wydajności — unikamy transferu wszystkich wpisów do aplikacji.

### 5.2. Zmodyfikowane zapytanie SQL

Nowe zapytanie (z uwzględnieniem quality_score):
```sql
SELECT id, content, answer, source, accepted, quality_score, last_used_at,
       1 - (embedding <=> CAST(:embedding AS vector)) AS semantic_score,
       (
           :alpha * (1 - (embedding <=> CAST(:embedding AS vector)))
         + :beta * quality_score
         + :gamma * (1.0 / (1.0 + EXTRACT(EPOCH FROM (NOW() - COALESCE(last_used_at, created_at))) / 86400.0 / :decayDays))
         + :delta * CASE WHEN accepted THEN 1.0 ELSE 0.3 END
       ) AS final_score
FROM knowledge_entries
WHERE tenant_id = :tenantId
  AND answer IS NOT NULL
  AND (project_id IS NULL OR project_id IN (:projectIds))
  AND 1 - (embedding <=> CAST(:embedding AS vector)) >= :minScore
ORDER BY final_score DESC
LIMIT :limit
```

**Uwaga:** `minScore` odnosi się nadal do `semantic_score` (nie `final_score`), aby nie odrzucać wpisów o niskim cosine distance, ale wysokiej jakości.

### 5.3. Aktualizacja `FeedbackService`

- **FR-05:** Po każdej aktualizacji `quality_score` wywoływana jest **re-embedding** wpisu, jeśli quality_score spadł poniżej progu (np. 0.3) — oznacza to, że embedding jest słaby i warto go wygenerować ponownie z uwzględnieniem kontekstu feedbacku.
- **FR-06:** Przy ponownym generowaniu embeddingu, LLM generuje ulepszone podsumowanie wpisu (nieco inne podejście niż oryginalny embedding — patrz sekcja 5.5).

### 5.4. Pętla zwrotna na poziomie wpisu

- **FR-07:** Wpisy z `quality_score < 0.3` (prog konfigurowalny) są oznaczane jako `accepted = false` — wykluczone z bezpośredniej odpowiedzi z bazy wiedzy (KNOWLEDGE_BASE), ale nadal dostępne jako kontekst dla AI_WITH_CONTEXT.
- **FR-08:** Wpisy z `quality_score > 0.8` i `usage_count > 3` są kandydatami do **promocji** — ich odpowiedź jest zapisywana jako `accepted = true` (nawet jeśli była AI_GENERATED).
- **FR-09:** Mechanizm **archiwizacji** — wpisy z `quality_score < 0.2` przez dłuższy czas (np. 30 dni) mogą być oznaczane jako `archived = true` (nowe pole w `knowledge_entries`), co wyklucza je z wyników wyszukiwania, ale zachowuje w historii.

### 5.5. LLM-powered Embedding Improvement (zaawansowane)

- **FR-10:** Dla wpisów z wysokim `quality_score` (> 0.8) i dużą liczbą głosów (≥ 5), system wywołuje LLM w celu wygenerowania **ulepszonego podsumowania** (rephrased summary), które lepiej odzwierciedla wiedzę z perspektywy użytkowników.
- **FR-11:** Ulepszone podsumowanie jest re-embedowane i zapisywane jako główny content wpisu (oryginał w `metadata.original_content`).
- **FR-12:** Proces re-embeddingu jest realizowany asynchronicznie przez outbox pattern.

### 5.6. Agregacja feedbacku per projekt

- **FR-13:** Nowy serwis `FeedbackAnalyticsService` agreguje statystyki feedbacku per projekt:
  - Średni quality_score wpisów z projektu
  - Rozkład głosów (pozytywne vs negatywne)
  - Najczęściej używane wpisy (usage_count)
  - Najgorzej oceniane wpisy (quality_score)
- **FR-14:** Statystyki są dostępne przez nowy endpoint REST `GET /api/v1/projects/{projectId}/feedback-analytics`.
- **FR-15:** Raport jest również logowany okresowo (np. codziennie) w celu monitorowania jakości bazy wiedzy.

### 5.7. Feedback na poziomie źródła odpowiedzi

- **FR-16:** Feedback jest korelowany ze źródłem odpowiedzi (`ResponseSource`):
  - `KNOWLEDGE_BASE` — feedback wpływa bezpośrednio na quality_score powiązanych wpisów
  - `AI_WITH_CONTEXT` — feedback wpływa na quality_score użytych wpisów ORAZ na nowy wpis AI-generated
  - `AI_GENERAL` — feedback wpływa tylko na nowy wpis AI-generated
- **FR-17:** Nowy typ outbox eventu: `FEEDBACK_QUALITY_UPDATE` — asynchroniczna aktualizacja quality_score i ewentualny re-embedding.

## 6. Wymagania niefunkcjonalne

### 6.1. Wydajność
- **NFR-01:** Hybrid reranking nie powinien dodawać więcej niż 5ms do zapytania wektorowego (obliczenia po stronie SQL).
- **NFR-02:** Re-embedding wpisu (FR-05, FR-10) odbywa się asynchronicznie — nie blokuje odpowiedzi na pytanie.
- **NFR-03:** Adaptacyjne wagi są obliczane raz na żądanie (nie cache'owane), ale zapytanie o liczbę feedbacków jest proste i szybkie.

### 6.2. Spójność
- **NFR-04:** Aktualizacja `quality_score` jest atomowa — głosy nie mogą być utracone.
- **NFR-05:** Re-embedding jest idempotenny — ponowne wywołanie dla tego samego wpisu daje ten sam wynik.

### 6.3. Obserwowalność
- **NFR-06:** Każdy hybrid scoring jest logowany na poziomie DEBUG (input weights, output final_score).
- **NFR-07:** Zmiana `quality_score` jest logowana na poziomie INFO z poprzednią i nową wartością.
- **NFR-08:** Proces re-embeddingu jest logowany z identyfikatorem wpisu i powodem (threshold breach / high quality promotion).

### 6.4. Kompatybilność wsteczna
- **NFR-09:** Istniejące zapytania (jeśli są wywoływane bezpośrednio przez inne serwisy) nadal działają — hybrid scoring jest opcjonalny i włączany przez flagę `rag.hybrid-scoring.enabled=true`.
- **NFR-10:** Jeśli flaga jest wyłączona, system działa tak jak dotychczas (pure cosine distance).

## 7. Modele danych

### 7.1. Rozszerzenie `SimilarityResultProjection`

```kotlin
data class SimilarityResultProjection(
    val id: UUID,
    val content: String,
    val answer: String?,
    val source: String,
    val accepted: Boolean,
    val semanticScore: Double,     // 1 - cosine distance
    val qualityScore: Double,      // z feedbacku (Bayesowski)
    val finalScore: Double,        // hybrid score
    val lastUsedAt: Instant?,      // ostatnie użycie
)
```

### 7.2. Nowe pola w `knowledge_entries`

```sql
ALTER TABLE knowledge_entries ADD COLUMN archived BOOLEAN DEFAULT FALSE;
ALTER TABLE knowledge_entries ADD COLUMN archived_at TIMESTAMP NULL;
ALTER TABLE knowledge_entries ADD COLUMN reembedded BOOLEAN DEFAULT FALSE;
ALTER TABLE knowledge_entries ADD COLUMN reembedded_at TIMESTAMP NULL;
```

### 7.3. Nowa tabela `feedback_analytics` (opcjonalna, materialized view)

```sql
CREATE MATERIALIZED VIEW feedback_analytics AS
SELECT
    ke.project_id,
    ke.tenant_id,
    COUNT(DISTINCT ke.id) AS total_entries,
    AVG(ke.quality_score) AS avg_quality_score,
    SUM(ke.usage_count) AS total_usage,
    COUNT(DISTINCT fm.id) AS total_feedbacks,
    SUM(CASE WHEN fm.votes->>'positive' IS NOT NULL THEN 1 ELSE 0 END) AS positive_votes,
    SUM(CASE WHEN fm.votes->>'negative' IS NOT NULL THEN 1 ELSE 0 END) AS negative_votes
FROM knowledge_entries ke
LEFT JOIN feedback_mappings fm ON fm.entry_ids @> ARRAY[ke.id::text]
GROUP BY ke.project_id, ke.tenant_id;
```

## 8. Architektura i nowe komponenty

### 8.1. Nowe klasy w warstwie domenowej

| Klasa | Lokalizacja | Opis |
|-------|-------------|------|
| `HybridScoringConfig` | `domain/model/` | Konfiguracja wag hybrid scoringu (α, β, γ, δ) + progi adaptacyjne |
| `FeedbackAnalyticsService` | `domain/service/` | Agregacja statystyk feedbacku per projekt |
| `FeedbackAnalyticsResult` | `domain/model/` | Wynik agregacji (avg quality, vote distribution, top entries) |
| `EntryPromotionService` | `domain/service/` | Promocja/archiwizacja wpisów na podstawie quality_score |

### 8.2. Nowe klasy w warstwie infrastrukturalnej

| Klasa | Lokalizacja | Opis |
|-------|-------------|------|
| `HybridRerankingQueryRewriter` | `infrastructure/database/knowledge/` | Buduje SQL z hybrid scoringiem na podstawie konfiguracji wag |
| `EntryReembeddingHandler` | `infrastructure/outbox/handler/` | Handler outbox event `ENTRY_REEMBEDDING` — re-embedding wpisu |
| `FeedbackAnalyticsMapper` | `infrastructure/database/` | Mapowanie wyników agregacji na model domenowy |

### 8.3. Zmiany w istniejących klasach

| Klasa | Zmiana |
|-------|--------|
| `KnowledgeService` | Użycie hybrid scoringu w `answerQuestion()` — parametry wag przekazywane z konfiguracji |
| `FeedbackService` | Po aktualizacji quality_score — sprawdzenie progów i ewentualne触发 re-embeddingu lub promocji |
| `SpringDataKnowledgeRepository` | Nowe zapytanie `findSimilarWithHybridScore()` z parametrami wag |
| `KnowledgeRepository` | Nowa metoda interfejsu |
| `Prompts` | Nowy prompt `EntryRephrasing` do generowania ulepszonych podsumowań |
| `OutboxEventHandler` | Nowy typ eventu `ENTRY_REEMBEDDING` |

## 9. Prompt do re-embedingu (ulepszanie podsumowania)

```
SYSTEM:
Jesteś ekspertem ds. inżynierii budowlanej. Twoim zadaniem jest przeformułowanie opisu wiedzy 
z bazy wiedzy tak, aby był bardziej precyzyjny i użyteczny dla przyszłych pytań.

Oryginalna treść: "{originalContent}"
Podsumowanie: "{currentSummary}"
Liczba pozytywnych głosów: {positiveCount}
Liczba negatywnych głosów: {negativeCount}

Przeformułuj treść, zachowując wszystkie istotne informacje techniczne, 
ale czyniąc ją bardziej zwięzłą i jednoznaczną. Usuń niejasności.

USER:
Przeformułuj poniższą wiedzę:
{originalContent}

Odpowiedz TYLKO nową treścią wiedzy (bez komentarzy):
```

## 10. Konfiguracja (application.yaml)

```yaml
vedra:
  rag:
    hybrid-scoring:
      enabled: true
      weights:
        semantic: 0.65
        quality: 0.25
        freshness: 0.05
        acceptance: 0.05
      freshness-decay-days: 90
      adaptive:
        enabled: true
        thresholds:
          low: 10
          high: 50
        weights-low:
          semantic: 0.85
          quality: 0.10
          freshness: 0.03
          acceptance: 0.02
    quality:
      auto-archive-threshold: 0.2
      auto-archive-days: 30
      auto-promote-threshold: 0.8
      auto-promote-min-votes: 5
      reembed-threshold: 0.3
```

## 11. Przepływy główne

### 11.1. Przepływ odpowiedzi na pytanie (zmodyfikowany)

```
1. Slack message event → MessageEventHandler → OutboxService
2. SlackMessageAnswerHandler → KnowledgeService.answerQuestion()
3. KnowledgeService:
   a. Generuj embedding pytania (EmbeddingPort)
   b. Wywołaj findSimilarWithHybridScore() zamiast findSimilarWithScore()
      - Wagi pobierane z HybridScoringConfig
      - Zapytanie SQL oblicza final_score
   c. Ranking wyników wg final_score (nie semantic_score!)
   d. Zastosuj progi HIGH/MEDIUM/MIN confidence na final_score
   e. Zwróć AnswerResult z entryIds
4. Wyślij odpowiedź w wątku Slack z feedback buttons
5. Zapisz FeedbackMapping
```

### 11.2. Przepływ obsługi feedbacku (zmodyfikowany)

```
1. Użytkownik klika thumbs up/down w Slack
2. SlackInteractiveHandler → FeedbackService.applyVote()
3. FeedbackService:
   a. Zapisz głos w FeedbackMapping
   b. Aktualizuj quality_score (Bayesowski wzór)
   c. Sprawdź progi:
      - IF quality_score < reembed-threshold → trigger ENTRY_REEMBEDDING (outbox)
      - IF quality_score > promote-threshold AND usage_count >= min-votes → 
        oznacz accepted = true (jeśli był AI_GENERATED)
      - IF quality_score < archive-threshold przez > archive-days → 
        oznacz archived = true
   d. Loguj zmianę quality_score
4. OutboxProcessor → EntryReembeddingHandler (async)
5. EntryReembeddingHandler:
   a. Wywołaj LLM z promptem EntryRephrasing
   b. Generuj nowy embedding z ulepszonego podsumowania
   c. Aktualizuj knowledge_entry (content, embedding, reembedded = true)
```

### 11.3. Przepływ agregacji feedbacku

```
1. Cron (codziennie) LUB żądanie REST
2. FeedbackAnalyticsService.getAnalytics(projectId):
   a. Policz wpisy per projekt
   b. Policz głosy per projekt
   c. Oblicz avg quality_score
   d. Znajdź top/bottom wpisy wg quality_score i usage_count
3. Zwróć FeedbackAnalyticsResult
4. Opcjonalnie: odśwież materialized view
```

## 12. Testy

### 12.1. Testy jednostkowe
- `HybridScoringConfigTest` — obliczanie adaptacyjnych wag
- `EntryPromotionServiceTest` — logika promocji/archiwizacji
- `FeedbackAnalyticsServiceTest` — agregacja (z mockowanym repozytorium)
- `FeedbackServiceTest` — rozszerzenie o progi re-embedingu

### 12.2. Testy integracyjne
- `HybridRerankingQueryTest` — zapytanie SQL z hybrid scoringiem (test z prawdziwą PG + pgvector)
- `ReembeddingIntegrationTest` — pełny cykl re-embedingu (LLM + embedding + zapis)

### 12.3. Testy E2E
- Wysłanie pytania → sprawdzenie że wpisy o wysokim quality_score pojawiają się wyżej
- Oddanie negatywnego głosu → sprawdzenie że quality_score maleje
- Wiele negatywnych głosów → sprawdzenie że wpis jest archiwizowany

## 13. Kolejność wdrożenia

1. Rozszerzenie `SimilarityResultProjection` o nowe pola
2. Nowe zapytanie SQL `findSimilarWithHybridScore()` w `SpringDataKnowledgeRepository`
3. `HybridScoringConfig` + konfiguracja w `application.yaml`
4. Aktualizacja `KnowledgeService.answerQuestion()` o hybrid scoring
5. Aktualizacja `FeedbackService` o progi re-embedingu/promocji/archiwizacji
6. Nowy outbox event `ENTRY_REEMBEDDING` + `EntryReembeddingHandler`
7. Prompt `EntryRephrasing` w `Prompts.kt`
8. `FeedbackAnalyticsService` + endpoint REST
9. Migracje SQL (nowe pola w `knowledge_entries`)
10. Testy jednostkowe
11. Testy integracyjne
12. Testy E2E

## 14. Ograniczenia i założenia

- System działa z lokalnymi modelami Ollama — jakość re-embedingu zależy od modelu llama3.2
- Hybrid scoring jest opcjonalny — może być wyłączony przez flagę konfiguracyjną
- Adaptacyjne wagi wymagają minimum 10 feedbacków, aby zacząć działać optymalnie
- Re-embedding jest kosztowny (LLM call) — dlatego triggered jest tylko przy spełnieniu progów
- Materialized view wymaga odświeżenia (cron lub trigger)
- Wagi α, β, γ, δ powinny być dostrajane w zależności od dominującego języka wpisów (polski vs angielski) — to zadanie na przyszłość
- Przy małej liczbie wpisów (< 100) hybrid scoring może dawać gorsze wyniki niż pure vector — dlatego adaptacyjne wagi
