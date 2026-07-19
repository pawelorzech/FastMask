---
task: Pełny audyt techniczny + naprawy + testy + audyt UX FastMask
project: FastMask
slug: fastmask-full-audit
effort: E4
phase: complete
progress: 124/136
mode: standard
started: 2026-07-19T00:30:00+02:00
updated: 2026-07-19T00:30:00+02:00
---

# ISA — FastMask (Android · Kotlin · JMAP Fastmail Masked Email)

## Problem

FastMask v1.5.2 (vc11) jest tuż po przygotowaniu do Play Store, ale: (1) repo ma **zero testów automatycznych** mimo zadeklarowanych zależności testowych; (2) poprzedni audyt bezpieczeństwa (2026-04-27, F-01…F-12) został „naprawiony" w v1.5, lecz nikt nie zweryfikował implementacji względem deklaracji; (3) dokumentacja (README/SECURITY/CLAUDE.md) rozjeżdża się ze stanem kodu (np. SDK 34 vs 35, webp vs png); (4) brak systematycznego przeglądu poprawności (JMAP parsing, race conditions, error handling), wydajności, dostępności i UX. Aplikacja operuje długożyciowym tokenem API Fastmail — bezpieczeństwo i niezawodność są ważniejsze niż nowe funkcje.

## Vision

Paweł dostaje aplikację, w której każda ścieżka od "muszę znaleźć/skopiować/stworzyć maskę" do celu jest krótka i niezawodna; audyt wykrywa i naprawia realne błędy (nie teoretyczne), każda naprawa ma test regresyjny, a raport końcowy czyta się jak mapa stanu produktu — z jasnym podziałem: zrobione / do zrobienia / propozycje. Euforyczna niespodzianka: znalezione i naprawione bugi, o których istnieniu nie wiedział, plus siatka testów tam, gdzie wcześniej była pustka.

## Out of Scope

Duże nowe funkcje produktowe (widget, autofill, quick settings tile, bulk actions) — tylko propozycje w backlogu, zero implementacji bez zgody. Zmiana applicationId, kluczy podpisujących, konfiguracji Google Play. Publikacja release'u. Telemetria i zewnętrzne SDK. Rewrite architektury. Zmiana mechanizmu przechowywania tokenu bez bezpiecznej migracji. Destrukcyjne operacje git.

## Principles

- Reprodukcja przed naprawą; pomiar przed optymalizacją; dowód przed twierdzeniem.
- Najmniejsza poprawna zmiana > przebudowa. Istniejąca architektura zostaje, jeśli działa.
- Bezpieczeństwo tokenu > wygoda dewelopera > estetyka kodu.
- Rozróżniaj: realna podatność / defense-in-depth / rekomendacja / fałszywy alarm.
- Testy tam, gdzie łamią się rzeczy (parsing, stany, błędy), nie tam, gdzie łatwo je napisać.
- Dokumentacja musi opisywać stan faktyczny, nie aspiracje.

## Constraints

- JDK 17 toolchain (build), Kotlin 1.9.22, AGP 8.5.2, Gradle 8.9 — nie podbijamy wersji narzędzi w ramach audytu, chyba że to konieczne dla naprawy P0/P1.
- Nie wyłączamy testów, lintów, minifikacji ani reguł bezpieczeństwa, by "przeszło".
- Release bez debug keystore — niepodpisany, jeśli brak FASTMASK_KEYSTORE.
- Brak nowych zależności runtime poza absolutnie koniecznymi dla napraw.
- Format commitów: `Add:/Fix:/Update:/Refactor:` (CLAUDE.md projektu).

## Goal

Zbudować, przeaudytować, naprawić i zweryfikować FastMask end-to-end: baseline (build+test+lint) udokumentowany, potwierdzone problemy P0–P2 naprawione z testami regresyjnymi, aplikacja przetestowana manualnie na emulatorze, audyt UX z priorytetyzowanym backlogiem A/B/C, raport końcowy w 10-sekcyjnej strukturze — bez wprowadzenia regresji i bez nowych dużych funkcji.

