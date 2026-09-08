# Specyfikacja: Auto-przestrzeń kanału + pełny help + `/vedra config` (007)

## 1. Cel

Likwidacja ręcznego `/vedra create` jako kroku obowiązkowego i uporządkowanie konfiguracji:

1. **Dodanie Vedry do kanału (`/invite @Vedra`) automatycznie tworzy przestrzeń w DB** — prywatny projekt + wiersz `channel_projects`. Kanał od pierwszej sekundy odpowiada i ingestuje bez komend.
2. **Help pokazuje wyłącznie `status|config|help`** — `create|list` tracą uzasadnienie przy auto-prowizacji (1 kanał = 1 przestrzeń, brak wyboru z listy) i spadają do ukrytych aliasów legacy obok `link|unlink`. Jedyne widoczne komendy: `status`, `config`, `help`.
3. **Nowa komenda `/vedra config` przejmuje edycję szczegółów kanału** (nazwa, opis, branża) — modal prefillowany aktualnymi danymi zlinkowanego projektu. `config` przejmuje też rolę awaryjnego tworzenia (brak linku → auto-utwórz i edytuj); `create` zostaje wyłącznie jako ukryty alias wsteczny.

Decyzje właściciela (do zamrożenia przed implementacją):
- (1) Auto-nazwa projektu: `kanał-<slug-nazwy-kanału>` gdy da się pobrać nazwę przez API, fallback `kanał-<6-znakowy-sufix-channelId>` — bez pytania użytkownika.
- (2) `create` i `list` znikają z helpa i nie mają już uzasadnienia funkcjonalnego: `create` na kanale zlinkowanym → komunikat kierujący do `/vedra config`; na niezlinkowanym działa jako ukryty alias `ensureChannelSpace` (kompatybilność, brak wpisu w helpu). `list` działa jak dziś, ukryte (brak wpisu w helpu) — przy modelu 1:1 wybór z listy nie jest potrzebny, a listowanie wszystkich projektów tenanta łamie izolację kanałów.
- (3) `config` edytuje wyłącznie projekt PRIVATE zlinkowany z tym kanałem; PUBLIC nigdy nie jest edytowalny przez `config`. Brak linku → `config` sam dowołuje `ensureChannelSpace` i otwiera modal (nie odsyła do `create`).
- (4) `status` pokazuje pełny kontekst konfiguracji kanału (nazwa, opis, branża przestrzeni + informacja o bazie publicznej), nie tylko fakt linku.

## 2. Kontekst

### Stan na 2026-09-08
- **Model:** 1 kanał → 1 projekt PRIVATE + doklejany PUBLIC (`TenantProjectService.getProjectIdsForChannel`, `createPrivateAndLink` z `private_metadata=channelId`, spec 003 FR-10/17).
- **Wejście na kanał:** wyłącznie ręczne — `/invite @Vedra` (po stronie Slacka) + potem ręczne `/vedra create` (modal: nazwa/opis/branża, `SlackModalService.openCreateProjectModal:20-135`). Brak obsługi eventu dołączenia bota; `SlackEvent.fromRawBody` parsuje tylko `type/subtype/text/channel/threadTs/user/team` (`SlackEvent.kt:3-44`), `MessageEventHandler` obsługuje tylko `message`.
- **Komendy:** parser `create|link|unlink|list|status|help`, reszta → `HelpCommand` (`SlackCommandParser.kt:31-41`). Help pokazuje tylko `create|list|status` (`HelpCommandHandler.kt:10-16`). `link|unlink` działają, ukryte (003 FR-06/07).
- **Edycja:** brak. Nazwa/opis/branża ustawiane raz w modalu `create`; potem niezmienialne ze Slacka (`TenantProjectService` ma tylko `createProject/createPrivateAndLink/ensurePublicProject`, brak `update`).
- **Branże:** `IndustryService.getOrCreateIndustry` + select w modalu (`SlackModalService.kt:27-42`, `SlackInteractiveRequestHandler.kt:140-156`).
- **Uprawnienia:** 0 ról (003 FR-01/04) — każdy członek kanału może wołać komendy.

