# CHANGELOG_AGENT.md — 2026-07-24 (pass C)

Gałąź: `feature/audit-2026-07-24c` (z `main` @ `c8bfc3d`, v1.8.1).
Weryfikacja: `testDebugUnitTest` **124/124 PASS** · `lintDebug` 0 errors · `assembleDebug` SUCCESS · `assembleRelease` SUCCESS.

Siedem commitów. Wersja podbita do **1.8.2 (versionCode 19)**.

| Commit | ID | Tytuł |
|---|---|---|
| `8a97711` | D1 | Fix: dead external links on Android 11+ |
| `5b6d035` | D2 | Fix: translate 13 strings into all 19 locales |
| `f8dee44` | D3 | Fix: keep the pasted token on a retryable login failure |
| `09c3759` | D4 | Fix: guard the demo-mode DataStore write |
| `1ea242e` | D5 | Add: refuse a signed release without a Play licence key |
| `4aa2d89` | D7 | Fix: strip stray %s from 18 translations |
| `a21b657` | A1–A3 | Add: UX quick wins |

## Zmienione pliki — kod produkcyjny

| Plik | Zmiana | ID |
|------|--------|----|
| `AndroidManifest.xml` | Dodane `<queries>` dla `SENDTO`/`mailto` i `VIEW`/`https` (widoczność pakietów API 30+) | D1 |
| `ui/common/ExternalLinks.kt` **(nowy)** | `openExternalIntent()` — launch + catch `ActivityNotFoundException`, zwraca `Boolean`; zastępuje wzorzec `resolveActivity() != null` | D1 |
| `ui/settings/SettingsScreen.kt` | Kontakt używa helpera; snackbar gdy brak aplikacji pocztowej | D1 |
| `ui/pro/ProScreen.kt` | Polityka prywatności i Regulamin przez helper `openLink`; snackbar przy braku przeglądarki | D1 |
| `ui/auth/LoginViewModel.kt` | Token czyszczony tylko po sukcesie lub odrzuceniu 401/403; zachowany przy błędach powtarzalnych | D3 |
| `ui/common/UiErrors.kt` | Nowe `isRetryable(Throwable)` — ta sama tabela co `messageRes`, żeby nie mogły się rozjechać | D3 |
| `ui/welcome/WelcomeViewModel.kt` | `CoroutineExceptionHandler` na `enterDemoMode()` | D4 |
| `build.gradle.kts` | Bramka `gradle.taskGraph.whenReady` — podpisany release bez klucza licencyjnego jest odrzucany | D5 |
| `ui/list/MaskedEmailListScreen.kt` | Snackbar kopiowania nazywa adres; nagłówek listy używa `pluralStringResource` | A1, A3 |
| `ui/settings/SettingsViewModel.kt` | `ExportFailed(messageRes)` — przyczyna przez `UiErrors` zamiast jednego komunikatu | A2 |
| `ui/settings/SettingsScreen.kt` | Osobny komunikat dla błędu **zapisu** pliku eksportu | A2 |
| `res/values/strings.xml` | Nowe `error_no_app_for_link`, `list_copied_value`, `settings_export_failed_write`, 2× `<plurals>`; `list_stats` → format `%1$s · %2$s`; usunięty `list_copied`; usunięte **10** `tools:ignore="MissingTranslation"` | D1, D2, A1–A3 |
| `res/values-*/strings.xml` (19 plików) | 247 tłumaczeń (D2) + 3 stringi i 2 plurals × 19 (A1–A3) + usunięty zbędny `%s` w 18 lokalach (D7) | D2, D7, A1–A3 |
| `CLAUDE.md` | SDK 35 → 36 (drift dokumentacji) | D6 |
| `app/build.gradle.kts` | versionCode 18 → 19, versionName 1.8.1 → 1.8.2 | release |
| `marketing/play/release-notes/{pl-PL,en-US}.txt` | Notatki 1.8.2, obie w limicie 500 znaków Play | release |

