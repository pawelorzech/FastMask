# FastMask — Pełny audyt techniczny + naprawy + audyt UX

**Data:** 2026-07-19 · **Rewizja wejściowa:** `0e9a22f` (v1.5.2, vc11) · **Rewizja końcowa:** `4552072`
**Zakres:** baseline, code review (poprawność/architektura/security/perf/a11y/i18n/release), naprawy P1–P2 z testami regresyjnymi, QA manualne na emulatorze (API 36), audyt UX, backlog.
**Metodyka:** 3 równoległe przeglądy wymiarowe + przegląd rdzenia data/security, cross-review Forge (GPT-5.4), audyt Cato, 33 screenshoty QA, gitleaks, lint, pełny cykl build debug+release.

---

## 1. Executive summary

**Stan przed:** aplikacja funkcjonalna i po solidnym hardeningu security v1.5 (zweryfikowałem — deklaracje z README/SECURITY.md pokrywają się z kodem), ale z **zerem testów automatycznych**, lintem failującym build (51 errorów), ~34% brakujących tłumaczeń w 18 z 20 języków, zepsutym chińskim locale i kilkoma realnymi bugami poprawnościowymi — w tym jednym cicho odwracającym edycje użytkownika.

**Najważniejsze znaleziska:** (1) czyszczenie pola opis/domena/URL w edycji maski było **cicho cofane** — null traktowany jako "bez zmian"; (2) double-tap na "Create" tworzył **duplikaty masek** na koncie Fastmail; (3) korupcja keysetu EncryptedSharedPreferences = **permanentny crash-loop** bez ścieżki wyjścia; (4) eventy jednorazowe (nawigacja po sukcesie) gubione w oknie rotacji; (5) filtr "Active" pokazywał mniej masek niż licznik na chipie (PENDING); (6) chiński był w 100% angielski (mismatch `zh`/`zh-Hans`/`values-zh-rCN`).

**Wykonane:** 15 napraw kodu (5×P1, 8×P2, 2×P3), 49 testów jednostkowych (było 0), ~950 przetłumaczonych stringów w 18 językach, lint z FAILED→zielony, dokumentacja zsynchronizowana. Wszystko zweryfikowane: unit testy, build debug+release, QA na emulatorze łącznie z live-probe naprawy clear-fields i 401 na produkcyjnym API, smoke test release z R8.

**Pozostałe ryzyka:** brak testu na API 26 (min SDK — brak AVD), kontrast `inkMuted` poniżej WCAG AA dla małego tekstu (decyzja designowa — propozycja w backlogu), plurale w `time_*` (formy skrócone, gramatyka przybliżona dla n=1 w części języków), stack techniczny rok do tyłu (AGP 8.5.2/Kotlin 1.9, działa, ale do planowego podbicia).

**Gotowość produkcyjna:** dobra — istotnie lepsza niż przed audytem. Do publikacji w Play brakuje głównie decyzji produktowych (backlog), nie napraw.

---

## 2. Wyniki baseline (przed zmianami)

| Komenda | Wynik |
|---|---|
| `./gradlew assembleDebug` | ✅ SUCCESS (APK 20.6 MB) |
| `./gradlew test` | ⚠️ NO-SOURCE — **0 testów w repo** |
| `./gradlew lint` | ❌ **FAILED — 51 errorów** (MissingTranslation ×51), 150 warningów (54 StringFormatCount, 37 Typos, 26 UnusedResources, 23 GradleDependency…) |
| `./gradlew assembleRelease` (bez env keystore) | ✅ SUCCESS — APK 3.4 MB, podpisany `CN=FastMask Release` z ~/.gradle props (NIE debug keystore — F-01 zweryfikowany) |
| `gitleaks detect --log-opts=--all` | ✅ 35 commitów, no leaks |
| Wersje | Kotlin 1.9.22 · AGP 8.5.2 · Gradle 8.9 · Compose BOM 2024.09 · JDK 17 toolchain · min 26 / target 35 / compile 35 |
| Środowisko buildu | JDK na PATH = 26 (niekompatybilny z Gradle 8.9) — build wymaga JBR 21 z Android Studio |

Weryfikacja hardeningu v1.5 (poprzedni audyt F-01…F-12): **wszystkie naprawy realnie wdrożone** — logging DEBUG-gated z redakcją Authorization, allowBackup=false, FLAG_SECURE+filterTouches (release), NSC system-CA-only, walidacja apiUrl, actions przypięte do SHA, token czyszczony ze stanu logowania.

