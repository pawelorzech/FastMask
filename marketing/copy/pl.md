# Google Play Store listing — polski

## Nazwa aplikacji (max 50 znaków)
**FastMask – maski e‑mail Fastmail**
(34 znaki)

## Krótki opis (max 80 znaków)
**Menedżer masek e‑mail Fastmail. Open source, prywatne, bez reklam.**
(66 znaków)

## Pełny opis (max 4000 znaków)

FastMask to szybki, prywatny, natywny klient Androida do zarządzania maskami e‑mail z Fastmaila.

**Maski e‑mail** to jednorazowe adresy przekierowujące, które chronią Twoją prawdziwą skrzynkę. Daj innym adres do każdej usługi, sprawdzaj kto wyciekł Twoje dane i wyłączaj te, które zostały nadużyte — bez podawania prawdziwego maila gdziekolwiek.

FastMask przenosi funkcje masek dostępne w webowym Fastmailu na telefon, w spokojnym, pozbawionym rozpraszaczy interfejsie zaprojektowanym pod jedno: wejść, skopiować lub stworzyć adres i wrócić do tego, co robiłeś.

⸻ FUNKCJE ⸻

• Pełna lista masek, sortowana po ostatniej aktywności (nie po dacie utworzenia)
• Tworzenie nowych masek z własnym opisem i przypisaniem do domeny
• Włączanie, wyłączanie i archiwizacja masek bez utraty adresu
• Edycja opisu, domeny i adresu przekierowania
• Kopiowanie do schowka jednym dotknięciem z delikatnym haptic feedback
• Wyszukiwanie i filtrowanie po statusie: Aktywne / Wyłączone / Zarchiwizowane
• Szybkie statystyki: ile maili każda maska odebrała i kiedy ostatnio
• Ustawienia: wybór języka, przełącznik raportów o awariach, kontakt i wylogowanie

⸻ DESIGN ⸻

• Paleta warm‑ink — pergamin i atramentowy granat, zamiast typowego niebieskiego
• Nagłówki Instrument Serif, treść Inter Tight, adresy JetBrains Mono
• Tryb jasny i ciemny, oba zgodne z motywem systemu
• Płynne shared‑element przejścia między listą a szczegółem
• Subtelna, oszczędna animacja — nigdy nie przeszkadza

⸻ PRYWATNOŚĆ I BEZPIECZEŃSTWO ⸻

• Bez śledzenia, bez analityki, bez reklam. Nie ma tu Google Analytics, identyfikatora reklamowego, śledzenia ekranów ani profilowania — samo otwarcie aplikacji niczego nie wysyła.
• Token API Fastmaila jest szyfrowany na urządzeniu przez EncryptedSharedPreferences
• Aplikacja łączy się bezpośrednio z api.fastmail.com przez HTTPS — żadnych pośredników
• Network Security Config przypina zaufanie do systemowego magazynu CA
• Wydania Release nie logują żadnego ruchu sieciowego
• Ekrany mają FLAG_SECURE — token nie trafia do zrzutów ani nagrań ekranu
• Raporty o awariach: domyślnie włączone, wyłączasz jednym dotknięciem. Gdy aplikacja się wywali, wysyła do Google Firebase Crashlytics ślad stosu, model urządzenia, wersję Androida i wersję aplikacji — żeby dało się znaleźć i naprawić błąd. Nigdy nie wysyła Twoich masek, opisów, przypisanych domen, tokenu API, adresu e‑mail ani treści wiadomości. Nie chcesz nawet tego? Ustawienia → Raporty o awariach → wyłącz. Działa od razu i kasuje to, co czekało w kolejce.
• Open source na licencji MIT — wszystko możesz sam zweryfikować. SDK Crashlytics jest wołane z dokładnie jednego pliku, a test wywala build, gdyby to się kiedyś zmieniło.

⸻ JĘZYKI ⸻

Pełne tłumaczenia: polski, angielski, hiszpański, niemiecki, francuski, włoski, portugalski, niderlandzki, rosyjski, ukraiński, turecki, arabski, hindi, bengalski, chiński (uproszczony), japoński, koreański, wietnamski, tajski, indonezyjski.

⸻ WYMAGANIA ⸻

• Android 8.0 (API 26) lub nowszy
• Konto Fastmail z dostępem do tokenu API (dowolny płatny plan)

Aby zacząć, w ustawieniach Fastmaila wygeneruj token API (Settings → Privacy & Security → Integrations → API tokens, zakres: Masked Email, odczyt i zapis), wklej go raz do FastMaska i działasz.

⸻ NIEOFICJALNY KLIENT ⸻

FastMask to nieoficjalna aplikacja społecznościowa. Nie jest powiązana z Fastmail Pty Ltd ani przez nich wspierana. "Fastmail" to znak towarowy Fastmail Pty Ltd, użyty tu wyłącznie do opisania usługi, z którą aplikacja się integruje.

⸻ OPEN SOURCE ⸻

Kod źródłowy, zgłoszenia i historia wydań:
https://github.com/pawelorzech/FastMask

Licencja MIT. Pull requesty mile widziane.

## Co nowego (max 500 znaków) — v1.5.1
**v1.5.1 — debiut na Play Store**

• Zupełnie nowa ikona i splash — paleta warm‑ink
• Zaostrzone reguły R8 — nieco mniejszy rozmiar instalacji
• Stabilny artefakt release, gotowy do produkcji
• Ta sama spokojna, prywatna aplikacja bez śledzenia

(252 znaki)

## Co nowego (max 500 znaków) — wydanie z raportami o awariach

**Raporty o awariach — domyślnie włączone, wyłączasz jednym dotknięciem**

• Nowość: gdy aplikacja się wywali, może wysłać ślad stosu, model urządzenia i wersję aplikacji do Firebase Crashlytics, żeby błąd dało się naprawić
• Nigdy nie wysyła Twoich masek, opisów, domen, tokenu ani e‑maila
• Nadal bez analityki i bez śledzenia reklamowego
• Nie chcesz? Ustawienia → Raporty o awariach → wyłącz. Działa od razu.
• Szczegóły w zaktualizowanej polityce prywatności

(459 znaków, całe pole)

## Kategoria, tagi, kontakt

- **Kategoria:** Produktywność
- **Tagi (5):** e‑mail · prywatność · fastmail · maski · alias
- **Email kontaktowy:** pawel@orzech.me
- **Strona aplikacji:** https://pawelorzech.github.io/FastMask/
- **Polityka prywatności:** https://pawelorzech.github.io/FastMask/privacy.html
