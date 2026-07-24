# FastMask — Rekomendacje UX (2026-07-24, pass C)

> Scoring 1–5: **Impact / Effort / Confidence / Risk**. `Priority = Impact × Confidence / Effort` — narzędzie pomocnicze, nie automat.
> Znaleziska naprawione w tym przebiegu są w `AUDIT_REPORT.md §3.1`. Tu są rzeczy **niewdrożone**, wymagające Twojej decyzji.

---

## Ocena UX — stan obecny

Produkt jest dojrzały i spójny. Cztery przebiegi audytu domknęły większość klasycznych braków: stany loading/empty/error/success istnieją i są rozróżnione, akcje destrukcyjne mają potwierdzenie i undo, cele dotyku są ≥ 48 dp, stany masek są ogłaszane przez TalkBack, a nie tylko kolorowane. Ekran tworzenia maski ma walidację inline, lista ma wyszukiwanie i filtry z licznikami.

Trzy rzeczy uważam za realnie słabe punkty — wszystkie dotyczą **pierwszych 60 sekund** i **momentów awarii**, nie codziennego użycia:

1. **Onboarding zaczyna się od żądania tokenu API.** To najwyższy próg wejścia, jaki aplikacja konsumencka może postawić. Tryb demo łagodzi go, ale użytkownik musi najpierw zrozumieć, że demo istnieje i po co.
2. **Brak lokalnego cache masek.** Każde wejście na listę to fetch. Bez sieci nie widzisz **nic** — nawet adresu, który skopiowałeś wczoraj. Dla aplikacji, której podstawowa czynność to „podaj mi mój zamaskowany adres", to poważne ograniczenie.
3. **Feedback po awarii bywa ślepym zaułkiem** w mniej uczęszczanych miejscach (patrz A1, A2 niżej).

---

## A. Quick wins

### A1. Snackbar „skopiowano" nie mówi, **co** skopiowano
**Problem użytkownika.** Przy kilku maskach o podobnych nazwach szybkie kopiowanie z listy nie potwierdza, którą maskę wzięto. Trzeba wejść w schowek albo wkleić, żeby sprawdzić.
**Rozwiązanie.** W komunikacie pokaż skrócony adres: „Skopiowano quiet.harbor412@…".
**Wpływ.** Usuwa krok weryfikacji z najczęstszej akcji w aplikacji.
**Zakres.** Jeden string z parametrem + jedno miejsce wywołania. **Ryzyko:** brak. **Walidacja:** obserwacja własna.
**Impact 3 · Effort 1 · Confidence 4 · Risk 1 → Priority 12,0**

### A2. Nieudany eksport CSV nie mówi dlaczego
**Problem użytkownika.** `SettingsEvent.ExportFailed` daje jeden generyczny komunikat niezależnie od tego, czy padła sieć, czy zapis na dysk. Użytkownik nie wie, czy ponawiać, czy zwolnić miejsce.
**Rozwiązanie.** Rozróżnij dwa przypadki — błąd pobierania (użyj `UiErrors.messageRes`, tak jak reszta aplikacji) i błąd zapisu pliku.
**Zakres.** Rozszerzenie eventu o przyczynę, 2 stringi. **Ryzyko:** brak. **Walidacja:** tryb samolotowy + zapełniony dysk.
**Impact 3 · Effort 2 · Confidence 4 · Risk 1 → Priority 6,0**

### A3. Brak `<plurals>` w licznikach
**Problem użytkownika.** „1 masks" / „1 masek" zamiast „1 maska". W polskim, rosyjskim i ukraińskim liczba mnoga ma trzy formy — obecny format je łamie.
**Rozwiązanie.** Zamień liczniki w nagłówku listy i chipach filtrów na `<plurals>`.
**Zakres.** ~4 zasoby × 20 lokali. Niebanalne przy 20 językach, ale mechaniczne. **Ryzyko:** niskie. **Walidacja:** przełącz na PL/RU i sprawdź 1, 2, 5, 22.
**Impact 3 · Effort 3 · Confidence 5 · Risk 1 → Priority 5,0**