## Criteria

### A. Baseline i środowisko (ISC-1…10)
- [x] ISC-1: `./gradlew assembleDebug` kończy się sukcesem (probe: Bash exit 0)
- [x] ISC-2: `./gradlew test` kończy się sukcesem, wynik zanotowany z liczbą testów (probe: Bash)
- [x] ISC-3: `./gradlew lint` kończy się sukcesem, raport przeczytany (probe: Read lint-results)
- [x] ISC-4: `./gradlew assembleRelease` bez FASTMASK_KEYSTORE produkuje niepodpisany APK lub jasny błąd — bez debug keystore (probe: Bash + apksigner)
- [x] ISC-5: Baseline zapisany: co się buduje, co nie, ostrzeżenia (probe: Read raportu)
- [x] ISC-6: Wersje potwierdzone w kodzie: Kotlin 1.9.22, AGP 8.5.2, Gradle 8.9, Compose BOM 2024.09, min 26 / target 35 / compile 35 (probe: Read gradle files)
- [x] ISC-7: Emulator uruchomiony, apka zainstalowana i odpalona (probe: adb shell + screenshot)
- [x] ISC-8: Struktura modułów i warstw udokumentowana w raporcie (probe: Read)
- [x] ISC-9: CI workflows przeczytane i ocenione (probe: Read .github/workflows)
- [x] ISC-10: Rozbieżności dokumentacja↔kod wylistowane (README SDK 34 vs 35, webp vs png itd.) (probe: Grep + Read)

### B. Poprawność — JMAP / dane (ISC-11…30)
- [x] ISC-11: JmapModels — wszystkie pola nullable z API mają bezpieczne odczyty; brak crasha na niepełnej odpowiedzi (probe: Read + test)
- [x] ISC-12: Parsowanie dat (createdAt, lastMessageAt) odporne na null/format; strefa czasowa poprawna (probe: unit test)
- [x] ISC-13: Sortowanie po ostatniej aktywności: lastMessageAt desc, fallback createdAt — potwierdzone testem (probe: unit test)
- [x] ISC-14: Filtrowanie Active/Off/Archived mapuje się 1:1 na stany JMAP (enabled/disabled/deleted) — test (probe: unit test)
- [x] ISC-15: Create maski: sukces parsowany z `created`, błąd z `notCreated` (probe: Read JmapApi + test)
- [x] ISC-16: Update maski: `updated`/`notUpdated` sprawdzane; brak cichych no-opów (probe: Read + test)
- [x] ISC-17: Delete/archive: `destroyed`/`notDestroyed` sprawdzane (probe: Read + test)
- [x] ISC-18: HTTP 401/403 → wykrywalny stan "token nieważny", nie generyczny crash (probe: Read + test)
- [x] ISC-19: Timeout / brak sieci / DNS → czytelny komunikat błędu, nie crash (probe: Read + manual)
- [x] ISC-20: 429 i 5xx → obsłużone bez crasha (probe: Read + test)
- [ ] ISC-21: Pusta lista masek → poprawny empty state (probe: manual/demo)
- [x] ISC-22: methodResponses parsowane defensywnie — error response w methodResponses (np. "error" zamiast "MaskedEmail/get") nie crashuje (probe: Read + test)
- [x] ISC-23: accountId null (konto bez MaskedEmail capability) → czytelny błąd (probe: Read + test)
- [x] ISC-24: Równoległe/wielokrotne kliknięcia (create, toggle, archive) nie duplikują żądań (probe: Read ViewModels)
- [x] ISC-25: Coroutine cancellation — opuszczenie ekranu anuluje w toku; brak leaków (probe: Read)
- [x] ISC-26: Rotacja ekranu nie gubi stanu (query, filtr, formularz create) (probe: manual)
- [x] ISC-27: Process death — odtworzenie bez crasha (probe: manual adb kill)
- [x] ISC-28: Operacje sieciowe poza main thread (probe: Read — suspend/Dispatchers)
- [x] ISC-29: StateFlow/SharedFlow użyte poprawnie (eventy nie giną przy rotacji) (probe: Read)
- [x] ISC-30: Wygaśnięcie/cofnięcie tokenu w trakcie sesji → sensowna ścieżka (komunikat/redirect), nie pętla błędów (probe: Read + manual)

