# Specyfikacja: Usunięcie stałej TENANT_ID z KnowledgeService (005)

## 1. Cel

Całkowita rezygnacja z hardcoded `KnowledgeService.TENANT_ID = 00001101-0000-1000-8000-00805F9B34FB` (`KnowledgeService.kt:308`):

1. `tenantId` zawsze jawny, tenant-aware — brak domyślnego tenanta w kodzie.
2. Usunięcie 4 przeciążeń `@Deprecated` delegujących do stałej + usunięcie `companion TENANT_ID`.
3. Zamknięcie ostatniego użytkownika stałej: `ChatController /api/v1/chat` (dziś woła overloady bez tenanta).
4. Migracja/runbook danych zapisanych pod stałą + regresja testowa.

Decyzje właściciela (zamrożone):
- (1) Stała znika całkowicie, nie zostaje nawet jako stała migracyjna w kodzie produkcyjnym (UUID migracyjne żyje tylko w SQL/runbooku).
- (2) `ChatController` nie dostaje domyślnego tenanta — albo nagłówek `X-Tenant-Id`, albo usunięcie/wyłączenie kontrolera.
- (3) Brak kompatybilności wstecznej dla overloadów bez `tenantId` — breaking change jawny.

## 2. Kontekst

### Stan na 2026-09-08

- **Stała:** `companion object { val TENANT_ID = UUID("00001101-...") }` (`KnowledgeService.kt:307-308`). Jedyna definicja w `main`, jedno użycie w `test` (`KnowledgeServiceTest.kt:350` w helperze `knowledgeEntry()`).
- **4 overloady `@Deprecated` delegujące do stałej (jedyne użycia stałej w `main`):**
  - `answerQuestion(question: String):35` → `answerQuestion(question, listOf(null), TENANT_ID)`
  - `answerQuestion(question, projectIds: List<UUID?>):39-42` → `answerQuestion(question, projectIds, TENANT_ID)`
  - `addKnowledge(question, answer):107-110` → `addKnowledge(question, answer, null, TENANT_ID)`
  - `addKnowledge(question, answer, projectId):114-118` → `addKnowledge(question, answer, projectId, TENANT_ID)`
- **Metody docelowe (już tenant-aware, bez zmian logiki):** `answerQuestion(question, projectIds, tenantId: UUID):45-103`, `addKnowledge(question, answer, projectId, tenantId):121-141`. Wewnątrz już zero użyć `TENANT_ID` (`findReusableAiAnswer`, `llmCacheService.lookup/save`, `findSimilarWithScore`, `saveAiAnswer` biorą parametr).
- **Slack naprawiony:** `SlackMessageAnswerHandler.kt:26,39-51` parsuje `tenantId` z payloadu, brak → warn + drop, woła wariant 3-arg. Nic do roboty.
- **Ostatni użytkownik prod:** `ChatController.kt:19,33` — `POST /api/v1/chat` woła `answerQuestion(request.question)`, `POST /api/v1/chat/knowledge` woła `addKnowledge(question, answer)`. Kontroler bez auth, bez tenanta (spec 003 sekcja 12 odłożył decyzję tutaj).
- **Testy:** `KnowledgeServiceTest.kt:84,112,128,149,173,199,225,249,279,304,340` wołają overload 1-arg; helper `:350` używa `KnowledgeService.TENANT_ID`. `SlackMessageAnswerHandlerTest` już na 3-arg (`:60,66`) — bez zmian.
- **Dane:** wpisy `knowledge_entries` i `llm_cache` zapisane przed FR-20 (spec 003) noszą `tenant_id = 00001101-...`. UUID projektów są globalnie unikalne, więc nie da się ich automatycznie przypisać do tenantów (spec 003 sekcja 12).
- **Spec 003 FR-20** zapowiedział to usunięcie (`companion TENANT_ID` delete po migracji danych).

### Docelowy stan

