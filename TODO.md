# TODOS

## Next steps
* Dodanie wiadomości powinno zbierać dane do RAGa, 
  zapisywanie do projektów w ramach tenant
* Wywołanie wiadomości z @Vedra powinno odpowiadać na pytania
* Jeżeli odpowiedź okazała się niepomocna to:
  * wywal ja z pamięci
  * poproś o pomoc eksperta - wiadomośc zachęcająća i po odp. w wątku analiza kontekstowa i zapis do raga.
* Czy MCP może jakoś pomóc w rozwiązaniu?
* Usprawnienia kodu
* normalizacja danych wejściowych i wyjściowych
* obsługa branży projektowej w przechowywaniu projektowym
* Przechowywanie wyników LLM zamiast wielokrotnych calli
* Testy E2e

## Dalej
7. Integracja z teams
8. Integracja z Gemini dla lepszych promptów :)
9. Zbieranie danych z dokumentów fizycznych
10. Tłumaczenia odpowiedzi/komunikatów statycznych i wybieranie jej na podstawie języka użytkownika

## Testy E2E całego rozwiązania,
* Czy wywołanie jakiejkolwiek operacji tworzy tenanta?
* Czy działa dodawanie industry PER TENANT
* Czy działają operacje commandy slackowe
* Czy działa feedback?
* Czy wszystkie dane są zamknięte per tenant w odpowiedni sposóļ?
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
