# FastMask — audyt techniczny 2026-07-27

**Rewizja wejściowa:** `be6b291` (v1.10.0, versionCode 21, `main`)
**Gałąź z poprawkami:** `feature/audit-2026-07-27`
**Zakres:** całe repozytorium — architektura, kod, dane, bezpieczeństwo, prywatność, zależności, wydajność, dostępność, i18n, UX, pliki doktrynalne i CI
**Metodyka:** przegląd kodu produkcyjnego i testowego; trzy równoległe audyty specjalistyczne (bezpieczeństwo, dostępność+i18n, dane+współbieżność); buildy debug i release; 446 testów jednostkowych i 19 instrumentowanych na emulatorze API 36; przejście ścieżek UI na żywo w trybie demo ze zrzutami ekranu

> **Kontekst:** piąty przebieg audytu tego repozytorium. Poprzednie cztery (2026-07-19, 07-23, 07-24 pass A/B/C) naprawiły ponad 40 problemów; ich raporty leżą w `Plans/audit-archive/`. Ten przebieg celowo nie powtarzał tamtych znalezisk — szukał tego, co przeoczyły albo wprowadziły.

**Status potwierdzenia** używany w całym dokumencie: **[K]** potwierdzony odczytem kodu · **[N]** potwierdzony uruchomionym narzędziem · **[U]** wymaga urządzenia lub konta · **[H]** hipoteza.

---

## 1. Stan bazowy (przed zmianami)

| Komenda | Wynik |
|---|---|
| `./gradlew clean testDebugUnitTest` | ✅ 442 testy, 0 porażek |
| `./gradlew lintDebug` | ✅ 0 errorów, 99 ostrzeżeń (42 `Typos`, 26 `GradleDependency`, 20 `UnusedResources`, 5 `IconLauncherShape`, 4 `InlinedApi`, 1 `LocaleFolder`, 1 `ObsoleteSdkInt`) |
| `./gradlew assembleDebug` | ✅ SUCCESS — APK 22,2 MB |
| `./gradlew assembleRelease` | ✅ SUCCESS (R8 + shrinkResources) — APK 4,15 MB |
| `./gradlew connectedDebugAndroidTest` | ✅ 16 testów na emulatorze `Pixel_9a_16.1` (API 36), 0 porażek |
| Środowisko | Gradle 8.9, Kotlin 1.9.22, compileSdk/targetSdk 36, minSdk 26, JDK 17 (toolchain) |

**Zielony build nie był dowodem poprawności.** Najpoważniejsze znalezisko tego przebiegu (A1) istniało mimo kompletu zielonych testów — bo jedyny test, który je pokrywał, uruchamiał *inną implementację* niż ta, którą dostaje konto użytkownika.

Fizyczny telefon (OnePlus 13, CPH2653) był podpięty, ale ma instalkę z Play App Signing — instrumentowane testy wymagałyby odinstalowania aplikacji, co skasowałoby token Fastmail. Testy poszły na emulatorze.

---

## 2. Rozpoznanie projektu

**Przeznaczenie.** Natywny klient Android do zarządzania maskowanymi adresami Fastmail. Logowanie tokenem API (JMAP), przeglądanie / tworzenie / edycja / archiwizacja masek. Model: darmowy rdzeń + jednorazowy zakup „FastMask Pro" (`pro_lifetime`) odblokowujący akcenty kolorystyczne, biometryczną blokadę aplikacji i eksport CSV.

| Warstwa | Zawartość |
|---|---|
| **Dane** | JMAP przez Retrofit/OkHttp (`JmapApi`, cache sesji pod mutexem), `TokenStorage` (EncryptedSharedPreferences), `MaskedEmailCache` (EncryptedFile), `ExportCache` (cacheDir), `SettingsDataStore` + `ProEntitlementStore` (DataStore), Play Billing 8.3, Firebase Crashlytics (opt-out) |
| **Domena** | Modele, interfejsy repozytoriów, 12 use case'ów, parser linków share'owanych, polityki quick-mask |
| **UI** | Compose + Material 3, 9 ekranów, `StateFlow<UiState>` + `Channel<Event>`, Hilt, Navigation z shared elementami, 20 języków |
| **Wejścia systemowe** | Launcher, share target (`ACTION_SEND`), kafelek Quick Settings, skrót z long-pressa, powiadomienie z akcją Cofnij |

**Przepływ danych.** ViewModel → UseCase → `MaskedEmailRepositoryDispatcher` → (real JMAP | demo in-memory), routing po `AppMode` z DataStore. Konto: token → sesja JMAP (`accountId`, `apiUrl`, walidowany do `*.fastmail.com`) → `MaskedEmail/get|set`.