### A4. `login_hero_suffix` = "." jako osobny zasób
**Problem.** Kropka jako tłumaczony string to zaproszenie do niespójności — 15 lokali ma ją „nieprzetłumaczoną", co zaśmieca każdy audyt i18n.
**Rozwiązanie.** Wciągnij kropkę do stringu nadrzędnego albo do kodu.
**Impact 1 · Effort 1 · Confidence 5 · Risk 1 → Priority 5,0**

---

## B. Średni zakres

### B1. Lokalny cache masek (offline-read)
**Problem użytkownika.** Bez sieci lista jest pusta. Najczęstszy przypadek użycia — „potrzebuję adresu, który podałem sklepowi" — zawodzi dokładnie wtedy, gdy jesteś w terenie ze słabym zasięgiem.
**Rozwiązanie.** Room albo serializowany DataStore z ostatnią udaną odpowiedzią; przy braku sieci pokaż dane z cache z wyraźnym znacznikiem „zaktualizowano X temu".
**Wpływ.** Największa pojedyncza poprawa użyteczności, jaką widzę w tym produkcie.
**Ryzyko.** Trzeba to zrobić uczciwie: cache masek to lista adresów użytkownika na dysku. Wymaga tej samej ochrony co token — szyfrowanie i wyczyszczenie przy wylogowaniu. **To nie jest quick win i nie należy go robić w pośpiechu.**
**Wpływ na prostotę.** Ujemny — dochodzi warstwa persystencji i pytanie o unieważnianie cache.
**Walidacja.** Tryb samolotowy po wcześniejszym udanym wejściu. **Metryka:** udział sesji kończących się pustym ekranem.
**Impact 5 · Effort 4 · Confidence 4 · Risk 3 → Priority 5,0**