### Docelowa ścieżka użycia
```
1. User: /invite @Vedra na #nowy-kanał
2. Slack → event member_joined_channel (bot dołączył) → Vedra: resolve tenant → ensure PRIVATE space + link → wiadomość powitalna w kanale
3. User pisze zdania → cichy ingest do auto-projektu; pyta z `?` → odpowiedź z PRIVATE + PUBLIC (zero komend konfiguracyjnych)
4. User: /vedra config → modal z aktualną nazwą/opisem/branżą → zapis → status potwierdza
5. /vedra help pokazuje: status, config, help (bez create/list/link/unlink)
```

### Odpowiadanie na pytania (bez komendy — istnieje, bez zmian)
Pytania nie mają dedykowanej komendy slash i nie dostają jej w tym specu. Działa detekcja dorozumiana: `MessageEventHandler.isQuestion()` (`endsWith("?") || contains("@vedra")`, `MessageEventHandler.kt:111-114`) → outbox `SLACK_MESSAGE_ANSWER {tenantId, channel, threadTs, question, projectIds}` → `SlackMessageAnswerHandler` → `KnowledgeService.answerQuestion(question, projectIds, tenantId)` (RAG tenant+PRIVATE+PUBLIC + LLM, progi HIGH/MEDIUM/MIN) → odpowiedź w wątku z przyciskami `Pomogło / Nie pomogło / Udostępnij 🌍`. Zwykłe zdania na kanale zlinkowanym → cichy `CHANNEL_MESSAGE_INGESTION`; na niezlinkowanym → cisza. Ten spec nie zmienia tej ścieżki; zmienia tylko teksty `status` (FR-13), które ją opisują.

## 3. Wymagania funkcjonalne

### 3.1. Auto-przestrzeń przy dodaniu bota do kanału
- **FR-01:** Nowy handler `BotChannelJoinHandler` (`SlackEventHandler`, `supports = type == "member_joined_channel" && joinedUserIsBot`): triggerem jest event dołączenia **bota Vedry** do kanału (detekcja: `event.user == bot_user_id` z `slack_installations` albo `subtype/bot_id`; dokładny warunek do domknięcia przy implementacji przez podgląd payloadu z `/invite`). Zwykłe dołączenia ludzi — ignorowane (cisza, log DEBUG).
- **FR-02:** Logika `TenantProjectService.ensureChannelSpace(tenantId, channelId, channelName?): ChannelSpaceResult` (`@Transactional`, idempotentna):
  a. `findByChannelId(channelId)` hit → `return AlreadyLinked(existing)` (bez duplikatu, bez 500),
  b. miss → wyznacz nazwę: `kanał-<slug(channelName, max 40)>` gdy nazwa znana, inaczej `kanał-<channelId.takeLast(6).lowercase()>`; kolizja `UNIQUE(tenant_id,name)` → dopnij `-2`, `-3`… (pętla max 10, potem fallback z timestamp),
  c. `createProject(PRIVATE, description="Przestrzeń auto-utworzona dla kanału <name>. Doprecyzuj przez /vedra config.", industry=null)` + `save ChannelProject(channelId → projectId)` w tej samej transakcji,
  d. `logger.info "[SPACE] Auto-provisioned channel=X project=Y tenant=..."`.
- **FR-03:** Nazwa kanału best-effort: `SlackClientPort.getChannelName(channelId, teamId)` (nowe `conversations.info`, scope `channels:read` już w 006 FR-01). Brak scope/HTTP błąd → fallback z FR-02b + `warn`, NIE blokuje utworzenia przestrzeni.
- **FR-04:** Po utworzeniu bot wysyła **jedną** wiadomość powitalną na kanał (jawna, nie ephemeral — kanał ma wiedzieć, że przestrzeń żyje):
  `":wave: Vedra dołączyła! Utworzyłam przestrzeń *<nazwa>*. Pisz śmiało — zbieram wiedzę. Szczegóły poprawisz przez `/vedra config`. Pomoc: `/vedra help`."`
  Gdy `AlreadyLinked` → brak wiadomości (cisza, tylko log). Błąd wysyłki → `warn`, przestrzeń zostaje.
- **FR-05:** Kanał bez nazwy / DM / `channelId` null → projekt z fallbackową nazwą (FR-02b), bez 500. Event bez `team_id` → jak dziś (`TenantExternalIdMissingException`, brak provisioningu).

