# Specyfikacja: Uproszczony model kanał–projekt (0 ról, auto-link, promocja do publicznej kopią)

## 1. Cel

Minimalizacja złożoności i konfiguracji użycia Vedry w chatach firmowych (Slack-only):

1. **0 ról w Vedrze** — uprawnienia dziedziczone z platformy chatu, brak RBAC.
2. **Minimalny help** — w helpu `/vedra create|list|status|help`, `link|unlink` ukryte (działają, bez wpisu w helpu).
3. **Jasna segregacja danych** — `PRIVATE` (default, 1 kanał = 1 projekt) vs `PUBLIC` (1 na tenant = Baza ogólna, doklejana do każdego pytania). Droga do publicznej wyłącznie przez **promocję kopii wpisu przyciskiem w wątku (wariant B)** — brak toggla `visibility` per kanał.

Decyzje właściciela (zamrożone):
- (1) `create` = full self-service, bez akceptacji admina — NIE wymaga potwierdzenia.
- (2) `link`/`unlink` zostają funkcjonalnie, usuwamy je **tylko z helpa** (ukryte, dla kompatybilności).
- (3) Ingest bez opt-in — NIE wymagamy `/vedra learn on`.
- (4) Wariant B: prywatne kanały są prywatne; droga do publicznej to **przycisk promowania wiadomości/kopii wpisu do general w wątku**, przechowywane jako **kopia** w projekcie PUBLIC. Brak masowego `publish` i brak toggla PRIVATE↔PUBLIC per kanał.
- (5) 1 projekt publiczny na tenant (= Baza ogólna, cel promocji).
- (6) Tylko Slack — inne integracje poza zakresem.
- (7) TAK — usuwamy `is_general` na rzecz `visibility` (znacznik 1 projektu PUBLIC).

## 2. Kontekst

### Istniejąca architektura (stan na 2026-09-08)

- **Install:** manualny Slack App, 3x Request URL (`/api/v1/slack/events,/commands,/interactive`), jeden globalny `SLACK_BOT_TOKEN` (`SlackConfig.kt:14-18`). Brak OAuth per workspace, brak tabeli instalacji.
- **Tenant:** auto-provisioning `TenantService.resolveByExternalId()` z `team_id` (`SlackTenantResolver.kt:30-41`, `SlackEvent.fromRawBody`).
- **Dodanie do chatu:** `/invite @Vedra` (po stronie Slacka) + ręczne `/vedra create|link|status|unlink|list` (`SlackSlashCommandController.kt:17-46`, `SlackCommandParser.kt:31-41`).
- **Mapowanie:** `channel_projects PK(channel_id) -> project_id` (`V9__channel_projects.sql:1-6`) = ściśle 1 kanał → 1 projekt (link nadpisuje). Do wyszukiwania doklejany projekt ogólny: `TenantProjectService.getProjectIdsForChannel():19-38` zwraca `[linked, general]`.
- **Role:** brak. Każdy na kanale może `create/link/unlink`. `ChatController /api/v1/chat` bez auth.
- **Prywatność:** brak flagi. Tylko `is_general BOOLEAN DEFAULT false` (`V8__tenant_projects.sql:6`, `TenantProject.kt:27-28` domain: `TenantProject.kt:12`). Semantyka: baza wspólna, nielinkowalna (`CANNOT_LINK_GENERAL`, `TenantProjectService.kt:50-52`), zawsze doklejana do wyszukiwania. Tworzenie z `isGeneral=true` tylko programowo (`createProject(..., isGeneral=false):89-96`).
- **Modal create:** `SlackModalService.openCreateProjectModal():20-135` już niesie `private_metadata=channelId` (`:51`), ale `SlackInteractiveRequestHandler.processCreateProjectSubmission():47-116` go **ignoruje** — projekt tworzy się bez linku.
- **Pytania:** `MessageEventHandler.isQuestion():111-114` (`endsWith("?") || contains("@vedra")`), pytania → outbox `SLACK_MESSAGE_ANSWER` z `{tenantId, channel, threadTs, question, projectIds}` (`:70-81`), reszta → `CHANNEL_MESSAGE_INGESTION` z `firstOrNull()` jako `specificProjectId` (`:93-106`).
- **Odpowiedź:** `SlackMessageAnswerHandler.kt:22-64` dokleja `null` (`:39`), woła `knowledgeService.answerQuestion(question, allProjectIds)` (`:45`).
- **Bug krytyczny multitenant:** SQL w `SpringDataKnowledgeRepository.kt:17-41` poprawny (`tenant_id + (project_id IS NULL OR IN)`), ale `KnowledgeService.answerQuestion(question, projectIds):37-40` woła `findSimilarWithScore(tenantId=TENANT_ID, ...)` ze stałą `TENANT_ID=00001101-...` (`:281`). `tenantId` z outboxa jest ignorowany. Wszystkie wywołania (`answerQuestion`, `addKnowledge:112`, `saveAiAnswer:265`, `llmCacheService` `:57,195,226`, `findReusableAiAnswer:126`) używają stałej. Efekt: cross-tenant read + zwykle zero trafień → fallback `answerWithoutContext()`.
- **Bug `getChannelStatus`:** `TenantProjectService.kt:71-85` szuka nazwy projektu przez `findByTenantId(UUID.randomUUID())` — zawsze null.

