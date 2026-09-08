# Vedra — Funkcjonalne testy integracyjne przez Slacka

Wszystko wykonujesz w Slacku (workspace z zainstalowaną Vedrą, aplikacja uruchomiona).
Odpowiedzi bota przychodzą jako wiadomości efemeryczne (widoczne tylko dla Ciebie)
albo w wątku pod Twoją wiadomością. Ciężkie operacje idą przez outbox — odpowiedź
może przyjść po kilku sekundach; jeśli nie ma jej po ~30 s, test nie przeszedł.

## Przygotowanie (raz)

1. Dwa kanały: `#vedra-test-prywatny` i `#vedra-test-ogolny`.
2. Zapisz ID projektu publicznego: na dowolnym kanale `/vedra list` → wiersz z tagiem
   `(publiczny)` — to `ID_PUBLICZNE` (potrzebne w FT-04).
3. Do FT-09 (feedback/promocja) potrzebna jest najpierw odpowiedź bota z FT-06.

Format: cel → kroki w Slacku → oczekiwany rezultat.

---

## A. Onboarding i kontekst kanału

### FT-01 `/vedra status` na kanale z auto-przestrzenią
Kroki: zaproś bota na świeży kanał (`/invite @Vedra`), odczekaj na powitanie, wpisz `/vedra status`.
Oczekiwane (kanał zlinkowany):
```
*Konfiguracja przestrzeni tego kanału*
• Nazwa: *<nazwa>*
• Opis: <opis lub —>
• Branża: <branża lub nie ustawiono>
Odpowiedzi szukane w tej przestrzeni + baza publiczna.
Zmień przez `/vedra config`.
```
Wariant niezlinkowany (np. po `/vedra unlink`):
```
Ten kanał nie ma jeszcze własnej przestrzeni.
Odpowiedzi szukane tylko w bazie publicznej.
Uruchom `/vedra config`, aby utworzyć i skonfigurować przestrzeń.
```

### FT-02 `/vedra help` i nieznana komenda
Kroki: `/vedra help`, potem `/vedra xyz`, potem inna komenda slash niż `/vedra`.
Oczekiwane dla dwóch pierwszych:
```
*Komendy Vedra:*
• `/vedra status` - Pokaż konfigurację przestrzeni tego kanału
• `/vedra config` - Edytuj nazwę, opis i branżę przestrzeni tego kanału
• `/vedra help` - Pokaż tę pomoc
```
Asercja negatywna: odpowiedź NIE zawiera `create`, `list`, `link`, `unlink`.
Trzecia (np. wpisanie samej treści bez `/vedra`) jest ignorowana albo — jeśli wyślesz
nieobsługiwany slash-command — dostajesz `Nieznana komenda`.

### FT-03 `/vedra list` (legacy, ukryte z helpa)
Kroki: `/vedra list`.
Oczekiwane: `Dostępne projekty:` i wiersz `• *<id>*: Baza ogólna (publiczny)`.
Wiersz ma format `• *ID*: nazwa (tagi)`; tag `branża: X` pojawia się tylko gdy ustawiona.
Uwaga: komenda działa, ale nie ma wpisu w helpu i nie jest ścieżką usera (model 1 kanał = 1 przestrzeń).

## B. Tworzenie projektu (modal — legacy, docelowo `/vedra config`)

### FT-04 `/vedra create` (ukryte, kompatybilność wsteczna)
Ścieżka usera to invite → auto-przestrzeń → `/vedra config` (FT-18/FT-19). `create` działa, bez wpisu w helpu.
a) Kanał NIEzlinkowany: `/vedra create` → modal `Utwórz projekt` jak dawniej → `Sukces` + link.
b) Kanał zlinkowany (normalny po invite): `/vedra create` → submit → modal `Informacja`:
   `Ten kanał ma już przestrzeń *<nazwa>*. Zmień szczegóły przez \`/vedra config\`.` (brak nowego projektu).