### 3.2. Help — wyłącznie `status|config|help`
- **FR-06:** Nowy tekst `HelpCommandHandler.handle()` (jedyne źródło prawdy o komendach):
  ```
  *Komendy Vedra:*
  • `/vedra status` - Pokaż konfigurację przestrzeni tego kanału
  • `/vedra config` - Edytuj nazwę, opis i branżę przestrzeni tego kanału
  • `/vedra help` - Pokaż tę pomoc
  ```
  Uzasadnienie usunięć: `create` nie ma uzasadnienia (przestrzeń powstaje z invite, FR-02; awaryjne dowołanie robi sam `config`, FR-08b); `list` nie ma uzasadnienia (model 1 kanał = 1 przestrzeń — nie ma z czego wybierać, a lista zdradza nazwy cudzych projektów tenanta). `create|list|link|unlink` nadal działają jako ukryte aliasy legacy (kompatybilność, brak wpisu w helpu). Nieznana komenda → jak dziś (`UNKNOWN_COMMAND_MESSAGE` → help).
- **FR-07:** `SlackCommandParser.parse()` dostaje jedną nową gałąź: `"config" -> ConfigCommand(channelId, workspaceId, triggerId)`; gałęzie `create|list|link|unlink` bez zmian funkcjonalnych (tylko znikają z helpa).

### 3.3. `/vedra config` — jedyny edytor szczegółów kanału
- **FR-08:** `ConfigCommandHandler.handle()`:
  a. resolve tenant (`SlackTenantResolver`, jak `CreateProjectCommandHandler`),
  b. `getPrivateLinkedProjectId(channelId, tenantId)` null → dowołaj `ensureChannelSpace` (FR-02) i otwórz modal dla świeżej przestrzeni (NIGDY komunikat "Użyj `/vedra create`" — `create` nie jest reklamowane),
  c. projekt PUBLIC zlinkowany (teoretycznie) → `"Przestrzeni publicznej nie można edytować."`,
  d. inaczej → `SlackModalService.openConfigModal(triggerId, channelId, tenantId, project)` — modal jak `create`, ale **prefill**: `initial_value` = aktualna nazwa/opis, `initial_option` = aktualna branża, `private_metadata=channelId`, `callback_id=config_project_modal`, tytuł `Konfiguracja przestrzeni`, submit `Zapisz`. Zwraca `""` (modal otwarty) jak `create`.
- **FR-09:** Submit `config_project_modal` w `SlackInteractiveRequestHandler` (obok `create_project_modal`): odczyt `private_metadata → channelId`, resolve tenant, walidacja nazwy (niepusta, trim, max 80 — te same reguły co `create`), `resolveIndustryName` reuse bez zmian, potem `TenantProjectService.updateLinkedProject(tenantId, channelId, name, description, industry): UpdateResult`.
- **FR-10:** `updateLinkedProject` (`@Transactional`):
  ```kotlin
  sealed class UpdateResult { data class Updated(val project: TenantProject) : UpdateResult(); data object NoLinkedSpace : UpdateResult(); data object PublicNotEditable : UpdateResult(); data class NameTaken(val name: String) : UpdateResult() }
  ```
  Reguły: brak linku → `NoLinkedSpace`; projekt PUBLIC → `PublicNotEditable`; `findByTenantIdAndName(name)` trafia w **inny** projekt (inne id) → `NameTaken` (linku nie ruszamy); ten sama nazwa/dane → `Updated` (no-op zapis dopuszczalny). Sukces = update pól `name/description/industry` istniejącego wiersza (bez zmiany `id`, bez ruszania `channel_projects`, bez re-embeddingu — embeddingi przeliczą się przy kolejnym ingeście).
- **FR-11:** Odpowiedzi submitu (modal `response_action=update`, style jak `create`):
  - `Updated` → `":white_check_mark: *Zapisano konfigurację*\n\n*<name>*\nBranża: <x lub nie wybrano>\n[Opis: ...]"`,
  - `NameTaken` → błąd pod polem nazwy (jak `AlreadyExists` w create): `"Projekt *<name>* już istnieje."`,
  - `NoLinkedSpace/PublicNotEditable` → teksty z FR-08b/c. Nigdy 500 do Slacka.