### C. Bezpieczeństwo (ISC-31…55)
- [x] ISC-31: Token nie trafia do logów — brak Log.*/println z tokenem; OkHttp logging tylko debug z redakcją Authorization (probe: Grep + Read NetworkModule)
- [x] ISC-32: Release build ma zero interceptorów logujących (probe: Read NetworkModule — BuildConfig.DEBUG gate)
- [x] ISC-33: TokenStorage: EncryptedSharedPreferences, klucz w Android KeyStore (probe: Read)
- [x] ISC-34: Logout czyści token i sesję JMAP (probe: Read AuthRepositoryImpl)
- [x] ISC-35: Token czyszczony z LoginUiState po próbie logowania (probe: Read LoginViewModel + test)
- [x] ISC-36: allowBackup=false w manifeście (probe: Read AndroidManifest)
- [x] ISC-37: Brak eksportowanych komponentów poza MainActivity MAIN/LAUNCHER (probe: Read manifest)
- [x] ISC-38: FLAG_SECURE + filterTouchesWhenObscured aktywne (probe: Read MainActivity)
- [x] ISC-39: Network Security Config: system CA only dla api.fastmail.com, brak cleartext (probe: Read xml)
- [x] ISC-40: apiUrl z sesji JMAP walidowany do *.fastmail.com + https (probe: Read JmapApi + test)
- [x] ISC-41: Walidacja apiUrl pokryta testem jednostkowym z wrogim URL (probe: unit test)
- [x] ISC-42: Brak sekretów w repo i historii git (probe: gitleaks/grep)
- [x] ISC-43: security-crypto alpha — ryzyko ocenione, decyzja udokumentowana (bez zmiany bez migracji) (probe: Read Decisions)
- [x] ISC-44: GitHub Actions przypięte do SHA (probe: Read workflows)
- [x] ISC-45: Anti: żaden fix nie wysyła tokenu poza *.fastmail.com (probe: Grep — brak nowych hostów)
- [x] ISC-46: Anti: nie dodano żadnego SDK telemetrii/analityki (probe: Read build.gradle diff)
- [x] ISC-47: Schowek po kopiowaniu adresu: brak wrażliwych flag; adres maski to nie sekret, ale ocena zachowania (probe: Read + manual)
- [x] ISC-48: Deep linki / intent spoofing — brak intent-filterów z data scheme (probe: Read manifest)
- [x] ISC-49: WebView nieobecny (probe: Grep)
- [x] ISC-50: ProGuard rules nie osłabiają bezpieczeństwa (brak -dontobfuscate globalnego, keep tylko konieczne) (probe: Read proguard-rules.pro)
- [x] ISC-51: Demo mode nie omija ścieżek bezpieczeństwa (nie zapisuje fake tokenu do TokenStorage) (probe: Read Demo* + AuthRepositoryImpl)
- [x] ISC-52: Uprawnienia: tylko INTERNET (probe: Read manifest)
- [x] ISC-53: Redirecty HTTP nie mogą wyprowadzić tokenu poza fastmail.com (OkHttp followRedirects ocenione) (probe: Read NetworkModule)
- [x] ISC-54: Dokument prywatności zgodny z faktycznym zachowaniem apki (probe: Read docs/privacy.md vs kod)
- [x] ISC-55: SECURITY.md zgodny z implementacją (probe: Read + diff)