### FT-05 Walidacje modala
a) Pusta `Nazwa projektu` → błąd pod polem: `Nazwa projektu jest wymagana.`
b) Nazwa `test-prywatny` ponownie (ten sam workspace) → `Projekt *test-prywatny* już istnieje.`
c) Nowa branża: wybierz `+ Dodaj nową`, wpisz np. `budownictwo` → projekt tworzy się
   z `Branża: budownictwo`, a branża pojawia się na liście przy kolejnym `create`.

## C. Link / unlink

### FT-06 `/vedra link` i `/vedra unlink`
Kroki: na `#vedra-test-ogolny`: `/vedra status` (ma być niezlinkowany) →
`/vedra link <ID-projektu-test-prywatny>` (ID z `/vedra list`).
Oczekiwane: `Kanał powiązany z projektem *<ID>*`; `/vedra status` pokazuje powiązanie.
Kroki: `/vedra unlink`.
Oczekiwane: `Kanał odłączony od projektu. Teraz używa bazy ogólnej.`;
`/vedra status` znowu pokazuje wariant niezlinkowany z FT-01.

### FT-07 Błędy linkowania
a) `/vedra link nie-istniejace-id` →
   `Projekt *nie-istniejace-id* nie istnieje. Użyj \`/vedra list\` aby zobaczyć dostępne projekty.`
b) `/vedra link <ID_PUBLICZNE>` → `Nie można powiązać kanału z projektem ogólnym.`

## D. Pytania i odpowiedzi (RAG)

### FT-08 Pytanie ze znakiem `?` — odpowiedź w wątku z przyciskami
Warunki: kanał zlinkowany (np. `#vedra-test-prywatny` z FT-04).
Kroki: napisz wiadomość kończącą się pytajnikiem, np. `Jaki jest termin odbioru?`
Oczekiwane: w wątku pod Twoją wiadomością pojawia się odpowiedź bota
(nagłówek + treść + źródło + pewność) z trzema przyciskami:
`Pomogło :+1:` (primary), `Nie pomogło :-1:` (danger), `Udostępnij 🌍`.
Odpowiedź dotyczy wiedzy projektu + bazy publicznej.

### FT-09 Wzmianka `@vedra` bez pytajnika też pyta
Kroki: napisz `hej @vedra jakie mamy ustalenia` (bez `?` na końcu).
Oczekiwane: jak w FT-08 — bot odpowiada w wątku. Sama wzmianka wystarcza.

### FT-10 Kanał niezlinkowany pyta tylko bazę publiczną
Kroki: na `#vedra-test-ogolny` (po unlink z FT-06) zadaj pytanie o fakt znany tylko
z kanału prywatnego.
Oczekiwane: bot odpowiada (wątek + przyciski), ale NIE zna faktu z projektu prywatnego —
izolacja projektów zachowana.

### FT-11 Zwykłe zdanie na kanale NIEzlinkowanym jest ignorowane
Kroki: na niezlinkowanym kanale napisz zdanie oznajmujące bez `?` i bez `@vedra`,
np. `Dzisiaj pada deszcz.`
Oczekiwane: brak jakiejkolwiek reakcji bota (cisza). Wiadomość nie trafia do wiedzy.

## E. Ingestion wiedzy (ciche zbieranie)

### FT-12 Zdanie na kanale ZLINKOWANYM zasila bazę projektu
Kroki:
1. Na zlinkowanym `#vedra-test-prywatny` napisz unikalny fakt bez `?`, np.
   `Kod dostępu do bramy to 9876 (fakt testowy).`
2. Oczekiwane: brak reakcji bota (ingestion jest ciche — wiadomość znika w bazie).
3. Odczekaj ~10–30 s (poll outbox + embedding + ocena użyteczności LLM).
4. Zapytaj: `Jaki jest kod dostępu do bramy?`
Oczekiwane: bot w odpowiedzi podaje `9876` (fakt wrócił z bazy projektu).