- **FR-12:** `create` jako ukryty alias legacy (decyzja 2, brak wpisu w helpu): kanał zlinkowany → NIE tworzy projektu, zwraca ephemeral/modal-update: `"Ten kanał ma już przestrzeń *<name>*. Zmień szczegóły przez \`/vedra config\`."` Kanał niezlinkowany → `createPrivateAndLink` jak w 003 (ścieżka awaryjna dla starych kanałów i testów, nieadvertised).
- **FR-13:** `status` jako podgląd konfiguracji (decyzja 4): `StatusCommandHandler` ładuje pełny projekt (`name/description/industry/visibility` z `findByTenantIdAndId`, nie tylko `projectName`), format:
  - zlinkowany PRIVATE: `"*Konfiguracja przestrzeni tego kanału*\n• Nazwa: *<name>*\n• Opis: <desc lub —>\n• Branża: <industry lub nie ustawiono>\nOdpowiedzi szukane w tej przestrzeni + baza publiczna.\nZmień przez \`/vedra config\`."`
  - niezlinkowany: `"Ten kanał nie ma jeszcze własnej przestrzeni.\nOdpowiedzi szukane tylko w bazie publicznej.\nUruchom \`/vedra config\`, aby utworzyć i skonfigurować przestrzeń."` (brak wzmianki o `create`/`list`/`link`).
  - `list` nie jest nigdzie wspominane (celowo — brak uzasadnienia).

## 4. Wymagania niefunkcjonalne
- **NFR-01:** Idempotentność join: N eventów `member_joined_channel` dla tego kanału → 1 wiersz `channel_projects` + 1 projekt (chroni `PK(channel_id)` + pętla unikalności nazw + `AlreadyLinked`).
- **NFR-02:** Transakcyjność: projekt + link albo nic (`@Transactional` w `ensureChannelSpace` i `updateLinkedProject`). Wiadomość powitalna i `openModal` PO commicie (nigdy w transakcji).
- **NFR-03:** Koszt: zero LLM/embedding w ścieżce join i config (jedynie 1x `conversations.info` best-effort). 0 ról — brak sprawdzania uprawnień.
- **NFR-04:** Logi: `INFO [SPACE] ...` dla auto-prowizji i config (`Auto-provisioned`, `Already linked, skipping`, `Updated space`), `WARN` dla fallbacku nazwy i błędów Slack API. Bez tokenów i treści wiadomości w logach poza 50-znakowym head jak dziś.
- **NFR-05:** Kompatybilność: `create|list|link|unlink` działają bez zmian funkcjonalnych, ukryte z helpa; eventy `message` i ścieżka pytań (`?`/`@vedra` → `answerQuestion`) bez zmian.

## 5. Modele danych
- Brak zmian schema. Wykorzystane: `channel_projects PK(channel_id)` (V9), `tenant_projects + uniq_public_project_per_tenant` (V16), `slack_installations(team_id → bot_user_id)` (006/V19, do detekcji bota).
- Nowe (kod, nie DB):
  ```kotlin
  data class ConfigCommand(val channelId: String, val workspaceId: String, val triggerId: String)
  sealed class ChannelSpaceResult { data class Created(val project: TenantProject) : ChannelSpaceResult(); data class AlreadyLinked(val project: TenantProject?) : ChannelSpaceResult() }
  sealed class UpdateResult { data class Updated(val project: TenantProject) : UpdateResult(); data object NoLinkedSpace : UpdateResult(); data object PublicNotEditable : UpdateResult(); data class NameTaken(val name: String) : UpdateResult() }
  ```
- `TenantProjectRepository`: nowa metoda `update(project): TenantProject` jeśli brak (zwykle `save` z id wystarczy — do potwierdzenia przy implementacji).

## 6. Architektura i komponenty

### 6.1. Bez zmian
| Klasa/plik | Decyzja |
|---|---|
| `link/*`, `unlink/*`, `create/*` (modal create), `list/*`, `TenantProjectService.linkChannelToProject/unlinkChannel` | bez zmian funkcjonalnych, `create|list` spadają do ukrytych (brak wpisu w helpu) |
| `MessageEventHandler`, ingest/answer (`isQuestion`, `SLACK_MESSAGE_ANSWER`, `KnowledgeService.answerQuestion`), promocja, onboarding (004), OAuth (006) | bez zmian |
| `IndustryService`, `resolveIndustryName`, opcje branż w modalu | reuse bez zmian |

