# Specyfikacja: Normalizacja danych i optymalizacja kosztów LLM

## 1. Cel

Wprowadzenie normalizacji danych wejściowych i wyjściowych oraz minimalizacji zapytań do LLM poprzez cache'owanie wyników, deduplikację pytań i wielokrotne wykorzystanie wygenerowanych odpowiedzi. Celem jest obniżenie kosztów operacyjnych przy jednoczesnym zachowaniu jakości odpowiedzi.

## 2. Kontekst

### Problem
- **Brak normalizacji wejścia** – wiadomości Slack przechodzą do LLM/embeddingów bez czyszczenia (emoji, formatowanie Slack, zbędne białe znaki, markup)
- **Brak cache'owania LLM** – każde pytanie generuje nowe wywołanie LLM, nawet jeśli identyczne lub bardzo podobne pytanie było już zadane
- **Brak normalizacji wyjścia** – odpowiedzi LLM nie są standaryzowane przed zapisem do bazy wiedzy
- **Niewykorzystane AI answers** – wygenerowane odpowiedzi AI są zapisywane z `accepted=false`, ale nigdy nie są powtórnie wykorzystywane
- **Brak deduplikacji pytań** – podobne pytania (parafrazy) generują niezależne wywołania LLM

### Istniejące komponenty
| Komponent | Rola | Braki |
|-----------|------|-------|
| `KnowledgeService` | Orchestracja Q&A, wywołania LLM | Brak cache, brak normalizacji wejścia |
| `OllamaLLMAdapter` | Wywołanie Ollama LLM | Bezpośrednie wywołanie bez cache |
| `OllamaEmbeddingAdapter` | Generowanie embeddingów | Brak cache wektorów |
| `KnowledgeMapper` | Mapowanie domain ↔ entity | Pomija normalizację treści |
| `AnswerBlockBuilder` | Formatowanie odpowiedzi Slack | Częściowa normalizacja (bold, bullets) |
| `OutboxService` | Deduplikacja eventów | Tylko na poziomie aggregateId |
| `FeedbackService` | Jakość odpowiedzi | Quality score obliczany, ale niewykorzystany |

## 3. Wymagania funkcjonalne

### 3.1. Normalizacja danych wejściowych

#### 3.1.1. Czyszczenie tekstu Slack
- **FR-01:** Przed przetworzeniem wiadomość Slack jest czyszczona z:
  - formatowania Slack mrkdwn (`*bold*`, `_italic_`, `~strike~`, `` `code` ``, ``` ```code block``` ```)
  - wzmianek Slack (`<@U0123ABC>` → `@User`, `<#C0123ABC|channel-name>` → `#channel-name`)
  - emoji Slack (`:white_check_mark:` → `✓`, `:thumbsup:` → `👍` lub usunięcie)
  - nadmiarowych białych znaków (wielokrotne spacje, puste linie)
  - tagów URL (`<https://example.com|example.com>` → `https://example.com`)
- **FR-02:** Czyszczenie jest realizowane przez dedykowany `SlackMessageNormalizer` w warstwie infrastruktury
- **FR-03:** Oryginalna treść wiadomości jest zawsze przechowywana w metadanych (`original_content`) dla celów audytu

#### 3.1.2. Normalizacja pytań
- **FR-04:** Pytania użytkowników przed embeddingiem/LLM są normalizowane:
  - usunięcie znaków interpunkcyjnych na końcu (pytanie bez `?` jest traktowane tak samo jak z `?`)
  - usunięcie wzmianek na bota (`@Vedra`, `@vedra`)
  - trim + normalizacja białych znaków
  - konwersja do małych liter TYLKO dla celów cache'owania (nie dla oryginalnej treści)
- **FR-05:** Normalizacja pytań jest realizowana przez `QuestionNormalizer` w warstwie domenowej

#### 3.1.3. Normalizacja odpowiedzi LLM
- **FR-06:** Odpowiedzi LLM przed zapisem do bazy wiedzy są normalizowane:
  - usunięcie fraz开场owych (np. "Odpowiadając na pytanie...", "Na podstawie dostarczonej wiedzy...")
  - usunięcie fraz końcowych (np. "Mam nadzieję, że pomogłem...", "W razie pytań...")
  - ekstrakcja głównej treści merytorycznej
  - standaryzacja formatu (usunięcie nadmiarowych nagłówków, list)