### FT-13 Bot ignoruje samego siebie (brak pętli)
Kroki: po FT-08/FT-12 sprawdź, że pod odpowiedzią bota nie pojawiają się kolejne
odpowiedzi bota (wątek ma dokładnie 1 odpowiedź na 1 pytanie).
Oczekiwane: brak zapętlenia — wiadomości bota (`bot_message`/`bot_id`) są filtrowane.

## F. Feedback i promocja do bazy publicznej

### FT-14 Przyciski 👍 / 👎 zapisują ocenę
Kroki: pod dowolną odpowiedzią bota kliknij `Pomogło :+1:`.
Oczekiwane: przyciski aktualizują się w wiadomości (twój głos oznaczony,
np. potwierdzenie wyboru); drugie kliknięcie nie wywala błędu.
Kontrola (drugi fakt z FT-12): odpowiedź na pytanie istnieje nadal w wątku.

### FT-15 `Udostępnij 🌍` promuje wiedzę do bazy publicznej
Kroki:
1. Na zlinkowanym kanale prywatnym kliknij `Udostępnij 🌍` pod odpowiedzią
   zawierającą fakt znany tylko projektowi (np. kod `9876` z FT-12).
2. Przejdź na NIEzlinkowany `#vedra-test-ogolny` i zapytaj o ten fakt:
   `Jaki jest kod dostępu do bramy?`
Oczekiwane: kanał publiczny zna już fakt (odpowiedź zawiera `9876`) —
wiedza została skopiowana z projektu prywatnego do `Bazy ogólnej`.

## G. Przypadki brzegowe

### FT-16 Szybka seria pytań / duplikat nie dubluje odpowiedzi
Kroki: wyślij to samo pytanie 2× pod rząd.
Oczekiwane: dwie odpowiedzi w dwóch wątkach (brak wysypania się, brak 500);
outbox deduplikuje po `aggregate_id` — brak odpowiedzi-widm bez wątku.

### FT-17 Pytanie na kanale bez żadnego projektu
Kroki: (sytuacja awaryjna) kanał, dla którego nie ma ani linku, ani projektu
publicznego w tym workspace → zadaj pytanie z `?`.
Oczekiwane: cisza (bot pomija — brak kontekstu do szukania), żadnych błędów
widocznych w Slacku.

## H. Nowy tenant (onboarding workspace)

Jak to działa w kodzie: Vedra nie ma ekranu rejestracji — tenant tworzy się
leniwie przy pierwszym kontakcie z nieznanego workspace (`team_id` ze Slacka).
`SlackTenantResolver` → `TenantService.resolveByExternalId` →
`TenantProvisioningService.provisionTenant`: zapis `tenants(name=<nazwa workspace>,
external_id=<team_id>)`, utworzenie projektu publicznego `Baza ogólna`
(`ensurePublicProject`), publikacja `TenantProvisionedEvent` (listener po commicie
loguje: instrukcję startową dla admina, powiadomienie do właściciela, ofertę planu
`Free`: 3 projekty / 3 branże+general / 500 ingest / 100 pytań mies.).
Powtórny kontakt z tego samego `team_id` zwraca istniejącego tenanta (`isNew=false`,
bez duplikatów; wyścig zabezpieczony przez unique `external_id`).

### FT-TEN-01 Pierwszy kontakt z nowego workspace zakłada tenanta
Warunki: świeży workspace (albo testowy `team_id` nigdy nie widziany przez aplikację).
Kroki: zainstaluj aplikację na workspace, zaproś bota na kanał (`/invite @Vedra`),
wpisz `/vedra status`.
Oczekiwane w Slacku: normalna odpowiedź jak w FT-01 (wariant niezlinkowany) —
NIE `Błąd: Nie można zidentyfikować workspace.` Samo to znaczy, że tenant się utworzył.
Kroki: `/vedra list`.
Oczekiwane: `Dostępne projekty:` z dokładnie jednym wierszem `Baza ogólna (publiczny)`.
Weryfikacja w backendzie (logi/DB):
- log `[ONBOARDING] Provisioned tenant=<uuid> externalId=<team_id> name="<nazwa>" ... plan=Free`,
- `tenants`: 1 wiersz `external_id=<team_id>`, `name` = nazwa workspace,
- `tenant_projects`: dokładnie 1 projekt `Baza ogólna` `PUBLIC` dla tego tenanta.