### 6.2. Modyfikacje
| Klasa | Zmiana |
|---|---|
| `SlackCommandParser` | gałąź `config` (FR-07); `create|list` zostają, tylko znikają z helpa |
| `HelpCommandHandler` | tekst FR-06 (tylko status/config/help) |
| `StatusCommandHandler` + `TenantProjectService.getChannelStatus` | podgląd pełnej konfiguracji FR-13 (name/description/industry); sygnatura `getChannelStatus` może zwrócić pełny projekt zamiast samego `projectName` |
| `SlackModalService` | `openConfigModal(...)` z prefillem (FR-08d) obok `openCreateProjectModal` |
| `SlackInteractiveRequestHandler` | routing `config_project_modal` → `updateLinkedProject` + odpowiedzi FR-11; `create` na zlinkowanym → komunikat FR-12 |
| `TenantProjectService` | `ensureChannelSpace` (FR-02) + `updateLinkedProject` (FR-10) |
| `SlackClientPort` + `SlackClientAdapter` | `getChannelName(channelId, teamId): String?` (`conversations.info`, token per team z 006) |
| `SlackEvent` | pola `joinedUser: String?` (z `event.user`) do detekcji bota, jeśli brak |

### 6.3. Nowe
| Klasa/plik | Opis |
|---|---|
| `slack/service/command/config/ConfigCommand.kt` + `ConfigCommandHandler.kt` | FR-08 |
| `slack/handler/BotChannelJoinHandler.kt` | FR-01 + woła `ensureChannelSpace` + wiadomość powitalną FR-04 |
| Testy sekcji 10 | — |

## 7. Przepływy główne

### 7.1. Invite bota → auto-przestrzeń
```
1. /invite @Vedra → Slack POST events {type: member_joined_channel, user: <bot>, channel: C123, team_id: T123}
2. SlackEventRequestHandler → EventProcessor → BotChannelJoinHandler.supports=true
3. withTenant(T123) → ensureChannelSpace(tenant, C123, getChannelName(C123)?) → commit (projekt PRIVATE + link)
4. postMessage(C123, powitanie FR-04) — tylko gdy Created
5. Kolejne message-eventy działają jak dziś (ingest do PRIVATE, pytania PRIVATE+PUBLIC)
```

### 7.2. `/vedra config` happy path
```
1. User: /vedra config → parser → ConfigCommand → handler: linked PRIVATE? tak → openConfigModal (prefill)
2. User edytuje → view_submission config_project_modal → updateLinkedProject → modal update "Zapisano konfigurację"
3. /vedra status pokazuje pełną konfigurację (nazwa/opis/branża, FR-13)
```

### 7.3. Legacy po auto-prowizacji (ukryte, nieadvertised)
```
1. Kanał zlinkowany (normalny po FR-02) → /vedra create → "Ten kanał ma już przestrzeń *X*. Zmień szczegóły przez /vedra config."
2. Kanał niezlinkowany (stary kanał sprzed wdrożenia, unlink ręczny) → /vedra create → createPrivateAndLink jak w 003; /vedra config → ensureChannelSpace + modal (bez odsyłania do create)
3. /vedra list → działa jak dziś (ukryte, do debugu); /vedra status nigdzie go nie wspomina
```

## 8. Schemat migracji bazy danych
Brak. Opcjonalny backfill jednorazowym jobem (poza DDL): dla każdego `channel_projects`-less kanału znanego z historii — nie da się wyliczyć listy kanałów z DB, więc stare kanały dostają przestrzeń leniwie przy następnym `/vedra create` lub join-evencie (FR-12/FR-02). Rozważyć `V20__channel_name_cache(channel_id PK, name TEXT)` tylko jeśli `conversations.info` okaże się za wolne — poza tym specem.

