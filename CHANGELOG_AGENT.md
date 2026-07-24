# CHANGELOG_AGENT.md — 2026-07-24 (pass C)

Gałąź: `feature/audit-2026-07-24c` (z `main` @ `c8bfc3d`, v1.8.1).
Weryfikacja: `testDebugUnitTest` **121/121 PASS** · `lintDebug` 0 errors · `assembleDebug` SUCCESS · `assembleRelease` SUCCESS.

Pięć commitów, jeden na znalezisko.

| Commit | ID | Tytuł |
|---|---|---|
| `8a97711` | D1 | Fix: dead external links on Android 11+ |
| `5b6d035` | D2 | Fix: translate 13 strings into all 19 locales |
| `f8dee44` | D3 | Fix: keep the pasted token on a retryable login failure |
| `09c3759` | D4 | Fix: guard the demo-mode DataStore write |
| `1ea242e` | D5 | Add: refuse a signed release without a Play licence key |

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
| `res/values/strings.xml` | Nowy `error_no_app_for_link`; usunięte **10** atrybutów `tools:ignore="MissingTranslation"` | D1, D2 |
| `res/values-*/strings.xml` (19 plików) | 247 uzupełnionych/poprawionych tłumaczeń | D2 |
| `CLAUDE.md` | SDK 35 → 36 (drift dokumentacji) | D6 |

## Dodane testy (+7, łącznie 121)

| Plik | Testy | Co pokrywa |
|---|---|---|
| `i18n/TranslationCompletenessTest.kt` **(nowy)** | 2 | Brak klucza w dowolnym lokalu; angielskie zdanie ocalałe w tłumaczeniu. Krótkie etykiety (OK, URL, Status) świadomie wyłączone — w tych językach naprawdę są identyczne |
| `ui/common/UiErrorsTest.kt` | +2 | `isRetryable` dla transportu/5xx/429 vs 401/403/nieznane |
| `ui/auth/LoginViewModelTest.kt` | +3, 1 przemianowany | Token zachowany przy IOException, 503 i 429; czyszczony przy 401 |

**Test i18n został sprawdzony negatywnie:** po celowym usunięciu `logout_confirm_message` z `values-pl` i podmianie `discard_changes_message` na tekst angielski oba testy zafailowały; po przywróceniu przechodzą.

## Zmiany zachowania (widoczne dla użytkownika)

1. **Kontakt, Polityka prywatności, Regulamin faktycznie się otwierają** na Androidzie 11+. Gdy naprawdę brak handlera — snackbar zamiast ciszy.
2. **Nieudane logowanie przy braku sieci / 429 / 5xx nie kasuje już wklejonego tokenu.** Przy 401/403 nadal kasuje.
3. **19 języków dostaje przetłumaczone** komunikaty walidacji prefiksu, pusty stan „brak wyników" oraz dialogi potwierdzenia wylogowania i odrzucenia zmian.
4. **Wejście w tryb demo nie wywala aplikacji** przy błędzie zapisu DataStore (zostajesz na ekranie powitalnym).
5. **Build:** `assembleRelease`/`bundleRelease` z keystore, ale bez klucza licencyjnego, teraz **failuje** zamiast po cichu wypuścić APK bez weryfikacji podpisu zakupu.

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
6. **Zakup Pro w internal testing** na buildzie z realnym kluczem licencyjnym (nietknięte tym przebiegiem, ale D5 zmienia proces budowania).