```
SlackMessageAnswerHandler (tenantId z outbox) → answerQuestion(q, pIds, tenantId) ✓ (już)
ChatController → wymaga X-Tenant-Id albo nie istnieje
KnowledgeService: tylko sygnatury z tenantId: UUID, brak companion TENANT_ID
Dane z 00001101-...: przypisane do prawdziwych tenantów albo usunięte, constraint to egzekwuje
```

## 3. Wymagania funkcjonalne

### 3.1. Usunięcie stałej i overloadów

- **FR-01:** Usunąć z `KnowledgeService.companion` pole `TENANT_ID`. Usunąć całe 4 metody `@Deprecated` (`:33-42`, `:105-118`). Zostają wyłącznie `answerQuestion(question, projectIds, tenantId)` i `addKnowledge(question, answer, projectId, tenantId)`.
- **FR-02:** Po zmianie `grep -r TENANT_ID src/main` zwraca zero trafień. `grep -r KnowledgeService.TENANT_ID` zwraca zero trafień w całym repo (poza wzmiankami w specs/runbooku).
- **FR-03:** Brak nowych wartości domyślnych tenanta (zakaz `tenantId: UUID = ...`, zakaz `listOf(null)` jako protezy tenanta). Kompilator ma wymuszać podanie `tenantId` w każdym wołaniu.

### 3.2. ChatController — domknięcie

- **FR-04:** Wariant A (rekomendowany): `ChatController` wymaga nagłówka `X-Tenant-Id: <uuid>`:
  ```kotlin
  fun chat(@RequestHeader("X-Tenant-Id") tenantId: UUID, @RequestBody request: ChatRequest)
  fun knowledge(@RequestHeader("X-Tenant-Id") tenantId: UUID, @RequestBody request: ChatKnowledgeRequest)
  ```
  Brak/invalid header → `400` z komunikatem PL (`"Brak nagłówka X-Tenant-Id"` / `"Nieprawidłowy X-Tenant-Id"`). `addKnowledge` wołane z `projectId=null` + tenant z nagłówka; `answerQuestion` z `projectIds=listOf(null)` + tenant z nagłówka.
- **FR-05:** Wariant B (alternatywa, decyzja na implementacji): usunięcie `ChatController` w całości, jeśli REST nie jest używany poza debugiem. Wtedy usunąć też `ChatRequest/ChatKnowledgeRequest/ChatResponse` i wpisy `http-client/`. Nie łączyć wariantów — jeden z nich.
- **FR-06:** Bez zmian kontraktu Slack (outbox, `SlackMessageAnswerHandler`, `MessageEventHandler`). Ten spec nie dotyka ścieżki Slack.

### 3.3. Dane historyczne pod stałą

- **FR-07:** Przed deployem kodu z FR-01 wykonać inwentaryzację:
  ```sql
  SELECT count(*), min(created_at), max(created_at) FROM knowledge_entries WHERE tenant_id = '00001101-0000-1000-8000-00805F9B34FB';
  SELECT count(*) FROM llm_cache WHERE tenant_id = '00001101-0000-1000-8000-00805F9B34FB';
  ```
  Wynik wkleić do runbooka wdrożenia (PR description).
- **FR-08:** Migracja czyszcząca jako część tego speca (następna wolna wersja po `V16`/`V17` — do potwierdzenia przy implementacji, np. `V18__delete_legacy_tenant_id.sql`):
  ```sql
  -- V18__delete_legacy_tenant_id.sql
  DELETE FROM knowledge_entries WHERE tenant_id = '00001101-0000-1000-8000-00805F9B34FB';
  DELETE FROM llm_cache WHERE tenant_id = '00001101-0000-1000-8000-00805F9B34FB';
  ```
  Uzasadnienie: automatyczne przypisanie do prawdziwych tenantów niemożliwe (UUID projektów unikalne globalnie, brak mapowania `team_id → wpisy`). Jeśli właściciel wskaże konkretny tenant docelowy dla stagingu — zamiast `DELETE` dopuszczalny `UPDATE ... SET tenant_id = '<real>'` ręcznym skryptem poza migracją, odnotowany w runbooku.
- **FR-09:** Po FR-08 zero wierszy z legacy UUID (weryfikacja `SELECT count(*)` = 0 w CI/migracji). Brak down-migracji (forward-only jak `V8..V16`).

