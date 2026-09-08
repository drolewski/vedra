# Specyfikacja: Pętla feedbacku RAG – uczenie się z odpowiedzi ekspertów

## 1. Cel

Zbudowanie zamkniętej pętli feedbacku, w której:
1. Pytanie @Vedra → odpowiedź z RAG + pytanie o trafność
2. Odpowiedź nie trafna → zachęta do wywołania eksperta w wątku
3. Ekspert odpowiada → kontekst jest analizowany, normalizowany przez LLM i zapisywany przyrostowo do RAG
4. Kolejne pytania bazują na ulepszonej wiedzy

## 2. Kontekst

### Istniejąca architektura
- **KnowledgeService.answerQuestion()** – 3-tierowa strategia odpowiedzi (bezpośredni hit → AI z kontekstem → AI ogólne)
- **FeedbackMapping + FeedbackService** – głosowanie +/- na odpowiedzi, aktualizacja `quality_score` w `knowledge_entries`
- **Outbox pattern** – asynchroniczne przetwarzanie eventów
- **SlackMessageService** – wysyłanie wiadomości z przyciskami feedbacku

### Istniejący przepływ (odpowiedź na pytania)
```
@Vedra question → MessageEventHandler → OutboxService → SlackMessageAnswerHandler
→ KnowledgeService.answerQuestion() → SlackMessageService.sendThreadMessageWithFeedback()
→ FeedbackMapping (Pomogło/Nie pomogło)
```

### Problem
Obecnie feedback jest jednorazowy – kliknięcie "Nie pomogło" aktualizuje `quality_score`, ale nie inicjuje procesu uczenia się z korekty eksperta.

## 3. Wymagania funkcjonalne

### 3.1. Wywołanie Vedry
- **FR-01:** Wiadomość zawierająca `@Vedra` lub rozpoczynająca się od `/vedra question` jest traktowana jako pytanie do systemu RAG.
- **FR-02:** Wykrycie wywołania odbywa się w `MessageEventHandler` na podstawie:
  - obecności wzmianki `<@BOT_USER_ID>` w treści wiadomości, LUB
  - prefiksu `/vedra question` (case-insensitive)
- **FR-03:** Po wykryciu wywołania system generuje odpowiedź z RAG (istniejący flow `SLACK_MESSAGE_ANSWER`).
- **FR-04:** Odpowiedź jest wysyłana w wątku z przyciskami feedbacku (Pomogło/Nie pomogło).

### 3.2. Pytanie o trafność
- **FR-05:** Po każdej odpowiedzi generated przez system, wiadomość zawiera:
  - wygenerowaną odpowiedź
  - przyciski "Pomogło" / "Nie pomogło"
  - komunikat informujący, że kliknięcie "Nie pomogło" uruchomi proces konsultacji z ekspertem
- **FR-06:** Komunikat towarzyszący jest generowany dynamicznie przez LLM w języku naturalnym, np.:
  - "Czy ta odpowiedź była pomocna? Kliknij 'Nie pomogło', aby wezwać eksperta do wątku."

### 3.3. Obsługa negatywnego feedbacku
- **FR-07:** Kliknięcie "Nie pomogło" przez użytkownika:
  - a) aktualizuje `quality_score` powiązanych `knowledge_entries` (istniejący flow)
  - b) **uruchamia nowy flow** – zachęcenie osoby pytającej o wywołanie eksperta
- **FR-08:** System wysyła w wątku wiadomość zachęcającą:
  ```
  Ta odpowiedź okazała się nie trafna. Aby uzyskać pomoc, wpisz w wątku:
  /vedra expert @ekspert-handle
  ```
- **FR-09:** Wiadomość zachęcająca jest wysyłana jako reply do oryginalnego pytania (w wątku).
- **FR-10:** Wiadomość zawiera tagowanie eksperta (jeśli handle jest znany) lub ogólną zachętę.

### 3.4. Wywołanie eksperta
- **FR-11:** Wiadomość w wątku zaczynająca się od `/vedra expert` jest traktowana jako oznaczenie eksperta.
- **FR-12:** System rozpoznaje:
  - `@handle` eksperta w treści (extrakcja user ID z mention)
  - pytanie pierwotne (pierwsza wiadomość w wątku)