- **FR-07:** Normalizacja odpowiedzi jest realizowana przez `AnswerNormalizer` w warstwie domenowej

### 3.2. Cache'owanie wyników LLM

#### 3.2.1. Cache odpowiedzi LLM (question → answer)
- **FR-08:** System przechowuje znormalizowane pytania wraz z wygenerowanymi odpowiedziami w dedykowanej tabeli `llm_response_cache`
- **FR-09:** Klucz cache'owania jest generowany z znormalizowanego pytania (hash SHA-256) w połączeniu z `project_id` i `tenant_id`
- **FR-10:** Przed wywołaniem LLM system sprawdza, czy w cache istnieje odpowiedź dla danego pytania w kontekście tego projektu
- **FR-11:** Trafienie w cache zwraca odpowiedź bezpośrednio BEZ wywołania LLM
- **FR-12:** Cache ma TTL (Time To Live) konfigurowalny domyślnie 30 dni
- **FR-13:** Cache jest aktualizowany przy każdej nowej odpowiedzi LLM
- **FR-14:** Cache jestömürany przy zmianie modelu LLM (pole `model_version`)

#### 3.2.2. Cache embeddingów (text → vector)
- **FR-15:** Wygenerowane embeddingi są przechowywane w dedykowanej tabeli `embedding_cache`
- **FR-16:** Klucz cache'owania to hash SHA-256 znormalizowanego tekstu
- **FR-17:** Przed generowaniem nowego embeddingu system sprawdza cache
- **FR-18:** Cache embeddingów ma TTL 90 dni (embeddingi są stabilniejsze niż odpowiedzi LLM)

#### 3.2.3. Cache kontekstu (project_id + question_hash → context_entries)
- **FR-19:** Wyniki wyszukiwania w wektorowej bazie wiedzy (podobne wpisy) są cache'owane dla często powtarzanych pytań
- **FR-20:** Klucz cache'owania to kombinacja `project_id` + hash znormalizowanego pytania
- **FR-21:** Cache kontekstu ma TTL 7 dni

### 3.3. Deduplikacja i reuse odpowiedzi

#### 3.3.1. Wykorzystanie istniejących AI answers
- **FR-22:** Przed wywołaniem LLM w ścieżce `answerWithContext()` system sprawdza, czy w `knowledge_entries` istnieje wpis z `answerSource=AI_GENERATED` o wysokim `qualityScore` (≥ 0.7) dla tego samego projektu i podobnego pytania
- **FR-23:** Podobność pytań jest określana przez:
  - dokładne dopasowanie znormalizowanego pytania (hash), LUB
  - cosine similarity embeddingów ≥ 0.95 (pytania niemalże identyczne)
- **FR-24:** Jeśli znaleziono pasujący wpis AI, odpowiedź jest zwracana BEZ nowego wywołania LLM
- **FR-25:** Wpis jest oznaczany jako `reused=true` w celach statystycznych

#### 3.3.2. Wykrywanie duplikatów pytań
- **FR-26:** System wykrywa pytania o identycznej lub bardzo podobnej treści (cosine similarity ≥ 0.98) i kieruje je do cache zamiast generować nową odpowiedź
- **FR-27:** Statystyki duplikatów są logowane na poziomie DEBUG

### 3.4. Normalizacja danych wyjściowych

#### 3.4.1. Standaryzacja formatu odpowiedzi
- **FR-28:** Odpowiedzi LLM przed wysłaniem do Slack/REST API są normalizowane:
  - konwersja Markdown → Slack mrkdwn (istniejący `AnswerBlockBuilder`)
  - usunięcie pustych sekcji
  - standaryzacja list (wędkowe → punktowane)
  - maximum długości odpowiedzi (konfigurowalne, domyślnie 3000 znaków dla Slack)

