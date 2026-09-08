# Specyfikacja: Zbieranie wiadomości z kanałów do bazy wiedzy RAG

## 1. Cel

Każda wiadomość tekstowa napisana na kanale Slack, do którego jest przypisany projekt w Vedra, powinna zostać przeanalizowana pod kątem przydatności dla tego projektu i – jeśli okaże się przydatna – zapisana w wektorowej bazie wiedzy (RAG) jako przyszłe źródło odpowiedzi.

## 2. Kontekst

### Istniejąca architektura
- **MessageEventHandler** – obecnie obsługuje tylko wiadomości w wątkach (`threadTs != null`) i traktuje je jako pytania do RAG
- **knowledge_entries** – tabela z wektorami (vector(768)), już przechowuje wpisy wiedzy z polem `embedding`, `quality_score`, `accepted`
- **Outbox pattern** – asynchroniczne przetwarzanie zdarzeń przez `OutboxProcessor`
- **EmbeddingPort + LLMPort** – porty do generowania embeddingów (Ollama nomic-embed-text) i wywołań LLM (Ollama llama3.2)

### Istniejący przepływ (odpowiedź na pytania)
```
Slack message event → MessageEventHandler → OutboxService → SlackMessageAnswerHandler → KnowledgeService.answerQuestion()
```

## 3. Wymagania funkcjonalne

### 3.1. Przechwytywanie wiadomości
- **FR-01:** Każda wiadomość tekstowa (nie-bot, nie-system) napisana na kanale posiadającym powiązany projekt (`ChannelProject`) jest rejestrowana przez system.
- **FR-02:** Wiadomości z wątków (odpowiedzi) również są rejestrowane, o ile kanał macierzysty ma przypisany projekt.
- **FR-03:** Wiadomości botowe, wiadomości systemowe (np. zmiana nazwy kanału) i wiadomości bez treści tekstowej (tylko obrazki, pliki) są pomijane.
- **FR-04:** Identyfikator Slack message (`ts`) służy jako klucz deduplikacji – ta sama wiadomość nie jest przetwarzana dwukrotnie.

### 3.2. Ocena przydatności przez LLM
- **FR-05:** Po zarejestrowaniu wiadomości system wywołuje LLM w celu oceny, czy wiadomość zawiera wiedzę przydatną dla projektu, do którego jest przypisany kanał.
- **FR-06:** Ocena przydatności jest realizowana przez dedykowany prompt, który na wejściu otrzymuje:
  - treść wiadomości
  - opis projektu (pole `description` z `TenantProject`)
  - branżę projektu (pole `industry` z `TenantProject`)
- **FR-07:** LLM zwraca strukturalną ocenę zawierającą:
  - `useful: Boolean` – czy wiadomość jest przydatna
  - `summary: String` – opcjonalne zwięzłe podsumowanie wiedzy zawartej w wiadomości (max 500 znaków)
  - `reason: String` – uzasadnienie decyzji