- **FR-13:** System tworzy `ExpertConsultation` (nowa encja) z:
  - `threadTs` – ID wątku
  - `channelId` – ID kanału
  - `tenantId` – ID tenanta
  - `projectId` – ID projektu
  - `expertUserId` – ID oznaczonego eksperta
  - `questionContent` – treść oryginalnego pytania
  - `status` – `PENDING_EXPERT_ANSWER`
  - `createdAt` – timestamp

### 3.5. Zbieranie odpowiedzi ekspertów
- **FR-14:** Każda wiadomość w wątku, w którym istnieje aktywna `ExpertConsultation` (status `PENDING_EXPERT_ANSWER`), jest traktowana jako potencjalna odpowiedź eksperta.
- **FR-15:** System identyfikuje odpowiedź eksperta na podstawie:
  - autor wiadomości = `expertUserId` z `ExpertConsultation`, LUB
  - wiadomość zawiera merytoryczną treść (ocena LLM – min. 50 znaków, brak powitań/reakcji)
- **FR-16:** Po zidentyfikowaniu odpowiedzi eksperta:
  - status `ExpertConsultation` zmienia się na `EXPERT_ANSWER_RECEIVED`
  - treść odpowiedzi jest zapisywana w `ExpertConsultation.expertAnswer`
  - inicjowany jest process normalizacji i zapisu do RAG

### 3.6. Normalizacja odpowiedzi eksperta
- **FR-17:** Odpowiedź eksperta jest przetwarzana przez LLM w celu:
  - a) wyodrębnienia istotnej wiedzy (usunięcie grzeczności, kontekstu czatowego)
  - b) sformatowania w ustandaryzowany format (pytanie + odpowiedź)
  - c) walidacji kompletności (czy odpowiedź zawiera wystarczająco informacji)
- **FR-18:** Prompt normalizujący przyjmuje:
  - treść pytania pierwotnego
  - treść odpowiedzi eksperta
  - opis projektu i branżę
- **FR-19:** LLM zwraca strukturalną odpowiedź:
  ```json
  {
    "normalizedQuestion": "Znormalizowane pytanie (max 500 znaków)",
    "normalizedAnswer": "Znormalizowana odpowiedź (max 2000 znaków)",
    "summary": "Krótkie podsumowanie wiedzy (max 300 znaków)",
    "qualityScore": 0.85,
    "tags": ["tag1", "tag2"],
    "completeness": "COMPLETE | PARTIAL | INSUFFICIENT"
  }
  ```
- **FR-20:** Jeśli `completeness == "INSUFFICIENT"` – system wysyła w wątku prośbę o doprecyzowanie: "Czy możesz doprecyzować swoją odpowiedź? Potrzebuję więcej informacji, aby zapisać to w bazie wiedzy."

### 3.7. Zapis przyrostowy do RAG
- **FR-21:** Po normalizacji system tworzy nowy `KnowledgeEntry`:
  - `source` = `EXPERT_ANSWER`
  - `content` = `normalizedQuestion`
  - `answer` = `normalizedAnswer`
  - `answerSource` = `USER_VERIFIED`
  - `embedding` = wektor z `normalizedQuestion`
  - `qualityScore` = wartość z normalizacji (min. 0.8)
  - `accepted` = `true` (odpowiedź eksperta jest zawsze akceptowana)
  - `metadata` = JSONB:
    - `expert_user_id` – ID eksperta
    - `original_question` – oryginalne pytanie
    - `original_answer` – oryginalna odpowiedź
    - `thread_ts` – ID wątku
    - `consultation_id` – ID konsultacji
    - `normalization_quality` – jakość normalizacji
- **FR-22:** Embedding jest generowany z `normalizedQuestion` (nie z odpowiedzi).
- **FR-23:** Istniejące `knowledge_entries` z tej samej domeny (podobny embedding, ten sam projekt) nie są usuwane – nowa wiedza jest dodawana jako kolejna wersja.

### 3.8. Aktualizacja kontekstu wątku
- **FR-24:** Po zapisie odpowiedzi eksperta do RAG, system wysyła w wątku potwierdzenie:
  ```
  Dziękuję za odpowiedź! Zapisałem wiedzę w bazie projektu.
  Od teraz będę mógł odpowiadać na podobne pytania.
  ```