## 9. Teksty odpowiedzi (PL)
- Powitanie (FR-04): `":wave: Vedra dołączyła! Utworzyłam przestrzeń *<nazwa>*. Pisz śmiało — zbieram wiedzę, na pytania odpowiadam w wątku. Szczegóły poprawisz przez `/vedra config`. Pomoc: `/vedra help`."`
- Help: blok FR-06 (3 pozycje: status/config/help; zakaz słów `create|list|link|unlink` w tekście).
- Status zlinkowany (FR-13): `"*Konfiguracja przestrzeni tego kanału*\n• Nazwa: *<name>*\n• Opis: <desc lub —>\n• Branża: <industry lub nie ustawiono>\nOdpowiedzi szukane w tej przestrzeni + baza publiczna.\nZmień przez \`/vedra config\`."`
- Status niezlinkowany (FR-13): `"Ten kanał nie ma jeszcze własnej przestrzeni.\nOdpowiedzi szukane tylko w bazie publicznej.\nUruchom \`/vedra config\`, aby utworzyć i skonfigurować przestrzeń."`
- Config PUBLIC: `"Przestrzeni publicznej nie można edytować."`
- Create na zlinkowanym (ukryte): `"Ten kanał ma już przestrzeń *<name>*. Zmień szczegóły przez \`/vedra config\`."`
- Config zapis: `":white_check_mark: *Zapisano konfigurację*\n\n*<name>*\nBranża: <x / nie wybrano>\n[Opis: ...]"`
- Config kolizja: `"Projekt *<name>* już istnieje."`

## 10. Testy

### 10.1. Jednostkowe (kod)
- `TenantProjectServiceTest`: `ensureChannelSpace` miss → projekt PRIVATE z auto-nazwą + link w 1 transakcji; hit → `AlreadyLinked`, bez nowego projektu; kolizja nazwy → sufiks `-2`; `updateLinkedProject` happy path (zmiana nazwy/opisu/branży, to samo `id`, link nietknięty), `NameTaken` (cudza nazwa → brak zapisu), `NoLinkedSpace`, `PublicNotEditable`, no-op ta sama nazwa → `Updated`.
- `SlackCommandParserTest`: `config` → `ConfigCommand` (z `channelId/workspaceId/triggerId`); `create|list|link|unlink` nadal parsowane (wszystkie ukryte z helpa).
- `HelpCommandHandlerTest` (nowy): tekst zawiera wyłącznie `status`, `config`, `help`; NIE zawiera `create`, `list`, `link` ani `unlink`.
- `ConfigCommandHandlerTest` (nowy): brak linku → `ensureChannelSpace` + `openConfigModal` (bez odsyłania do `create`); PUBLIC → nieedytowalny; PRIVATE → `openConfigModal` z danymi projektu.
- `StatusCommandHandlerTest` (nowy/update): zlinkowany → tekst FR-13 z nazwą/opisem/branżą; niezlinkowany → tekst FR-13 bez wzmianki o `create`/`list`/`link`; brak `workspaceId` → komunikat jak dziś.
- `SlackInteractiveRequestHandlerTest`: submit `config_project_modal` → `updateLinkedProject` + modal `Zapisano`; `NameTaken` → błąd nazwy; submit `create` na zlinkowanym → komunikat FR-12 bez `createProject`.
- `BotChannelJoinHandlerTest` (nowy): event bota → `ensureChannelSpace` + powitanie tylko gdy `Created`; event człowieka → cisza (`verify(exactly=0)`); brak `team_id` → brak provisioningu.
- `SlackClientAdapterTest`: `getChannelName` woła `conversations.info` z tokenem teamu; błąd API → null (fallback nazwy w serwisie).

### 10.2. Integracyjne
- `ChannelSpaceFlowTest` (nowy): event join nowego kanału → wiersz `tenant_projects(PRIVATE)` + `channel_projects`; drugi join → bez duplikatów.
- `ConfigFlowTest` (nowy): join → `/vedra config` submit (nowa nazwa/opis/branża) → `status` pokazuje nowe (FR-13); kolizja z projektem sąsiada → `NameTaken`, stare dane nietknięte.
- `CreateAfterJoinTest`: join → ukryte `/vedra create` → brak nowego projektu, komunikat FR-12.

### 10.3. E2E (Slack mock / staging)
- Czysty kanał: `/invite @Vedra` → auto-wiadomość powitalna → `/vedra status` od razu pokazuje pełną konfigurację (bez `create`) → zdanie → pytanie `?`/`@vedra` odpowiada w wątku → `/vedra config` zmienia nazwę → `/vedra help` pokazuje 3 komendy bez create/list/link/unlink.