### Istniejący przepływ (do uproszczenia)

```
Slack message → MessageEventHandler → getProjectIdsForChannel → outbox → answer/ingest
/vedra create → modal → createProject (bez linku)
/vedra link <uuid> → linkChannelToProject (nadpisuje)
/vedra unlink → deleteByChannelId
```

## 3. Wymagania funkcjonalne

### 3.1. Role i uprawnienia — 0 ról w Vedrze

- **FR-01:** Vedra NIE wprowadza ról (`Admin/Member/Owner` nie istnieją). Jedyny byt to `tenant` (auto-provisioning jak dziś).
- **FR-02:** Dodanie Vedry do workspace robi dowolny Workspace Admin platformy (raz, istniejący flow Install App). Bez panelu Vedry.
- **FR-03:** Dodanie Vedry do kanału robi dowolny członek kanału (`/invite @Vedra` po stronie Slacka). Bez dodatkowych uprawnień w kodzie.
- **FR-04:** Każdy członek kanału może wykonać `/vedra create|list|status`. Brak sprawdzania uprawnień w `CommandHandler`ach.
- **FR-05:** Tworzenie projektów NIE wymaga akceptacji — `createProject` od razu `Success` (istniejący `AlreadyExists` po `UNIQUE(tenant_id,name)` bez zmian).

### 3.2. Komendy — link/unlink ukryte z helpa (działają)

- **FR-06:** `SlackCommandParser.parse()` bez zmian — nadal obsługuje `create|link|unlink|list|status|help`. Brak kasowania gałęzi.
- **FR-07:** Klasy `command/link/*` i `command/unlink/*` oraz `TenantProjectService.linkChannelToProject/unlinkChannel/LinkResult` **zostają** bez zmian funkcjonalnych (ukryty escape hatch, kompatybilność).
- **FR-08:** (zarezerwowane — brak kasowania; przyszły config np. `visibility` będzie osobną komendą, poza zakresem).
- **FR-09:** Nowy `HelpCommandHandler.handle()` (bez `link/unlink`):
  ```
  *Komendy Vedra:*
  • `/vedra create` - Utwórz projekt dla tego kanału
  • `/vedra list` - Pokaż dostępne projekty
  • `/vedra status` - Pokaż aktualny kontekst kanału
  ```
  Wywołanie `/vedra link ...` / `/vedra unlink` nadal działa jak dziś.

### 3.3. Tworzenie projektu = auto-link (PRIVATE default)