### 3.4. Testy jako część definicji done

- **FR-10:** `KnowledgeServiceTest`: wszystkie wołania 1-arg (`:84,112,...`) przepisane na 3/4-arg z `tenantId = UUID.randomUUID()` per test; helper `knowledgeEntry()` bierze `tenantId` jako parametr zamiast `KnowledgeService.TENANT_ID`. Nowy test regresyjny: `grep`-asercja niepotrzebna — wystarczy, że kod z `TENANT_ID` się nie kompiluje; dodatkowo test `answerQuestion` weryfikuje `findSimilarWithScore(tenantId=<ten UUID>)` (przekazanie, nie stała).
- **FR-11:** `ChatControllerTest` (nowy, MockMvc): brak headera → 400; zły UUID → 400; poprawny UUID → delegacja do `answerQuestion(question, listOf(null), tenantId)` / `addKnowledge(question, answer, null, tenantId)` (mockk verify). Wariant B: testy kontrolera usuwane razem z kontrolerem.
- **FR-12:** `SlackMessageAnswerHandlerTest` bez zmian (już tenant-aware) — tylko regresja zielona.

## 4. Wymagania niefunkcjonalne

- **NFR-01:** Zero zmiany logiki RAG (progi `HIGH=0.85/MEDIUM=MIN=0.65`, kolejność cache → embedding → LLM bez zmian). Ten spec to czyszczenie, nie tuning.
- **NFR-02:** Kompilacja jest bramką: jakikolwiek pozostały caller overloadu bez tenanta = błąd kompilacji, nie runtime. Brak refleksji/workaroundów.
- **NFR-03:** Migracja `DELETE` idempotentna i szybka (prosty `WHERE tenant_id = ...`; przy dużej tabeli dopuszczalny `CREATE INDEX CONCURRENTLY` poza zakresem — odnotować w runbooku jeśli `count(*)` duży).
- **NFR-04:** Logi bez zmian; brak nowych sekretów, brak nowych wywołań sieciowych.

## 5. Modele danych

- Brak zmian schema domenowego (`KnowledgeEntry.tenantId: UUID` już wymagane, `TenantProject` bez zmian).
- Jedyna zmiana DB: migracja czyszcząca FR-08 (`knowledge_entries`, `llm_cache`).

## 6. Architektura i komponenty

### 6.1. Bez zmian

| Klasa/plik | Decyzja |
|---|---|
| `SlackMessageAnswerHandler`, `MessageEventHandler`, `SlackMessageIngestionHandler` | bez zmian (już tenant-aware) |
| `LlmCacheService`, `EmbeddingCacheService`, `KnowledgeRepository.findSimilarWithScore` | bez zmian sygnatur |
| `TenantService`, `TenantProvisioningService`, `TenantProjectService` | bez zmian |

### 6.2. Modyfikacje

| Klasa | Zmiana |
|---|---|
| `KnowledgeService` | delete `companion TENANT_ID` + 4 overloady `@Deprecated`; zostają 2 metody z `tenantId` |
| `ChatController` | wariant A: `X-Tenant-Id` header + 400 handling; wariant B: delete kontrolera + DTO |
| `KnowledgeServiceTest` | przepisanie na jawny `tenantId`, helper bez stałej |

### 6.3. Nowe

| Klasa/plik | Opis |
|---|---|
| `V18__delete_legacy_tenant_id.sql` (numer do potwierdzenia) | `DELETE` wierszy z legacy UUID (FR-08) |
| `ChatControllerTest.kt` | testy nagłówka `X-Tenant-Id` (tylko wariant A) |

## 7. Przepływy główne

### 7.1. Happy path (wariant A)

```
1. REST POST /api/v1/chat {X-Tenant-Id: <uuid>, body: {question}} → ChatController
2. ChatController → knowledgeService.answerQuestion(question, listOf(null), tenantId)
3. RAG filtrowany po prawdziwym tenant_id → odpowiedź
```

### 7.2. Błąd nagłówka (wariant A)