**Miejsca przechowywania danych.** Token → EncryptedSharedPreferences. Snapshot masek → EncryptedFile w `filesDir`. Eksport CSV → `cacheDir/exports` **jawnym tekstem**. Ustawienia i uprawnienie Pro → DataStore. Nic nie opuszcza urządzenia poza Fastmailem, Play i (opcjonalnie) Crashlyticsem.

**Główne ścieżki.** Welcome → (token | demo) → lista → szczegół (edycja / włącz-wyłącz / archiwizacja z Undo) → tworzenie → ustawienia → paywall. Plus trzy ścieżki poza aplikacją: share URL-a, kafelek, skrót.

**Obszary największego ryzyka.** (1) Archiwizacja — jedyna operacja mogąca skasować dane użytkownika. (2) Zamek biometryczny — jedyna bariera prywatności na urządzeniu. (3) Token API — daje pełny dostęp do skrzynki. (4) Cache masek i eksport CSV — jedyne miejsca, gdzie komplet masek leży na dysku. (5) Wejścia systemowe — jedyna powierzchnia, którą może dotknąć obcy proces.

---

## 3. Znalezione problemy

### 3.1 Naprawione

| ID | Prio | Status | Problem | Lokalizacja | Przyczyna źródłowa |
|---|---|---|---|---|---|
| **A1** | **P0** | **[K]** | Przycisk **„Archiwizuj maskę" wysyłał JMAP `destroy`** — trwałe usunięcie — podczas gdy dialog potwierdzenia obiecuje „Mail sent here will bounce. **You can restore it later**", a lista oferuje Undo, które wysyła `update {state}` na id już usunięte | `data/repository/MaskedEmailRepositoryImpl.kt:70`, `data/api/JmapApi.kt:184`, `values/strings.xml:110` | Jedno `deleteMaskedEmail` obsługiwało dwa przeciwne zamiary: „archiwizuj odwracalnie" (ekran szczegółów) i „usuń pomyłkę" (Undo kafelka). Realizowało oba twardym `destroy` |
| **A2** | **P1** | **[K]** | **Zamek aplikacji rozbrajał się po utracie Pro — niespójnie w czterech miejscach.** Zimny start: odblokowany. Powrót z tła: zablokowany. Ustawienia: przełącznik pokazuje „wyłączony". Kafelek: tworzy maski mimo zamka | `MainActivity.kt:156` vs `:218`, `ui/settings/SettingsScreen.kt:325`, `domain/usecase/QuickMaskCreator.kt:57` | Kontrola prywatności (kto widzi maski) sprzężona z kontrolą monetyzacji (kto zapłacił). Trzy miejsca użyły `&& isPro`, jedno nie — a komentarz przy tym jednym poprawnie uzasadniał, dlaczego nie powinno |
| **A3** | **P1** | **[K]** | **Uszkodzony DataStore `settings` = trwały crash-loop.** `appModeBlocking()` opakowywał `runCatching` wokół parsowania enuma, ale nie wokół odczytu; wołane w konstruktorach trzech ViewModeli i przy każdym wywołaniu repozytorium | `data/local/SettingsDataStore.kt:74` | Niedopatrzenie, nie decyzja — `crashReportingEnabledBlocking()` obok **ma** ten guard. Brak `corruptionHandler` na obu DataStore'ach |
| **A4** | **P1** | **[K]** | **Cache masek nie był związany z kontem**, a `clear()` przy wylogowaniu ignorował wynik `File.delete()`. Logowanie nie czyściło niczego | `data/local/MaskedEmailCache.kt:37,78`, `data/repository/AuthRepositoryImpl.kt:22` | Snapshot anonimowy, pod stałą nazwą pliku. Jedyne, co dzieliło maski konta A od konta B, to nieweryfikowane `delete()` |
| **A5** | **P1** | **[K]** | **Szyfrowany zapis całej listy masek, odczyt tokenu i `runBlocking` na DataStore szły na wątku głównym.** Retrofit wznawia korutynę na dyspozytorze wywołującego, a każdym wywołującym jest ViewModel na `Main.immediate` | `data/repository/MaskedEmailRepositoryImpl.kt` (wszystkie metody), `MaskedEmailRepositoryDispatcher.kt:33` | Założenie, że „Retrofit i tak robi I/O w tle" — prawdziwe tylko dla samego HTTP |
| **A6** | **P1** | **[K]** | **Mutacje w `viewModelScope`**: cofnięcie ekranu w trakcie anulowało żądanie, które mogło już dojść do serwera. Przy tworzeniu = maska-sierota bez adresu; przy archiwizacji = brak snackbara z Undo dla zmiany, która się wydarzyła | `ui/create/CreateMaskedEmailViewModel.kt:102`, `ui/detail/MaskedEmailDetailViewModel.kt:121,161,184` | `@ApplicationScope` istniał w repo i był używany przez billing, ale nie przez mutacje masek |
| **A7** | **P1** | **[K]** | **Kafelek Quick Settings nie miał guardu in-flight.** Trzy szybkie tapnięcia = trzy prawdziwe maski, jedno powiadomienie, Undo tylko dla ostatniej, schowek tylko z ostatnią | `quickmask/QuickMaskRunner.kt:49` | Wzorzec synchronicznej flagi in-flight jest w repo konsekwentny we wszystkich ViewModelach; ten jeden punkt wejścia go nie dostał — a jest najłatwiejszy do tapnięcia dwa razy, bo nie daje feedbacku aż do powrotu z sieci |
| **A8** | **P2** | **[K]** | **Undo archiwizacji działało tylko raz na cykl życia ekranu.** `pendingUndo` nigdy nie wracało do `null`; bliźniaczy `pendingCreated` obok wracał. Skutek uboczny: nieskonsumowane id zostawało w `SavedStateHandle` i po obrocie wyskakiwał „zombie" snackbar dla dawno zarchiwizowanej maski | `ui/list/MaskedEmailListScreen.kt:154` | Asymetria dwóch sąsiadujących bloków — pominięcie jednej linii |
| **A9** | **P2** | **[K]** | **Weryfikacja podpisu zakupu była fail-open**: pusty `PLAY_LICENSE_KEY` = `isSignatureValid()` zwraca `true` | `data/billing/PlayBillingDataSource.kt:266` | Wygoda dev/CI zaimplementowana w warstwie runtime zamiast build-time. Kontrola build-time (guard w `build.gradle.kts`) istnieje i działa, ale runtime nadal ufał domyślnie |
| **A10** | **P2** | **[K]** | **Retencja eksportu CSV nie działała dla nikogo, kto wyeksportował raz.** Klasa deklaruje godzinę życia, ale czyszczenie było efektem ubocznym `write()` — czyli następowało dopiero przy **następnym** eksporcie | `data/local/ExportCache.kt:37` | Reguła retencji zaimplementowana jako efekt uboczny zapisu, a nie niezależny cykl życia. To jedyna kopia kompletu masek na dysku, która **nie** jest szyfrowana |
| **A11** | **P2** | **[K]** | **`gradle.properties` pinował `org.gradle.java.home` do ścieżki Homebrew z jednego laptopa.** Repo jest publiczne i przyjmuje kontrybucje | `gradle.properties:2` | Konfiguracja per-maszyna w pliku wersjonowanym. Repo ma już ustalony wzorzec dla takich rzeczy (`~/.gradle/gradle.properties` trzyma klucze podpisu i klucz licencyjny Play) |
| **A12** | **P2** | **[K]** | **Polityka prywatności deklarowała kasowanie preferencji przy wylogowaniu, którego kod nie robi.** §6: „The API token, language, and other local preferences remain on your device until you log out… At that point they are removed". Faktycznie `logout()` kasuje token, cache i eksporty, ale zostawia język, akcent, flagę zamka, przełącznik raportów awarii | `docs/privacy.md:84` vs `data/repository/AuthRepositoryImpl.kt:28` | Dokument opisywał zamiar, nie implementację. Zachowanie kodu jest tu lepsze (preferencje dotyczą aplikacji, nie konta) — nieprawdziwa była deklaracja |
| **A13** | **P3** | **[K]** | **Reguły backupu chroniły wyłącznie token.** Snapshot masek i katalog DataStore nie były wykluczone | `res/xml/backup_rules.xml`, `data_extraction_rules.xml` | Dziś bez skutku (`allowBackup="false"`), ale pliki są tam świadomie jako siatka bezpieczeństwa — a siatka miała dziurę dokładnie tam, gdzie leżą maski |