## Dodane testy (+10, łącznie 124)

| Plik | Testy | Co pokrywa |
|---|---|---|
| `i18n/TranslationCompletenessTest.kt` **(nowy)** | 3 | Brak klucza w dowolnym lokalu; angielskie zdanie ocalałe w tłumaczeniu; niezgodność argumentów formatujących. Krótkie etykiety (OK, URL, Status) świadomie wyłączone z drugiego testu — w tych językach naprawdę są identyczne |
| `ui/common/UiErrorsTest.kt` | +2 | `isRetryable` dla transportu/5xx/429 vs 401/403/nieznane |
| `ui/auth/LoginViewModelTest.kt` | +3, 1 przemianowany | Token zachowany przy IOException, 503 i 429; czyszczony przy 401 |
| `ui/settings/SettingsViewModelTest.kt` | +2, 1 zaktualizowany | `ExportFailed` niesie przyczynę: sieć, rate limit, fallback |

**Wszystkie trzy testy i18n sprawdzone negatywnie** — celowo przywróciłem każdy z bugów, przeciw którym stoją (usunięty klucz w `values-pl`, angielskie zdanie w `values-pl`, `%s` z powrotem w `values-de`). Każdy failuje; po przywróceniu stanu przechodzą.

## Zmiany zachowania (widoczne dla użytkownika)

1. **Kontakt, Polityka prywatności, Regulamin faktycznie się otwierają** na Androidzie 11+. Gdy naprawdę brak handlera — snackbar zamiast ciszy.
2. **Nieudane logowanie przy braku sieci / 429 / 5xx nie kasuje już wklejonego tokenu.** Przy 401/403 nadal kasuje.
3. **19 języków dostaje przetłumaczone** komunikaty walidacji prefiksu, pusty stan „brak wyników" oraz dialogi potwierdzenia wylogowania i odrzucenia zmian.
4. **Wejście w tryb demo nie wywala aplikacji** przy błędzie zapisu DataStore (zostajesz na ekranie powitalnym).
5. **Build:** `assembleRelease`/`bundleRelease` z keystore, ale bez klucza licencyjnego, teraz **failuje** zamiast po cichu wypuścić APK bez weryfikacji podpisu zakupu.
6. **18 języków nie pokazuje już surowego `%s`** na ekranie szczegółów maski (D7).
7. **Snackbar kopiowania nazywa adres**, nieudany eksport CSV podaje przyczynę, licznik masek ma poprawne formy liczby mnogiej (A1–A3).

## Świadoma rewizja wcześniejszej decyzji

