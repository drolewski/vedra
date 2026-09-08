# Vedra — Test Plan (regresja funkcjonalna przez Slacka)

Cel: po każdej zmianie (własnej lub z AI) potwierdzić ręcznie w Slacku, że Vedra działa.
Jedyny interfejs testowy to Slack — workspace z zainstalowaną aplikacją.
Szczegóły w `TEST_CASES.md`, odhaczanie w `CHECKLIST.md`.

## Kolejność wykonywania (zależności!)

0. `FT-TEN-01 → FT-TEN-02` — pierwszy kontakt zakłada tenanta + `Bazę ogólną`, powtórny nie duplikuje.
   Na istniejącym workspace ten krok to sanity (`/vedra list` pokazuje 1 `Bazę ogólną`).
1. `FT-01 → FT-03` — status (pełna konfiguracja), help (status/config/help), lista legacy (aplikacja żyje).
2. `FT-18 → FT-20` — invite zakłada auto-przestrzeń, `/vedra config` edytuje (nazwa/opis/branża), błędy config.
   `FT-04 → FT-05` — legacy: `create` ukryte (zlinkowany → odesłanie do config).
3. `FT-06 → FT-07` — link/unlink na drugim kanale + błędy.
4. `FT-08 → FT-11` — pytania (`?`, `@vedra`), izolacja projektu, cisza na kanale niezlinkowanym.
5. `FT-12 → FT-13` — cicha ingestion + brak pętli bota.
6. `FT-14 → FT-15` — feedback i promocja do bazy publicznej (odwraca FT-10: fakt ma być widoczny publicznie).
7. `FT-TEN-03` — izolacja między workspace'ami (po FT-12: fakt z A niewidoczny na B).
8. `FT-16 → FT-17`, `FT-TEN-04 → FT-TEN-05` — brzegi (duplikaty, brak kontekstu, plan Free).

## Pytania kontrolne po każdej zmianie (przed testami w Slacku)

1. Czy ruszałem parser/komendy (`/vedra ...`)? → obowiązkowo FT-01–FT-03 + FT-18–FT-20.
2. Czy ruszałem routing pytań (`?` / `@vedra`, filtry bota)? → FT-08–FT-11, FT-13.
3. Czy ruszałem ingestion, prompty, embedding, RAG? → FT-12, potem FT-08 (jakość odpowiedzi).
4. Czy ruszałem feedback/promocję (`Udostępnij 🌍`)? → FT-14–FT-15.
5. Czy ruszałem multi-tenancy, widoczność `PRIVATE|PUBLIC`, onboarding? → FT-TEN-01–FT-TEN-03 + FT-10 i FT-15 od końca (izolacja!).
6. Czy ruszałem outbox, retry, `aggregate_id`? → FT-16, obserwuj opóźnienia (~5 s poll).
7. Czy ruszałem modal/projekty/branże/join? → FT-18–FT-20 + FT-04–FT-05 (legacy).
8. Czy odpowiedź nie przyszła w ~30 s? Sprawdź logi aplikacji (outbox PENDING, Ollama, Slack API) — to też wynik testu.

## Kryteria wyjścia

- Zielone: FT-TEN-01–FT-TEN-03 i FT-01–FT-03 + FT-18–FT-20 i FT-08–FT-15 (blokery), FT-04–FT-07 (legacy), FT-16–FT-17, FT-TEN-04–FT-TEN-05 (ostrzeżenia — dopuszczalny wyjątek po uzgodnieniu).
- Każdy fail: numer FT + treść z Slacka (skopiowana wiadomość bota) + czas + logi backendu.