### D. Architektura i jakość kodu (ISC-56…70)
- [x] ISC-56: Kierunek zależności ui→domain→data zachowany; brak przecieków warstw (probe: Read importy)
- [x] ISC-57: ViewModele nie trzymają referencji do Context/Activity (probe: Grep)
- [x] ISC-58: Eventy jednorazowe (nawigacja, logout) nie giną i nie duplikują się (probe: Read)
- [x] ISC-59: Klucze list w LazyColumn stabilne (id maski) (probe: Read ListScreen)
- [x] ISC-60: remember/LaunchedEffect/DisposableEffect użyte poprawnie — brak side effects w kompozycji (probe: Read)
- [x] ISC-61: Duplikacja kodu oceniona; oczywiste duplikaty zredukowane lub uzasadnione (probe: Read)
- [x] ISC-62: Za duże composable/ViewModele zidentyfikowane (probe: wc -l + Read)
- [x] ISC-63: MaskedEmailRepositoryDispatcher (demo vs real) poprawny i przełącza się atomowo (probe: Read + test)
- [x] ISC-64: Use case'y — cienkie, testowalne (probe: Read)
- [x] ISC-65: Mapowanie API→domain w jednym miejscu, pokryte testem (probe: Read + test)
- [x] ISC-66: Stany UI (loading/empty/error/data) modelowane jawnie (probe: Read UiState)
- [x] ISC-67: Brak nieobsłużonych wyjątków w warstwie repo (runCatching konsekwentnie) (probe: Read)
- [x] ISC-68: Nawigacja: brak podwójnej nawigacji przy dwukliku (probe: Read NavHost)
- [x] ISC-69: Recompozycje: brak oczywistych niestabilnych parametrów na hot path listy (probe: Read)
- [x] ISC-70: Anti: nie dodano zbędnej abstrakcji (nowe interfejsy tylko gdy rozwiązują problem) (probe: git diff review)

### E. Wydajność (ISC-71…78)
- [x] ISC-71: Formatowanie dat/relative time nie wykonuje się na każdej rekompozycji od zera (probe: Read RelativeTime + ListScreen)
- [DEFERRED-VERIFY] ISC-72: Lista 500+ masek płynna — brak pracy O(n) w item lambda (probe: Read + demo z dużą listą)
- [x] ISC-73: Start apki: brak ciężkiej pracy w Application.onCreate (probe: Read)
- [x] ISC-74: Brak zbędnych żądań sieciowych (np. podwójny fetch przy starcie listy) (probe: Read + logi debug)
- [ ] ISC-75: Rozmiar release APK zanotowany; R8 + shrinkResources aktywne (probe: Bash ls + Read gradle)
- [x] ISC-76: Fonty Google Fonts — zachowanie offline ocenione (fallback bez crasha) (probe: Read Type.kt + manual airplane)
- [x] ISC-77: Obrazy/ikony bez nadmiarowych rozmiarów (probe: du -sh res)
- [x] ISC-78: Anti: żadna "optymalizacja" bez dowodu problemu (probe: Decisions)