#### 3.4.2. Strukturyzacja metadanych odpowiedzi
- **FR-29:** Każda odpowiedź (z cache lub nowo wygenerowana) zawiera ustandaryzowane metadane:
  - `responseSource: ResponseSource` – skąd pochodzi odpowiedź
  - `confidence: Double` – poziom pewności (0.0–1.0)
  - `normalizedQuestion: String` – znormalizowane pytanie (dla celów diagnostycznych)
  - `modelVersion: String` – wersja modelu LLM użytego do wygenerowania
  - `cached: Boolean` – czy odpowiedź pochodzi z cache
  - `reused: Boolean` – czy odpowiedź jest ponownie wykorzystana z bazy wiedzy

### 3.5. Zarządzanie cache

#### 3.5.1. Invalidation cache
- **FR-30:** Cache jest invalidated (kasowany) gdy:
  - zmienia się treść wpisu w `knowledge_entries` (dla cache kontekstu)
  - zmienia się model LLM (dla cache odpowiedzi i embeddingów)
  - upływa TTL
- **FR-31:** Ręczna invalidacja jest możliwa przez endpoint admina

#### 3.5.2. Statystyki cache
- **FR-32:** System zbiera statystyki cache'owania:
  - `cache_hits: Long` – trafienia w cache
  - `cache_misses: Long` – pudła cache
  - `cache_hit_ratio: Double` – stosunek trafień
  - `llm_calls_saved: Long` – oszczędzone wywołania LLM
  - `tokens_saved: Long` – oszczędzone tokeny
- **FR-33:** Statystyki są dostępne przez endpoint `GET /api/v1/admin/cache/stats`

## 4. Wymagania niefunkcjonalne

### 4.1. Wydajność
- **NFR-01:** Cache lookup nie powinien trwać dłużej niż 50ms ( indexed hash lookup )
- **NFR-02:** Normalizacja tekstu nie powinna dodawać więcej niż 10ms do przetworzenia wiadomości
- **NFR-03:** Cache jest zasilane z bazy PostgreSQL (brak zewnętrznych serwisów cache)

### 4.2. Niezawodność
- **NFR-04:** Błąd cache'owania NIE blokuje przetwarzania – w przypadku błędu cache system generuje odpowiedź normalną ścieżką
- **NFR-05:** Cache jest asynchronicznie zapisywany (fire-and-forget) aby nie spowalniać ścieżki krytycznej
- **NFR-06:** Usunięcie cache'owanego wpisu nie powoduje utraty danych – oryginalne odpowiedzi pozostają w `knowledge_entries`

### 4.3. Spójność
- **NFR-07:** Cache odpowiedzi LLM jest eventualnie spójny z `knowledge_entries` – maksymalne opóźnienie synchronizacji: 5 minut
- **NFR-08:** Przy zmianie modelu LLM całość cache odpowiedzi jest oznaczana jako nieaktualna (ale nie kasowana fizycznie)

### 4.4. Priorytet odpowiedzi
- **NFR-09:** Hierarchia źródeł odpowiedzi (od najwyższego do najniższego priorytetu):
  1. `answerFromKnowledgeBase()` – wpis zweryfikowany przez użytkownika (`accepted=true, answerSource=USER_VERIFIED`)
  2. Cache odpowiedzi LLM (dla tego samego projektu i znormalizowanego pytania)
  3. `answerWithContext()` – LLM z kontekstem z bazy wiedzy
  4. `answerWithoutContext()` – LLM bez kontekstu (ogólna wiedza)

## 5. Modele danych

### 5.1. Nowe tabele

#### `llm_response_cache`
```sql
CREATE TABLE llm_response_cache (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    project_id UUID REFERENCES tenant_projects(id),
    question_hash VARCHAR(64) NOT NULL,        -- SHA-256 znormalizowanego pytania
    normalized_question TEXT NOT NULL,          -- znormalizowane pytanie (czytelne)
    original_question TEXT NOT NULL,            -- oryginalne pytanie
    answer TEXT NOT NULL,                       -- wygenerowana odpowiedź
    normalized_answer TEXT,                     -- znormalizowana odpowiedź
    model_version VARCHAR(100) NOT NULL,        -- wersja modelu (np. "llama3.2:latest")
    confidence DOUBLE PRECISION,                -- pewność odpowiedzi
    response_source VARCHAR(50) NOT NULL,       -- AI_WITH_CONTEXT / AI_GENERAL
    context_entry_ids JSONB,                    -- ID wpisów użytych jako kontekst
    usage_count INTEGER DEFAULT 0,             -- ile razy odpowiedź została wykorzystana
    expires_at TIMESTAMP NOT NULL,             -- data wygaśnięcia cache
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_llm_cache_lookup ON llm_response_cache(tenant_id, project_id, question_hash);
CREATE INDEX idx_llm_cache_expires ON llm_response_cache(expires_at);
```