## 3. Znalezione problemy

| ID | Prio | Obszar | Problem | Skutek | Status |
|----|------|--------|---------|--------|--------|
| A1 | P1 | Dane | Edycja: wyczyszczone pole (opis/domena/URL) mapowane na null = "bez zmian" → serwer zachowuje starą wartość, UI cicho przywraca | Użytkownik nie może usunąć opisu; cicha utrata edycji | ✅ naprawione + test + live probe |
| A2 | P1 | Dane | Brak guarda na double-tap w create/login/update/delete (flaga w launch, za późno) | Duplikaty masek na koncie Fastmail | ✅ naprawione + testy |
| A3 | P1 | Stabilność | TokenStorage: korupcja keysetu Tink/KeyStore → wyjątek w lazy init → crash-loop przy każdym starcie, bez recovery | Aplikacja trwale nie do uruchomienia (naprawa = wyczyść dane) | ✅ naprawione (delete+retry+fallback) |
| A4 | P1 | UI | Eventy one-shot: `MutableSharedFlow(replay=0)` gubi event bez kolektora (okno rotacji); `collectLatest` kasuje obsługę poprzedniego | Zgubiona nawigacja po create/delete/login; user "utknięty" | ✅ naprawione (Channel.BUFFERED + collect) + test |
| A5 | P1 | UI | Filtr "Active" = tylko ENABLED, licznik chipa = ENABLED+PENDING | Licznik 8, lista 7 — niespójność danych | ✅ naprawione + test |
| A6 | P2 | Dane | `parseSetResponseDestroyed` bez pozytywnej weryfikacji `destroyed` (asymetria z updated) | Cichy no-op serwera raportowany jako sukces archiwizacji | ✅ naprawione + 3 testy |
| A7 | P2 | i18n | 51 kluczy nieprzetłumaczonych w 18 locale (~34%); tutorial/welcome/login/czasy po angielsku | Mieszany język UI dla 18 języków; lint FAILED | ✅ naprawione (~950 stringów, Haiku) |
| A8 | P2 | i18n | Chiński: `Language("zh")` + `locales_config zh-Hans` + katalog `values-zh-rCN` — trzy niezgodne formy | Chiński NIGDY się nie rozwiązywał — pełny fallback EN | ✅ naprawione (values-b+zh+Hans, "zh-Hans") + live probe |
| A9 | P2 | i18n | `email_detail_created`/`created_by`: 18 locale z `: %s`, EN bez argumentu | Literalne "%s" w UI 18 języków | ✅ naprawione (36 wartości) |
| A10 | P2 | UX/i18n | Surowe `Throwable.message` ("HTTP 401", "Unable to resolve host…") pokazywane użytkownikowi; fallbacki hardcoded EN | Techniczne, nieprzetłumaczone błędy | ✅ naprawione (UiErrors → @StringRes; error_network/error_auth ×20 locale) + testy + live probe 401 |
| A11 | P2 | Perf | Start: EncryptedSharedPrefs init (KeyStore+IO) + 2×runBlocking DataStore na main thread przed pierwszą klatką | Jank startu, ANR-risk na wolnych urządzeniach | ✅ naprawione (async + splash gate + fallback) |
| A12 | P2 | Perf | Podwójny fetch listy przy pierwszym wejściu (init + on-resume po 250 ms) | 2× `MaskedEmail/get` na starcie | ✅ naprawione (guard) + test |
| A13 | P2 | UI | `runBlocking` w logout na main thread (2× DataStore edit) | Blokada UI przy wylogowaniu | ✅ naprawione (suspend) |
| A14 | P2 | Nawigacja | Brak `launchSingleTop` — double-tap wiersza = 2× detail na stacku | Podwójne ekrany, mylący back | ✅ naprawione (11 wywołań) |
| A15 | P2 | A11y | Touch targety < 48dp: PillIconButton 40dp, clear search 24dp, toggle tokenu 28dp | Trudne trafienie; poniżej wytycznych | ✅ naprawione (48dp target, wizual bez zmian) |
| A16 | P2 | A11y | Filtry/język: selekcja tylko kolorem, brak semantics selected/Role | TalkBack nie ogłasza stanu | ✅ częściowo (FilterPill); LanguageRow → backlog |
| A17 | P2 | Build | compileSdk 35 na AGP 8.5.2 bez flagi | Warning przy każdym buildzie | ✅ flaga suppress + rekomendacja podbicia AGP |
| A18 | P3 | Kod | Martwy kod: MaskedEmailCard, ShimmerEffect (niepodpięte), logout w ListVM | Mylące utrzymanie | ✅ usunięte |
| A19 | P3 | Dok | README: logo .webp (plik .png) = zepsuty obrazek na GitHubie; CLAUDE.md: SDK 34 | Dokumentacja kłamie | ✅ naprawione |
| A20 | P3 | A11y | Kontrast `inkMuted` (#8A8170 na #F4EFE6) ~3:1 przy tekstach 10–12sp — poniżej AA 4.5:1 | Słaba czytelność drobnych tekstów | ⏸ backlog (decyzja designowa Pawła) |
| A21 | P3 | i18n | `time_*` bez plurali (skróty "%dm ago") | Przybliżona gramatyka w ru/uk/ar dla pełnych form | ⏸ backlog |
| A22 | P3 | Kod | R8: bardzo szerokie keep rules (cały compose.runtime, wszystkie Companiony, "paranoid" keep całych pakietów) | Większy APK, słabsza obfuskacja — ale działa | ⏸ świadomie nietknięte (ryzyko > zysk w audycie); backlog |
| A23 | P3 | UI | Snackbar "Created" (Long, ~10 s) blokuje powrót na listę po utworzeniu maski | Wygląda jak zwis; nowa maska niewidoczna | ⏸ backlog (decyzja UX) |
| A24 | P3 | A11y | PillIconButton: label tylko w `onClickLabel`, węzeł bez contentDescription | Niepełny opis dla czytników/testów | ⏸ backlog |
| A25 | P3 | UX | 401 w trakcie sesji: komunikat + retry, ale brak CTA "zaloguj ponownie" | Dłuższa droga po wygaśnięciu tokenu | ⏸ backlog B |

## 4. Wykonane zmiany

**Commit `b24e431` — tłumaczenia** (20 plików res): 49 kluczy × 18 locale + 2 klucze błędów × 18 + naprawa `%s` + rename `values-zh-rCN → values-b+zh+Hans` + `Language.CHINESE("zh-Hans")` + pozycyjny `list_stats`. Wykonane przez 4 agentów Haiku (koszt), zweryfikowane lintem (0 errorów) i live (chiński UI na emulatorze).

**Commit `4552072` — naprawy + testy** (33 pliki):
- `TokenStorage.kt` — recovery przy korupcji (A3); ryzyko regresji: minimalne (ścieżka happy-path nietknięta)
- `MaskedEmailDetailViewModel.kt` — per-field change detection + trim, "" czyści (A1); ryzyko: zmiana semantyki update — pokryta 3 testami + live probe
- 5×ViewModel — Channel(BUFFERED) + guardy synchroniczne (A2, A4); ryzyko: niskie, wzorzec standardowy, 6 testów
- `MaskedEmailListViewModel.kt` — filtr isActive, refresh guard, usunięty martwy logout (A5, A12)
- `JmapApi.kt` — pozytywny check `destroyed` (A6); ryzyko: jeśli Fastmail kiedyś nie zwraca `destroyed` przy sukcesie — złamanie RFC 8620, test by to wykrył
- `ui/common/UiErrors.kt` (nowy) + 4×VM/ekrany na errorRes (A10)
- `MainActivity.kt` — async startDestination + try/catch fallback (A11)
- `AuthRepository/Impl/LogoutUseCase` — suspend (A13)
- `FastMaskNavHost.kt` — launchSingleTop ×11 (A14)
- `DesignKit.kt`/`MaskedEmailListScreen.kt` — touch targets + semantics (A15, A16)
- usunięte: `MaskedEmailCard.kt`, `ShimmerEffect.kt` (A18)
- `app/src/test/**` — 6 plików testowych, 49 testów

Cross-review Forge (GPT-5.4): werdykt **SHIP**, 0 blockerów; 3 z 4 minorów domknięte w tym samym commicie (trim, try/catch startu, szeroki catch w getToken).

## 5. Wyniki testów po zmianach

| Komenda | Wynik |
|---|---|
| `./gradlew testDebugUnitTest` | ✅ **49 tests, 0 failed** (JmapApiTest 13 · ListVM 8 · DetailVM 5 · LoginVM 7 · CreateVM 5 · UiErrors 3 · RepoImpl 4 + reguły) |
| `./gradlew assembleDebug` | ✅ SUCCESS |
| `./gradlew assembleRelease` | ✅ SUCCESS — 3 435 395 B (3.4 MB), R8+shrinkResources, podpis `CN=FastMask Release` |
| `./gradlew lintDebug` | ✅ **PASSED — 0 errorów** (było 51); pozostałe warningi: 37 Typos (fałszywe alarmy na nie-EN tekstach), 24 GradleDependency (nowsze wersje dostępne — patrz plan), 19 UnusedResources, 5 IconLauncherShape, 1 PluralsCandidate |
| Instrumented/UI tests | brak w repo (przed i po) — propozycja w planie |
| QA emulator (API 36, debug) | 22 screenshoty: welcome, demo+tutorial, filtry (licznik=lista ✓), detail, **clear-note zapisane ✓**, create+snackbar, rotacja (formularz zachowany ✓), process death (przywrócony ekran, 0 crashy ✓), dark ✓, font 200% ✓, chiński ✓, arabski RTL ✓, predictive back ✓, sign-out ✓, **401 live → zlokalizowany komunikat ✓** |
| QA release (R8) | welcome ✓, demo ✓, sign-out ✓, login 401 live ✓, logcat: **0 FATAL** |

## 6. Problemy nienaprawione

| Co | Dlaczego | Ryzyko | Next step |
|---|---|---|---|
| Test na API 26 (minSdk) | Brak AVD z API 26 na maszynie | TLS/vector/edge-case'y najstarszego wspieranego OS nieprzetestowane | Utworzyć AVD API 26, przejść smoke (30 min) — **[DEFERRED-VERIFY]** |
| Kontrast inkMuted < AA | Paleta warm-ink to świadomy design Pawła — nie zmieniam gustu jednostronnie | Czytelność drobnych tekstów dla słabowidzących | Propozycja: Light #6F6656, Dark #9A9181 — decyzja + 15 min |
| Plurale time_* | 9 kluczy × 20 locale w `<plurals>` — zmiana wzorca formatu | Przybliżona gramatyka skrótów ("1m ago") | Backlog B, 2–3 h z tłumaczeniami |
| R8 keep rules zbyt szerokie | Działa; dotknięcie = ryzyko runtime crash w release za P3-zysk | Większy APK (~), słabsza obfuskacja | Zawęzić `**$$serializer`→com.fastmask, wyciąć duplikaty; osobny PR z testem release |
| AGP 8.5.2/Kotlin 1.9.22 (rok wstecz) | Podbicie = osobna zmiana z pełną weryfikacją, poza zakresem audytu | Brak wsparcia nowych API lint/compose | AGP 8.7+ / Kotlin 2.0 + compose compiler plugin — 0.5–1 dzień |
| LanguageRow bez semantics selected | Zeszło z priorytetu w trakcie | TalkBack nie ogłasza wybranego języka | 10 min, razem z A24 |
| Instrumented tests (Compose UI) | Wymaga infrastruktury CI z emulatorem | Regresje UI wykrywane ręcznie | 2–4 h: 3–4 testy krytycznych ścieżek |

## 7. Audyt UX

Oceniałem 12 przepływów na emulatorze (screenshoty w scratchpadzie sesji).

**Mocne strony:** droga do celu jest krótka — kopia adresu z listy to 2 tapy (otwórz detail → copy), utworzenie maski 2–3 tapy z sensownymi defaultami (random prefix). Demo mode z tutorialem to świetny onboarding "przed tokenem". Instrukcja tokenu na ekranie logowania (5 kroków) — dobra. Spójność warm-ink wysoka; RTL i dark dopracowane; stany błędów po naprawach zlokalizowane i konkretne.

**Największa bariera — token (flow 2/3):** instrukcja jest, ale użytkownik musi opuścić apkę, przejść 4 poziomy ustawień Fastmail na webie i wrócić. Brak linku otwierającego bezpośrednio stronę API tokens (`https://app.fastmail.com/settings/security/tokens`), brak walidacji formatu przed wysłaniem (np. prefiks `fmu1-`), brak "wklej ze schowka" jednym tapem. Po nieudanej próbie pole jest czyszczone (security-by-design F-08) — ale to oznacza ponowne ręczne wklejanie przy literówce; komunikat nie tłumaczy dlaczego pole puste.

**Pozostałe obserwacje (naj→najmniej istotne):**
1. Po create snackbar Long (~10 s) trzyma użytkownika na formularzu zanim wróci na listę (A23) — odczucie zwisu; akcja Copy jest cenna, ale powinna żyć na liście.
2. Kopiowanie z listy wymaga wejścia w detail — brak akcji copy bezpośrednio na karcie (najczęstszy use-case!).
3. Archiwizacja: dialog potwierdzenia jest, ale brak **Undo** po fakcie (snackbar z cofnięciem zdjąłby friction z dialogu).
4. 401 w sesji: komunikat + Retry, brak skrótu "Zaloguj ponownie" (A25).
5. Pusta lista wyszukiwania — nie zweryfikowałem dedykowanego komunikatu "brak wyników dla X" (empty state istnieje dla pustego konta).
6. Arabski: tytuł "الإعدادات" łamie się mid-word przy displayLarge (szerokość kontenera); kosmetyka.
7. Rytm listy: timestamp "2mo ago" konkuruje wizualnie z nazwą maski; drobne.
8. Font-scale 200%: działa, ale adresy ucinają się wielokropkiem — long-press mógłby pokazywać pełny adres (tooltip/copy).

## 8. Backlog UX

### A. Quick wins
| Prio | Propozycja | Problem użytkownika | Efekt | Nakład | Ryzyko |
|---|---|---|---|---|---|
| 1 | **Copy bezpośrednio na karcie listy** (ikona/long-press) | Najczęstsza akcja wymaga 2 ekranów | Kopia w 1 tap; sedno produktu ("jak najszybciej skopiować") | S | niskie |
| 2 | **Undo archiwizacji w snackbarze** (+ opcjonalnie zdjęcie dialogu) | Strach przed nieodwracalnością; dialog spowalnia | Szybciej + poczucie kontroli | S | niskie |
| 3 | **Link "Otwórz ustawienia Fastmail" na login** (`https://app.fastmail.com/settings/security/tokens` przez Custom Tab) | Ręczna nawigacja po webie Fastmail | Skraca największą barierę produktu | XS | niskie |
| 4 | **Przycisk "Wklej" przy polu tokenu + walidacja formatu przed wysłaniem** | Ręczne wklejanie, enter, czekanie na 401 | Mniej błędnych prób; jaśniejszy feedback | XS–S | niskie |
| 5 | **Snackbar Created na liście zamiast na create** (nawigacja od razu, Copy w snackbarze na liście) | 10 s "zwisu" po utworzeniu | Natychmiastowy powrót + widoczna nowa maska | S | niskie |
| 6 | **Kontrast inkMuted → AA** (Light #6F6656 / Dark #9A9181, tylko dla ≤12sp) | Nieczytelne drobne teksty | Dostępność bez utraty charakteru palety | XS | średnie (gust — decyzja Pawła) |
| 7 | Empty state dla wyszukiwania bez wyników ("Brak masek dla 'X' — utwórz?") | Pusta lista bez wyjaśnienia | Jasność + skrót do create | XS | niskie |
| 8 | Semantics: LanguageRow selected + contentDescription na PillIconButton | TalkBack nie ogłasza stanu/roli | Realna dostępność czytnika | XS | niskie |

Pomiar (privacy-first, bez telemetrii): lokalne testy z 3–5 użytkownikami (czas od instalacji do pierwszej kopii; liczba prób logowania), ankieta w repo/community, developerskie pomiary makrobenchmark.

### B. Średni zakres
| Prio | Propozycja | Problem | Efekt | Nakład | Ryzyko |
|---|---|---|---|---|---|
| 1 | **Cache ostatniej listy (offline-first read)** — DataStore/Room + odświeżenie w tle | Zimny start = spinner; offline = pusto | Lista natychmiast, działa w samolocie | M | średnie |
| 2 | **Ścieżka wygasłego tokenu**: 401 → banner "Token wygasł" + CTA do logowania (zamiast samego retry) | Użytkownik nie wie co zrobić po cofnięciu tokenu | Samodzielne wyjście z błędu | S–M | niskie |
| 3 | **Plurale + pełne formy czasu** (`<plurals>` dla time_*) | Gramatyka w ru/uk/ar/pl | Poprawna językowo apka | M | niskie |
| 4 | **Sortowanie wybierane przez użytkownika** (aktywność/nazwa/data utworzenia) + zapamiętanie | Jedno sortowanie nie pasuje wszystkim | Kontrola dla power-userów | S | niskie |
| 5 | Sugestie domeny z historii + autouzupełnianie opisu | Powtarzalne wpisywanie | Szybszy create | M | niskie |
| 6 | Zapamiętanie query/filtra przez proces death (SavedStateHandle) | Reset po ubiciu procesu | Ciągłość | S | niskie |

### C. Duże funkcje (wymagają Twojej zgody — NIE implementowane)
| Propozycja | Wartość | Nakład |
|---|---|---|
| **Android Autofill integration** — maska podpowiadana w polach email w innych apkach | Największa dźwignia produktu: maska w miejscu użycia, zero przełączania | XL |
| **Share-sheet target** ("Udostępnij → nowa maska dla domeny z URL") | Tworzenie maski z kontekstu przeglądarki | M–L |
| **Quick Settings tile / skrót ikony** — "nowa maska" z systemu | Skraca create do 1 gestu | M |
| **Widget** — lista ostatnich + create | Szybki dostęp bez otwierania apki | L |
| Bulk actions (multi-select archive/off) | Porządki przy dużych bibliotekach | M–L |
| Ulubione/pin masek | Szybki dostęp do 2–3 najużywańszych | S–M |

## 9. Rekomendowana kolejność dalszych prac

**Następny dzień:** AVD API 26 + smoke (zamknięcie [DEFERRED-VERIFY]) · quick wins A3+A4 (link+wklej na login) · A8 (semantics) · decyzja Pawła: kontrast (A6) i Copy na karcie (A1).
**Następny tydzień:** A1 (copy na karcie) + A2 (undo archiwizacji) + A5 (snackbar na liście) · B2 (ścieżka wygasłego tokenu) · 3–4 testy instrumentowane (login, lista, create) w CI · podbicie zależności z lint GradleDependency (bezpieczne minory).
**2–4 tygodnie:** AGP 8.7+/Kotlin 2.0 (osobny PR) · B1 (cache offline) · B3 (plurale) · R8 zawężenie reguł z testem release · decyzja o kierunku C (rekomendacja: **Autofill** jako następna duża rzecz — najbliżej sedna produktu).

## 10. Ocena końcowa (1–10)

| Wymiar | Ocena | Uzasadnienie |
|---|---|---|
| Stabilność | **8** | Crash-loop scenariusz zamknięty, race'y load/eventy naprawione, process death OK; minus: brak testu API 26, brak crash-reportingu (świadome, privacy) |
| Bezpieczeństwo | **8.5** | Wzorowe jak na indie apkę: szyfrowany token+recovery, walidacja apiUrl+test, NSC, FLAG_SECURE, zero logów tokenu, podpis release zweryfikowany, gitleaks czysty; minus: brak pinningu (świadomie udokumentowane), security-crypto alpha (udokumentowany trade-off — stabilna 1.0.0 wymaga migracji) |
| Jakość kodu | **7.5** | Czysta architektura warstw, cienkie use case'y, spójne wzorce; minus: duże pliki ekranów (600+ linii), szerokie reguły R8, drobna duplikacja counts |
| Testowalność | **7** | 49 testów rdzenia logiki (było 0), czyste fake'i, DI sprzyja; minus: zero testów instrumentowanych, SettingsDataStore wymaga mockk |
| Wydajność | **8** | Start odchudzony z main-thread IO, podwójny fetch usunięty, lista zmemoizowana z kluczami, APK 3.4 MB; minus: brak pomiarów na 500+ maskach i baseline profile |
| Dostępność | **6.5** | Touch targets naprawione, semantics filtrów, RTL/font-scale działają; minus: kontrast inkMuted, LanguageRow/ikony bez pełnych semantics, brak testu TalkBack live |
| UX | **7.5** | Krótkie ścieżki, świetne demo+tutorial, spójny design; minus: copy przez detail, snackbar blokujący, bariera tokenu bez skrótów |
| Gotowość do publikacji | **8** | Build+lint+testy zielone, release podpisany i przetestowany z R8, i18n kompletne, docs zgodne; minus: API 26 niezweryfikowane, decyzje backlogu A przed premierą podniosłyby jakość pierwszego wrażenia |

---
*Testy manualne: 33 screenshoty w scratchpadzie sesji audytu. ISA projektu: `ISA.md` (system of record audytu, 136 kryteriów).*