```
1. POST bez X-Tenant-Id → 400 "Brak nagłówka X-Tenant-Id"
2. POST z X-Tenant-Id=zzz → 400 "Nieprawidłowy X-Tenant-Id"
3. KnowledgeService nigdy nie wołane (verify exactly 0)
```

### 7.3. Wdrożenie danych

```
1. Staging: SELECT count(*) legacy → decyzja DELETE vs ręczny UPDATE
2. Migracja V18 aplikuje DELETE → SELECT count(*) = 0
3. Deploy kodu bez TENANT_ID (kompilacja dowodzi braku callerów)
```

## 8. Schemat migracji bazy danych

```sql
-- V18__delete_legacy_tenant_id.sql (numer wersji potwierdzić z V16/V17 przy implementacji)
DELETE FROM knowledge_entries WHERE tenant_id = '00001101-0000-1000-8000-00805F9B34FB';
DELETE FROM llm_cache WHERE tenant_id = '00001101-0000-1000-8000-00805F9B34FB';
```

Weryfikacja po migracji:

```sql
SELECT count(*) FROM knowledge_entries WHERE tenant_id = '00001101-0000-1000-8000-00805F9B34FB';
-- oczekiwane: 0
SELECT count(*) FROM llm_cache WHERE tenant_id = '00001101-0000-1000-8000-00805F9B34FB';
-- oczekiwane: 0
```

## 9. Teksty odpowiedzi (PL)

- Brak headera: `400 "Brak nagłówka X-Tenant-Id"` (wariant A).
- Zły UUID: `400 "Nieprawidłowy X-Tenant-Id"` (wariant A).
- Reszta tekstów bez zmian (spec 003 sekcja 9).

## 10. Testy

### 10.1. Jednostkowe

- `KnowledgeServiceTest`: jawny `tenantId` wszędzie; verify `findSimilarWithScore` z tym UUID; helper `knowledgeEntry(tenantId)`.
- `ChatControllerTest` (nowy, wariant A): 400 bez headera, 400 zły UUID, delegacja z dobrym UUID.

### 10.2. Integracyjne

- Migracja V18 na kopii prod/staging: `count(*) legacy` przed > 0 (jeśli dane istnieją) → po = 0; aplikacja startuje, Slack `?` odpowiada z prawdziwego tenanta.
- `RagIsolationTest` (z 003) zielony bez zmian — potwierdza brak cross-tenant read po usunięciu stałej.

### 10.3. E2E (Slack mock / REST mock)

- Wariant A: `POST /api/v1/chat` z `X-Tenant-Id` działa; bez headera 400. Wariant B: endpoint nie istnieje (404), Slack działa normalnie.

## 11. Kolejność wdrożenia

1. Inwentaryzacja `count(*)` legacy UUID na staging/prod (FR-07) → wpis do runbooka.
2. Migracja V18 (FR-08/09) → weryfikacja `count(*) = 0`.
3. `KnowledgeService`: delete stałej + overloadów (FR-01/02).
4. `ChatController` wariant A albo B (FR-04/05).
5. `KnowledgeServiceTest` + nowy `ChatControllerTest` (FR-10/11).
6. `./gradlew test` + `grep -r TENANT_ID src/` = zero w `main`.
7. Update `SETUP.md`/`http-client/` jeśli wariant A zmienia kontrakt REST.

## 12. Ograniczenia i założenia

- Brak automatycznego przypisania legacy wierszy do tenantów (niemożliwe bez mapowania `team_id`; decyzja jak w 003 sekcja 12 — DELETE default).
- `projectIds=listOf(null)` w `ChatController` wariant A zostaje (wpisy globalne `project_id IS NULL` nadal obsługiwane przez SQL) — ten spec nie rusza semantyki `null` projektu.
- Auth pełny (`Spring Security`, OAuth) poza zakresem — nagłówek `X-Tenant-Id` to jawny tenant, nie autoryzacja.
- Wzmianki o `TENANT_ID`/`00001101-...` w specs i runbooku zostają jako dokumentacja historyczna (zakaz dotyczy tylko `src/main` + `src/test`).