### 10.4. Manualne (delta do `test-plan/TEST_CASES.md` i `TEST_PLAN.md`)
- **FT-01/FT-03 (update):** `status` zwraca format FR-13 (nazwa/opis/branża); `list` oznaczone legacy (do wycofania z planu po weryfikacji).
- **FT-02 (update):** help zwraca blok FR-06 (3 pozycje: status/config/help); asercja negatywna: brak słów `create`/`list`/`link`/`unlink` w odpowiedzi.
- **FT-04 (deprecated → ukryte):** `create` nie jest już ścieżką usera; wariant 04c: `create` na kanale zlinkowanym → komunikat FR-12, brak nowego projektu. Docelowo FT-04 do usunięcia po okresie kompatybilności.
- **FT-18 (nowy) Invite → auto-przestrzeń:** na nowym kanale `/invite @Vedra` → w ≤30 s wiadomość powitalna z nazwą; `/vedra status` pokazuje pełną konfigurację FR-13 (bez `create`); drugi invite/kolejny join → brak duplikatu i brak drugiej wiadomości.
- **FT-19 (nowy) `/vedra config` happy path:** `/vedra config` → modal prefillowany (nazwa/opis/branża jak z FT-18) → zmień wszystkie trzy → `Zapisz` → modal `Zapisano konfigurację`; kontrola `/vedra status` pokazuje nowe dane (bez użycia `list`).
- **FT-20 (nowy) `/vedra config` błędy:** a) config na kanale po `/vedra unlink` → auto-odtworzenie przestrzeni + modal (bez wzmianki o `create`); b) zmiana nazwy na istniejącą (z innego kanału) → `"Projekt *X* już istnieje."`, stare dane nietknięte.
- **TEST_PLAN.md (update):** kolejność: `FT-18` zaraz po `FT-01–FT-03`; `FT-19→FT-20` zamiast `FT-04–FT-05`; pytanie kontrolne: "Czy ruszałem join/config/help/status? → FT-18–FT-20 + FT-02".

## 11. Kolejność wdrożenia
1. `ConfigCommand` + gałąź parsera + `updateLinkedProject` + testy 10.1 (serwisowo, bez eventów — `config` działa na ręcznych linkach).
2. `openConfigModal` + submit `config_project_modal` + FR-11/FR-12 + `HelpCommandHandler` FR-06.
3. `getChannelName` w porcie/adapterze + `ensureChannelSpace`.
4. `BotChannelJoinHandler` + powitanie FR-04 + whitelist/detekcja bota (podgląd realnego payloadu `/invite` na stagingu przed merge).
5. Testy 10.2 → 10.3 → manualne FT-18–FT-20 na stagingu.
6. Update `TEST_CASES.md` (FT-02/FT-04 + FT-18–FT-20), `TEST_PLAN.md` (kolejność), `SETUP.md` (_INSTALL_: dopisać "po `/invite` przestrzeń tworzy się sama").
7. Backfill-świadomość: ogłosić, że stare kanały bez linku dostają przestrzeń przy następnym invite-evencie lub przez jednorazowe `/vedra create`.

## 12. Ograniczenia i założenia
- Detekcja "bot dołączył" zależy od payloadu Slacka (`member_joined_channel` + `user == bot_user_id`); jeśli Slack wyśle inny typ (np. `app_mention` bez join), handler do poprawki w implementacji bez zmiany kontraktu FR-02.
- Nazwa kanału z API może być niedostępna (DM, brak scope, rate-limit) — fallback `kanał-<sufix>` jest pełnoprawny, do poprawy przez `/vedra config`.
- Auto-projekt startuje z `industry=null` i generycznym opisem — celowy trade-off (zero pytań przy invite); doprecyzowanie to rola `config`.
- Limity planu Free (max 3 projekty, TODO pkt 15) NIE są egzekwowane w tym specu — auto-prowizja może przekroczyć limit; egzekucja osobnym specem pakietowym.
- RODO/usuwanie przestrzeni przy `member_left_channel` (bot usunięty) poza zakresem — kanał po kicku czyta PUBLIC, link zostaje do ręcznego `unlink` lub re-invite (idempotentny `AlreadyLinked`).
