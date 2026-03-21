# TODOS

## Testy E2E całego rozwiązania,
* Czy wywołanie jakiejkolwiek operacji tworzy tenanta?
* Czy działa dodawanie industry PER TENANT
* Czy działają operacje commandy slackowe
* Czy działa feedback?
* Czy wszystkie dane są zamknięte per tenant w odpowiedni sposóļ?
## Przejście po kodzie i ręczne refactory zanim dalej wpuszczę Agenta
* Refaktoryzacja i enkapsulacja procesów biznesowych

1. Poprawa procesu zbierania pytań/odpowiedzi 
 + normalizacja danych 
 + przechowywanie odpowiedzi
 + obsługa branży
 + konfiguracji tenanta (czy chce korzytstać z LLM, czy chce udostępniać swoje informacje innym tenantom z branży)
 + Przechowywanie wyników promptów zamiast wielokrotne calle do LLM :)
 + ? czy coś wokół tego jest potrzebne - przegadać z Gemini, czy widzi tu jakieś szanse i możliwości
6. Refactorki niezbędne
7. Integracja z teams
8. Integracja z Gemini dla lepszych promptów :)
9. Zbieranie danych z dokumentów fizycznych
10. Jeżeli odpowiedź okazała się niepomocna, 
  to poproszenie o pomoc osoby doświadczonej 
  i możliwość zauploadowania jej odpowiedzi do kontekstu

-----
1. Logger
2. Konfiguracja serwera aplikacji :)
3. Metryki?

---
Slack
* Jak zbierać dane z kanałów?
  * może wiadomości pisane
* Jak odpowiadać na pytania?
  * może wiadomości z tagiem @Vedra 
  * może na odpowiednio skonfigurowanych kanałach?
* Kiedy pytać o feedback
  * przy każdej odpowiedzi
  * przy każdej odpowiedzi, która została wygenerowana przez AI
* Jak zbierać odpowiedzi ekspertów
  * jako wiadomości z kanałów?
* Jak działa apka zainstalowana na profilu firmowym?
  * Czy jest dostępna na wszystkich kanałach

---
# Pakiety -> Wycenić
## Free
- 3 projekty maksymalnie 3 branż + general
- Analiza 500 wiadomości miesięcznie na kanałach 
- Odpowiedź na 100 pytań miesiecznie

## Pakiet I
- 10 projektów maksymalnie 3 branż + general
- Analiza 1000 wiadomości miesięcznie na kanałach
- Odpowiedź na 1000 pytań miesiecznie

## Pakiet II
- 50 projektów maksymalnie 10 branż + general
- Analiza 1000 wiadomości miesięcznie na kanałach
- Odpowiedź na 1000 pytań miesiecznie

## Pakiet III
- 50 projektów maksymalnie 50 branż + general
- Analiza 10000 wiadomości miesięcznie na kanałach
- Odpowiedź na 10000 pytań miesiecznie

## Enterprise
- 100 projektów maksymalnie 100 branż + general
- Analiza 1000000 wiadomości miesięcznie na kanałach
- Odpowiedź na 1000000 pytań miesiecznie