#### `embedding_cache`
```sql
CREATE TABLE embedding_cache (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    text_hash VARCHAR(64) NOT NULL UNIQUE,     -- SHA-256 znormalizowanego tekstu
    normalized_text TEXT NOT NULL,              -- znormalizowany tekst
    embedding vector(768) NOT NULL,             -- wektor embedding
    model_version VARCHAR(100) NOT NULL,        -- wersja modelu embedding
    expires_at TIMESTAMP NOT NULL,             -- data wygaśnięcia
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_embedding_cache_hash ON embedding_cache(text_hash);
CREATE INDEX idx_embedding_cache_expires ON embedding_cache(expires_at);
```

### 5.2. Zmiany w istniejących tabelach

#### `knowledge_entries` – nowe pola
```sql
ALTER TABLE knowledge_entries
    ADD COLUMN normalized_content TEXT,          -- znormalizowana treść
    ADD COLUMN content_hash VARCHAR(64),        -- hash treści (dla deduplikacji)
    ADD COLUMN reused_count INTEGER DEFAULT 0;  -- ile razy wpis został ponownie wykorzystany
```

### 5.3. Struktury domain model

```kotlin
// Cache entry for LLM responses
data class LlmResponseCacheEntry(
    val id: UUID,
    val tenantId: UUID,
    val projectId: UUID?,
    val questionHash: String,
    val normalizedQuestion: String,
    val originalQuestion: String,
    val answer: String,
    val normalizedAnswer: String?,
    val modelVersion: String,
    val confidence: Double?,
    val responseSource: ResponseSource,
    val contextEntryIds: List<UUID>,
    val usageCount: Int,
    val expiresAt: Instant,
    val createdAt: Instant,
    val updatedAt: Instant
)

// Cache entry for embeddings
data class EmbeddingCacheEntry(
    val id: UUID,
    val textHash: String,
    val normalizedText: String,
    val embedding: PGvector,
    val modelVersion: String,
    val expiresAt: Instant,
    val createdAt: Instant
)

// Normalized question for cache key generation
data class NormalizedQuestion(
    val original: String,
    val normalized: String,
    val hash: String
)

// Normalized answer
data class NormalizedAnswer(
    val original: String,
    val normalized: String
)

// Cache statistics
data class CacheStats(
    val cacheHits: Long,
    val cacheMisses: Long,
    val cacheHitRatio: Double,
    val llmCallsSaved: Long,
    val tokensSaved: Long
)
```

## 6. Architektura i nowe komponenty

### 6.1. Nowe klasy w warstwie domenowej

| Klasa | Lokalizacja | Opis |
|-------|-------------|------|
| `QuestionNormalizer` | `domain/service/` | Normalizacja pytań: czyszczenie, hash, standaryzacja |
| `AnswerNormalizer` | `domain/service/` | Normalizacja odpowiedzi LLM: usunięcie fraz开场owych/końcowych |
| `LlmCacheService` | `domain/service/` | Orchestration cache'owania: lookup, save, invalidation |
| `EmbeddingCacheService` | `domain/service/` | Cache'owanie embeddingów |
| `LlmResponseCacheEntry` | `domain/model/` | Model cache entry dla odpowiedzi LLM |
| `EmbeddingCacheEntry` | `domain/model/` | Model cache entry dla embeddingów |
| `NormalizedQuestion` | `domain/model/` | Model znormalizowanego pytania |
| `NormalizedAnswer` | `domain/model/` | Model znormalizowanej odpowiedzi |
| `CacheStats` | `domain/model/` | Statystyki cache'owania |
| `LlmResponseCachePort` | `domain/port/` | Port do cache'owania odpowiedzi LLM |
| `EmbeddingCachePort` | `domain/port/` | Port do cache'owania embeddingów |