### B2. Onboarding: pokaż wartość zanim poprosisz o token
**Problem użytkownika.** Ekran powitalny stawia obok siebie „Zaloguj przez Fastmail" i „Wypróbuj demo", ale nie tłumaczy, dlaczego zdobycie tokenu API jest tego warte ani jak długo to trwa.
**Rozwiązanie.** Krótki, konkretny krok pośredni: co robi aplikacja (jedno zdanie), po czym instrukcja zdobycia tokenu z liczbą kroków („3 tapnięcia w ustawieniach Fastmail") i linkiem prosto tam.
**Ryzyko.** Dodatkowy ekran to dodatkowe tarcie — łatwo pogorszyć. **Dlatego to eksperyment do zmierzenia, nie pewniak.**
**Metryka:** ukończone logowania / uruchomienia aplikacji.
**Impact 4 · Effort 3 · Confidence 3 · Risk 3 → Priority 4,0**

### B3. Testy instrumentowane głównych ścieżek
**Problem.** Zero testów UI. Cztery przebiegi audytu znalazły defekty, które test E2E złapałby od razu — martwe linki (D1) i angielskie dialogi (D2) w tym.
**Rozwiązanie.** 5–8 testów Compose: login → lista → tworzenie → szczegół → archiwizacja z undo → ustawienia.
**Wpływ.** Nie jest to UX per se, ale jest to najskuteczniejsza dostępna ochrona jakości UX.
**Impact 4 · Effort 4 · Confidence 4 · Risk 1 → Priority 4,0**

---

## C. Eksperymenty (wartość niepewna — najpierw zmierz)

### C1. Autouzupełnianie domeny z schowka
Gdy w schowku jest URL, zaproponuj domenę w polu „dla domeny". Może być pomocne, może być wścibskie. **Odczyt schowka bez wyraźnej akcji użytkownika jest sygnałem prywatnościowym** — na Androidzie 12+ wywołuje systemowy toast. Prototyp + własna obserwacja przed decyzją.
**Impact 3 · Effort 2 · Confidence 2 · Risk 3 → Priority 3,0**

### C2. Widget / szybkie tworzenie maski ze skrótu
Skrót na ekranie głównym tworzący maskę i od razu kopiujący ją do schowka. Hipoteza: główna czynność to „daj mi adres teraz". Wymaga zmierzenia, ile sesji to wyłącznie tworzenie.
**Impact 3 · Effort 4 · Confidence 2 · Risk 2 → Priority 1,5**

---

## D. Odrzucone

| Rekomendacja | Dlaczego odrzucam |
|---|---|
| **Certificate pinning dla api.fastmail.com** | Konfiguracja opisuje to jako świadomie pominięte i **zgadzam się**. Pinning bez procesu rotacji zamurowuje aplikację przy każdej zmianie certyfikatu Fastmail. Obecne ograniczenie do systemowego CA store już blokuje MDM/mitmproxy — realny model zagrożeń jest pokryty |
| **Wzmocnienie „dowodu" uprawnienia Pro (E7)** | Digest tokenu zakupu nie jest i nie może być zabezpieczeniem — atakujący z dostępem do DataStore podrobi go razem ze statusem. Realną bramą jest weryfikacja podpisu Play (D5). Rozbudowa digestu to **pozorne zabezpieczenie** |
| **Analityka zachowań użytkownika (Firebase/Amplitude)** | Aplikacja obsługuje adresy e-mail — dane z definicji wrażliwe. `LogMonetizationAnalytics` pisze tylko do logcatu i to jest właściwy poziom. Wysyłanie czegokolwiek na zewnątrz kłóci się z pozycjonowaniem „privacy-first · open source" |
| **Skrócenie retencji eksportu CSV poniżej 1 h (E6)** | Komentarz w kodzie ma rację: agresywne czyszczenie potrafi uciąć URI, które wolny odbiorca (upload na Dysk) wciąż czyta. Utrata eksportu w trakcie udostępniania jest gorsza niż plik w prywatnym katalogu cache |
| **Migracja na Room / KMP / przepisanie architektury** | ~11 000 linii, jeden deweloper, czysta Clean Architecture bez długu blokującego. Nie ma problemu, który by to uzasadniał |
| **Masowy bump zależności** | Wszystkie wersje są spójne i działają; Billing 8.3 spełnia wymóg Play do sierpnia 2026. Bump bez powodu to ryzyko bez zysku. Dependabot ma otwarte PR-y — przejrzyj selektywnie |
| **Usunięcie trybu demo** | Jedyne, co obniża próg tokenu API przed pierwszym kontaktem z produktem |

---

## Roadmapa

**Najbliższy patch (v1.8.2) — to, co już jest zrobione na `feature/audit-2026-07-24c`.** D1 (martwe linki) to jedyna pozycja, która realnie wymaga wydania: Polityka prywatności i Regulamin na paywallu są wymogiem Play, a od v1.8.1 nie działają.

**Kolejny release (v1.9.0).** A1 i A2 (feedback kopiowania i eksportu), A3 (`<plurals>`), A4. Razem 1–2 wieczory.

**Większy release (v2.0).** B1 (cache offline) jako pojedyncza duża pozycja, poprzedzona decyzją o szyfrowaniu. Równolegle B3 (testy instrumentowane) — najlepiej **przed** B1, żeby cache miał czym być pilnowany.

**Do walidacji zanim cokolwiek zbudujesz.** B2 (onboarding) i C1/C2 — wszystkie trzy opierają się na hipotezach o zachowaniu użytkowników, których obecnie nie mierzysz.

---

## Metryki

Dobrane tak, by **nie wymagały zbierania treści użytkownika ani danych wrażliwych**. Wszystkie da się policzyć z Play Console albo z anonimowego licznika lokalnego.

| Metryka | Skąd | Po co |
|---|---|---|
| Crash-free users, ANR rate | Play Console (za darmo, bez SDK) | Podstawowa higiena; D4 dotyczył realnej ścieżki crashu |
| Ukończone logowania / uruchomienia | licznik lokalny | Próg wejścia tokenu API — punkt odniesienia dla B2 |
| Udział sesji kończących się pustą listą | licznik lokalny | Uzasadnienie (lub obalenie) B1 |
| Odinstalowania w ciągu 24 h | Play Console | Najostrzejszy sygnał porażki onboardingu |
| Oceny i zgłoszenia supportu | Play Console + mail | **Uwaga:** kanał mailowy do v1.8.1 był zepsuty (D1) — dane historyczne z tego kanału są niemiarodajne |
| Konwersja na Pro wg `source` | istniejące `MonetizationEvent` | `PAYWALL_VIEWED` już nosi źródło (settings / accent / app_lock / export) — wiadomo, która funkcja sprzedaje |

**Czego nie proponuję zbierać:** treści masek, adresów, opisów, zapytań wyszukiwania ani niczego, co pozwala zidentyfikować konto Fastmail. Nie ma potrzeby produktowej, a jest podstawa prawna przeciw.
