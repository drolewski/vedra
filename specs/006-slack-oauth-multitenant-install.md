# Specyfikacja: Slack OAuth multi-workspace install — zero konfiguracji klienta (006)

> Status: NIEZAIMPLEMENTOWANY (zweryfikowano z kodem 2026-09-08). Brak `slack_installations`, brak kontrolera/serwisu OAuth, brak `V19` (ostatnia to `V18__delete_legacy_tenant_id.sql`). Ten spec jest precondition dla 007 (`slack_installations(team_id → bot_user_id)`, `getChannelName(channelId, teamId)`).

## 1. Cel

Likwidacja ręcznej konfiguracji `SLACK_BOT_TOKEN` + `SLACK_SIGNING_SECRET` po stronie klienta. Stan docelowy:

1. Klient (Workspace Admin) robi tylko: `Add to Slack → Allow → /invite @Vedra`. Zero kopiowania tokenów, zero ustawiania Request URL-i, zero edycji `.env`.
2. `SLACK_BOT_TOKEN` znika z `.env` jako wymóg per-workspace; tokeny trzymane są per `team_id` w DB (`slack_installations`), pozyskane automatycznie przez OAuth v2.
3. `SLACK_SIGNING_SECRET` zostaje, ale wyłącznie jako **jeden sekret operatora Vedry** (nie klienta). Weryfikacja sygnatur bez zmian.
4. Kompatybilność wsteczna w trakcie migracji: jeden deployment przełącza się ze starego jednego tokena na słownik tokenów bez przestoju.

Decyzje właściciela (do zamrożenia przed implementacją):
- (1) Dystrybucja przez publiczny Slack App (OAuth v2, `oauth.v2.access`) — nie Socket Mode w tym specu (Socket jako kandydat na 007+).
- (2) `SLACK_BOT_TOKEN` usuwany całkowicie po migracji (nie zostaje jako fallback na stałe).
- (3) `app_uninstalled` dezaktywuje tenanta (nie kasuje danych).

## 2. Kontekst

