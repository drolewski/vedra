# Specyfikacja: Onboarding workspace Slack → Tenant + seed (004)

## 1. Cel

Jedna ścieżka użycia: osoba uprawniona (Workspace Admin) dodaje Verdrę do Przestrzeni Slack → system tworzy nowego Tenanta i wszystkie potrzebne mu elementy startowe (struktury, feature'y, limity). Zero konfiguracji po stronie klienta.

Zdarzenia okołorejestracyjne (docelowo integracje zewnętrzne) w tym specu wyłącznie jako logi w konsoli:
1. email powitalny do zarządcy danego Slacka,
2. email do właściciela Vedry z informacją o nowym kliencie,
3. wygenerowanie dokumentów związanych z ofertą.

## 2. Kontekst

### Stan na 2026-09-08
- **Install:** manualny Slack App, 3x Request URL (`/api/v1/slack/events,/commands,/interactive`), jeden globalny `SLACK_BOT_TOKEN` (`SlackConfig.kt:9-21`). Brak OAuth per workspace, brak tabeli instalacji.
- **Tenant:** lazy auto-provisioning przy pierwszym evencie/komendzie: `TenantService.resolveByExternalId(team_id, workspaceName)` (`TenantService.kt:14-33`) wołany z `SlackTenantResolver.withTenant/withTenantResult` (`SlackTenantResolver.kt:17-42`). `createTenant` zapisuje tylko `Tenant(name, externalId)` — brak seeda, brak eventu.
- **Seed PUBLIC:** wyłącznie leniwy `TenantProjectService.ensurePublicProject(tenantId)` (`TenantProjectService.kt:98-109`) przy promocji (spec 003 FR-26) + seed migracyjny `V16` dla istniejących tenantów. Nowy tenant po pierwszym `message` nie ma nic aż do pierwszej promocji.
- **Pakiety:** tylko opis w `TODO.md:15-39` (Free / I / II / III / Enterprise). Brak egzekucji limitów w kodzie.
- **Powiadomienia:** brak. Brak `ApplicationEvent`, brak serwisu mailowego, brak generatora dokumentów.

### Docelowa ścieżka użycia
```
1. Workspace Admin: Slack → Add apps → Vedra → Install (+ /invite @Vedra na kanał)
2. Dowolny pierwszy event z team_id (message / command / interactive / app_home)
   → resolve-or-create Tenant → seed (PUBLIC + defaults) → log-zdarzenia (3x logger.info)
3. Admin dostaje (docelowo mailem, dziś logiem) instrukcję: /vedra create, /vedra status
4. Właściciel Vedry dostaje (docelowo mailem, dziś logiem) info o nowym kliencie
5. System odkłada (docelowo generuje, dziś loguje) dokumenty ofertowe
```

## 3. Wymagania funkcjonalne

### 3.1. Trigger — pierwsze dotknięcie workspace = provisioning
- **FR-01:** Każdy punkt wejścia Slack z `team_id` musi przechodzić przez jedną metodę provisionującą: `TenantService.resolveByExternalId()` pozostaje jedynym miejscem tworzenia tenanta. `SlackTenantResolver`, `SlackEvent.fromRawBody`, wszystkie `CommandHandler`y i `SlackInteractiveRequestHandler` bez zmian kontraktu — korzystają z efektu automatycznie.
- **FR-02:** Rozróżnienie `created vs resolved`: `resolveByExternalId` zwraca `Tenant` jak dziś, ale wewnętrznie publikuje zdarzenie domenowe `TenantProvisionedEvent(tenantId, externalId, workspaceName, installerUserId?)` wyłącznie gdy wiersz został faktycznie wstawiony (flaga `isNew`). Ponowne eventy z tego samego `team_id` NIE publikują zdarzenia (idempotentność po `UNIQUE(external_id)`).
- **FR-03:** Brak `team_id` → bez zmian: `TenantExternalIdMissingException` + log error, brak provisioningu, brak zdarzeń.

### 3.2. Seed nowego Tenanta (atomowo z kreacją)
- **FR-04:** Nowy `@Transactional` `TenantProvisioningService.provisionTenant(externalId, workspaceName, installerUserId?) : ProvisionResult(Tenant, isNew)`:
  a. `tenantRepository.findByExternalId` → hit = `return(existing, false)`,
  b. miss = `save Tenant(name=workspaceName?:externalId, externalId)` + seed (c–e) w tej samej transakcji + publikacja `TenantProvisionedEvent`,
  c. `tenantProjectService.ensurePublicProject(tenantId)` → `Baza ogólna / Wiedza współdzielona tenanta / PUBLIC`,
  d. defaults pakietu **Free** (TODO.md:17-19) jako stałe w kodzie, bez nowej tabeli w tym specu: `maxProjects=3, maxIndustries=3(+general), monthlyIngest=500, monthlyAnswers=100` — przechowywane wyłącznie w logu zdarzenia i zwracane w `ProvisionResult` (egzekucja limitów poza zakresem, osobny spec),
  e. `logger.info Provisioned tenant=... externalId=... publicProjectId=... plan=Free`.
- **FR-05:** `TenantService.resolveByExternalId` deleguje kreację do `TenantProvisioningService` (przeniesienie `createTenant`, usunięcie duplikacji). Sygnatura publiczna bez zmian.
- **FR-06:** Wyścig (2 eventy równolegle z nowego workspace): `UNIQUE(external_id)` wygrywa jeden; przegrany łapie constraint → `findByExternalId` retry → `return(existing, false)`, bez zdarzenia, bez duplikatu PUBLIC (chroni też `uniq_public_project_per_tenant` z V16).

### 3.3. Zdarzenia post-rejestracyjne — dziś wyłącznie logi
- **FR-07:** `TenantProvisionedEvent` (Spring `ApplicationEvent`, sync, brak outbox w tym specu):
  ```kotlin
  data class TenantProvisionedEvent(val tenantId: UUID, val externalId: String, val workspaceName: String, val installerUserId: String?, val plan: String = "Free")
  ```
- **FR-08:** Jeden listener `TenantProvisionedListener` (lub 3 osobne beany, decyzja implementacyjna) → 3x `logger.info` o stałej treści (PL), każdy z prefiksem umożliwiającym grep:
  1. `[ONBOARDING] Welcome email → workspace admin (installerUserId=...|unknown) tenant=...: "Dziękujemy za dodanie Vedry! Zacznij od /invite @Vedra na kanał, potem /vedra create. Pomoc: /vedra help."`
  2. `[ONBOARDING] Owner notification → owner@vedra: "Nowy klient: <workspaceName> (<externalId>), tenant=<tenantId>, plan=Free."` (adres ownera jako stała konfiguracyjna `vedra.owner-email`, default `owner@vedra.local`, bez wysyłki)
  3. `[ONBOARDING] Offer docs → generated for tenant=...: "Oferta_Free_<externalId>.md (szablon: pakiet Free, 3 projekty, 500 ingest / 100 pytań mies.)"` (bez zapisu pliku w tym specu — tylko log; fizyczny generator poza zakresem)
- **FR-09:** Listener NIE rzuca wyjątków na zewnątrz (try/catch + warn). Błąd logowania zdarzeń nigdy nie wycofuje transakcji provisioningu ani nie zwraca 500 do Slacka.
- **FR-10:** Brak nowych wywołań sieciowych (Slack API poza istniejącym `getTeamName`, brak SMTP, brak S3/dysku). Jedyny dozwolony efekt uboczny to log.

### 3.4. Widoczność dla użytkownika Slacka
- **FR-11:** Brak nowych komend i modalów w tym specu. Pierwsza interakcja użytkownika działa jak dziś (`/vedra status` na nowym workspacu: `"Ten kanał nie ma własnego projektu..."` per spec 003 FR-19) — tenant i PUBLIC już istnieją w tle.
- **FR-12:** Ephemeral z instrukcją startową po pierwszym `/vedra` z nowego tenanta — poza zakresem (kandydat na follow-up po weryfikacji logów).

## 4. Wymagania niefunkcjonalne
- **NFR-01:** Idempotentność: N eventów z tego samego `team_id` → 1 wiersz `tenants`, 1 wiersz PUBLIC, 1x `TenantProvisionedEvent`.
- **NFR-02:** Transakcyjność: tenant + PUBLIC albo nic. Logi-zdarzenia po commicie (`@TransactionalEventListener(AFTER_COMMIT)`), nie w trakcie.
- **NFR-03:** Koszt: zero wywołań LLM/embedding przy provisioningu. `getTeamName` jak dziś (cache poza zakresem).
- **NFR-04:** Logi grepowalne: wszystkie linie onboardingu z prefiksem `[ONBOARDING]`, poziom INFO, język jak w istniejących logach (mieszany EN/PL dopuszczalny, treści user-facing PL).
- **NFR-05:** Brak nowych sekretów i konfiguracji obowiązkowej. `vedra.owner-email` opcjonalne z defaultem.

## 5. Modele danych
- Brak zmian schema w tym specu. Wykorzystane: `tenants(id, name, external_id UNIQUE, active)` (`V11`, `V14`), `tenant_projects + uniq_public_project_per_tenant` (`V16`).
- Nowe (kod, nie DB):
  ```kotlin
  data class ProvisionResult(val tenant: Tenant, val isNew: Boolean, val plan: String = "Free")
  data class TenantProvisionedEvent(val tenantId: UUID, val externalId: String, val workspaceName: String, val installerUserId: String?, val plan: String = "Free")
  object OnboardingPlans { const val DEFAULT = "Free"; val LIMITS = mapOf(...) } // Free z TODO.md
  ```

## 6. Architektura i komponenty

### 6.1. Bez zmian
| Klasa/plik | Decyzja |
|---|---|
| `SlackConfig`, Request URL-e, `SLACK_BOT_TOKEN` | bez zmian (OAuth per workspace poza zakresem) |
| `SlackTenantResolver`, `SlackEvent`, `MessageEventHandler`, `CommandDispatcher`, `SlackInteractiveRequestHandler` | bez zmian kontraktu, zyskują provisioning za darmo |
| `TenantProjectService.ensurePublicProject` | bez zmian (użyte jako krok seeda) |

### 6.2. Modyfikacje
| Klasa | Zmiana |
|---|---|
| `TenantService` | `createTenant` prywatne → delegacja do `TenantProvisioningService.provisionTenant`; `resolveByExternalId` bez zmian sygnatury |
| `application.yml` | opcjonalne `vedra.owner-email` (default `owner@vedra.local`) |

### 6.3. Nowe
| Klasa/plik | Opis |
|---|---|
| `domain/service/TenantProvisioningService.kt` | `provisionTenant(externalId, workspaceName, installerUserId?): ProvisionResult` (`@Transactional`), seed PUBLIC + publikacja eventu po zapisie |
| `domain/event/TenantProvisionedEvent.kt` | zdarzenie domenowe (sekcja 3.3) |
| `infrastructure/onboarding/TenantProvisionedListener.kt` | `@TransactionalEventListener(AFTER_COMMIT)` → 3x log FR-08, try/catch per linia |
| `infrastructure/onboarding/OnboardingPlans.kt` (lub obiekt w provisioningu) | stałe limitów Free z TODO.md |

## 7. Przepływy główne

### 7.1. Pierwszy kontakt z nowego workspace (happy path)
```
1. Slack POST /api/v1/slack/events|commands|interactive {team_id: T123, user_id: U456, ...}
2. SlackTenantResolver → TenantService.resolveByExternalId(T123, teamName?)
3. → TenantProvisioningService.provisionTenant: miss → INSERT tenants + ensurePublicProject → publish TenantProvisionedEvent → commit
4. AFTER_COMMIT → TenantProvisionedListener: log welcome + log owner + log offer-docs
5. Pierwotny handler Slacka kontynuuje normalnie (status/list/create/answer/ingest per spec 003)
```

### 7.2. Powtórny kontakt (ten sam workspace)
```
1. POST z team_id=T123 → findByExternalId hit → return(existing, isNew=false)
2. Brak eventu, brak logów [ONBOARDING], brak duplikatów. Handler kontynuuje.
```

### 7.3. Wyścig / błąd
```
1. 2x równoległy POST T123 → jeden INSERT wygrywa, drugi dostaje unique-violation → retry findByExternalId → hit → isNew=false.
2. Błąd listenera (np. null workspaceName) → warn, provisioning pozostaje zacommitowany, Slack dostaje normalną odpowiedź.
```

## 8. Schemat migracji bazy danych
Brak. Weryfikacja przed wdrożeniem: `UNIQUE` na `tenants(external_id)` musi istnieć (do potwierdzenia przy implementacji — jeśli go brak, dodać `V17__tenants_external_id_unique.sql` w ramach tego speca).

## 9. Teksty logów (PL, finalne szablony)
- Seed: `[ONBOARDING] Provisioned tenant=<id> externalId=<T...> name="<workspace>" publicProjectId=<uuid> plan=Free`
- Istniejący: brak logu onboardingowego (cisza).
- Welcome: `[ONBOARDING] Welcome email → admin (<installerUserId|unknown>) tenant=<id>: Vedra gotowa! Zaproś @Vedra na kanał (/invite @Vedra), utworz projekt (/vedra create), sprawdz status (/vedra status).`
- Owner: `[ONBOARDING] Owner notification → <owner-email>: Nowy klient <workspace> (<externalId>) tenant=<id> plan=Free.`
- Docs: `[ONBOARDING] Offer docs → tenant=<id>: Oferta_Free_<externalId>.md (3 projekty / 3 branże+general / 500 ingest / 100 pytań mies.).`

## 10. Testy

### 10.1. Jednostkowe
- `TenantProvisioningServiceTest`: miss → tenant + PUBLIC + event published + `isNew=true`; hit → `isNew=false`, brak eventu, brak nowego PUBLIC; `workspaceName=null` → `name=externalId`; limit Free w `ProvisionResult`.
- `TenantProvisionedListenerTest`: event → 3 linie INFO z prefiksem `[ONBOARDING]` (caplog/appender), wyjątek w jednej linii nie blokuje pozostałych, brak propagacji wyjątku.
- `TenantServiceTest` (regresja): `resolveByExternalId` deleguje, sygnatura bez zmian.

### 10.2. Integracyjne
- `OnboardingFlowTest`: POST eventu z nowym `team_id` → wiersz `tenants` + wiersz `tenant_projects(PUBLIC)`; drugi POST z tym samym `team_id` → bez duplikatów, bez drugiego eventu.
- `OnboardingRaceTest` (opcjonalnie): 2x równoległy provisioning → 1 tenant + 1 PUBLIC.
- Weryfikacja `AFTER_COMMIT`: rollback provisioningu → brak logów listenera.

### 10.3. E2E (Slack mock)
- Nowy workspace: dowolny event → `/vedra status` działa z pustym kanałem (PUBLIC istnieje); logi zawierają 3 linie FR-08; `/vedra create` potem linkuje PRIVATE jak w spec 003.

## 11. Kolejność wdrożenia
1. `TenantProvisionedEvent` + `OnboardingPlans` (stałe Free).
2. `TenantProvisioningService` + przepięcie `TenantService`.
3. `TenantProvisionedListener` (3 logi, AFTER_COMMIT, try/catch).
4. `vedra.owner-email` w configu z defaultem.
5. Weryfikacja UNIQUE na `tenants(external_id)` (ew. V17).
6. Testy 10.1 → 10.2 → 10.3.
7. Obserwacja logów `[ONBOARDING]` na stagingu przed budową prawdziwych maili/dokumentów.

## 12. Ograniczenia i założenia
- Brak OAuth per workspace, brak weryfikacji roli instalującego (ufamy, że Install App robi Workspace Admin; `installerUserId` best-effort z payloadu, może być null).
- Brak wysyłki maili (SMTP poza zakresem), brak zapisu plików dokumentów, brak egzekucji limitów Free — wszystko to follow-upy podmieniające 3 logi na prawdziwe adaptery (`EmailPort`, `OfferDocumentPort`) bez zmiany triggera i seeda.
- Brak panelu admina, brak komendy `/vedra billing`, brak obsługi `app_uninstalled` (dezaktywacja tenanta poza zakresem).
- Zmiana nazwy workspace w Slacku nie aktualizuje `tenants.name` w tym specu (kandydat na sync przy każdym resolve).