- **FR-10:** `/vedra create` otwiera ten sam modal (`SlackModalService`, `CreateProjectCommandHandler` bez zmian). Po `view_submission` system w jednej transakcji: (a) `createProject(visibility=PRIVATE)`, (b) `save ChannelProject(channelId, projectId)`. `channelId` odczytane z `view.private_metadata` (już wysyłane w `SlackModalService.kt:51`, dziś ignorowane).
- **FR-11:** `AlreadyExists` (ten sam `name` w `tenant_id`) NIE nadpisuje linku — zwraca błąd `"Projekt *<name>* już istnieje."` jak dziś (`SlackInteractiveRequestHandler.kt:108-111`). Użytkownik ma użyć innej nazwy (brak `link` celowo).
- **FR-12:** Jeśli kanał ma już `ChannelProject`, `create` nadpisuje go nowym `projectId` (semantyka 1:1 jak dzisiejszy `link`: `deleteByChannelId` + `save`). Stary projekt prywatny NIE jest kasowany (dane zostają, tracą tylko powiązanie z tym kanałem). Log `Linked channel=X to project=Y`.
- **FR-13:** Odpowiedź sukcesu modala jak dziś (`response_action=update`, `:150-178`) + dopisek o powiązaniu, np. `Powiązano z tym kanałem.` (teksty PL bez zmian poza tym zdaniem).
- **FR-14:** Brak `channelId` w `private_metadata` (stary modal, test) → projekt tworzy się BEZ linku + log warn (fallback, nie błąd 500).

### 3.4. Model visibility zamiast is_general

- **FR-15:** Nowy enum domenowy (np. `domain/model/ProjectVisibility.kt` lub pole w `TenantProject`):
  ```kotlin
  enum class ProjectVisibility { PRIVATE, PUBLIC }
  data class TenantProject(..., val visibility: ProjectVisibility = PRIVATE, ...)
  ```
  Pole `isGeneral` usunięte z domain (`TenantProject.kt:12`), JPA (`database/project/TenantProject.kt:27-28,45`), mappera (`TenantProjectMapper.kt:12,23`), serwisu (`createProject(isGeneral=false):95,108`).
- **FR-16:** Twardy limit **1 projekt PUBLIC na tenant** (wymóg decyzji 5). Egzekwowany na dwóch poziomach: unique partial index w DB (sekcja 10) + walidacja w `createProject`/`ensurePublicProject` (np. `CreateResult.PublicAlreadyExists` lub cichy `return existing`). Brak komendy zmiany visibility per kanał — PUBLIC powstaje wyłącznie przez seed/migrację (FR-20) i jest **celem promocji kopii** (sekcja 3.7). Brak toggla PRIVATE↔PUBLIC.
- **FR-17:** `getProjectIdsForChannel(tenantId, channelId)` nowa semantyka (zamiennik `:19-38`):
  ```kotlin
  val linked = channelProjectRepository.findByChannelId(channelId) // PRIVATE lub PUBLIC
  val public = tenantProjectRepository.findPublicByTenantId(tenantId)
  return listOfNotNull(linked?.projectId, public?.id.takeIf { it != linked?.projectId }).distinct()
  ```
  Gdy kanał niezlinkowany → `[public]` (jak dzisiejszy fallback do samego general). Gdy brak obu → `[]` (bot milczy, `MessageEventHandler:43-46` bez zmian).
- **FR-18:** Ingest (`MessageEventHandler:51-56`) zapisuje wyłącznie do `linked.projectId` gdy zlinkowany projekt to PRIVATE. Gdy kanał czyta tylko PUBLIC (brak linku) — ingest pomijany (`No specific project... skipping ingestion` jak dziś). Nigdy nie ingestujemy do PUBLIC z chatu.
- **FR-19:** `list` oznacza projekty tagami z `visibility`: `(publiczny)` zamiast dzisiejszego `(ogólna)` (`ListProjectsCommandHandler.kt:26`), plus `branża:` bez zmian. `status` mówi: `"Odpowiedzi będą szukane w projekcie *X* + baza publiczna."` gdy zlinkowany, a gdy nie: `"Ten kanał nie ma własnego projektu. Odpowiedzi z bazy publicznej. Użyj /vedra create..."` (zamiennik `StatusCommandHandler.kt:16-23`, bez wzmianki o `link`).

### 3.5. Multitenant — naprawa przekazywania tenantId (w zakresie speca)

