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
