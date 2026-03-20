# TODOS

1. Poprawa procesu zbierania pytań/odpowiedzi 
 + normalizacja danych 
 + przechowywanie odpowiedzi
 + obsługa branży
 + konfiguracji tenanta (czy chce korzytstać z LLM, czy chce udostępniać swoje informacje innym tenantom z branży)
 + Przechowywanie wyników promptów zamiast wielokrotne calle do LLM :)
 + ? czy coś wokół tego jest potrzebne - przegadać z Gemini, czy widzi tu jakieś szanse i możliwości
2. Dodanie kontekstu branżowego/domenowego i prompty
3. Formatowanie odpowidzi dla Slacka - dekorator
5. Dodanie obsługi tenanta
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