- **FR-08:** Wiadomości nieprzydatne (np. „cześć", „ok", „dzięki", luźne rozmowy) – NIE są zapisywane do bazy wiedzy.

### 3.3. Zapis do bazy wektorowej
- **FR-09:** Wiadomości ocenione jako przydatne są zapisywane w tabeli `knowledge_entries` z następującymi polami:
  - `tenant_id` – identyfikator tenanta (z kontekstu Slack team_id)
  - `project_id` – identyfikator projektu powiązanego z kanałem
  - `source` – stała wartość `CHANNEL_MESSAGE`
  - `content` – treść wiadomości (lub podsumowanie z LLM, jeśli wygenerowane)
  - `embedding` – wektor 768-wymiarowy wygenerowany z treści wiadomości
  - `metadata` – JSONB zawierający:
    - `slack_message_ts` – ID wiadomości Slack
    - `slack_channel_id` – ID kanału
    - `slack_user_id` – ID autora wiadomości
    - `thread_ts` – ID wątku (jeśli wiadomość jest odpowiedzią)
    - `usefulness_reason` – uzasadnienie LLM
    - `original_content` – oryginalna treść (jeśli zapisano podsumowanie)
  - `quality_score` – wartość początkowa na podstawie oceny LLM (np. 0.7 dla przydatnych)
  - `accepted` – `true` (zaakceptowane przez ocenę LLM)
  - `usage_count` – 0 (domyślnie)

### 3.4. Generowanie embeddingów
- **FR-10:** Embedding jest generowany z treści wiadomości (lub podsumowania, jeśli LLM wygenerował krótszą wersję) przy użyciu istniejącego `EmbeddingPort` (Ollama nomic-embed-text).
- **FR-11:** Embedding jest generowany tylko dla wiadomości ocenionych jako przydatne – nie generuje się wektorów dla odrzuconych wiadomości.

### 3.5. Architektura przepływu
- **FR-12:** Nowy przepływ działania (ingestion) jest realizowany asynchronicznie poprzez istniejący outbox pattern, aby nie blokować obsługi eventów Slack.
- **FR-13:** Nowy typ eventu outbox: `CHANNEL_MESSAGE_INGESTION` – odrębny od `SLACK_MESSAGE_ANSWER` (odpowiedzi na pytania).
- **FR-14:** W `MessageEventHandler` wiadomości z kanałów z przypisanym projektem kierowane są do nowego pipeline'u ingestion, niezależnie od tego, czy są w wątku, czy nie.
- **FR-15:** Istniejący przepływ odpowiedzi na pytania (`SLACK_MESSAGE_ANSWER`) zostaje wywołany tylko wtedy, gdy wiadomość jest oznaczona jako pytanie (np. kończy się znakiem zapytania lub zawiera wzmiankę @Vedra).

## 4. Wymagania niefunkcjonalne

### 4.1. Wydajność
- **NFR-01:** Przetworzenie jednej wiadomości (ocena LLM + embedding + zapis) nie powinno trwać dłużej niż 10 sekund w warunkach lokalnych.
- **NFR-02:** System obsługuje jednoczesne przetwarzanie minimum 5 wiadomości (outbox processor parallelism).

### 4.2. Niezawodność
- **NFR-03:** Deduplikacja zapobiega wielokrotnemu zapisowi tej samej wiadomości (na poziomie `slack_message_ts` w metadanych).
- **NFR-04:** Błędy w ocenie LLM lub generowaniu embeddingów nie blokują dalszego przetwarzania – wiadomość jest oznaczana jako `FAILED` w outbox z możliwością ponownej próby (max 3 retenty).
- **NFR-05:** Wiadomości o statusie `FAILED` po wyczerpaniu prób są logowane, ale nie powodują utraty danych – oryginalna wiadomość pozostaje w outbox do przeglądu.

### 4.3. Izolacja danych
- **NFR-06:** Wpisy wiedzy są izolowane per `tenant_id` – żaden tenant nie ma dostępu do wpisów innego tenanta.
- **NFR-07:** Wpisy są dodatkowo izolowane per `project_id` w obrębie tenanta.

### 4.4. Monitorowanie
- **NFR-08:** Statystyki przetwarzania (liczba wiadomości ocenionych jako przydatne/nieprzydatne, błędy) są logowane z poziomu INFO.
- **NFR-09:** Możliwość filtrowania `knowledge_entries` po `source = CHANNEL_MESSAGE` w celu przeglądu zebranych danych.

## 5. Modele danych

### 5.1. Nowe wartości w istniejących tabelach

**knowledge_entries – nowe wartości `source`:**
```
CHANNEL_MESSAGE  // wiadomość z kanału Slack oceniona jako przydatna
```

**outbox – nowy typ eventu:**
```
CHANNEL_MESSAGE_INGESTION  // event ingestion wiadomości z kanału
```

### 5.2. Struktura payload outbox event

```json
{
  "tenantId": "uuid",
  "projectId": "uuid",
  "channelId": "C0123ABC",
  "messageTs": "1234567890.123456",
  "userId": "U0123ABC",
  "threadTs": "1234567890.123456|null",
  "content": "Treść wiadomości...",
  "messageType": "text",
  "eventType": "CHANNEL_MESSAGE_INGESTION"
}
```

## 6. Architektura i nowe komponenty

### 6.1. Nowe klasy w warstwie domenowej

| Klasa | Lokalizacja | Opis |
|-------|-------------|------|
| `MessageIngestionService` | `domain/service/` | Serwis orchestrujący ingestion: ocena LLM → generowanie embeddingu → zapis |
| `MessageUsefulnessResult` | `domain/model/` | Model wyniku oceny przydatności (useful, summary, reason) |
| `MessageUsefulnessPort` | `domain/port/` (nowy pakiet `port/`) | Port do oceny przydatności wiadomości przez LLM |
| `MessageIngestionPort` | `domain/port/` | Port do zapisu przetworzonych wiadomości (lub bezpośrednio KnowledgeRepository) |

### 6.2. Nowe klasy w warstwie infrastrukturalnej

| Klasa | Lokalizacja | Opis |
|-------|-------------|------|
| `MessageUsefulnessEvaluator` | `infrastructure/chat/` | Implementacja `MessageUsefulnessPort` – wywołanie LLM z dedykowanym promptem |
| `SlackMessageIngestionHandler` | `infrastructure/slack/service/outbox/` | Handler outbox event `CHANNEL_MESSAGE_INGESTION` |
| `MessageIngestionMapper` | `infrastructure/database/knowledge/` | Mapowanie domain model → JPA entity dla knowledge_entries |

### 6.3. Zmiany w istniejących klasach

| Klasa | Zmiana |
|-------|--------|
| `MessageEventHandler` | Dodanie obsługi wiadomości tekstowych z kanałów z projektem (nie tylko wątki). Route'owanie do outbox z `CHANNEL_MESSAGE_INGESTION`. |
| `OutboxEventHandler` | Dodanie obsługi nowego typu eventu `CHANNEL_MESSAGE_INGESTION`. |
| `Prompts` | Dodanie nowego szablonu promptu `MessageUsefulness` w języku polskim. |
| `ChannelProjectRepository` | Dodanie metody `findByChannelIdAndTenantId` (jeśli nie istnieje). |

## 7. Prompt do oceny przydatności

```
SYSTEM:
Jesteś asystentem oceniającym przydatność wiadomości z kanału Slack dla projektu "{projectName}" 
w branży "{industry}". 

Oceń, czy wiadomość zawiera wiedzę, która może być przydatna w przyszłości dla tego projektu.
Przydatna wiedza to:
- Informacje techniczne, decyzje projektowe, ustalenia
- Opisy problemów i ich rozwiązań
- Wnioski z dyskusji, rekomendacje
- dane kontaktowe, terminy, wymagania
- Konkretne fakty, liczby, parametry

NIEPRZYDATNA jest:
- Powitania, pożegnania, luźne rozmowy
- Reakcje emoji bez treści merytorycznej
- Powiadomienia systemowe
- Wiadomości zawierające tylko linki bez kontekstu
- Pytania bez odpowiedzi (te są obsługiwane osobno)

USER:
Treść wiadomości: "{content}"
Autor: {userId}

Odpowiedz w formacie JSON:
{
  "useful": true/false,
  "summary": "Zwięzłe podsumowanie wiedzy (max 500 znaków) lub puste jeśli nieprzydatne",
  "reason": "Uzasadnienie decyzji"
}
```

## 8. Przepływy główne

### 8.1. Przepływ ingestion wiadomości z kanału

```
1. Slack message event → /api/v1/slack/events
2. SlackEventsController → SlackEventRequestHandler
3. SlackEventProcessor → BotMessageFilter (filtruje boty)
4. MessageEventHandler:
   a. Sprawdź czy kanał ma przypisany projekt (ChannelProject)
   b. Jeśli TAK → zapisz do outbox z typem CHANNEL_MESSAGE_INGESTION
   c. Jeśli NIE → zignoruj
5. OutboxProcessor (polling co 5s) → SlackMessageIngestionHandler
6. MessageIngestionService.processMessage():
   a. Wywołaj MessageUsefulnessPort.evaluate(message, project)
   b. Jeśli useful == false → zakończ (nie zapisuj)
   c. Jeśli useful == true → generuj embedding przez EmbeddingPort
   d. Zapisz KnowledgeEntry w bazie danych
   e. Loguj statystyki
```

### 8.2. Przepływ odpowiedzi na pytanie (istniejący, z modyfikacją)

```
1. Slack message event z @Vedra lub pytaniem (kończy się "?")
2. MessageEventHandler → zapisz do outbox z typem SLACK_MESSAGE_ANSWER
3. OutboxProcessor → SlackMessageAnswerHandler
4. KnowledgeService.answerQuestion() → RAG search + LLM response
5. Wyślij odpowiedź w wątku Slack
```

### 8.3. Warunek decyzyjny w MessageEventHandler

```
Jeśli wiadomość jest od bota → POMIŃ
Jeśli wiadomość nie ma treści tekstowej → POMIŃ
Jeśli kanał nie ma przypisanego projektu → POMIŃ
Jeśli wiadomość zawiera @Vedra LUB kończy się "?" → SLACK_MESSAGE_ANSWER
W przeciwnym razie → CHANNEL_MESSAGE_INGESTION
```

## 9. Schemat migracji bazy danych

```sql
-- Brak nowych tabel - wykorzystujemy istniejącą tabelę knowledge_entries
-- Zmiany w istniejących danych:
-- knowledge_entries.source: dodanie wartości enum 'CHANNEL_MESSAGE'
-- (jeśli source jest typem enum w PostgreSQL, dodać nową wartość)
-- (jeśli source jest String - brak zmian schema)
```

## 10. Testy

### 10.1. Testy jednostkowe
- `MessageIngestionServiceTest` – orchestracja (mock portów)
- `MessageUsefulnessEvaluatorTest` – formatowanie promptu, parsowanie odpowiedzi LLM
- `SlackMessageIngestionHandlerTest` – obsługa eventów outbox

### 10.2. Testy integracyjne
- `MessageEventHandlerIngestionTest` – end-to-end z mockami Slack API
- `KnowledgeEntryCreationTest` – zapis i odczyt z wektorem

### 10.3. Testy E2E
- Wysłanie wiadomości na kanał → sprawdzenie czy została oceniona i zapisana (jeśli przydatna)
- Wysłanie wiadomości nieprzydatnej → sprawdzenie czy NIE została zapisana

## 11. Kolejność wdrożenia

1. Dodanie promptu `MessageUsefulness` do `Prompts.kt`
2. Implementacja `MessageUsefulnessResult` i `MessageUsefulnessPort`
3. Implementacja `MessageUsefulnessEvaluator` (LLM adapter)
4. Implementacja `MessageIngestionService`
5. Implementacja `SlackMessageIngestionHandler` (outbox handler)
6. Modyfikacja `MessageEventHandler` – route'owanie ingestion vs answer
7. Dodanie logowania i statystyk
8. Testy jednostkowe
9. Testy integracyjne
10. Testy E2E

## 12. Ograniczenia i założenia

- System działa wyłącznie z lokalnymi modelami Ollama (llama3.2 + nomic-embed-text)
- Ocena LLM może generować fałszywie dodatnie (wiadomości nieprzydatne ocenione jako przydatne) – jakość zależy od modelu
- Embeddingi są generowane w języku polskim – jakość zależy od modelu nomic-embed-text
- Priorytet: wiadomości tekstowe; obsługa obrazków/plików może być dodana w przyszłości
- Przetwarzanie jest asynchroniczne – opóźnienie ingestion wynosi do 5 sekund (interwał outbox pollingu)
