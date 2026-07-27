# FastMask — rekomendacje UX, audyt 2026-07-27

Podstawa: przejście głównych ścieżek na emulatorze (API 36) w trybie demo ze zrzutami ekranu, odczyt wszystkich dziewięciu ekranów, wyliczenia kontrastu WCAG dla obu motywów, przegląd 20 lokali.

Scoring 1–5. `Priority = Impact × Confidence / Effort`. To narzędzie porządkujące, nie automat decyzyjny — kolejność w tabelach jest moją rekomendacją, nie wynikiem wzoru.

> Poprzednie rekomendacje (2026-07-24) leżą w `Plans/audit-archive/UX_RECOMMENDATIONS-2026-07-24.md`.

---

## Ocena ogólna

FastMask jest dopracowany powyżej przeciętnej dla aplikacji jednoosobowej i wyraźnie powyżej przeciętnej dla klienta API. Ekran pusty wyszukiwania mówi pełnym zdaniem, co się stało. Baner demo jest stale widoczny i ma wyjście. Błędy sieciowe mają osobne komunikaty dla braku sieci, 429, 5xx i 401 zamiast jednego „coś poszło nie tak". Tryb offline mówi wprost, że pokazuje snapshot i kiedy go pobrał, zamiast udawać dane bieżące. To rzadkie.

Trzy rzeczy obniżają tę ocenę i wszystkie trzy mają wspólny mianownik: **aplikacja jest zaprojektowana dla wzroku i dotyku pełnosprawnego użytkownika**, a warstwa niewidoczna — czytnik ekranu, niski kontrast, duża czcionka, drżąca ręka — została pominięta konsekwentnie, nie przypadkowo. Nie ma w całym `ui/` ani jednego `liveRegion`, ani jednego `stateDescription`, ani jednego `heading()`. To nie jest zbiór niedopatrzeń, tylko brakujący wymiar.

Czwarta obserwacja, mniejsza: **overlay tutoriala wycina „reflektor" w połowie karty listy** (zrzut z demo). Granica przyciemnienia przecina kartę „Quick test" w poziomie, co wygląda jak błąd renderowania, a nie jak celowe podświetlenie.

---

## A. Quick wins — małe, tanie, niskiego ryzyka

| # | Rekomendacja | Impact | Effort | Confidence | Risk | Priority |
|---|---|---|---|---|---|---|
| A1 | Zdjąć `alpha = 0.7f` z licznika w wybranej pigułce filtra | 3 | 1 | 5 | 1 | **15,0** |
| A2 | Podnieść cztery obszary dotyku do 48 dp | 4 | 1 | 5 | 1 | **20,0** |
| A3 | Nie podmieniać etykiety przycisku na `"…"` w trakcie ładowania | 4 | 1 | 5 | 1 | **20,0** |
| A4 | `heading()` na tytułach ekranów | 3 | 1 | 5 | 1 | **15,0** |
| A5 | `ImeAction.Next` między polami formularza tworzenia | 3 | 1 | 4 | 1 | **12,0** |
| A6 | Poprawić przycięcie reflektora tutoriala do granic karty | 2 | 2 | 4 | 1 | **4,0** |
| A7 | `heightIn(max = 360.dp)` zamiast sztywnego `height` w dialogu języka | 2 | 1 | 4 | 1 | **8,0** |
| A8 | Separator hero na ekranie logowania jako zasób (pusty w ja/zh/th) | 2 | 1 | 5 | 1 | **10,0** |

**A2 — obszary dotyku.**
*Problem użytkownika:* pigułki filtrów mają ~32 dp wysokości, przycisk kopiowania na szczegółach ~30×34 dp, „Skip" w tutorialu ~30 dp. Przy drżeniu rąk, w ruchu albo jedną ręką to są cele, w które się nie trafia. *Rozwiązanie:* `Modifier.heightIn(min = 48.dp).wrapContentHeight(Alignment.CenterVertically)` przed `.padding(...)` — wzorem `ProScreen.kt:368`, gdzie zrobiono to poprawnie. Wygląd się nie zmienia, rośnie tylko obszar reakcji. *Walidacja:* Accessibility Scanner przestaje zgłaszać te cztery. *Metryka:* liczba ostrzeżeń w raporcie pre-launch Play.

**A3 — etykieta „…".**
*Problem:* w trakcie logowania, tworzenia i zapisu tekst przycisku jest podmieniany na `"…"`. Dla TalkBacka to „wielokropek, przycisk" — użytkownik traci nazwę akcji dokładnie wtedy, gdy najbardziej potrzebuje wiedzieć, co trwa. *Rozwiązanie:* zostawić etykietę, dodać `Modifier.semantics { stateDescription = … }` na `PillButton`, który już przyjmuje parametr `loading`. *Metryka:* porzucenia formularza logowania.

**A1 — licznik w pigułce.** Kontrast 3,27:1 przy 10 sp — najgorsza para w aplikacji. Zdjęcie alphy daje 5,02:1 bez dotykania palety.

---