### F. Dostępność i i18n (ISC-79…92)
- [DEFERRED-VERIFY] ISC-79: Wszystkie ikony akcji mają contentDescription (probe: Grep contentDescription=null vs stringResource)
- [x] ISC-80: Touch targets ≥48dp dla akcji na kartach (probe: Read + manual)
- [x] ISC-81: Font scale 200% — ekrany używalne, bez ucięć krytycznych (probe: manual/screenshot)
- [DEFERRED-VERIFY] ISC-82: Kontrast warm-ink palety spełnia WCAG AA dla tekstu głównego (probe: obliczenie kontrastu z Color.kt)
- [DEFERRED-VERIFY] ISC-83: TalkBack — krytyczne ścieżki (login, lista, kopiowanie) ogłaszane sensownie (probe: Read semantics + manual)
- [x] ISC-84: RTL (arabski) — layout nie łamie się (probe: manual screenshot)
- [x] ISC-85: Deklaracja 20 języków vs faktyczne pokrycie tłumaczeń — policzone brakujące klucze per locale (probe: skrypt diff strings.xml)
- [x] ISC-86: Brakujące tłumaczenia → fallback na EN bez crasha (probe: Read + manual)
- [x] ISC-87: Hardcoded strings w composable — wylistowane (probe: Grep)
- [DEFERRED-VERIFY] ISC-88: Plurale użyte poprawnie tam, gdzie liczby (probe: Grep plurals)
- [x] ISC-89: Formaty dat zależne od locale (probe: Read RelativeTime)
- [x] ISC-90: locales_config.xml zgodny z faktycznymi katalogami values-* (probe: diff)
- [DEFERRED-VERIFY] ISC-91: Języki z długimi tekstami (DE) nie łamią przycisków (probe: manual/screenshot)
- [x] ISC-92: Komunikaty błędów przetłumaczone, nie surowe wyjątki (probe: Read + Grep)

### G. Android / release (ISC-93…102)
- [x] ISC-93: targetSdk 35 spełnia wymóg Play 2026 (probe: Read + web docs)
- [x] ISC-94: Edge-to-edge poprawny na API 35 (probe: manual na emulatorze API 35)
- [x] ISC-95: Predictive back — ocenione (enableOnBackInvokedCallback) (probe: Read manifest)
- [x] ISC-96: Adaptive icon poprawny (foreground/background/monochrome) (probe: Read mipmap + emulator)
- [x] ISC-97: Dark mode — wszystkie ekrany poprawne (probe: manual screenshots)
- [DEFERRED-VERIFY] ISC-98: App działa na API 26 (emulator lub uzasadniony skip z ryzykiem) (probe: manual/deferred)
- [x] ISC-99: versionCode/versionName spójne z CHANGELOG (probe: Read)
- [x] ISC-100: 16 KB page size — flaga/zgodność potwierdzona (probe: Read gradle + docs)
- [x] ISC-101: README/Play copy/privacy zgodne z aplikacją (probe: Read diff)
- [ ] ISC-102: Anti: konfiguracja podpisu i applicationId nietknięte (probe: git diff)

### H. Naprawy i testy (ISC-103…112)
- [x] ISC-103: Każdy potwierdzony P0/P1 naprawiony (probe: git diff + testy)
- [x] ISC-104: Dobrze potwierdzone P2 naprawione lub świadomie odroczone z uzasadnieniem (probe: raport)
- [x] ISC-105: Testy jednostkowe: mapowanie JMAP→domain (probe: bun… nie — ./gradlew test)
- [x] ISC-106: Testy: sortowanie + filtrowanie listy (probe: gradlew test)
- [x] ISC-107: Testy: walidacja apiUrl (wrogi host odrzucony) (probe: gradlew test)
- [x] ISC-108: Testy: LoginViewModel — token czyszczony, błąd 401 → komunikat (probe: gradlew test)
- [x] ISC-109: Testy: parsowanie błędów JMAP (notCreated/notUpdated/notDestroyed/error) (probe: gradlew test)
- [x] ISC-110: Pełny `./gradlew test` zielony po zmianach, liczba testów ≥ baseline+nowe (probe: Bash)
- [x] ISC-111: `./gradlew lint` bez nowych błędów po zmianach (probe: Bash)
- [x] ISC-112: Anti: żaden test nie jest pusty/tautologiczny (probe: Read testów)