- **FR-25:** Status `ExpertConsultation` zmienia się na `COMPLETED`.

### 3.9. Budowanie kontekstu z kolejnych odpowiedzi
- **FR-26:** Jeśli w wątku pojawiają się kolejne wiadomości od eksperta (po `COMPLETED`):
  - są traktowane jako uzupełnienie kontekstu
  - normalizacja uwzględnia poprzednie odpowiedzi z wątku jako kontekst
  - nowy `KnowledgeEntry` jest tworzony z odniesieniem do poprzedniego (pole `parentEntryId` w metadata)
- **FR-27:** System buduje "drzewo wiedzy" – powiązania między wpisami z tego samego wątku (pole `metadata.thread_entries: [entryId1, entryId2, ...]`).

### 3.10. Zamknięcie wątku
- **FR-28:** System może zamknąć wątek (oznaczyć jako rozwiązany) po:
  - otrzymaniu odpowiedzi eksperta o `completeness == "COMPLETE"`
  - upływie 48h od ostatniej aktywności (opcjonalne, konfigurowalne)
- **FR-29:** Zamknięcie wątku jest opcjonalne i zależne od konfiguracji projektu.

## 4. Wymagania niefunkcjonalne

### 4.1. Wydajność
- **NFR-01:** Normalizacja odpowiedzi eksperta nie powinna trwać dłużej niż 15 sekund.
- **NFR-02:** Generowanie embeddingu nie powinno trwać dłużej niż 5 sekund.
- **NFR-03:** Cały proces (od otrzymania odpowiedzi eksperta do zapisu w RAG) nie powinien trwać dłużej niż 30 sekund.

### 4.2. Niezawodność
- **NFR-04:** Błędy normalizacji nie blokują wątku – odpowiedź eksperta jest zapisywana w `ExpertConsultation` niezależnie od wyniku normalizacji.
- **NFR-05:** Ponowna próba normalizacji jest możliwa (retry max 3 razy).
- **NFR-06:** Dane eksperta (userId) są walidowane – only users with EXPERT role (lub oznaczeni w systemie) mogą inicjować konsultacje.

### 4.3. Izolacja danych
- **NFR-07:** Wpisy wiedzy z `EXPERT_ANSWER` są izolowane per `tenant_id` i `project_id`.
- **NFR-08:** Konsultacje ekspertów są powiązane z konkretnym tenantem i projektem.

### 4.4. Monitorowanie
- **NFR-09:** Logowanie: liczba konsultacji, czas normalizacji, jakość normalizacji (completeness), liczba wpisów RAG z `EXPERT_ANSWER`.
- **NFR-10:** Dashboard metryk: % odpowiedzi kompletnych, % pytań wymagających eksperta, średnia jakość wpisów RAG.

## 5. Modele danych

### 5.1. Nowa tabela `expert_consultations`

```sql
CREATE TABLE expert_consultations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    project_id UUID NOT NULL REFERENCES tenant_projects(id),
    channel_id VARCHAR(20) NOT NULL,
    thread_ts VARCHAR(20) NOT NULL,
    expert_user_id VARCHAR(20) NOT NULL,
    question_content TEXT NOT NULL,
    expert_answer TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_EXPERT_ANSWER',
    -- Statusy: PENDING_EXPERT_ANSWER, EXPERT_ANSWER_RECEIVED, NORMALIZING, COMPLETED, FAILED
    normalized_question TEXT,
    normalized_answer TEXT,
    normalization_quality JSONB,
    knowledge_entry_id UUID REFERENCES knowledge_entries(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMP
);

CREATE INDEX idx_expert_consultations_thread ON expert_consultations(channel_id, thread_ts);
CREATE INDEX idx_expert_consultations_status ON expert_consultations(status);
CREATE INDEX idx_expert_consultations_tenant ON expert_consultations(tenant_id, project_id);
```

### 5.2. Rozszerzenie `knowledge_entries` – nowe wartości `source`

```
EXPERT_ANSWER  // normalizowana odpowiedź eksperta zapisana w RAG
```

### 5.3. Rozszerzenie `knowledge_entries` – nowe pola w metadata