### FT-TEN-02 Powtórny kontakt nie duplikuje tenanta ani Bazy ogólnej
Kroki: na tym samym workspace wyślij kolejno `/vedra status`, `/vedra list`,
dowolne pytanie z `?`.
Oczekiwane w Slacku: za każdym razem poprawne odpowiedzi, `/vedra list` nadal
pokazuje DOKŁADNIE jedną `Bazę ogólną`.
Weryfikacja w backendzie: liczba wierszy w `tenants` dla tego `external_id` = 1,
projektów PUBLIC tenanta = 1.

### FT-TEN-03 Dwa workspace'y nie widzą swoich danych
Kroki: na workspace A (z FT-TEN-01) utwórz projekt prywatny i wchłoń unikalny fakt
(ścieżka FT-04 + FT-12, np. `Kod alfa to 1111`). Na workspace B zapytaj o ten fakt.
Oczekiwane: workspace B NIE zna faktu (`/vedra list` na B nie pokazuje projektu z A,
pytanie o `1111` nie zwraca kodu). Izolacja po `external_id` zachowana.

### FT-TEN-04 Plan domyślny Free
Weryfikacja: log z FT-TEN-01 zawiera `plan=Free`; nowa instalacja startuje z limitami
3 projekty / 3 branże / 500 ingest / 100 pytań mies. (przyszłe egzekwowanie limitów
testuj zgodnie z TODO pkt 15–16, gdy powstaną).

### FT-TEN-05 Brak kontekstu workspace → czytelny błąd (poziom kodu)
Ścieżka `SlackTenantResolver` bez `team_id`: komendy zwracają
`Błąd: Nie można zidentyfikować workspace.`, `/vedra status` bez kontekstu:
`Nie można ustalić kontekstu workspace.` Przez Slacka trudne do wywołania ręcznie —
pokryte testem kodowym `TenantOnboardingRegressionTest.FT-TEN-05`.
W Slacku: jeśli kiedykolwiek zobaczysz ten komunikat na normalnym kanale, to fail —
zdrowy workspace zawsze się resolve'uje (FT-TEN-01).

## I. Auto-przestrzeń kanału i `/vedra config` (007)

### FT-18 Invite → auto-przestrzeń
Kroki: na nowym kanale wpisz `/invite @Vedra`.
Oczekiwane: w ≤30 s wiadomość powitalna `:wave: Vedra dołączyła! Utworzyłam przestrzeń *<nazwa>*...`;
`/vedra status` od razu pokazuje pełną konfigurację (format FT-01, bez `create`);
drugi join / ponowny invite → brak duplikatu przestrzeni i brak drugiej wiadomości.

### FT-19 `/vedra config` — happy path
Kroki: na kanale z FT-18 wpisz `/vedra config` → modal `Konfiguracja przestrzeni`
prefillowany aktualną nazwą/opisem/branżą → zmień wszystkie trzy → `Zapisz`.
Oczekiwane: modal `Sukces` (`:white_check_mark: *Zapisano konfigurację*`);
kontrola `/vedra status` pokazuje nowe dane.

### FT-20 `/vedra config` — błędy
a) Kanał po `/vedra unlink` → `/vedra config` auto-odtwarza przestrzeń i otwiera modal
   (bez odsyłania do `create`).
b) Zmiana nazwy na istniejącą (projekt innego kanału) → błąd `Projekt *X* już istnieje.`,
   stare dane nietknięte.