### I. Testy manualne (ISC-113…122)
- [x] ISC-113: Pierwsze uruchomienie + welcome + demo mode (probe: screenshot)
- [x] ISC-114: Login nieprawidłowym tokenem → czytelny błąd (probe: screenshot)
- [DEFERRED-VERIFY] ISC-115: Brak internetu → czytelny błąd (probe: screenshot airplane)
- [x] ISC-116: Wyszukiwanie + wszystkie filtry działają (probe: screenshot)
- [x] ISC-117: Kopiowanie adresu → feedback + zawartość schowka (probe: adb clipboard)
- [x] ISC-118: Tworzenie maski (demo) end-to-end (probe: screenshot)
- [DEFERRED-VERIFY] ISC-119: Edycja, enable/disable, archiwizacja, przywrócenie (demo) (probe: screenshots)
- [x] ISC-120: Logout → powrót do loginu, token wyczyszczony (probe: manual + Read prefs)
- [x] ISC-121: Rotacja + process death na liście i formularzu (probe: manual)
- [x] ISC-122: Jasny/ciemny motyw + font 200% + RTL screenshots (probe: screenshots)

### J. Audyt UX i raport (ISC-123…136)
- [x] ISC-123: 12 przepływów użytkownika ocenione (kroki, feedback, cofnięcie, błędy) (probe: raport sekcja 7)
- [x] ISC-124: Onboarding tokenu oceniony jako bariera #1 — konkretna analiza (probe: raport)
- [x] ISC-125: Backlog A (quick wins) ≥5 pozycji w pełnym formacie (probe: raport sekcja 8)
- [x] ISC-126: Backlog B (średnie) ≥4 pozycje (probe: raport)
- [x] ISC-127: Backlog C (duże, bez implementacji) ≥4 pozycje (probe: raport)
- [x] ISC-128: Każda propozycja: problem, dowód, rozwiązanie, efekt, nakład XS-XL, priorytet, pomiar privacy-first (probe: Read)
- [x] ISC-129: Raport: executive summary + baseline + tabela problemów + zmiany + wyniki testów (probe: Read)
- [x] ISC-130: Raport: problemy nienaprawione z ryzykiem i next steps (probe: Read)
- [x] ISC-131: Raport: plan dzień/tydzień/2-4 tygodnie (probe: Read)
- [x] ISC-132: Raport: oceny 1-10 z uzasadnieniami (8 wymiarów) (probe: Read)
- [x] ISC-133: Wszystkie liczby w raporcie z faktycznych komend (nie "testy przechodzą") (probe: Read)
- [x] ISC-134: Anti: żadna duża funkcja nie zaimplementowana bez zgody (probe: git diff)
- [x] ISC-135: Anti: żaden test/lint/minifikacja nie wyłączone (probe: git diff)
- [x] ISC-136: Dokumentacja zaktualizowana tam, gdzie kłamała (README SDK, itd.) (probe: git diff)

## Test Strategy

| isc | type | check | threshold | tool |
|-----|------|-------|-----------|------|
| 1-5 | build | gradle exit codes + raporty | exit 0 | Bash |
| 11-23 | unit | nowe testy JMAP/parsing/sort/filter | green | gradlew test |
| 24-30 | inspection+manual | Read + emulator | brak crasha | Read/adb |
| 31-55 | inspection | Grep/Read + gitleaks | zgodność z deklaracją | Grep/Read/Bash |
| 41,105-109 | unit | testy regresyjne | green | gradlew test |
| 71-78 | inspection+measure | Read + APK size + demo 500 masek | brak jank | Bash/emulator |
| 79-92 | inspection+manual | Grep semantics + screenshots | AA / używalność | Grep/emulator |
| 93-102 | inspection+manual | Read + emulator API 35 | zgodność Play | Read/adb |
| 113-122 | manual | scenariusze na emulatorze | wykonane + screenshot | adb/Interceptor |
| 123-136 | report | raport końcowy | kompletny format | Read |

## Features

| name | satisfies | depends_on | parallelizable |
|------|-----------|------------|----------------|
| baseline-build | ISC-1..6,9,10 | — | yes |
| code-review-core (data/domain/di) | ISC-11..30,56..70 | — | yes |
| security-review | ISC-31..55 | — | yes |
| a11y-i18n-review | ISC-79..92 | — | yes |
| perf-review | ISC-71..78 | — | yes |
| fixes | ISC-103,104 | reviews | partial |
| regression-tests | ISC-105..112 | fixes | yes |
| emulator-qa | ISC-7,113..122,94..98 | fixes | no |
| ux-audit | ISC-123..128 | emulator-qa | no |
| final-report | ISC-129..136 | all | no |