### Stan kodu na 2026-09-08 (zweryfikowany)
- **Install:** manualny Slack App, 3x Request URL = realne mappingi: `SlackEventsController @RequestMapping("/api/v1/slack/events") + @PostMapping`, `SlackSlashCommandController @RequestMapping("/api/v1/slack/commands") + @PostMapping(team_id, channel_id, trigger_id)`, `SlackInteractiveController @PostMapping("/api/v1/slack/interactive")`. Jeden globalny `SLACK_BOT_TOKEN` (`SlackConfig.kt:10-11` `@Value`, `:14-17` `App()` + `authTest` z jednym tokenem). Drugi workspace = ręczna podmiana env + restart. Multi-tenant martwy.
- **Klient:** `SlackClientAdapter.kt:14-108` woła `slackApp.client()` bez tokena per team. Port `SlackClientPort.kt:5-35` ma **7 metod, nie 5**: `postThreadMessage`, `postThreadMessageWithBlocks`, `updateMessage`, `openModal`, `getTeamName(teamId)`, `getChannelName(channelId)` (1-arg, `:29`), `postChannelMessage`. Wszystkie idą na ten sam jeden token.
- **Weryfikacja:** własny `SlackSignatureVerificationFilter.kt:36-39` filtruje wyłącznie `POST` pod `/api/v1/slack/`; `GET` (czyli przyszłe `/install`, `/oauth/callback`, `/success`) omija filtr automatycznie — brak zmian w filtrze. Tryb testowy `:44-48` przepuszcza bez nagłówków (do uszczelnienia poza specem). Sekret z `slack.signing-secret: ${SLACK_SIGNING_SECRET}` (`application.yaml:50-51`, `application-local.yaml:63-64`). Test `SlackSignatureVerificationFilterTest.kt:15-16` wstrzykuje sekret wprost — bez zmian.
- **Env:** `.env.example:1-2` wymaga obu zmiennych; `SETUP.md:4-7` każe kopiować Bot Token i Signing Secret. To jest tarcie usuwane tym specem.
- **Onboarding (004, wdrożony):** `TenantProvisioningService.kt:28-32 provisionTenant(externalId, workspaceName, installerUserId? = null)` + `TenantService.kt:10-20` deleguje; `TenantRepository` ma `findByExternalId/findByActiveTrue/findById/save`. OAuth dostarczy wiarygodne `installerUserId` z `authed_user.id` (dziś null/best-effort).
- **Eventy:** `SlackEvent.kt:3-44 workspaceId` z `team_id`, nullable; `SlackEvent.fromRawBody` rzuca przy braku `event`; `MessageEventHandler.kt:32` dropuje `channel/text/workspaceId/userId == null`; `SlackTenantResolver.kt:17-42` woła `getTeamName(externalId)` → `resolveByExternalId`. `SlackEventRequestHandler.kt:14-26` obsługuje dziś tylko sparsowane `SlackEvent` — brak gałęzi `app_uninstalled`/`tokens_revoked` (payload `event_callback` ma inny kształt).
- **Istniejący 007 (częściowo na kodzie):** `BotChannelJoinHandler.kt:15-64` już działa na `supports(type == "member_joined_channel")`, woła 1-arg `slackClient.getChannelName(channel)` (`:36`) i `slackMessageService.sendChannelMessage` (`:44-49` → `SlackMessageService.kt:24-30` → `port.postChannelMessage`). Testy mockują 1-arg (`BotChannelJoinHandlerTest.kt:32`, `MessageEventHandlerTest.kt:34`). Przejście na `getChannelName(channelId, teamId)` z FR-08 to breaking change także dla 007 — skoordynować.
- **Tenant:** `tenants(external_id UNIQUE)` (`V17__tenants_external_id_unique.sql`), rename `slack_workspace_id → external_id` (`V14`), seed PUBLIC (`V16`). Brak tabeli instalacji — ten spec ją dodaje jako `V19` (pierwsza wolna po `V18`).
- **Bolt:** `com.slack.api:bolt:1.48.0` już na classpath (`build.gradle.kts:32-33`) — ma wbudowany OAuth (`App(OAuthConfig)`, `DBInstallationService`). Nie dodajemy SDK; OAuth obsługujemy własnym kontrolerem (hook na provisioning), nie wbudowanym `OAuthFlow` Bolta.
- **Chat (005, wdrożony):** `ChatController` wymaga `X-Tenant-Id` — bez wpływu na ten spec.

### Docelowa ścieżka użycia
```
1. Admin klienta: strona Vedry / Slack App Directory → "Add to Slack" → Allow (scopes)
2. Slack → GET /api/v1/slack/oauth/callback?code=...&state=... → Vedra wymienia code na bot_token → INSERT slack_installations + provisionTenant (004) → redirect na stronę sukcesu
3. Admin: /invite @Vedra na kanał → wszystko dalej jak dziś (003/004/007: join → auto-przestrzeń → status/config/answer/ingest)
4. Odinstalowanie: Slack POST app_uninstalled → tenant.active=false, token unieważniony
```

## 3. Wymagania funkcjonalne

### 3.1. Konfiguracja operatora (zamiast konfiguracji klienta)
- **FR-01:** Nowe envy operatora (`.env.example`, `application.yaml`, `application-local.yaml`):
  ```
  SLACK_CLIENT_ID=...
  SLACK_CLIENT_SECRET=...
  SLACK_SIGNING_SECRET=...   # bez zmian semantyki, dalej jeden sekret aplikacji
  SLACK_REDIRECT_URL=https://<vedra-host>/api/v1/slack/oauth/callback  # prod; local: https://<ngrok>/api/v1/slack/oauth/callback
  SLACK_SCOPES=app_mentions:read,channels:history,channels:read,chat:write,commands,groups:history,im:history,mpim:history,team:read,users:read
  ```
  `SLACK_BOT_TOKEN` oznaczony deprecated w tym specu (fallback, sekcja 3.4), usuwany w następnym sprincie po weryfikacji.