### 6.2. Nowe klasy w warstwie infrastrukturalnej

| Klasa | Lokalizacja | Opis |
|-------|-------------|------|
| `SlackMessageNormalizer` | `infrastructure/slack/` | Czyszczenie wiadomości Slack z markupu |
| `JpaLlmResponseCacheRepository` | `infrastructure/database/cache/` | Implementacja cache'owania w PostgreSQL |
| `JpaEmbeddingCacheRepository` | `infrastructure/database/cache/` | Implementacja cache'owania embeddingów |
| `LlmResponseCacheMapper` | `infrastructure/database/cache/` | Mapowanie domain ↔ entity dla cache |
| `EmbeddingCacheMapper` | `infrastructure/database/cache/` | Mapowanie domain ↔ entity dla cache embeddingów |
| `CacheStatsCollector` | `infrastructure/cache/` | Zbieranie i raportowanie statystyk cache |

### 6.3. Zmiany w istniejących klasach

| Klasa | Zmiana |
|-------|--------|
| `KnowledgeService` | Dodanie lookup cache przed wywołaniem LLM. Dodanie zapisu do cache po wygenerowaniu odpowiedzi. Dodanie sprawdzania istniejących AI answers przed nowym wywołaniem. |
| `OllamaLLMAdapter` | Dodanie cache'owania wyników (opcjonalnie przez port). |
| `OllamaEmbeddingAdapter` | Dodanie cache'owania embeddingów przed generowaniem. |
| `KnowledgeMapper` | Dodanie mapowania `normalized_content` i `content_hash`. |
| `MessageIngestionService` | Dodanie normalizacji treści przed zapisem i generowaniem embeddingu. |
| `KnowledgeRepository` | Dodanie metody findByContentHash dla deduplikacji. |
| `AnswerBlockBuilder` | Rozszerzenie normalizacji odpowiedzi Slack. |

## 7. Algorytmy normalizacji

### 7.1. Czyszczenie wiadomości Slack (`SlackMessageNormalizer`)

```kotlin
class SlackMessageNormalizer {

    fun normalize(rawMessage: String): String {
        var result = rawMessage
        result = removeSlackFormatting(result)    // *bold*, _italic_, ~strike~, `code`
        result = resolveMentions(result)           // <@U0123> → @User
        result = resolveChannels(result)           // <#C0123|name> → #name
        result = resolveLinks(result)              // <https://url|text> → url
        result = resolveEmoji(result)              // :emoji_name: → unicode
        result = normalizeWhitespace(result)       // wielokrotne spacje → pojedyncza
        return result.trim()
    }
}
```

### 7.2. Normalizacja pytań (`QuestionNormalizer`)

```kotlin
class QuestionNormalizer {

    fun normalize(question: String, projectId: UUID): NormalizedQuestion {
        var result = question
        result = removeBotMentions(result)         // @Vedra, @vedra
        result = removeTrailingPunctuation(result) // końcowe ? ! .
        result = normalizeWhitespace(result)
        val lowercased = result.lowercase()
        val hash = sha256(lowercased + projectId.toString())
        return NormalizedQuestion(
            original = question,
            normalized = lowercased,
            hash = hash
        )
    }
}
```

### 7.3. Normalizacja odpowiedzi LLM (`AnswerNormalizer`)

```kotlin
class AnswerNormalizer {

    private val openingPhrases = listOf(
        "Odpowiadając na pytanie",
        "Na podstawie dostarczonej wiedzy",
        "W odpowiedzi na",
        "Zgodnie z dostępną wiedzą",
        "Mając na uwadze",
        "Analizując dostarczony kontekst"
    )

    private val closingPhrases = listOf(
        "Mam nadzieję, że pomogłem",
        "W razie pytań",
        "Jeśli potrzebujesz więcej informacji",
        "Pozdrawiam",
        "Życzę powodzenia"
    )

    fun normalize(llmResponse: String): NormalizedAnswer {
        var result = llmResponse.trim()
        result = removeOpeningPhrases(result)
        result = removeClosingPhrases(result)
        result = normalizeWhitespace(result)
        return NormalizedAnswer(
            original = llmResponse,
            normalized = result
        )
    }
}
```