### 3.2 A1 zasługuje na osobne omówienie

To najpoważniejsze znalezisko pięciu przebiegów audytu i warto rozumieć, **dlaczego przeżyło cztery poprzednie**.

Całe UI aplikacji jest zbudowane pod miękkie archiwum. Istnieje `EmailState.DELETED`. Istnieje filtr „Archived" z licznikiem. Istnieje etykieta `state_deleted`. Istnieje dialog obiecujący przywracalność i snackbar z Undo, który przywraca **poprzedni** stan maski, nie byle jaki. Repozytorium demo archiwizuje przerzutem stanu, a jego własny KDoc mówi, że robi to „mirroring how Fastmail's JMAP API behaves".

Tylko jedno miejsce w całym systemie mówiło co innego: jedna linia wysyłająca `destroy`. I żaden produkcyjny kod nie ustawiał nigdy `EmailState.DELETED` przez prawdziwe API — czyli filtr „Archived" **nie mógł się zapełnić** z poziomu aplikacji.

Test, który miał to pokrywać, jest opisany w kodzie jako „the highest-risk path in the app" i biegnie **w trybie demo**. Demo implementuje semantykę, którą obiecuje UI. Produkcja implementowała inną. Test był zielony i mierzył nie to, co trzeba.

**Wniosek procesowy, ważniejszy od samej poprawki:** test ścieżki krytycznej uruchamiany na atrapie o innej semantyce niż implementacja produkcyjna jest gorszy niż brak testu — daje fałszywe poczucie pokrycia. Poprawka rozdziela `archiveMaskedEmail` i `destroyMaskedEmail` na poziomie **interfejsu repozytorium**, więc obie implementacje muszą teraz zadeklarować obie semantyki osobno i nie da się ich pomylić przez przypadek.