- **FR-02:** Slack App przestawiona na **Distribution: Public**, OAuth Redirect URL wpisany raz przez operatora (nie przez klienta). 3x Request URL z sekcji 2 wpisane raz przez operatora. Klient ich nigdy nie dotyka.

### 3.2. Start instalacji — jeden klik
- **FR-03:** `GET /api/v1/slack/install` → `302` do `https://slack.com/oauth/v2/authorize?client_id=...&scope=...&redirect_uri=...&state=...`. `state` = signed nonce HMAC na `SLACK_SIGNING_SECRET`, TTL 10 min, bez nowej tabeli (stateless; alternatywa `slack_oauth_states` odrzucona).
- **FR-04:** `GET /api/v1/slack/oauth/callback?code=&state=`:
  a. weryfikacja `state`, brak/podmiana → `400` + strona PL z błędem (sekcja 9),
  b. `oauth.v2.access(code, redirect_uri)` przez `MethodsClient` (timeout 10 s, 1 retry),
  c. sukces → mapowanie: `team.id/name`, `bot_user_id`, `access_token (xoxb-)`, `authed_user.id`, `scope`, `app_id`,
  d. `SlackInstallationService.save(...)` (upsert po `team_id`, sekcja 5) + `TenantProvisioningService.provisionTenant(team.id, team.name, authed_user.id)` (reuse 004, `isNew` steruje eventem),
  e. redirect `302` na `GET /api/v1/slack/oauth/success` (PL: `/invite @Vedra`, `/vedra config`, `/vedra status` — bez `create` w treści, zgodnie z 007),
  f. błąd Slacka (`invalid_code`, brak `team.id`) → `502/log warn` + strona błędu PL, bez provisioningu, bez wiersza w `slack_installations`.

### 3.3. Przechowywanie instalacji
- **FR-05:** Tabela `slack_installations` (migracja `V19`, sekcja 8):
  ```
  team_id TEXT PK, team_name TEXT, bot_token TEXT NOT NULL (szyfrowany — NFR-03),
  bot_user_id TEXT, installer_user_id TEXT, scopes TEXT NOT NULL, app_id TEXT,
  installed_at TIMESTAMPTZ NOT NULL DEFAULT now(), revoked_at TIMESTAMPTZ NULL, active BOOLEAN NOT NULL DEFAULT true
  ```
  Upsert: powtórny install = `UPDATE bot_token/scopes/bot_user_id/installed_at, active=true, revoked_at=NULL` (bez duplikatu, bez drugiego tenanta — idempotentność po PK).
- **FR-06:** `SlackInstallationService`: `saveOrUpdate(dto)`, `findBotToken(teamId): String?` (tylko `active AND revoked_at IS NULL`), `markRevoked(teamId)`, `isInstalled(teamId)`. Token odszyfrowany dopiero przy odczycie, nigdy w logach.

### 3.4. Użycie tokena per workspace (serce zmiany)
- **FR-07:** `SlackConfig.slackApp()` przestaje robić `authTest` z jednym tokenem. `App` z samym signing; OAuth Bolta nieużywany (własny kontroler FR-04, bo potrzebny hook na provisioning).
- **FR-08:** `SlackClientPort` dostaje `teamId` we wszystkich 7 metodach (breaking change; `getChannelName(channelId, teamId)` zgodnie z 007 FR-03/6.2):
  - callerzy do przepisania (z grepa): `SlackMessageService` (`postThreadMessage`, `postChannelMessage`, `postThreadMessageWithBlocks`, `updateMessage` x2 w `replaceFeedbackButtons/removePromoteButton`), `SlackModalService.openModal`, `SlackTenantResolver.getTeamName`, `BotChannelJoinHandler.getChannelName`. Handlery outbox (`Answer/Promote/Ingestion/Feedback`) idą przez `SlackMessageService`, nie wprost do portu — niosą `team_id`/`tenantId` z payloadu outbox (`MessageEventHandler.kt:70-108` już zapisuje `tenantId`; `team_id` dołożyć do payloadu lub join `tenants.external_id`).
  - Token per wywołanie: `findBotToken(teamId) ?: legacyFallback() ?: throw SlackNotInstalledException(teamId)`; wołanie `req.token(botToken).channel(...)`, nie globalny `authTest`.
  - Testy do update: `BotChannelJoinHandlerTest.kt:32`, `MessageEventHandlerTest.kt:34` (mock 1-arg → 2-arg).