## 8. Przepływy główne

### 8.1. Przepływ Q&A z cache'owaniem (zmodyfikowany `answerQuestion`)

```
1. Otrzymanie pytania (z Slack/REST)
2. QuestionNormalizer.normalize(question, projectId)
   → NormalizedQuestion(original, normalized, hash)
3. LlmCacheService.lookup(tenantId, projectId, questionHash)
   a. Trafienie w cache → zwróć odpowiedź z cache
      - Inkrementuj usage_count
      - Loguj: "Cache hit for question"
      - Zakończ (BEZ wywołania LLM)
   b. Pudło cache → kontynuuj normalną ścieżkę
4. [Istniejący flow: embedding → similarity search → LLM]
5. Po wygenerowaniu odpowiedzi:
   a. AnswerNormalizer.normalize(llmResponse)
   b. LlmCacheService.save(cacheEntry)
   c. KnowledgeService zapisz wpis w knowledge_entries
6. Zwróć odpowiedź
```

### 8.2. Przepływ embeddingu z cache'owaniem

```
1. Otrzymanie tekstu do zembedowania
2. Znormalizuj tekst (trim, lowercase)
3. Oblicz hash SHA-256 znormalizowanego tekstu
4. EmbeddingCacheService.lookup(textHash)
   a. Trafienie → zwróć cached vector
   b. Pudło → OllamaEmbeddingAdapter.embed(text)
      - Zapisz w embedding_cache
      - Zwróć nowy wektor
```

### 8.3. Przepływ deduplikacji pytań

```
1. Otrzymanie nowego pytania
2. QuestionNormalizer.normalize(question, projectId)
3. Sprawdź knowledge_entries:
   WHERE content_hash = ?normalizedHash
     AND project_id = ?
     AND accepted = true
   a. Znaleziono → użyj istniejącej odpowiedzi (reuse)
   b. Nie znaleziono → kontynuuj normalny flow
4. Sprawdź llm_response_cache:
   WHERE question_hash = ?hash
     AND project_id = ?
     AND expires_at > NOW()
   a. Trafienie → użyj cache
   b. Pudło → wywołaj LLM
```

### 8.4. Hierarchia odpowiedzi (priorytet)

```
1. knowledge_entries WHERE accepted=true AND answerSource=USER_VERIFIED
   →命中 = najlepsza odpowiedź, zero kosztów LLM
2. llm_response_cache WHERE question_hash=? AND project_id=?
   →命中 = odpowiedź z cache, zero kosztów LLM
3. knowledge_entries WHERE answerSource=AI_GENERATED AND qualityScore >= 0.7
   →命中 = reuse istniejącej odpowiedzi AI
4. answerWithContext() → nowe wywołanie LLM z kontekstem
5. answerWithoutContext() → nowe wywołanie LLM bez kontekstu
```

## 9. Schemat migracji bazy danych

```sql
-- ============================================
-- Migracja: Normalizacja i cache'owanie LLM
-- ============================================

-- 1. Tabela cache odpowiedzi LLM
CREATE TABLE llm_response_cache (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    project_id UUID REFERENCES tenant_projects(id),
    question_hash VARCHAR(64) NOT NULL,
    normalized_question TEXT NOT NULL,
    original_question TEXT NOT NULL,
    answer TEXT NOT NULL,
    normalized_answer TEXT,
    model_version VARCHAR(100) NOT NULL,
    confidence DOUBLE PRECISION,
    response_source VARCHAR(50) NOT NULL,
    context_entry_ids JSONB DEFAULT '[]'::jsonb,
    usage_count INTEGER DEFAULT 0,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_llm_cache_lookup ON llm_response_cache(tenant_id, project_id, question_hash);
CREATE INDEX idx_llm_cache_expires ON llm_response_cache(expires_at);
CREATE INDEX idx_llm_cache_tenant ON llm_response_cache(tenant_id);

-- 2. Tabela cache embeddingów
CREATE TABLE embedding_cache (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    text_hash VARCHAR(64) NOT NULL UNIQUE,
    normalized_text TEXT NOT NULL,
    embedding vector(768) NOT NULL,
    model_version VARCHAR(100) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_embedding_cache_hash ON embedding_cache(text_hash);
CREATE INDEX idx_embedding_cache_expires ON embedding_cache(expires_at);

-- 3. Dodatkowe pola w knowledge_entries
ALTER TABLE knowledge_entries
    ADD COLUMN normalized_content TEXT,
    ADD COLUMN content_hash VARCHAR(64),
    ADD COLUMN reused_count INTEGER DEFAULT 0;

CREATE INDEX idx_knowledge_content_hash ON knowledge_entries(content_hash);
CREATE INDEX idx_knowledge_tenant_project ON knowledge_entries(tenant_id, project_id);

-- 4. Czyszczenie wygasłego cache (uruchamiane cyklicznie)
-- SELECT cleanup_expired_cache(); -- funkcja do utworzenia
```