- **FR-20:** `KnowledgeService` przyjmuje prawdziwy tenant zamiast stałej:
  - `answerQuestion(question, projectIds, tenantId)` — `tenantId` wymagany; overload `answerQuestion(question)` deleguje wyłącznie do testów/REST z jawnym brakiem tenanta (do decyzji w implementacji: usunąć lub oznaczyć `@Deprecated`).
  - Wszystkie użycia `TENANT_ID` (`:57,74,112,126,195,226,265`) zastąpione parametrem. `companion TENANT_ID` usunięte po migracji danych (lub zostaje tylko jako stała migracyjna).
  - `SlackMessageAnswerHandler.kt:26,45` parsuje `tenantId` z payloadu (już tam jest) i przekazuje dalej; brak `tenantId` → warn + drop (jak brak `channel/threadTs/question` `:28-31`). Usunąć doklejanie `null` (`:39`) — `projectIds` z `getProjectIdsForChannel` są wystarczające (SQL już obsługuje `project_id IS NULL` dla wpisów globalnych).
  - `addKnowledge(question, answer, projectId, tenantId)` i `saveAiAnswer` analogicznie tenant-aware. `ChatController.kt:19` (REST bez tenanta) — do zamknięcia lub przekazania jawnego tenanta (decyzja implementacyjna, nie zmienia API Slack).
- **FR-21:** `getChannelStatus(channelId, tenantId)` — sygnatura rozszerzona o `tenantId`, usunięty `UUID.randomUUID()` (`:76`). `StatusCommand` niesie `workspaceId` (jak `ListProjectsCommand`) i handler resolwuje tenant przez `SlackTenantResolver` przed wywołaniem statusu.

### 3.6. Prywatność — PRIVATE nigdy nie wycieka masowo

- **FR-22:** Zapis: `knowledge_entries(project_id)` = PRIVATE kanału źródłowego (bez zmian ścieżki `MessageIngestionService.ingestMessage`, która już jest tenant+project aware) lub PUBLIC (tylko seed + kopie z promocji, nigdy bezpośredni ingest z chatu).
- **FR-23:** Odczyt: `WHERE tenant_id=:t AND (project_id IN (:linked_private, :public))` — istniejący `findSimilarWithScore` bez zmian SQL, zmienia się tylko caller (FR-17 + FR-20).
- **FR-24:** Zakaz masowej zmiany PRIVATE→PUBLIC (brak toggla, brak komendy). Jedyna droga do publicznej to promocja pojedynczych wpisów przyciskiem (sekcja 3.7).

### 3.7. Promocja do publicznej — przycisk w wątku, zapis jako kopia (wariant B)

- **FR-25:** Trigger: przycisk `Udostępnij 🌍` (label PL do finalizacji, np. `Udostępnij publicznie`) obok `Pomogło/Nie pomogło` w odpowiedzi Vedry w wątku. Budowany w `FeedbackBlockBuilder`, klik obsługiwany w `SlackInteractiveRequestHandler.handleButtonInteraction` → nowy outbox event `PROMOTE_TO_PUBLIC {feedbackId, userId, tenantId, channelId}`. 0 ról: każdy członek kanału może kliknąć; klik logowany jako `promoted_by=userId`.
- **FR-26:** Handler (`SlackPromoteToPublicHandler`, nowy `OutboxEventHandler`): (a) `FeedbackService`/mapping `feedbackId → entryIds`, (b) load źródłowych `KnowledgeEntry` (PRIVATE, ten sam `tenantId`), (c) `ensurePublicProject(tenantId)` (seed `Baza ogólna` gdy brak), (d) dla każdego `entryId` utwórz **kopię**: ten sam `content/answer/embedding/normalizedContent/contentHash`, `projectId=publicId`, `answerSource=USER_VERIFIED`, `accepted=true`, `qualityScore=max(src, 0.8)`, `metadata = src.metadata + {source_entry_id, source_project_id, source_channel_id, promoted_by, promoted_at}`. Bez re-embeddingu. Idempotentność: ponowny klik tego samego `feedbackId+userId` nie duplikuje (klucz `aggregateId=promote_<feedbackId>_<userId>` lub check po `source_entry_id` w PUBLIC).
- **FR-27:** Oryginał PRIVATE nietknięty (nie przenosimy, kopiujemy). Usunięcie kopii z PUBLIC (`unpublish`) poza zakresem. Brak promocji wpisów `AI_GENERATED` niezaakceptowanych? Decyzja implementacyjna: promować tylko `accepted=true` (jak feedback dziś), reszta → warn + drop.
- **FR-28:** Po promocji wysłać potwierdzenie w wątku (ephemeral lub reply, do decyzji implementacyjnej, PL np. `Udostępniono publicznie 🌍`), bez zmiany przycisków `Pomogło/Nie pomogło`. Błędy (brak mappingu, brak PUBLIC, zły tenant) → log warn, brak 500 do Slacka.