- **FR-09:** Legacy fallback (tylko w tym specu): brak wiersza + ustawiony deprecated `SLACK_BOT_TOKEN` → użyj go + `logger.warn "[INSTALL] legacy global token used team=..."`. Deploy → re-install → gdy `count(active installations) == count(active tenants)` (sekcja 8/10), osobny task kasuje fallback + env.
- **FR-10:** Brak instalacji → `SlackNotInstalledException(teamId)` → ephemeral PL `"Vedra nie jest zainstalowana na tym workspace. Poproś admina o instalację: <install-url>."` (URL z bazy `SLACK_REDIRECT_URL` lub `vedra.install-url`). Log `warn` z `team_id`, bez tokena. Brak `team_id` w payloadzie → jak dziś (`TenantExternalIdMissingException`, bez ephemeral install).

### 3.5. Dezinstalacja i odwołania
- **FR-11:** `SlackEventsController`/`SlackEventRequestHandler` dostaje gałąź `event_callback(app_uninstalled)` + `tokens_revoked` (osobny parser — `fromRawBody` dziś rzuca na takim kształcie): `markRevoked(teamId)` + `tenant.active=false` (update po `external_id` przez `TenantRepository.save(copy(active=false))`, jeśli istnieje) + `logger.info [INSTALL] Uninstalled team=... tenant=...`. Dalsze eventy → FR-10 (nie provisioning na nowo; re-install reaktywuje `active=true` w obu tabelach).
- **FR-12:** `app_uninstalled` nigdy nie kasuje `knowledge_entries`/`tenants` (RODO osobnym procesem).

### 3.6. Powiązanie z onboardingiem (004)
- **FR-13:** OAuth callback jest drugim triggerem provisioningu obok pierwszego eventu. Oba wołają `provisionTenant` — `UNIQUE(external_id)` rozstrzyga wyścig. `installerUserId` z OAuth ma pierwszeństwo przy `isNew`, nie nadpisuje istniejących.
- **FR-14:** 3 logi `[ONBOARDING]` z 004 bez zmian.

## 4. Wymagania niefunkcjonalne
- **NFR-01:** Zero konfiguracji klienta: czysty workspace bez `.env`/konsoli Slacka, tylko klik + `/invite`.
- **NFR-02:** Filtr sygnatur bez zmian kodu (POST-only już omija GET OAuth). Zakaz dotykania `SlackSignatureVerificationFilter` w tym specu.
- **NFR-03:** `bot_token` szyfrowany w spoczynku (JPA `AttributeConverter` AES/GCM, klucz `SLACK_TOKEN_ENCRYPTION_KEY`; logi bez tokenów — test regresyjny). `SLACK_CLIENT_SECRET` tylko serwer-side.
- **NFR-04:** Idempotentność: retry callback z tym samym `code` → `invalid_code` ze Slacka, ale instalacja już istnieje → sukces (redirect `/success`).
- **NFR-05:** Zero LLM/embedding w OAuth. Callback p50 < 2 s (1x Slack API + upsert + provisioning).
- **NFR-06:** `SLACK_CLIENT_ID/SECRET` fail-fast na starcie (PL komunikat), `SLACK_BOT_TOKEN` opcjonalny-deprecated z warnem.

## 5. Modele danych
- Nowa tabela `slack_installations` (sekcja 8, FR-05). Kod:
  ```kotlin
  @Entity @Table(name = "slack_installations")
  class SlackInstallation(
    @Id val teamId: String, var teamName: String?, var botToken: String, // @Convert(TokenCryptoConverter)
    var botUserId: String?, var installerUserId: String?, var scopes: String, var appId: String?,
    var installedAt: Instant, var revokedAt: Instant?, var active: Boolean,
  )
  data class OAuthCallbackResult(val teamId: String, val teamName: String?, val botToken: String, val botUserId: String?, val installerUserId: String?, val scopes: String, val appId: String?)
  ```