```json
{
  "expert_user_id": "U0123ABC",
  "original_question": "Oryginalne pytanie użytkownika",
  "original_answer": "Oryginalna odpowiedź eksperta",
  "thread_ts": "1234567890.123456",
  "consultation_id": "uuid",
  "normalization_quality": "COMPLETE | PARTIAL | INSUFFICIENT",
  "parent_entry_id": "uuid|null",
  "thread_entries": ["entryId1", "entryId2"]
}
```

### 5.4. Struktura payload outbox event

**Nowy typ eventu: `EXPERT_CONSULTATION_START`**
```json
{
  "tenantId": "uuid",
  "projectId": "uuid",
  "channelId": "C0123ABC",
  "threadTs": "1234567890.123456",
  "expertUserId": "U0123ABC",
  "questionContent": "Treść pierwotnego pytania...",
  "eventType": "EXPERT_CONSULTATION_START"
}
```

**Nowy typ eventu: `EXPERT_ANSWER_NORMALIZATION`**
```json
{
  "consultationId": "uuid",
  "tenantId": "uuid",
  "projectId": "uuid",
  "channelId": "C0123ABC",
  "threadTs": "1234567890.123456",
  "expertAnswer": "Treść odpowiedzi eksperta...",
  "questionContent": "Oryginalne pytanie...",
  "eventType": "EXPERT_ANSWER_NORMALIZATION"
}
```

## 6. Architektura i nowe komponenty

### 6.1. Nowe klasy w warstwie domenowej

| Klasa | Lokalizacja | Opis |
|-------|-------------|------|
| `ExpertConsultation` | `domain/model/` | Model konsultacji z ekspertem |
| `ExpertConsultationStatus` | `domain/model/` | Enum statusów konsultacji |
| `NormalizedExpertAnswer` | `domain/model/` | Model wyniku normalizacji odpowiedzi eksperta |
| `ExpertConsultationService` | `domain/service/` | Serwis zarządzający konsultacjami |
| `ExpertAnswerNormalizer` | `domain/port/` | Port do normalizacji odpowiedzi eksperta |
| `ExpertConsultationRepository` | `domain/port/` | Repozytorium konsultacji |

### 6.2. Nowe klasy w warstwie infrastrukturalnej

| Klasa | Lokalizacja | Opis |
|-------|-------------|------|
| `ExpertAnswerNormalizerAdapter` | `infrastructure/chat/` | Implementacja `ExpertAnswerNormalizer` – wywołanie LLM |
| `SlackExpertConsultationHandler` | `infrastructure/slack/service/outbox/` | Handler outbox event `EXPERT_CONSULTATION_START` |
| `SlackExpertNormalizationHandler` | `infrastructure/slack/service/outbox/` | Handler outbox event `EXPERT_ANSWER_NORMALIZATION` |
| `ExpertConsultationMapper` | `infrastructure/database/knowledge/` | Mapowanie domain model → JPA entity |
| `ExpertInviteBlockBuilder` | `infrastructure/slack/formatter/` | Budowanie wiadomości zachęcającej do wywołania eksperta |

### 6.3. Zmiany w istniejących klasach

| Klasa | Zmiana |
|-------|--------|
| `MessageEventHandler` | Dodanie rozpoznawania `/vedra expert @handle` i kierowania do outbox `EXPERT_CONSULTATION_START` |
| `OutboxEventHandler` | Dodanie obsługi nowych typów eventów |
| `SlackMessageAnswerHandler` | Po wysłaniu odpowiedzi z `qualityScore < 0.65` (nie pewna) – dodanie wiadomości zachęcającej do wywołania eksperta |
| `SlackMessageService` | Nowa metoda: `sendExpertInviteMessage(channel, threadTs, expertHandle)` |
| `FeedbackService` | Po negatywnym feedbacku – inicjalizacja `ExpertConsultation` (status `PENDING_EXPERT_ANSWER`) |
| `Prompts` | Dodanie nowych promptów: `NormalizeExpertAnswer`, `ExpertInviteMessage` |
| `KnowledgeEntry` | Dodanie pola `parentEntryId` (opcjonalne, dla powiązań wątkowych) |

## 7. Prompt do normalizacji odpowiedzi eksperta