## 4. Wymagania niefunkcjonalne

- **NFR-01:** Kardynalność twarda: `channel_projects PK(channel_id)` bez zmian (1:1). `N:1` dozwolone tylko odczytem (wiele kanałów czyta ten sam PUBLIC); `1:N` zabronione (brak API).
- **NFR-02:** Izolacja: każdy odczyt RAG filtrowany po `tenant_id` (prawdziwym, FR-20) + `project_id IN (linked, public)`. Brak `userId` w filtrze (0 ról).
- **NFR-03:** Transakcyjność `create+link`: obie operacje w jednej `@Transactional` — albo projekt z linkiem, albo nic (brak wiszących projektów bez kanału z tego flow).
- **NFR-04:** Kompatybilność komend: `link|unlink` działają jak dziś (ukryte z helpa). Nowy przycisk promocji nie zmienia istniejącego feedbacku `Pomogło/Nie pomogło`.
- **NFR-05:** Logowanie PL jak dziś (`Linked channel=...`, `Skipping...`), plus `Promoted entry X → public by U` na INFO.
- **NFR-06:** Koszt LLM bez zmian (progi `HIGH=0.85/MEDIUM=MIN=0.65` w `KnowledgeService`, polling outbox 5s). Promocja nie woła LLM ani embeddingu (reuse wektora z kopii).

## 5. Modele danych

### 5.1. Zmiana `tenant_projects`

```kotlin
// domain/model/TenantProject.kt — po zmianie
data class TenantProject(
    val id: UUID? = null,
    val tenantId: UUID,
    val name: String,
    val description: String? = null,
    val industry: String? = null,
    val visibility: ProjectVisibility = ProjectVisibility.PRIVATE,
    val createdAt: Instant = Instant.now(),
)
enum class ProjectVisibility { PRIVATE, PUBLIC }
```

```kotlin
// infrastructure/database/project/TenantProject.kt — po zmianie
@Column(name = "visibility", nullable = false, length = 20)
@Enumerated(EnumType.STRING)
val visibility: ProjectVisibility = ProjectVisibility.PRIVATE,
```

### 5.2. `channel_projects` — bez zmian schema

`PK(channel_id)`, `FK project_id → tenant_projects(id) ON DELETE CASCADE` (`V9`). Jedyna zmiana to writer: zapis wyłącznie z flow `create` (FR-10/FR-12).

### 5.3. Repozytoria

- `TenantProjectRepository`: `findByTenantIdAndIsGeneralTrue` → `findPublicByTenantId(tenantId): TenantProject?` (+ `findByTenantId`, `findByTenantIdAndName`, `findByTenantIdAndId` bez zmian).
- `SpringDataTenantProjectRepository`: `findByTenantIdAndVisibility(tenantId, PUBLIC)` (Spring-derived) zamiast `findByTenantIdAndIsGeneralTrue`.
- `ChannelProjectRepository`: bez zmian (`findByChannelId`, `save`, `deleteByChannelId`).

## 6. Architektura i komponenty

### 6.1. Zachowania bez zmian (jawnie nie ruszamy)

| Klasa/plik | Decyzja |
|------------|---------|
| `slack/service/command/link/*`, `unlink/*` | zostają (ukryte z helpa) |
| `SlackCommandParser` | bez zmian |
| `TenantProjectService.linkChannelToProject/unlinkChannel/LinkResult` | zostają |
| `KnowledgeService.TENANT_ID` | delete po migracji danych (FR-20) |

### 6.2. Modyfikacje