## B. Średni zakres — nowe stany, zmiany w kilku miejscach

| # | Rekomendacja | Impact | Effort | Confidence | Risk | Priority |
|---|---|---|---|---|---|---|
| B1 | Podnieść kontrast granic pól do 3:1 (zmiana dwóch kolorów palety) | 4 | 2 | 5 | 3 | **10,0** |
| B2 | Akcja „Zaloguj ponownie" w banerze błędu przy 401 | 4 | 2 | 4 | 2 | **8,0** |
| B3 | Tutorial modalny dla czytnika ekranu | 3 | 2 | 4 | 1 | **6,0** |
| B4 | Pobieranie pojedynczej maski (`MaskedEmail/get` z `ids`) | 3 | 3 | 5 | 3 | **5,0** |
| B5 | Ogłaszanie stanów ładowania i banera offline (`liveRegion`) | 3 | 2 | 4 | 1 | **6,0** |
| B6 | Powiązanie błędów walidacji z polem przez `error()` | 3 | 2 | 5 | 1 | **7,5** |
| B7 | Respektowanie systemowego wyłączenia animacji | 2 | 2 | 4 | 1 | **4,0** |

**B1 — kontrast granic pól.**
*Problem:* pole edycji jest wizualnie nieodróżnialne od tła: 1,15:1 w jasnym motywie, 1,04:1 w ciemnym; ramka 1,28:1 i 1,25:1. WCAG 1.4.11 wymaga 3:1 dla granic komponentów. Dotyczy ekranu logowania (wklejanie tokenu), tworzenia maski i edycji szczegółów — czyli każdego miejsca, gdzie użytkownik ma coś wpisać. *Rozwiązanie:* pociemnić `LightLineStrong` do ok. `#8E846E`, rozjaśnić `DarkLineStrong` do ok. `#6E6555`, i użyć `outlineVariant` zamiast `outline` dla stanu nieaktywnego. *Ryzyko:* to jest zmiana palety „warm ink", która jest świadomym elementem produktu — dlatego nie zrobiłem tego w audycie. *Walidacja:* zdjęcie ekranu w słońcu; wyliczenie kontrastu skryptem. *Metryka:* ukończenia logowania.

**B2 — martwa pętla po odwołaniu tokenu.**
*Problem:* po odwołaniu tokenu w panelu Fastmaila każdy ekran pokazuje „Authentication failed", a jedyne wyjście to samodzielne znalezienie Ustawień → Wyloguj. Sesja JMAP nie ma TTL i nic jej nie czyści. *Rozwiązanie:* na 401/403 czyścić sesję JMAP (nie token) i pokazać w banerze akcję „Zaloguj ponownie". *Dlaczego nie automatyczne wylogowanie:* przejściowe 401 z proxy skasowałoby poprawny token. *Metryka:* zgłoszenia supportu na „aplikacja nie działa".

**B4 — pobieranie pojedynczej maski.**
*Problem:* ekran szczegółów pobiera całą listę, żeby pokazać jedną maskę — przy otwarciu, po zapisie i po każdym przełączeniu stanu. Przy ~265 maskach jedna edycja to trzy pełne pobrania listy. *Rozwiązanie:* `MaskedEmail/get` przyjmuje `ids`; dodać wariant i użyć go na szczegółach. *Ryzyko (istotne):* pobranie jednej maski **nie może** nadpisać całego snapshotu offline — to trzeba obsłużyć jawnie, inaczej cache zredukuje się do jednej pozycji. *Metryka:* czas do wyświetlenia szczegółów, transfer na sesję.

---

## C. Eksperymenty — wartość niepewna, najpierw walidacja

| # | Hipoteza | Jak sprawdzić przed budową |
|---|---|---|
| C1 | Ekran powitalny marnuje ~40% wysokości na tanich telefonach — wyśrodkowanie treści poprawia pierwsze wrażenie | Zrzuty na 5" i 6,7" obok siebie; ocena własna Pawła. Zero kodu, zero danych |
| C2 | Chipy filtrów pokazujące liczniki całej kolekcji przy zerze wyników są mylące („All 10" nad „Brak dopasowań") | Pokazać trzem osobom zrzut z pustym wyszukiwaniem i zapytać, ile masek pasuje |
| C3 | Sortowanie po aktywności to nie zawsze to, czego szuka użytkownik z 265 maskami — może brakować sortowania po nazwie/domenie | Zapytać, po czym Paweł szuka maski, gdy jej szuka. Jeśli odpowiedź brzmi „wpisuję domenę w wyszukiwarkę", to funkcja już istnieje i nic nie trzeba dodawać |
| C4 | Stan maski zakodowany wyłącznie kolorem kropki jest nieczytelny dla daltonisty (aktywna/wyłączona/zarchiwizowana to trzy odcienie) | TalkBack **czyta** stan poprawnie, więc problem dotyczy tylko widzącego daltonisty. Sprawdzić symulatorem deuteranopii, zanim cokolwiek się doda |