## 10. Testy

### 10.1. Testy jednostkowe
- `SlackMessageNormalizerTest` – czyszczenie formatowania Slack
- `QuestionNormalizerTest` – normalizacja pytań, generowanie hashy
- `AnswerNormalizerTest` – usuwanie fraz开场owych/końcowych
- `LlmCacheServiceTest` – lookup, save, invalidation cache
- `EmbeddingCacheServiceTest` – cache'owanie embeddingów
- `KnowledgeServiceCacheIntegrationTest` – integracja cache z Q&A flow

### 10.2. Testy integracyjne
- `CacheHitIntegrationTest` – end-to-end: pytanie → cache miss → LLM → cache save → drugie pytanie → cache hit
- `CacheInvalidationTest` – invalidacja przy zmianie modelu
- `DeduplicationIntegrationTest` – wykrywanie duplikatów pytań
- `NormalizationPipelineTest` – pełen pipeline normalizacji

### 10.3. Testy E2E
- Wysłanie identycznego pytania dwukrotnie → druga odpowiedź z cache (szybsza)
- Wysłanie parafrazy pytania → sprawdzenie czy cache trafia
- Zmiana modelu LLM → cache jest invalidowany
- Wyczerpanie TTL cache → nowe wywołanie LLM

## 11. Kolejność wdrożenia

1. Utworzenie migracji bazy danych (tabele cache)
2. Implementacja `SlackMessageNormalizer` (czyszczenie wiadomości Slack)
3. Implementacja `QuestionNormalizer` (normalizacja pytań)
4. Implementacja `AnswerNormalizer` (normalizacja odpowiedzi LLM)
5. Implementacja `LlmResponseCacheEntry` + `EmbeddingCacheEntry` (model)
6. Implementacja portów cache (`LlmResponseCachePort`, `EmbeddingCachePort`)
7. Implementacja repository cache (JPA adapters)
8. Implementacja `LlmCacheService` + `EmbeddingCacheService`
9. Integracja z `KnowledgeService` (dodanie cache lookup/save)
10. Integracja z `OllamaEmbeddingAdapter` (cache embeddingów)
11. Dodanie statystyk cache (`CacheStatsCollector`)
12. Dodanie endpointu admina do invalidacji cache
13. Testy jednostkowe
14. Testy integracyjne
15. Testy E2E

## 12. Ograniczenia i założenia

- Cache jest przechowywany w PostgreSQL (brak Redis/Memcached) – wystarczające przy małym obciążeniu
- Cache odpowiedzi LLM jest eventualnie spójny – przy zmianie wiedzy w `knowledge_entries` cache może przez krótki czas zwracać nieaktualne odpowiedzi
- TTL jest konfigurowalny per projekt (domyślnie 30 dni dla odpowiedzi, 90 dni dla embeddingów)
- Normalizacja jest języko-zależna (polskie frazy开场owe/końcowe) – wymaga rozszerzenia dla innych języków
- Hash pytania uwzględnia projectId – to samo pytanie w różnych projektach ma różne cache entries
- Hierarchia odpowiedzi faworyzuje zweryfikowane przez użytkownika wpisy nad AI-generated
- Statystyki cache są aktualizowane asynchronicznie (brak wpływu na ścieżkę krytyczną)