**Czego nie wiem:** co dokładnie Fastmail robi z `destroy` na masce (kasuje twardo, czy tłumaczy na `state=deleted`). Nie mam tokenu, a szukanie w dokumentacji Fastmaila nie dało rozstrzygającej odpowiedzi. Poprawka jest jednak poprawna **niezależnie od tej odpowiedzi**: „Archiwizuj" powinno wysyłać operację zdefiniowaną jako odwracalna, a Undo kafelka tę zdefiniowaną jako usunięcie. Weryfikacja na koncie jest punktem 1 listy QA.

### 3.3 Nienaprawione (świadomie)

| ID | Prio | Status | Problem | Lokalizacja | Powód pozostawienia |
|---|---|---|---|---|---|
| B1 | P2 | [K] | Pola tekstowe nie mają widocznej granicy: tło pola vs tło ekranu **1,15:1** (jasny) i **1,04:1** (ciemny), ramka 1,28:1 i 1,25:1 — wszystko poniżej wymaganych 3:1 (WCAG 1.4.11) | `ui/components/DesignInput.kt:81`, `ui/theme/Color.kt` | Naprawa wymaga pociemnienia `LightLineStrong` i rozjaśnienia `DarkLineStrong`, czyli **zmiany palety** — a paleta „warm ink" jest świadomie zaprojektowanym elementem produktu. To decyzja projektowa Pawła, nie poprawka błędu. Propozycja z konkretnymi wartościami w `UX_RECOMMENDATIONS.md` §B1 **→ ZAMKNIĘTE w drugiej turze, patrz §6.** |
| B2 | P2 | [K] | Overlay tutoriala nie jest modalny dla TalkBacka — scrim łyka dotyk, ale nie usuwa treści pod spodem z drzewa semantyki; dymek nie dostaje fokusu | `ui/components/TutorialOverlay.kt:113` | Poprawka jest jednoznaczna (`invisibleToUser()` + `isTraversalGroup`), ale weryfikacja wymaga TalkBacka na urządzeniu. Naprawa bez możliwości sprawdzenia efektu to zgadywanie **→ ZAMKNIĘTE w drugiej turze, patrz §6.** |
| B3 | P2 | [K] | Brak `liveRegion` i `stateDescription` w całej aplikacji (0 wystąpień). Etykiety przycisków podmieniane na `"…"` w trakcie ładowania — TalkBack czyta „wielokropek, przycisk" | `ui/components/DesignKit.kt:210` i 5 ekranów | To samo — wymaga weryfikacji czytnikiem ekranu. Zakres większy niż jedna linia (dotyka wspólnego `PillButton` i pięciu ekranów) **→ ZAMKNIĘTE w drugiej turze, patrz §6.** |
| B4 | P2 | [K] | Cztery obszary dotyku poniżej 48 dp: pigułki filtrów (~32 dp), kopiuj na szczegółach (~30×34 dp), „Skip" w tutorialu (~30 dp), segment Active/Off (~38 dp) | `MaskedEmailListScreen.kt:556`, `MaskedEmailDetailScreen.kt:274`, `TutorialOverlay.kt:243`, `CreateMaskedEmailScreen.kt:314` | Poprawka jest bezpieczna i mechaniczna (`heightIn(min = 48.dp)`), ale zmienia rozkład czterech ekranów — chcę, żeby Paweł zobaczył efekt, zanim to wejdzie. Rekomendacja A2 **→ ZAMKNIĘTE w drugiej turze, patrz §6.** |
| B5 | P2 | [K] | Trzy pary kolorów poniżej 4,5:1 dla małego tekstu: akcent na `surfaceVariant` (4,32:1), `LightOffInk` na `LightOffBg` (4,31:1), licznik w wybranej pigułce przy `alpha 0.7` (**3,27:1** przy 10 sp) | `DemoBanner.kt:66`, `Color.kt:20`, `MaskedEmailListScreen.kt:577` | Licznik da się naprawić bez dotykania palety (zdjąć alpha → 5,02:1) i to jest rekomendacja A1. Pozostałe dwie to znowu zmiana palety **→ ZAMKNIĘTE w drugiej turze, patrz §6.** |
| B6 | P2 | [K] | Ekran szczegółów pobiera **całą listę masek**, żeby wyświetlić jedną — przy otwarciu, po każdym zapisie i po każdym przełączeniu stanu. `JmapApi` nie ma wariantu z filtrem `ids` | `ui/detail/MaskedEmailDetailViewModel.kt:59`, `data/api/JmapApi.kt` | Przy ~265 maskach to trzy pełne pobrania listy na jedną edycję. Poprawka wymaga nowej metody API i ostrożności, żeby pobranie jednej maski **nie nadpisało** całego cache'u — zmiana projektowa, nie poprawka. Rekomendacja B4 |
| B7 | P2 | [H] | Chwilowo „pusta" autorytatywna odpowiedź Play (wylogowany Sklep, wyczyszczone dane) odbiera Pro istniejącemu właścicielowi | `data/repository/ProRepositoryImpl.kt:204` | Obsługa błędów jest tu **wzorowa** — błąd sieci/dostępności nie degraduje uprawnienia. Degraduje tylko `Ok` z pustą listą, czyli autorytatywne „nie masz nic". Czy Play potrafi tak skłamać, to hipoteza, której nie potwierdziłem. Szkoda jest odwracalna („Przywróć zakup"). Wprowadzanie histerezy na podstawie hipotezy byłoby leczeniem objawu |
| B8 | P2 | [K] | Brak reakcji na 401: `cachedSession` nie ma TTL, token nie jest czyszczony, brak wylogowania. Po odwołaniu tokenu w panelu Fastmaila użytkownik krąży w pętli „Authentication failed" | `data/api/JmapApi.kt:299`, `ui/common/UiErrors.kt` | Automatyczne wylogowanie na 401 jest ryzykowne (przejściowe 401 z proxy skasowałoby poprawny token). Właściwa poprawka to akcja „Zaloguj ponownie" w banerze błędu — zmiana UX wymagająca projektu. Rekomendacja B2 |
| B9 | P3 | [K] | Brak idempotencji tworzenia maski: timeout po dotarciu żądania → użytkownik klika ponownie → dwie maski | `data/api/JmapApi.kt:96` | JMAP nie daje klucza idempotencji; obejście wymagałoby heurystyki po `forDomain`/`createdAt`, która sama może się mylić. Guard in-flight (A7) zamyka najczęstszy wyzwalacz |
| B10 | P3 | [K] | `Log.w` w `QuickMaskRunner` nie jest bramkowany `BuildConfig.DEBUG` i przeżywa release; `JmapException` niesie serwerowy `description` | `quickmask/QuickMaskRunner.kt:40` | Ekspozycja realnie ograniczona do ADB i bug reportów (`READ_LOGS` jest signature-only). Wpisałem do backlogu zamiast rozdmuchiwać |
| B11 | P3 | [K] | `QuickMaskUndoReceiver` kasuje maskę o dowolnym id z extras, bez sprawdzenia, że pochodzi z ostatniego quick-create | `quickmask/QuickMaskUndoReceiver.kt:16` | Dziś niewykorzystywalne: receiver nieeksportowany, PendingIntent immutable. Wart drugiego zamka analogicznego do `isQuickCreateLaunch`, ale to hardening na przyszłość, nie luka |
| B12 | P3 | [K] | `docs/privacy.md` deklaruje restrykcje klucza Firebase, których nie mogę zweryfikować; klucz `AIzaSy…` jest w repo w `app/google-services.json` | `app/google-services.json:18` | **To nie jest podatność** — klucz Firebase dla Androida i tak trafia do APK i jest identyfikatorem, nie sekretem; Google jawnie tak go opisuje. Ryzyko zależy wyłącznie od restrykcji ustawionych w konsoli GCP (package + SHA-1). Zero kodu do zmiany, jedna rzecz do sprawdzenia w konsoli |
| B13 | P3 | [K] | `.github/workflows/claude.yml` reaguje na `@claude` w komentarzu do issue bez filtra autora, w **publicznym** repo | `.github/workflows/claude.yml:14` | Uprawnienia workflow są read-only, a `claude-code-action` ma własną kontrolę uprawnień aktora, której nie zweryfikowałem. Do potwierdzenia, nie do naprawy w ciemno |
| B14 | P3 | [K] | README §Features nie wymienia Pro, zamka biometrycznego, eksportu CSV, share targetu, kafelka, skrótu ani trybu offline; §Settings opisuje ekran sprzed kilku wersji | `README.md:42` | Drift dokumentacji, zero wpływu na działanie. Wymaga decyzji Pawła co do marketingowego tonu — nie chcę pisać opisu produktu za niego |
| B15 | P3 | [K] | 9 nieużywanych stringów przetłumaczonych na 19 języków + 400 zduplikowanych wpisów (endonimy języków i sufiks domeny powinny mieć `translatable="false"`) | `values*/strings.xml` | Czysta higiena, zero wpływu na użytkownika. Usuwanie 180+ wpisów z 20 plików w tym samym przebiegu, co zmiany semantyki archiwizacji, niepotrzebnie rozdmuchałoby diff |
| B16 | P3 | [K] | `MaskedEmail.formattedCreatedAt` / `formattedLastMessageAt` — martwy kod z formatterem, który zamraża locale w momencie ładowania klasy | `domain/model/MaskedEmail.kt:19` | Nieużywane, więc nie boli. Pułapka dla następnego, kto po nie sięgnie. Do backlogu |
| B17 | P3 | [K] | `CachedSnapshot` nie ma pola wersji formatu | `data/local/MaskedEmailCache.kt:93` | Dziś ratują to `ignoreUnknownKeys`, wartości domyślne i fallback nieznanego stanu. Znacznik właściciela (A4) daje przy okazji mechanizm odrzucania niezgodnych snapshotów. Jawne pole wersji jest zalecane przed kolejną zmianą kształtu |

### 3.4 Zweryfikowane negatywnie — **nie są** defektami

Podaję, bo to połowa wartości audytu: żeby nikt tego nie „naprawiał" po raz drugi.

**Bezpieczeństwo.** Confused deputy przez `QuickMaskActivity` — zamknięte podwójnie (`exported="false"` + wymóg własnej akcji). Wszystkie cztery `PendingIntent` są `FLAG_IMMUTABLE` z jawnym targetem. `QuickMaskTileService` eksportowany, ale za `BIND_QUICK_SETTINGS_TILE`. Parser share'u nie ma regexa, ma gwarancję postępu O(n) i twardy cap 8192 znaków. Share nie omija zamka (`WaitForUnlock` trzyma go, `consumes()` odmawia wyczyszczenia). Logging interceptor tylko w debug, z redakcją `Authorization`. `apiUrl` z sesji walidowany **przed** przypisaniem — trik z userinfo (`https://api.fastmail.com@evil.com`) nie przechodzi. Neutralizacja formuł CSV wg OWASP, z inspekcją pierwszego niebiałego znaku. FileProvider nieeksportowany, ograniczony do `cache-path exports/`. `FLAG_SECURE` + `filterTouchesWhenObscured` w release. Adres maski **nie jest** w treści powiadomienia; `VISIBILITY_SECRET` na builderze i na kanale.

**Prywatność.** `CrashReporter` wystawia wyłącznie dwa przełączniki — nie ma API, którym dałoby się przekazać dane do raportu, a `CrashReportingPrivacyTest` czyta źródła **i skompilowane klasy**, żeby to egzekwować. `firebase_sessions_enabled=false` w manifeście, z komentarzem opisującym dokładnie, czego ta flaga **nie** zatrzymuje. Zero SDK analitycznych.

**i18n.** 232 stringi + 8 `<plurals>` w każdym z 20 locale — **zero brakujących kluczy, zero niezgodnych placeholderów, zero nadmiarowych**. Kategorie CLDR poprawne wszędzie (`ar` ma pełne zero/one/two/few/many/other; `pl`/`ru`/`uk` one/few/many/other). `locales_config.xml` zgadza się 1:1 z katalogami i z `Language.kt`. **Notatka z pamięci projektu o `time_year_ago` jako zwykłym `<string>` jest nieaktualna** — to `<plurals>` z poprawnymi formami polskimi, a dwa testy pilnują regresji.

**Dostępność (to, co działa).** Wszystkie 24 wystąpienia `contentDescription = null` są poprawnie dekoracyjne. `StateDot` przyjmuje opis stanu, filtry mają `Role.Tab`, wybór akcentu i języka `Role.RadioButton`, przełączniki `toggleable(role = Role.Switch)` na całym wierszu. Etykiety pól **są** powiązane semantycznie. 100% typografii w `sp`. RTL: zero `Absolute`/`Left`/`Right`, ikony przez `AutoMirrored`, `list_stats` przestawiony w `values-ar`.

**Dane.** `JmapApi.ensureSession` — poprawny double-checked locking pod `Mutex` z polami `@Volatile`. `parseSetResponseUpdated`/`Destroyed` **pozytywnie potwierdzają** skutek po stronie serwera, zamiast zakładać sukces. `ProRepositoryImpl` degraduje uprawnienie wyłącznie na odpowiedź autorytatywną, ma `seedJob` z semantyką compare-and-set, ponawia `acknowledge` i rozróżnia „ten sam zakup" od „nowy zakup po zwrocie". `QuickMaskUndoReceiver.goAsync()` utrzymuje proces do zakończenia operacji.

---

## 4. Weryfikacja po zmianach

| Komenda | Przed | Po |
|---|---|---|
| `./gradlew clean testDebugUnitTest` | 442 / 0 porażek | **446 / 0 porażek** |
| `./gradlew lintDebug` | 0 errorów | **0 errorów** |
| `./gradlew assembleDebug` | SUCCESS | **SUCCESS** |
| `./gradlew assembleRelease` | SUCCESS | **SUCCESS** |
| `./gradlew connectedDebugAndroidTest` (emulator API 36) | 16 / 0 porażek | **19 / 0 porażek** |

**Podział weryfikacji — co czym potwierdzone:**

- **Zweryfikowane automatycznie:** wszystkie poprawki mają pokrycie w testach jednostkowych albo instrumentowanych, poza A2 (bramka zamka — wymaga urządzenia z biometrią), A5 (dyspozytory — brak StrictMode w testach), A6 (scope mutacji — testy potwierdzają, że kod się wykonuje, nie że przeżywa zniszczenie ViewModelu) i A11/A12 (konfiguracja i dokumentacja).
- **Zweryfikowane ręcznie:** ścieżki UI przejściem w trybie demo na emulatorze ze zrzutami ekranu (welcome → demo → lista → tutorial → wyszukiwanie bez wyników → koniec listy). Build bez `JAVA_HOME` sprawdzony i **potwierdzony jako niedziałający** na systemowym JDK 26 — to znany koszt A11, zaakceptowany przez Pawła.
- **Nieweryfikowalne w tym środowisku:** semantyka `destroy` vs `state: deleted` po stronie Fastmaila (brak tokenu), zachowanie zamka biometrycznego (emulator bez skonfigurowanej biometrii), rzeczywisty zysk wydajności A5 przy ~265 maskach, restrykcje klucza Firebase (B12), kontrola uprawnień aktora w `claude-code-action` (B13).

**Jedna poprawka została złapana przez testy instrumentowane, nie przez przegląd kodu.** Atomowy zapis cache'u (zapis do pliku tymczasowego + `renameTo`) rozbił odszyfrowywanie: `EncryptedFile` przekazuje `File.getName()` do Tinka jako associated data AEAD, więc plik zaszyfrowany pod nazwą `…bin.tmp` i przemianowany nigdy się już nie odszyfruje. Testy JVM tego nie wyłapią — nie ma tam Keystore'a. Rozwiązanie: staging w osobnym katalogu, pod **tą samą** nazwą pliku.

---

## 5. Ryzyka i ograniczenia audytu

1. **A1 nie jest zweryfikowane na prawdziwym koncie.** To najpoważniejsza zmiana i najważniejsza pozycja QA. Kierunek poprawki jest poprawny niezależnie od zachowania serwera, ale jej *skutek* — czy zarchiwizowane maski faktycznie pojawią się pod „Archived" — trzeba zobaczyć.
2. **Nie miałem tokenu Fastmail ani dostępu do konta Pawła** i świadomie o niego nie prosiłem. Cała warstwa JMAP jest zweryfikowana wyłącznie przez testy z atrapami.
3. **Testy instrumentowane poszły na emulatorze, nie na telefonie.** Fizyczny OnePlus 13 ma instalkę podpisaną kluczem Play, więc instalacja lokalnego builda wymagałaby odinstalowania aplikacji, co skasowałoby token.
4. **Brak weryfikacji czytnikiem ekranu.** Wszystkie znaleziska a11y opierają się na odczycie kodu i wyliczeniach kontrastu, nie na TalkBacku. Dlatego B2 i B3 zostały nienaprawione mimo jasnych poprawek.
5. **Trzy audyty specjalistyczne prowadziły równoległe agenty.** Ich ustalenia weryfikowałem samodzielnie odczytem kodu przed przyjęciem; jedno twierdzenie (możliwość wyzwolenia A2 przez odcięcie sieci) **odrzuciłem** po przeczytaniu `ProRepositoryImpl` — degradacja uprawnienia następuje tylko na odpowiedź autorytatywną.
6. **Nie ruszałem zależności.** Lint zgłasza 26 `GradleDependency`, a Dependabot ma otwarte PR-y. Selektywny bump wymaga osobnego przebiegu z własnym zakresem testów — mieszanie go z poprawkami semantyki archiwizacji zaciemniłoby, co zepsuło ewentualną regresję.

---

## 6. Druga tura — dostępność (2026-07-27, po decyzji Pawła „napraw wszystko")

Po pierwszym zestawie poprawek Paweł poprosił o domknięcie wszystkiego, co da się bezpiecznie domknąć. Zamknięte zostały wszystkie pozycje a11y z §3.3 poza tymi, które wymagają jego decyzji projektowej albo urządzenia z TalkBackiem do walidacji efektu.

| ID | Było | Jak naprawione |
|---|---|---|
| **B1** | Obramowanie pól 1,15:1 (jasny) / 1,04:1 (ciemny) — pole nieodróżnialne od tła | Nowe `LightInputLine` `#8E846E` (3,23:1) i `DarkInputLine` `#776D5C` (3,58:1), użyte **wyłącznie** jako obramowanie `DesignInput`. Hairline'y, obramowania kart i dividery zostały nietknięte — paleta „warm ink" nie została przerysowana, żeby naprawić problem trzech ekranów |
| **B2** | Overlay tutoriala niemodalny dla czytnika ekranu | `isTraversalGroup` + `traversalIndex` na overlayu i `clearAndSetSemantics` na treści pod spodem, gdy tutorial jest widoczny |
| **B3** | Etykieta przycisku podmieniana na `"…"`; zero `liveRegion`/`stateDescription` w całym `ui/` | `PillButton` dostał `loadingDescription`; cztery miejsca wywołań zachowują nazwę akcji. Baner offline ogłasza się przy pojawieniu. Nowy string `state_working` w 20 lokalach |
| **B4** | Cztery cele dotykowe 30–38 dp | Pigułki filtrów, kopiowanie na szczegółach, „Skip" w tutorialu i segment Active/Off — wszystkie 48 dp, bez zmiany wyglądu |
| **B5** | Licznik w wybranej pigułce 3,27:1 przy 10 sp | Zdjęta `alpha = 0.7f` → 5,02:1 |
| **B6** | `isError` zmieniało tylko kolory; podpowiedź niepowiązana z polem | `semantics { error(hint) }` na węźle pola |
| — | Tytuły ekranów bez `heading()` | Cztery ekrany oznaczone, TalkBack oferuje nawigację po nagłówkach |
| — | Sztywna wysokość 360 dp w dialogu języka | `heightIn(max = 360.dp)` |

**Jedna regresja złapana i naprawiona w trakcie.** Uczynienie tutoriala modalnym dla czytnika wywaliło 6 testów instrumentowanych: ich wspólny helper czekał na tytuł listy, a tytuł jest teraz — celowo — niewidoczny w drzewie semantyki, dopóki coach marks są na wierzchu. Poprawiony został helper, nie zachowanie: test opisywał założenie, które przestało być prawdziwe, a nowe zachowanie jest dokładnie tym, czego doświadcza użytkownik czytnika ekranu.

**Nadal otwarte i świadomie niezamknięte:**

| Pozycja | Powód |
|---|---|
| Weryfikacja wszystkich powyższych TalkBackiem | Nie mam urządzenia z włączonym czytnikiem. Poprawki są wyliczone i skompilowane, ale „działa dla czytnika" potwierdzi dopiero czytnik |
| Pozostałe dwie pary kolorów < 4,5:1 (akcent na `surfaceVariant`, `LightOffInk` na `LightOffBg`) | Zmiana palety produktu — Twoja decyzja, propozycja w `UX_RECOMMENDATIONS.md` §B1 |
| B6/B7/B8/B9 z §3.3 (pobieranie pojedynczej maski, degradacja Pro, 401, idempotencja) | Zmiany projektowe, nie poprawki — nieproporcjonalne do audytu |
| `values-v31` bez zasobów, ostrzeżenia lintu (99) | Higiena bez wpływu na użytkownika |