```
SYSTEM:
Jesteś asystentem normalizującym odpowiedzi ekspertów w systemie wiedzy projektowej.
Twoim zadaniem jest wyodrębnienie istotnej wiedzy z odpowiedzi eksperta i przygotowanie
jej do zapisu w bazie wiedzy (RAG).

Projekt: "{projectName}" (branża: {industry})

Zasady normalizacji:
- Usuń grzeczności, powitania, reakcje emocjonalne
- Zachowaj merytoryczną treść odpowiedzi
- Sformatuj jako pytanie → odpowiedź
- Oceń kompletność: COMPLETE (wystarczająco info), PARTIAL (częściowo), INSUFFICIENT (za mało)
- Określ jakość normalizedAnswer (0.0-1.0)

USER:
Pytanie użytkownika: "{questionContent}"
Odpowiedź eksperta: "{expertAnswer}"

Odpowiedz w formacie JSON:
{
  "normalizedQuestion": "Znormalizowane pytanie (max 500 znaków)",
  "normalizedAnswer": "Znormalizowana odpowiedź (max 2000 znaków)",
  "summary": "Krótkie podsumowanie wiedzy (max 300 znaków)",
  "qualityScore": 0.85,
  "tags": ["tag1", "tag2"],
  "completeness": "COMPLETE | PARTIAL | INSUFFICIENT"
}
```

## 8. Prompt do generowania wiadomości zachęcającej

```
SYSTEM:
Jesteś asystentem Slacka projektu "{projectName}". Wygeneruj krótką, uprzejmą wiadomość
zachęcającą użytkownika do wywołania eksperta w wątku, ponieważ poprzednia odpowiedź
nie była trafna.

Styl: profesjonalny, ale ciepły. Max 3 zdania.
Język: polski.

USER:
Użytkownik {userId} zapytał: "{question}"
System odpowiedział, ale odpowiedź nie była trafna.
Wygeneruj wiadomość zachęcającą do wywołania eksperta.

Odpowiedz samą wiadomością (bez JSON).
```

## 9. Przepływy główne

### 9.1. Pytanie @Vedra → odpowiedź z RAG + feedback

```
1. Użytkownik pisze: "@Vedra jak skonfigurować build?"
2. MessageEventHandler wykrywa @Vedra → outbox SLACK_MESSAGE_ANSWER
3. SlackMessageAnswerHandler → KnowledgeService.answerQuestion()
4. Wysłanie odpowiedzi z przyciskami feedbacku
5. Komunikat: "Czy ta odpowiedź była pomocna? Kliknij 'Nie pomogło', aby wezwać eksperta."
```

### 9.2. Negatywny feedback → zachęta do eksperta

```
1. Użytkownik klika "Nie pomogło"
2. SlackInteractiveRequestHandler → outbox SLACK_FEEDBACK
3. SlackFeedbackHandler → FeedbackService.applyVote() (aktualizacja quality_score)
4. FeedbackService wykrywa negatywny feedback → tworzy ExpertConsultation(PENDING_EXPERT_ANSWER)
5. Wysłanie wiadomości zachęcającej w wątku:
   "Ta odpowiedź okazała się nie trafna. Aby uzyskać pomoc, wpisz:
    /vedra expert @jan-kowalski"
```

### 9.3. Wywołanie eksperta

```
1. Użytkownik pisze: "/vedra expert @jan-kowalski"
2. MessageEventHandler wykrywa /vedra expert → outbox EXPERT_CONSULTATION_START
3. SlackExpertConsultationHandler:
   a. Aktualizuje ExpertConsultation (expertUserId, status)
   b. Wysyla wiadomość do eksperta: "@jan-kowalski, użytkownik potrzebuje pomocy w wątku [link]"
```

### 9.4. Odpowiedź eksperta → normalizacja → zapis do RAG

```
1. Ekspert odpowiada w wątku: "Aby skonfigurować build, należy..."
2. MessageEventHandler → outbox SLACK_MESSAGE_ANSWER (normalny flow)
3. System wykrywa, że wątek ma aktywną ExpertConsultation
4. Identyfikacja autora jako eksperta
5. outbox EXPERT_ANSWER_NORMALIZATION
6. SlackExpertNormalizationHandler:
   a. ExpertAnswerNormalizerAdapter → LLM normalizacja
   b. Tworzenie KnowledgeEntry(source=EXPERT_ANSWER, accepted=true)
   c. Generowanie embeddingu
   d. Zapis w bazie
   e. Aktualizacja statusu ExpertConsultation → COMPLETED
   f. Wysłanie potwierdzenia w wątku:
      "Dziękuję za odpowiedź! Zapisałem wiedzę w bazie projektu."
```