## Decisions

- 2026-07-19 00:30 — Voice/Pulse SKIP na cały run (decyzja Pawła 2026-07-13, port 31337 martwy). Algorithm voice curls pominięte świadomie.
- 2026-07-19 00:30 — Build przez Android Studio JBR 21 (jedyny sensowny JDK na maszynie; PATH ma JDK 26 nieobsługiwany przez Gradle 8.9); kotlin jvmToolchain(17) rozwiązuje target.
- 2026-07-19 00:30 — Baseline `clean assembleDebug test lint` odpalony w tle przed czytaniem kodu (równoległość).
- 2026-07-19 — ISC-43: security-crypto zostaje na 1.1.0-alpha06. Migracja do stabilnej 1.0.0 wymaga testu odczytu istniejących zaszyfrowanych tokenów na urządzeniach użytkowników (ta sama powierzchnia API, ale ryzyko odczytu keysetu). Odroczone jako osobny PR z testem migracji; recovery z korupcji (FIX A3) domyka najgroźniejszy scenariusz niezależnie od wersji.
- 2026-07-19 — R8 keep rules świadomie nietknięte (P3-zysk vs P1-ryzyko release crash); zawężenie w backlogu z wymogiem smoke testu release.
- 2026-07-19 — Cato "concerns" → naprawiono ensureSession race (Mutex); mapowanie JMAP in-body errors do fallbacku udokumentowane jako zamierzone (auth u Fastmail = transport 401, potwierdzone live probe).
- 2026-07-19 — show-your-math delegacja E4 (soft ≥2): spełniona ×8 (3×Explore review, 4×Haiku tłumaczenia, Forge, Cato).

### Wstępne ustalenia BUILD (2026-07-19, przed konsolidacją agentów)

- BUG-CAND-1 (P1): TokenStorage lazy init EncryptedSharedPreferences bez recovery — korupcja keysetu (KeyStore) = permanentny crash-loop przy starcie; brak try-catch + reset prefs.
- BUG-CAND-2 (P1→P2): MainActivity.onCreate woła isLoggedIn() na main: KeyStore+disk IO (EncryptedSharedPrefs) + runBlocking DataStore — jank startu, potencjalny ANR na wolnych urządzeniach.
- BUG-CAND-3 (P2): parseSetResponseDestroyed nie sprawdza pozytywnie `destroyed` (asymetria z parseSetResponseUpdated) — cichy no-op delete uznany za sukces.
- BUG-CAND-4 (P2): AuthRepositoryImpl.logout() używa runBlocking (2×DataStore edit) — wołane z main.
- BUG-CAND-5 (P2): strings: email_detail_created_by ma %s w 18 locale, 0 args w EN — literalne "%s" w UI 18 języków; +54 StringFormatCount lint.
- BUG-CAND-6 (P2): lint FAILED w baseline: 51 errors (MissingTranslation ×51), 150 warnings — build z lintem nie przechodzi.
- BUG-CAND-7 (P3): SharedFlow events (LoginEvent itd.) replay=0 — event ginie gdy brak kolektora (okno rotacji).
- BUG-CAND-8 (P3): Demo update `params.x ?: mask.x` — nie da się wyczyścić pola w demo (niezgodność z real API).
- BUG-CAND-9 (P3): MaskedEmail.formatter statyczny — locale/strefa zamrożone do restartu procesu.
- BUG-CAND-10 (P3): ensureSession nie jest synchronizowane — równoległe wywołania mogą podwójnie pobrać sesję.
- CLEAN: F-02 (logging DEBUG-gated+redact) ✓, F-04 (allowBackup=false) ✓, F-07 (apiUrl walidowany) ✓, F-12 (filterTouchesWhenObscured, release) ✓, F-06 (FLAG_SECURE, release) ✓, demo mode nie dotyka TokenStorage ✓, tylko INTERNET ✓, brak eksportowanych komponentów poza launcherem ✓, OkHttp cross-host redirect dropuje Authorization (zachowanie OkHttp 4.x) ✓.