D3 zmienia kontrakt, który poprzedni audyt celowo ustanowił i pokrył testem („token hygiene"). Nie usunąłem tego testu — zawęziłem kontrakt i przemianowałem test:

- **Zachowane z pierwotnej intencji:** token znika ze stanu UI, gdy spełnił swoją rolę (sukces → jest w EncryptedSharedPreferences) albo został ostatecznie odrzucony (401/403).
- **Zmienione:** przy błędzie powtarzalnym token zostaje. `UiErrors` mówi wtedy użytkownikowi „spróbuj ponownie", a aplikacja jednocześnie kasowała zamaskowany ~40-znakowy sekret potrzebny do tej próby. Te dwa zachowania były wzajemnie sprzeczne.

Jeśli uznasz, że higiena sekretu ma pierwszeństwo nad wygodą — cofnięcie to jedna linia w `LoginViewModel` plus aktualizacja trzech testów.

## Potencjalne regresje do sprawdzenia

| Ryzyko | Dlaczego niskie | Jak sprawdzić |
|---|---|---|
| `<queries>` zmienia widoczność pakietów | Deklaracja tylko rozszerza widoczność, nic nie odbiera | Manifest zmergowany — zweryfikowany odczytem |
| Snackbar w `ProScreen` koliduje z komunikatami zakupu | Ten sam `SnackbarHostState`, komunikaty kolejkowane | Tapnij Regulamin w trakcie trwającego zakupu |
| Bramka release blokuje CI | Warunkowana obecnością keystore; bez keystore przechodzi | Zweryfikowane — `assembleRelease` bez keystore SUCCESS |
| Tłumaczenia psują layout (dłuższe teksty) | Dialogi mają swobodny layout, brak stałych szerokości | Manualnie: DE i RU (najdłuższe) na wąskim ekranie |

## Do manualnego QA przed publikacją

1. **Android 11+ (najlepiej 13/14): Ustawienia → Kontakt** — musi otworzyć klienta poczty. To główny nienaprawialny-automatycznie dowód dla D1.
2. **Paywall → Polityka prywatności i Regulamin** — muszą otworzyć przeglądarkę (wymóg Play).
3. **Logowanie w trybie samolotowym** — błąd sieci, token **zostaje** w polu, przycisk działa po włączeniu sieci.
4. **Logowanie błędnym tokenem** — 401, pole **czyszczone**.
5. **Przełącz język na polski** → wyloguj się i odrzuć zmiany w edycji maski — oba dialogi po polsku.
6. **Niemiecki / rosyjski → szczegóły maski** — etykieta „Letzte Nachricht" bez `%s` (D7).
7. **Polski → lista masek** — licznik odmienia się: 1 aktywna, 2 aktywne, 5 aktywnych (A3).
8. **Zakup Pro w internal testing** na buildzie z realnym kluczem licencyjnym (nietknięte tym przebiegiem, ale D5 zmienia proces budowania).

---

# Aneks — backlog po v1.8.2 (2026-07-25)

Gałąź: `feature/backlog-e1-e7` (z `main` @ `74e20cf`). **Niezmergowana, niewydana.**
Weryfikacja: `testDebugUnitTest` **143/143 PASS** · `connectedDebugAndroidTest` **12/12 PASS** (Pixel 9a, API 36) · `lintDebug` 0 errors · `assembleRelease` SUCCESS.

| Commit | ID | Tytuł |
|---|---|---|
| `116907f` | E1–E7 | Fix: audit backlog E1-E7 |
| `fdecf5d` | B3, D8–D10 | Add: instrumented tests for the main user paths |
| `837ceab` | B1 | Add: encrypted offline cache for the mask list |

## Zmienione i nowe pliki — kod produkcyjny

| Plik | Zmiana | ID |
|------|--------|----|
| `ui/list/MaskedEmailListViewModel.kt` | Jedno `fetch()` + jedna flaga in-flight zamiast dwóch ścieżek; fallback na cache przy braku sieci; pole `cachedAt` | E1, B1 |
| `ui/detail/MaskedEmailDetailViewModel.kt` | `isDeleting` czyszczone po wysłaniu eventu | E2 |
| `data/local/SettingsDataStore.kt` | Jedna deklaracja klucza języka | E4 |
| `data/local/ExportCache.kt` **(nowy)** | Właściciel `cacheDir/exports`: zapis, wygasanie po godzinie, `clear()` | E6 |
| `data/local/MaskedEmailCache.kt` **(nowy)** | Zaszyfrowany snapshot listy (`EncryptedFile` + Keystore), DTO oddzielone od modelu domenowego | B1 |
| `data/repository/AuthRepositoryImpl.kt` | `logout()` czyści eksport CSV i snapshot masek | E6, B1 |
| `data/repository/MaskedEmailRepositoryImpl.kt` | Write-through do cache przy każdym udanym fetchu; `cachedMaskedEmails()` | B1 |
| `data/repository/DemoMaskedEmailRepositoryImpl.kt` | Implementuje `DemoSession.reset()`; brak cache w demo | D8, B1 |
| `data/repository/ProRepositoryImpl.kt` | Zapis dowodu także przy zmianie tokenu zakupu | E7 |
| `domain/repository/DemoSession.kt` **(nowy)** | Kontrola cyklu życia danych demo | D8 |
| `domain/model/CachedMasks.kt` **(nowy)** | Snapshot + znacznik czasu razem, żeby trudno było zapomnieć o wieku | B1 |
| `domain/usecase/GetCachedMaskedEmailsUseCase.kt` **(nowy)** | Jawne pytanie o dane nieaktualne | B1 |
| `ui/components/DesignKit.kt` | `PillIconButton` nadaje `contentDescription`, nie tylko `onClickLabel` | **D9** |
| `ui/components/DesignInput.kt` | Pole tekstowe dostaje nazwę z etykiety | **D10** |
| `ui/welcome/WelcomeViewModel.kt` | Reset danych demo przy wejściu w tryb demo | D8 |
| `ui/settings/SettingsScreen.kt` | Eksport przez `ExportCache` zamiast logiki cache w Composable | E6 |
| `ui/list/MaskedEmailListScreen.kt` | Pasek „Offline · zaktualizowano X temu" | B1 |
| `AndroidManifest.xml` | Komentarz: dlaczego reguły backupu zostają przy `allowBackup=false` | E5 |
| `res/values*/strings.xml` (20) | `list_offline_cached` w 20 językach | B1 |
| `build.gradle.kts` | `HiltTestRunner`; AndroidX Test 1.6–1.7 / Espresso 3.7.0 (tylko test) | B3 |

## Dodane testy (+7 jednostkowych, +12 instrumentowanych)

| Plik | Co pokrywa |
|---|---|
| `androidTest/MainFlowsTest.kt` **(nowy)** | Welcome→demo, tworzenie maski, archiwizacja z undo (liczniki chipów), wyszukiwanie, ustawienia, stan maski dla czytnika ekranu |
| `androidTest/MaskedEmailCacheTest.kt` **(nowy)** | Round-trip, brak cache, **adresy niewidoczne w bajtach pliku**, uszkodzony cache → null, nadpisanie, `clear()` |
| `data/local/ExportCacheTest.kt` **(nowy)** | Nazwa pliku, wygasanie po godzinie vs plik sprzed 5 minut, `clear()` |
| `data/repository/AuthRepositoryImplTest.kt` **(nowy)** | Wylogowanie czyści eksport, token, sesję, tryb demo |
| `ui/list/MaskedEmailListViewModelTest.kt` | Wyścig dwóch ścieżek ładowania; pull-to-refresh zgłasza błąd mimo danych; 5 testów cache offline |
| `data/repository/ProRepositoryImplTest.kt` | Nowy token przepisuje dowód; ten sam token nie generuje zapisu |
| `data/repository/MaskedEmailRepositoryImplTest.kt` | Write-through przy sukcesie; brak zapisu przy porażce |

Testy dla E1, E6 i E7 sprawdzone **negatywnie** — po przywróceniu starego warunku failują.

## Zmiany zachowania (widoczne dla użytkownika)

1. **Lista działa offline** — pokazuje ostatni snapshot z paskiem „Offline · zaktualizowano X temu".
2. **TalkBack nazywa przyciski ikonowe i pola formularzy** (wcześniej „Button" / „Edit box").
3. **„Wypróbuj demo" zaczyna od czystej listy**, także po wcześniejszym demo w tym samym uruchomieniu.
4. **Eksport CSV nie przeżywa wylogowania.**
5. Pull-to-refresh w trakcie odświeżania w tle nie startuje drugiego zapytania.

## Do manualnego QA (1.8.2 jest na testach wewnętrznych)

1. **Android 11+: Ustawienia → Kontakt** oraz **paywall → Polityka / Regulamin** — nadal niezweryfikowane na urządzeniu (D1).
2. **Tryb samolotowy po wcześniejszym udanym wejściu** — lista pokazuje maski + pasek offline.
3. **Wyloguj się i sprawdź, że po ponownym zalogowaniu lista ładuje się z sieci**, a nie ze starego cache'u innego konta.
4. **TalkBack** na liście i w formularzu tworzenia.
5. **Biometria** — E3 (podwójny prompt) nadal niepotwierdzony.