### 9.5. Kolejne odpowiedzi w wątku (budowanie kontekstu)

```
1. Ekspert dodaje uzupełnienie: "Jeszcze jedno - ważne jest też..."
2. Identyfikacja jako kontekst (ExpertConsultation.status == COMPLETED)
3. Normalizacja z uwzględnieniem poprzednich odpowiedzi z wątku
4. Tworzenie nowego KnowledgeEntry z parentEntryId
5. Aktualizacja metadata.thread_entries
6. Potwierdzenie: "Zaktualizowałem wiedzę o dodatkowe informacje."
```

## 10. Schemat migracji bazy danych

```sql
-- Nowa tabela
CREATE TABLE expert_consultations (... jak w sekcji 5.1)

-- Rozszerzenie knowledge_entries
-- Dodanie nowej wartości enum 'EXPERT_ANSWER' do source (jeśli enum)
-- Dodanie pola parentEntryId (opcjonalne)

-- Indeksy
CREATE INDEX idx_expert_consultations_thread ON expert_consultations(channel_id, thread_ts);
CREATE INDEX idx_expert_consultations_status ON expert_consultations(status);
```

## 11. Testy

### 11.1. Testy jednostkowe
- `ExpertConsultationServiceTest` – zarządzanie konsultacjami
- `ExpertAnswerNormalizerAdapterTest` – formatowanie promptu, parsowanie odpowiedzi LLM
- `SlackExpertConsultationHandlerTest` – obsługa eventów outbox
- `SlackExpertNormalizationHandlerTest` – normalizacja + zapis
- `FeedbackServiceExpertTest` – tworzenie konsultacji po negatywnym feedbacku

### 11.2. Testy integracyjne
- `ExpertConsultationFlowTest` – end-to-end: pytanie → feedback → ekspert → normalizacja → RAG
- `KnowledgeEntryCreationFromExpertTest` – zapis i odczyt wpisu z `EXPERT_ANSWER`

### 11.3. Testy E2E
- Pytanie @Vedra → nie trafna odpowiedź → wywołanie eksperta → odpowiedź eksperta → sprawdzenie wpisu w RAG
- Kolejne pytanie po zapisie odpowiedzi eksperta → sprawdzenie czy korzysta z ulepszonej wiedzy
- Budowanie kontekstu z kilku odpowiedzi eksperta w wątku

## 12. Kolejność wdrożenia

1. Migracja bazy danych (nowa tabela `expert_consultations`)
2. Modele domenowe (`ExpertConsultation`, `NormalizedExpertAnswer`)
3. Porty (`ExpertConsultationRepository`, `ExpertAnswerNormalizer`)
4. Serwis domenowy `ExpertConsultationService`
5. Prompt `NormalizeExpertAnswer` + `ExpertInviteMessage`
6. Implementacja adaptera `ExpertAnswerNormalizerAdapter`
7. Handler `SlackExpertConsultationHandler` (outbox)
8. Handler `SlackExpertNormalizationHandler` (outbox)
9. Modyfikacja `FeedbackService` – tworzenie konsultacji
10. Modyfikacja `SlackMessageAnswerHandler` – wiadomość zachęcająca
11. Modyfikacja `MessageEventHandler` – rozpoznawanie `/vedra expert`
12. Testy jednostkowe
13. Testy integracyjne
14. Testy E2E

## 13. Ograniczenia i założenia

- System zakłada, że ekspert odpowiada w wątku (nie w oddzielnej wiadomości)
- Identyfikacja eksperta odbywa się na podstawie userId (nie roli w Slack)
- Normalizacja zależy od jakości modelu LLM (lokalny Ollama llama3.2)
- Nie jest obsługiwane jednoczesne wywoływanie wielu ekspertów (jeden ekspert na konsultację)
- System nie zamyka wątków automatycznie (opcjonalne w przyszłości)
- Embeddingi dla `EXPERT_ANSWER` są generowane z `normalizedQuestion` (pytania, nie odpowiedzi)
