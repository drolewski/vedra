# Vedra — Checklista regresji (Slack, odhacz po zmianie)

Sanity: aplikacja działa, ngrok + Slack events podpięte.

- [ ] FT-01 status na świeżym kanale (wariant niezlinkowany)
- [ ] FT-02 help + nieznana komenda
- [ ] FT-03 lista pokazuje `Bazę ogólną (publiczny)`; zapisane ID_PUBLICZNE
- [ ] FT-04 create z modala + status pokazuje powiązanie
- [ ] FT-05 walidacje: pusta nazwa, duplikat, nowa branża
- [ ] FT-06 link + unlink na drugim kanale
- [ ] FT-07 link: złe ID + projekt ogólny (dwa komunikaty błędów)
- [ ] FT-08 pytanie z `?` → odpowiedź w wątku + 3 przyciski
- [ ] FT-09 `@vedra` bez `?` też odpowiada
- [ ] FT-10 kanał niezlinkowany nie zna faktu prywatnego
- [ ] FT-11 zdanie na kanale niezlinkowanym = cisza
- [ ] FT-12 fakt wchłonięty po cichu, wraca w odpowiedzi (odczekane ~30 s)
- [ ] FT-13 brak pętli bota (1 pytanie = 1 odpowiedź)
- [ ] FT-14 👍/👎 aktualizuje przyciski bez błędu
- [ ] FT-15 po `Udostępnij 🌍` fakt widoczny na kanale publicznym
- [ ] FT-16 duplikat pytania nie sypie błędami
- [ ] FT-17 kanał bez kontekstu = cisza, bez błędów
- [ ] FT-TEN-01 pierwszy kontakt: status działa + lista ma 1 `Bazę ogólną` + log `[ONBOARDING]`
- [ ] FT-TEN-02 powtórny kontakt: nadal 1 tenant i 1 `Baza ogólna` (DB)
- [ ] FT-TEN-03 fakt z workspace A niewidoczny na workspace B
- [ ] FT-TEN-04 plan Free w logu (3 / 3 / 500 / 100)
- [ ] `./gradlew test --tests "io.vedra.e2e.*"` zielony (18 testów kodowych)