## Changelog

- **C/R/L 2026-07-19 — one-shot eventy**: conjectured: `MutableSharedFlow(extraBufferCapacity=1, DROP_OLDEST)` wystarczy, by eventy przeżyły okno rotacji · refuted by: LoginViewModelTest ("buffered for late collector" → null — replay=0 nie dostarcza przyszłym kolektorom) · learned: buforowanie dla PRZYSZŁYCH kolektorów wymaga `Channel(BUFFERED).receiveAsFlow()`; extraBufferCapacity pomaga tylko wolnym ISTNIEJĄCYM · criterion now: ISC-29 probe = test dostarczenia eventu kolektorowi subskrybującemu po emisji.
- **C/R/L 2026-07-19 — double-tap guard**: conjectured: `if (isLoading) return` z flagą ustawianą w launch chroni przed podwójnym żądaniem · refuted by: 4 testy na StandardTestDispatcher (2 żądania przechodziły — flaga ustawiana dopiero po dispatchu) · learned: flaga-guard musi być ustawiona synchronicznie PRZED viewModelScope.launch; Main.immediate maskuje błąd na urządzeniu, test z deferred dispatcherem go obnaża · criterion now: ISC-24 probe = unit test double-invoke z licznikiem wywołań repo.

## Verification

- ISC-1/2: Bash — baseline `assembleDebug` OK; `test` NO-SOURCE (0 testów); po zmianach `testDebugUnitTest`: **49 tests, 0 failed**.
- ISC-3: Read lint-results — baseline lint FAILED: 51 errors (MissingTranslation), 150 warnings.
- ISC-4: Bash apksigner — release APK 3.4 MB podpisany `CN=FastMask Release, O=Pawel Orzech` (klucz z ~/.gradle props), NIE debug keystore.
- ISC-6: Read gradle — Kotlin 1.9.22 / AGP 8.5.2 / Gradle 8.9 / BOM 2024.09 / min 26 target 35 compile 35 potwierdzone.
- ISC-11..17,22,23: JmapApiTest (13 testów) — parsing get/set/error/empty, apiUrl walidacja, evilfastmail.com odrzucony.
- ISC-13/14: MaskedEmailListViewModelTest — sort lastMessageAt→createdAt, filtr ENABLED zawiera PENDING (regresja naprawiona).
- ISC-31/32: Read NetworkModule — logging tylko BuildConfig.DEBUG, HEADERS + redactHeader(Authorization/Cookie/Set-Cookie).
- ISC-35: LoginViewModelTest — token czyszczony po sukcesie i porażce.
- ISC-36..39: Read manifest + xml — allowBackup=false, tylko MAIN/LAUNCHER, FLAG_SECURE+filterTouches (release), NSC system CA + brak cleartext.
- ISC-40/41: Read JmapApi + testy — apiUrl https+*.fastmail.com wymuszone; test wrogiego hosta przechodzi.
- ISC-42: gitleaks — 35 commitów, no leaks found.
- ISC-44: Read workflows — actions przypięte do SHA (checkout@692973e, claude-code-action@567fe95).
- ISC-85/90: agent i18n + naprawa — było 51 brakujących kluczy w 18 locale; po naprawie 152 klucze we wszystkich 20; zh naprawione (values-b+zh+Hans + Language "zh-Hans").
- ISC-103..110: git diff + testy — P1: TokenStorage recovery, clear-fields, double-tap, Channel events, filtr PENDING; P2: destroyed check, error i18n, startup IO async, launchSingleTop; 49 testów zielonych.
