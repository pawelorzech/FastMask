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
| **Domena** | Modele, interfejsy repozytoriów, 12 use case'ów, parser linków share'owanych |
| **Prezentacja** | Single-Activity, Jetpack Compose, 8 ekranów, `DesignKit.kt` (tokeny kolorów, typografia, komponenty), nawigacja NavHost |
| **Testy** | 442 jednostkowych (JUnit 5 + Turbine + MockK), 16 instrumentowanych (Compose Testing) — wszystkie zielone |

---

## 3. Znaleziska

Znaleziska są ponumerowane w kolejności priorytetu biznesowego. Każde ma identyfikator (`A-n`, `S-n`, …), opis problemu, dowód w kodzie, ocenę ryzyka i propozycję naprawy.

---

### A-1 · Stan maski na liście rozróżnialny wyłącznie kolorem (WCAG 1.4.1, poziom A) **[K]**

**Priorytet:** wysoki — naruszenie normy dostępności poziomu A (obowiązkowy pułap dla aplikacji rządowych i wielu komercyjnych)

**Plik:** `MaskedEmailListScreen.kt:654`, `DesignKit.kt` (`StateDot`)

**Problem.** Na ekranie listy — najczęściej przewijanym w aplikacji — jedynym wskaźnikiem stanu maski jest kolorowa kropka (`StateDot`). Zmierzone stosunki kontrastu *między wypełnieniami kropek*:

| Para | Kolory | Stosunek |
|---|---|---|
| jasny: zarchiwizowana vs oczekująca | `#7D3D1E` / `#6B4C0D` | **1,04:1** |
| ciemny: włączona vs oczekująca | `#B8D49A` / `#E6C576` | **1,02:1** |
| jasny: włączona vs wyłączona | `#3A5724` / `#6B6450` | 1,39:1 |
| jasny: zarchiwizowana vs wyłączona | `#7D3D1E` / `#6B6450` | 1,39:1 |

Barwa jest jedynym wyróżnikiem; luminancja jest praktycznie identyczna, więc przy każdej symulacji dwubarwności stany zlewają się ze sobą. Każda kropka jest doskonale *widoczna* na tle karty (5,5–10,3:1) — po prostu nie jest *identyfikowalna*.

Ścieżka czytnika ekranu jest już obsłużona (`stateContentDescription`, `:626`), a ekran szczegółów jest bez zarzutu (`StatePill` niesie etykietę tekstową). To jedyna luka dostępności, w której aplikacja jest gorsza dla widzącego użytkownika niż dla użytkownika czytnika ekranu.

**Dlaczego etykieta tekstowa została odrzucona.** Czwarty wiersz tekstu w każdym wierszu listy pogrubiłby najgęstszy ekran aplikacji. Kształt rozwiązuje WCAG 1.4.1 bez kosztu układu.

**Propozycja naprawy.** Zachować kolory, dodać drugi kanał do `StateDot` — ten sam rozmiar, ta sama pozycja, bez zmiany układu:

| Stan | Kształt |
|---|---|
| `enabled` | wypełnione koło (jak dziś) |
| `disabled` | pierścień (pusty w środku) |
| `archived` | wypełniony kwadrat (lub koło z ukośnikiem) |
| `pending` | przerywany / kreskowany kontur |

**Kryterium ukończenia.** Zrzuty ekranu listy przepuszczone przez symulator dwubarwności pokazują cztery stany rozróżnialne w skali szarości.

**Dlaczego audyt tego nie naprawił.** Zmienia to język wizualny centralnego ekranu aplikacji, a ciepło-atramentowy design jest zadeklarowaną wartością produktu. To decyzja projektowa, a nie naprawa defektu.

---

### A-2 · `ExportCache` zapisuje CSV w katalogu cache bez szyfrowania **[K]**

**Priorytet:** wysoki

**Plik:** `ExportCache.kt`

**Problem.** Eksport CSV tworzy plik w `context.cacheDir` jako zwykły tekst. Na urządzeniach bez szyfrowania dysku (API 26–28, producenci OEM z wyłączonym FBE) lub po wykonaniu pełnego backupu ADB plik jest czytelny bez roota. Plik zawiera wszystkie adresy e-mail użytkownika.

**Propozycja naprawy.** Użyć `EncryptedFile` (jak robi to `MaskedEmailCache`) albo zapisyw