- Bez zmian `tenants`/`tenant_projects`. `tenant.external_id == slack_installations.team_id` (join logiczny, bez FK — okno migracji).

## 6. Architektura i komponenty

### 6.1. Bez zmian
| Klasa/plik | Decyzja |
|---|---|
| `SlackSignatureVerificationFilter`, `SlackSignatureVerificationFilterTest` | bez zmian (GET omija filtr z natury) |
| `TenantProvisioningService`, `TenantProvisionedListener`, logi `[ONBOARDING]` | reuse (FR-13/14) |
| `KnowledgeService`, RAG, typy eventów outbox | bez zmian |

### 6.2. Modyfikacje
| Klasa | Zmiana |
|---|---|
| `SlackConfig` | usuwa `bot-token` + `authTest`; `App` z signing + `MethodsClient`; `clientId/Secret/RedirectUrl/Scopes` z `@Value` |
| `SlackClientAdapter` + `SlackClientPort` (7 metod) | token per `teamId`; `teamInfo`/`conversationsInfo` z tokenem teamu |
| `SlackMessageService`, `SlackModalService`, `SlackTenantResolver`, `BotChannelJoinHandler` | przekazują `teamId` (lista FR-08) |
| `SlackEventsController`/`SlackEventRequestHandler` + `SlackEvent` | parser `app_uninstalled`/`tokens_revoked` → FR-11 |
| `application.yaml`, `application-local.yaml`, `.env.example`, `SETUP.md` | envy FR-01, koniec kopiowania Bot Token |

### 6.3. Nowe
| Klasa/plik | Opis |
|---|---|
| `infrastructure/slack/install/SlackInstallation.kt` + `SlackInstallationRepository.kt` | JPA + `findByTeamId`, `countByActiveTrue` |
| `infrastructure/slack/install/TokenCryptoConverter.kt` | `AttributeConverter` AES/GCM |
| `domain/service/SlackInstallationService.kt` | FR-06 `@Transactional` |
| `infrastructure/slack/install/SlackOAuthController.kt` | `GET /install`, `GET /oauth/callback`, `GET /oauth/success` |
| `infrastructure/slack/install/SlackOAuthService.kt` | `code → token`, mapowanie DTO, błędy Slacka |
| `infrastructure/slack/exception/SlackNotInstalledException.kt` | FR-10 + ephemeral |
| `V19__slack_installations.sql` | DDL FR-05 |

## 7. Przepływy główne

### 7.1. Happy path
```
1. Admin → GET /slack/install → 302 slack.com/oauth/v2/authorize
2. Allow → GET /oauth/callback?code=..&state=..
3. oauth.v2.access → {team T123, bot xoxb-..., user U456}
4. saveOrUpdate(T123) + provisionTenant(T123, name, U456) → commit
5. 302 /oauth/success: "Gotowe! /invite @Vedra, potem /vedra config."
6. /invite → BotChannelJoinHandler (007) na tokenie T123 → auto-przestrzeń + powitanie
```

### 7.2. Re-install / wyścig
```
1. Drugi install T123 → upsert → provisionTenant hit (isNew=false), brak eventu
2. Callback ∥ message-event → UNIQUE wygrywa jeden; slack_installations PK bez duplikatów
3. Retry code → invalid_code, instalacja istnieje → /success
```

### 7.3. Uninstall
```
1. POST app_uninstalled T123 → markRevoked + tenant.active=false → [INSTALL]
2. Kolejny event → SlackNotInstalledException → ephemeral reinstall
3. Re-install → active=true obie tabele, historia RAG zachowana
```

## 8. Schemat migracji bazy danych
```sql
-- V19__slack_installations.sql (pierwsza wolna; V18 zajęta przez delete_legacy_tenant_id)
CREATE TABLE slack_installations (
  team_id TEXT PRIMARY KEY,
  team_name TEXT,
  bot_token TEXT NOT NULL,
  bot_user_id TEXT,
  installer_user_id TEXT,
  scopes TEXT NOT NULL,
  app_id TEXT,
  installed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  revoked_at TIMESTAMPTZ NULL,
  active BOOLEAN NOT NULL DEFAULT true
);
```
Weryfikacja: `SELECT count(*) FROM slack_installations WHERE active;` vs `SELECT count(*) FROM tenants WHERE active;` — rozjazd = backlog re-installa przed kasacją fallbacku.