| Klasa | Zmiana |
|-------|--------|
| `HelpCommandHandler` | nowy tekst bez `link/unlink` (FR-09) |
| `StatusCommand` + `StatusCommandHandler` | nieś `workspaceId`, resolwuj tenant, wołaj `getChannelStatus(channelId, tenantId)`, nowe teksty (FR-19/FR-21) |
| `ListProjectsCommandHandler` | tag `(publiczny)` z `visibility`, usuń `isGeneral` (`:26`) |
| `TenantProjectService` | nowa `getProjectIdsForChannel` (FR-17), `createProject` bez `isGeneral` + walidacja 1 PUBLIC + `ensurePublicProject(tenantId)`, nowa `createPrivateAndLink(tenantId, channelId, name, description, industry): CreateResult` (`@Transactional`), fix `getChannelStatus` (FR-21) |
| `SlackInteractiveRequestHandler.processCreateProjectSubmission` | odczytaj `view.private_metadata` jako `channelId`, wołaj `createPrivateAndLink` zamiast `createProject`, dopisek o powiązaniu w sukcesie; plus routing kliku promocji → outbox `PROMOTE_TO_PUBLIC` (FR-25) |
| `SlackMessageAnswerHandler` | parsuj `tenantId`, wołaj `answerQuestion(question, projectIds, tenantId)`, usuń `+ null` |
| `KnowledgeService` | sygnatury tenant-aware (FR-20) |
| `MessageEventHandler` | bez zmian logiki (działa na nowym `getProjectIdsForChannel`); opcjonalnie doprecyzuj log `No projects` → `No public nor linked project` |
| `TenantProjectMapper`, JPA entity, `SpringData*`, `JpaTenantProjectRepository` | mapowanie `visibility` |
| `FeedbackBlockBuilder`, `SlackMessageService/Formatter` | trzeci przycisk promocji + `replaceFeedbackButtons` bez regresji |

### 6.3. Nowe

| Klasa/plik | Opis |
|------------|------|
| `domain/model/ProjectVisibility.kt` | enum `PRIVATE, PUBLIC` |
| `V16__project_visibility.sql` | migracja (sekcja 8) |
| `domain/service/KnowledgePromotionService` (lub metoda w `KnowledgeService`) | kopiowanie `PRIVATE → PUBLIC` z provenance w `metadata` (FR-26) |
| `slack/service/outbox/SlackPromoteToPublicHandler.kt` + `PROMOTE_TO_PUBLIC` | handler outbox promocji (FR-26/28) |

## 7. Przepływy główne

### 7.1. Utworzenie projektu dla kanału (jedyna ścieżka linkowania)

```
1. User: /vedra create (dowolny członek kanału)
2. SlackSlashCommandController → SlackCommandParser → CreateProjectCommand(triggerId, channelId, workspaceId)
3. CreateProjectCommandHandler → SlackModalService.openCreateProjectModal (private_metadata=channelId)
4. User wypełnia modal → view_submission → SlackInteractiveRequestHandler
5. Odczyt view.private_metadata → channelId + resolve tenant z team_id
6. TenantProjectService.createPrivateAndLink (1 transakcja):
   a. unique check (tenant_id, name) → AlreadyExists?
   b. insert tenant_projects (visibility=PRIVATE)
   c. delete+insert channel_projects (channel_id → new projectId)
7. Modal update: "Projekt utworzony + Powiązano z tym kanałem."
```

### 7.2. Pytanie (PRIVATE + PUBLIC)

```
1. Wiadomość "?"/"@vedra" → MessageEventHandler
2. getProjectIdsForChannel → [private?, public?] (FR-17)
3. outbox SLACK_MESSAGE_ANSWER {tenantId, channel, threadTs, question, projectIds}
4. SlackMessageAnswerHandler → answerQuestion(question, projectIds, tenantId)
5. SQL: tenant_id=:t AND project_id IN (...) → odpowiedź w wątku z feedbackiem (bez zmian)
```

### 7.3. Ingest (tylko PRIVATE)

```
1. Zwykła wiadomość → MessageEventHandler → specificProjectId = linked PRIVATE
2. Brak linku (tylko PUBLIC) → skip ingestion (log jak dziś)
3. Outbox CHANNEL_MESSAGE_INGESTION → MessageIngestionService (bez zmian)
```

### 7.4. Promocja do publicznej (wariant B — przycisk + kopia)