**Uwaga do C4:** kuszące jest dodanie tekstowej etykiety stanu na każdą kartę. Odradzam bez walidacji — karta ma już nazwę, adres i czas; czwarty element to zagracenie listy, którą użytkownik przewija setki razy. Kropka o innym *kształcie* dla każdego stanu rozwiązałaby to samo za mniejszą cenę.

---

## D. Odrzucone

| Pomysł | Dlaczego nie |
|---|---|
| Automatyczne wylogowanie przy 401 | Przejściowe 401 z proxy skasowałoby poprawny token. Ryzyko utraty dostępu przewyższa wygodę — patrz B2 po właściwe rozwiązanie |
| Etykieta tekstowa stanu na każdej karcie listy | Zagraca najczęściej używany ekran, żeby rozwiązać problem, który TalkBack już rozwiązuje. Najpierw C4 |
| Cache pojedynczych masek obok snapshotu listy | Druga ścieżka spójności danych w aplikacji, która właśnie naprawiła jedną. Koszt utrzymania nieproporcjonalny do zysku |
| Przypomnienia/powiadomienia o nieużywanych maskach | Wprost odrzucone przez Pawła 2026-07-27 po zobaczeniu na realnych danych (265 masek, ekran ogłaszał „221 wymaga uwagi"). Maska założona przy rejestracji, przez którą nic nigdy nie przyszło, to stan normalny — nie usterka. Sam próg tego nie naprawi |
| Onboarding przy pierwszym uruchomieniu poza demo | Tryb demo **jest** onboardingiem i jest lepszy: pokazuje działającą aplikację zamiast opowiadać o niej |
| Pinning certyfikatu dla `api.fastmail.com` | Scaffold jest w `network_security_config.xml` z uzasadnieniem. Pinning wymaga rotacji hashy poza pasmem; nieodświeżony pin to aplikacja, która przestaje działać na całym świecie w jeden dzień. Bez procesu rotacji to ryzyko, nie zabezpieczenie |
| Zbieranie metryk użycia, żeby priorytetyzować powyższe | Sprzeczne z tym, czym jest ten produkt i co obiecuje polityka prywatności. Metryki poniżej są celowo takie, które da się odczytać z Play Console i Crashlyticsa bez śledzenia użytkownika |

---

## Metryki

Wszystkie dostępne bez dokładania choćby jednego zdarzenia telemetrycznego — z Play Console i Crashlyticsa.

| Metryka | Skąd | Co mierzy |
|---|---|---|
| Crash-free users | Crashlytics | Czy A3 (crash-loop DataStore) i A7 faktycznie coś zamknęły |
| ANR rate | Play Console → Vitals | Bezpośredni efekt A5 (zejście szyfrowanego zapisu z wątku głównego) |
| Ostrzeżenia w raporcie pre-launch | Play Console | Postęp A2 i B1 |
| Odinstalowania w 7 dni od instalacji | Play Console | Proxy pierwszego wrażenia (C1) |
| Oceny i treść recenzji | Play Console | Jedyne źródło mówiące, czy „Archiwizuj" kiedykolwiek kogoś zaskoczyło utratą maski |
| Zgłoszenia na `pawel@orzech.me` | Skrzynka | B2 — pętla „Authentication failed" trafiłaby tutaj |

**Czego celowo nie proponuję:** zdarzeń ukończenia logowania, czasu do pierwszej maski, lejka onboardingu. Wszystkie byłyby użyteczne i wszystkie wymagają analityki, której ta aplikacja nie ma i której polityka prywatności jawnie się wypiera. Nie warto tego zmieniać dla priorytetyzacji backlogu UX.

---

## Roadmapa

**Najbliższy patch (1.10.1) — to, co już jest na `feature/audit-2026-07-27`**
Poprawki A1–A13 z `AUDIT_REPORT.md`. Blokada wydania: punkty 1–5 listy QA w `CHANGELOG_AGENT.md`, przede wszystkim potwierdzenie archiwizacji na prawdziwym koncie.

**Kolejne wydanie (1.11.0) — dostępność**
A1–A5, A7, A8 (quick wins) + B5, B6 (ogłaszanie stanów i błędów). Razem to jeden spójny temat i jedna sesja. Wymaga urządzenia z TalkBackiem — bez niego nie ma sensu tego zaczynać.

**Większe wydanie (1.12.0)**
B1 (paleta — decyzja Pawła), B2 (odzyskiwanie po 401), B3 (tutorial modalny), B4 (pobieranie pojedynczej maski). B4 najlepiej razem z jawnym polem wersji w snapshocie cache'u (B17 z raportu).

**Do walidacji, nie do budowy**
C1–C4. Każde kosztuje jedną rozmowę albo jeden zrzut ekranu i może zaoszczędzić funkcję, której nikt nie potrzebuje.

**Backlog higieny, kiedykolwiek**
B10 (log w release), B11 (drugi zamek na receiverze Undo), B14 (README), B15 (martwe stringi), B16 (martwy formatter), B17 (wersja formatu cache'u). Plus selektywny bump zależności — osobny przebieg z własnym zakresem testów.