## 9. Teksty odpowiedzi (PL)
- Sukces: `"Vedra zainstalowana na <team_name>! Zaproś bota: /invite @Vedra — przestrzeń utworzy się sama. Szczegóły: /vedra config. Pomoc: /vedra help."`
- Brak instalacji: `"Vedra nie jest zainstalowana na tym workspace. Poproś admina o instalację: <install-url>."`
- Błąd callback: `"Instalacja nie powiodła się (<reason>). Spróbuj ponownie: <install-url>. Jeśli problem wróci, napisz do <owner-email>."`
- Bez `code`/`token` w treści i logach.

## 10. Testy

### 10.1. Jednostkowe
- `SlackOAuthServiceTest` (mock `MethodsClient`): OK → DTO; `invalid_code` → exception + brak zapisu; brak `team.id` → exception.
- `SlackInstallationServiceTest`: save→find→upsert (1 wiersz, nowy token); `markRevoked` → `findBotToken=null`; `bot_token` w DB != `xoxb-...`.
- `SlackClientAdapterTest`: wywołanie z `teamId` → `token(...)` tego teamu; brak + brak fallbacku → `SlackNotInstalledException`; fallback → warn.
- `SlackOAuthControllerTest` (MockMvc): zły `state` → 400; OK → 302 `/success`.
- Update mocków 1-arg → 2-arg: `BotChannelJoinHandlerTest.kt:32`, `MessageEventHandlerTest.kt:34`.

### 10.2. Integracyjne
- `OAuthProvisioningFlowTest`: callback nowy `team_id` → instalacja + `tenants` + PUBLIC; drugi → bez duplikatów/bez eventu.
- `UninstallFlowTest`: `app_uninstalled` → `revoked_at` + `tenant.active=false`; potem ephemeral FR-10; re-install → `active=true`, `knowledge_entries` zachowane.
- V19 na kopii staging: upsert 2x → 1 wiersz.

### 10.3. E2E (staging)
- Czysty workspace: klik → `/invite` → auto-przestrzeń (007) → `/vedra status` → `?` odpowiada; zero `[INSTALL] legacy global token used`; `count(active installations) == count(active tenants)`.

## 11. Kolejność wdrożenia
1. `V19` + entity/repository + `TokenCryptoConverter`.
2. `SlackInstallationService` + testy (bez kontrolera).
3. `SlackOAuthService` + `SlackOAuthController` (bez zmian filtra — GET omija).
4. Slack App Public + Redirect URL (operator raz) + `SETUP.md`/`.env.example`.
5. Port/adapter → `teamId` (7 metod, w tym `getChannelName` dla 007) + fallback FR-09 + `SlackNotInstalledException`.
6. `app_uninstalled`/`tokens_revoked` → FR-11.
7. Testy 10.1 → 10.2 → 10.3; `grep "\[INSTALL\]"` + `count(active)`.
8. Re-install wszystkich → rozjazd 0 → osobny task: delete fallback + `SLACK_BOT_TOKEN`.

## 12. Ograniczenia i założenia
- `SLACK_SIGNING_SECRET` niezbywalny (podpis Slacka); minimalizacja = 1 sekret operatora.
- Klucz `SLACK_TOKEN_ENCRYPTION_KEY`; rotacja poza zakresem.
- Publiczny HTTPS wymagany (`redirect_uri`); local = ngrok tylko operatora.
- Scopes FR-01 minimalne (w tym `channels:read` dla 007 `conversations.info`); `files:read` osobnym specem z re-consentem.
- Socket Mode poza zakresem (kandydat 007+).
- Wzmianki o `SLACK_BOT_TOKEN` w specs = historia; zakaz nowych użyć po kasacji fallbacku.