```
1. Odpowiedź Vedry w wątku ma [Pomogło | Nie pomogło | Udostępnij 🌍]
2. Klik Udostępnij → SlackInteractiveRequestHandler → outbox PROMOTE_TO_PUBLIC {feedbackId, userId, tenantId}
3. SlackPromoteToPublicHandler:
   a. feedback mapping → entryIds (PRIVATE, tenant check)
   b. ensurePublicProject(tenantId) → publicId
   c. kopia każdego entry → knowledge_entries (projectId=publicId, ten sam embedding, metadata + provenance)
   d. potwierdzenie w wątku: "Udostępniono publicznie 🌍"
4. Kolejne pytanie z innego kanału → getProjectIdsForChannel → [linkedB, public] → trafia kopię
```

## 8. Schemat migracji bazy danych

Następna wolna wersja to `V16` (ostatnia `V15__llm_cache_and_normalization.sql`).

```sql
-- V16__project_visibility.sql
ALTER TABLE tenant_projects ADD COLUMN visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE';

UPDATE tenant_projects SET visibility = 'PUBLIC' WHERE is_general = true;
UPDATE tenant_projects SET visibility = 'PRIVATE' WHERE is_general = false OR is_general IS NULL;

ALTER TABLE tenant_projects DROP COLUMN is_general;

CREATE UNIQUE INDEX uniq_public_project_per_tenant
  ON tenant_projects (tenant_id) WHERE visibility = 'PUBLIC';

-- Seed 1 PUBLIC per tenant bez publicznego (nazwa do uzgodnienia, np. 'Baza ogólna'):
INSERT INTO tenant_projects (tenant_id, name, description, visibility)
SELECT t.id, 'Baza ogólna', 'Wiedza współdzielona tenanta', 'PUBLIC'
FROM tenants t
WHERE NOT EXISTS (
  SELECT 1 FROM tenant_projects p WHERE p.tenant_id = t.id AND p.visibility = 'PUBLIC'
);

-- Dane z hardcoded TENANT_ID (00001101-0000-1000-8000-00805F9B34FB):
-- do przeniesienia ręcznym skryptem per tenant ALBO czyszczenia na stagingu
-- (zakres poza DDL — odnotować w runbooku wdrożenia).
```

Uwagi: `CHECK (visibility IN ('PRIVATE','PUBLIC'))` opcjonalnie (enum JPA i tak pilnuje). Rollback: odtworzyć `is_general` z `visibility` przed dropem — w praktyce forward-only jak reszta migracji (`V8..V15` bez down).

## 9. Teksty komend (PL)

- `help` (FR-09), `status` zlinkowany: `"Ten kanał jest powiązany z projektem *X*.\nOdpowiedzi będą szukane w bazie projektu + baza publiczna."`, niezlinkowany: `"Ten kanał nie ma własnego projektu.\nOdpowiedzi będą szukane tylko w bazie publicznej.\nUżyj /vedra create aby utworzyć projekt dla tego kanału."`
- `list`: `"• *<id>*: <name> (publiczny, branża: X)"` / `"(branża: X)"` dla prywatnych.
- `link|unlink` działają jak dziś, bez wpisu w helpu.
- Przycisk promocji: `Udostępnij 🌍`, potwierdzenie: `Udostępniono publicznie 🌍`.

## 10. Testy

### 10.1. Jednostkowe
- `TenantProjectServiceTest`: `createPrivateAndLink` tworzy + linkuje w 1 transakcji; drugie `create` na tym kanale nadpisuje link, stary projekt zostaje; `AlreadyExists` nie rusza linku; limit 1 PUBLIC (drugi `create PUBLIC` → błąd/zwrot istniejącego); `getProjectIdsForChannel` → `[linked, public]`, `[public]`, `[]`; `getChannelStatus(channelId, tenantId)` zwraca nazwę (regresja `UUID.randomUUID()`).
- `SlackCommandParserTest`: `create|list|status|link|unlink` bez zmian (link/unlink działają, tylko help je ukrywa).
- `SlackInteractiveRequestHandlerTest`: `private_metadata` → link; brak metadata → projekt bez linku + warn; klik promocji → outbox `PROMOTE_TO_PUBLIC`.
- `KnowledgeServiceTest`: `answerQuestion` wymaga `tenantId`, weryfikacja `findSimilarWithScore(tenantId=...)` z prawdziwym UUID (regresja `TENANT_ID`); istniejące wołania `service.answerQuestion("...")` (`KnowledgeServiceTest.kt:82,110,...`) do aktualizacji.
- `SlackMessageAnswerHandlerTest`: parsuje `tenantId`, brak `tenantId` → drop (`verify(exactly=0) answerQuestion`, jak `:101,119,137,156`); brak doklejanego `null`.
- `KnowledgePromotionServiceTest` / `SlackPromoteToPublicHandlerTest`: kopia ma `projectId=publicId`, ten sam `embedding/content/answer`, `metadata.source_entry_id/promoted_by/promoted_at`; idempotentność (2x klik → 1 kopia); obcy `tenantId` → drop; `accepted=false` → drop.

### 10.2. Integracyjne
- `ChannelProjectFlowTest`: modal submit → wiersz w `tenant_projects (PRIVATE)` + wiersz w `channel_projects`; unikalność `uniq_public_project_per_tenant` (drugi PUBLIC → błąd constraint).
- `RagIsolationTest`: wpis PRIVATE kanału A niewidoczny z kanału B (ten sam tenant, różne linki); wpis PUBLIC (seed + kopia z promocji) widoczny z obu; wpis innego tenanta niewidoczny.
- `PromoteToPublicFlowTest`: klik → kopia w PUBLIC, oryginał PRIVATE nietknięty, pytanie z kanału B trafia kopię.
- Migracja `V16`: `is_general=true → PUBLIC`, reszta → PRIVATE, seed PUBLIC per tenant.

### 10.3. E2E (Slack mock)
- `/vedra create` → status pokazuje projekt; pytanie `?` odpowiada z PRIVATE+PUBLIC; wiadomość zwykła ląduje w PRIVATE (nie w PUBLIC); `/vedra link` nadal działa (ukryte); klik `Udostępnij 🌍` → kopia w PUBLIC widoczna z innego kanału.

## 11. Kolejność wdrożenia

1. Migracja `V16` + enum `ProjectVisibility` + JPA/domain/mapper.
2. Repozytoria (`findPublicByTenantId`, usunięcie `IsGeneralTrue`).
3. `TenantProjectService` (nowa `getProjectIdsForChannel`, `createPrivateAndLink`, `ensurePublicProject`, fix `getChannelStatus`; `link/unlink` bez zmian).
4. Nowy `help` (bez `link/unlink` w tekście).
5. `StatusCommand/Handler` + `ListProjectsCommandHandler` (teksty + tenant).
6. `SlackInteractiveRequestHandler` (private_metadata → auto-link).
7. `KnowledgeService` + `SlackMessageAnswerHandler` tenant-aware (FR-20) + runbook danych z `00001101-...`.
8. Promocja B: przycisk + `PROMOTE_TO_PUBLIC` + kopia (FR-25..28).
9. Testy 10.1 → 10.2 → 10.3.
10. Update `SETUP.md` (opcjonalnie: nowy help-text w docs).

## 12. Ograniczenia i założenia

- Tylko Slack. OAuth per workspace, Teams/GChat, toggle PRIVATE↔PUBLIC per kanał, `/learn on`, role per user — jawnie poza zakresem.
- Kanał bez linku odpowiada wyłącznie z PUBLIC i nic nie ingestuje (celowy trade-off prostoty).
- Promocja to kopia (nie przeniesienie); `unpublish`/usuwanie kopii poza zakresem; brak re-embeddingu przy kopii.
- Zmiana nazwy projektu, kasowanie projektów, przenoszenie linków między kanałami — brak poza `create` (nadpisanie) i ukrytymi `link/unlink`.
- Dane historyczne z `TENANT_ID=00001101-...` wymagają ręcznego runbooka (nie da się zgadnąć tenanta z samego UUID projektu — UUID są globalnie unikalne, więc mapowanie `projectIds` zwykle da zero trafień do czasu migracji).
- `ChatController` REST (`/api/v1/chat`) poza Slackiem — do decyzji: zamknąć auth lub dopisać tenant; spec nie zmienia jego kontraktu.
