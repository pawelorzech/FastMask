# CHANGELOG_AGENT.md — 2026-07-24 (pass B)

Gałąź: `feature/audit-2026-07-24b` (z `main` @ `f1e89ee`, v1.8.0). Wszystkie fixy potwierdzone: `testDebugUnitTest` 109/109 PASS · `lintDebug` 0 errors · `assembleRelease` SUCCESS.

## Zmienione pliki (kod produkcyjny)

| Plik | Zmiana | ID |
|------|--------|----|
| `data/api/JmapApi.kt` | `@Volatile` na `cachedSession` i `cachedAccountId` | CC4 |
| `data/repository/ProRepositoryImpl.kt` | `handlePurchases(fromBuyFlow=true)` emituje event terminalny na gałęziach „brak SKU" i `FREE` (spinner buy nie wisi) | CC2 |
| `domain/usecase/ExportMasksUseCase.kt` | Neutralizacja CSV na pierwszym nie-białym znaku (`trimStart().firstOrNull()`) | SEC2 |
| `ui/list/MaskedEmailListViewModel.kt` | `CoroutineExceptionHandler` na zapisie tutorialu; synchroniczny `refreshInFlight` + guard `isLoading` na `loadMaskedEmails` | CC1, CC5 |
| `ui/settings/SettingsViewModel.kt` | `CoroutineExceptionHandler` na 5 zapisach DataStore/logout | CC1 |
| `ui/components/DemoBanner.kt` | `CoroutineExceptionHandler` na `exitDemoMode`; „Sign in" → 48dp tap-target + `Role.Button` | CC1, R6 |
| `ui/detail/MaskedEmailDetailViewModel.kt` | `loadEmail(resetEdits: Boolean)`; reload po save/toggle nie kasuje pól edytowanych | CC3 |
| `ui/detail/MaskedEmailDetailScreen.kt` | `hasChanges` liczony na `.trim()`; spinner przy `isDeleting`; retry w błędzie ładowania | B7, B6, B3 |
| `ui/create/CreateMaskedEmailViewModel.kt` | `prefixError: String?` → `prefixErrorRes: Int?` (i18n) | B2 |
| `ui/create/CreateMaskedEmailScreen.kt` | Użycie współdzielonego `copyToClipboard` (sensitive flag); resolve `prefixErrorRes`; segmented `selectable(Role.RadioButton)`; usunięto lokalny `copyToClipboard` | SEC3, B2, R7 |
| `ui/list/MaskedEmailListScreen.kt` | `StateDot` z `contentDescription` (helper `stateContentDescription`); `NoMatchesBlock`; label semantyczny search | B1, B4, R5 |
| `ui/components/DesignKit.kt` | `StateDot` przyjmuje opcjonalny `contentDescription` → semantyka a11y | B1 |
| `res/values/strings.xml` | +4 stringi (`create_email_error_prefix_length/_chars`, `email_list_no_matches/_sub`) z `tools:ignore="MissingTranslation"`; dodano `xmlns:tools` | B2, B4 |

## Zmienione pliki (testy)

| Plik | Dodane testy |
|------|--------------|
| `test/.../MaskCsvTest.kt` | +2: neutralizacja z wiodącą spacją; benign whitespace nietknięty (SEC2) |
| `test/.../CreateMaskedEmailViewModelTest.kt` | +2: unicode→`prefix_chars`, >64→`prefix_length` (B2); `@file:OptIn` (zero nowych warningów); update `prefixError`→`prefixErrorRes` |
| `test/.../MaskedEmailDetailViewModelTest.kt` | +2: edycja przetrwa reload po toggle; initial load seeduje pola (CC3) |
| `test/.../MaskedEmailListViewModelTest.kt` | +1: dwa refresh/klatka = jeden fetch (CC5) |
| `test/.../ProRepositoryImplTest.kt` | +1: buy-flow OK bez SKU emituje event terminalny (CC2) |

## Zmiany zachowania (do świadomości)

- **Logout/exitDemo przy błędzie zapisu:** jeśli `DataStore.edit`/`logout` rzuci (dysk/korupcja), korutyna jest przerwana i event nawigacyjny **nie** odpala — user zostaje na miejscu zamiast crashować lub nawigować na niespójnym stanie. Wcześniej: crash.
- **Detail edit + reload:** po zapisie/toggle pola edycji nie są już przeładowywane z serwera. Jeśli serwer znormalizował wartość (np. trim), UI pokaże wersję użytkownika (równą po trim) do następnego pełnego wejścia na ekran. Kosmetyczne, samonaprawialne.
- **Buy-flow edge (OK bez naszego SKU):** pokazuje teraz stan „nieudane" zamiast wisieć — rzadka ścieżka Play.
- **Pusty stan listy:** przy aktywnym filtrze/wyszukiwaniu bez wyników pokazuje „No matches" zamiast „Create a mask…".

## Potencjalne regresje / do manualnego QA

- Buy-flow na realnym urządzeniu (internal testing): potwierdź że spinner znika we wszystkich wynikach (Completed/Pending/Cancelled/Failed/edge).
- Detail: wpisz zmianę, tapnij toggle stanu — sprawdź że wpisany tekst nie znika.
- Lista: filtr „Disabled/Deleted" bez trafień → „No matches"; puste konto → „Create a mask".
- TalkBack: kropka stanu na wierszu ogłasza stan; pole search ma etykietę; „Sign in" w demo-bannerze jako Button.
- 4 nowe stringi wyświetlają się po angielsku poza en — **do przetłumaczenia na 19 języków**.

## Batch 2 — po decyzji Pawła (SEC1, R1, B5, R4)

| Plik | Zmiana | ID |
|------|--------|----|
| `data/billing/PurchaseSecurity.kt` (nowy) | Weryfikacja podpisu Play SHA1withRSA (java.util.Base64, testowalne na JVM) | SEC1 |
| `data/billing/PlayBillingDataSource.kt` | `isPurchased` wymaga ważnego podpisu; klucz z `BuildConfig.PLAY_LICENSE_KEY` | SEC1 |
| `app/build.gradle.kts` | `buildConfigField PLAY_LICENSE_KEY` z `fastmask.playLicenseKey` / env | SEC1 |
| `~/.gradle/gradle.properties` (poza repo) | `fastmask.playLicenseKey=<Base64 RSA>` pobrany z Play Console | SEC1 |
| `ui/theme/Color.kt` | `DarkAccentAmber #C9761F` | R1 |
| `ui/theme/StatusColors.kt`, `Theme.kt`, `AccentColors.kt` | Dark accent = DarkAccentAmber + dark-ink on-accent; amber spójny z resztą | R1 |
| `ui/components/ConfirmDialog.kt` (nowy) | Współdzielony dialog potwierdzenia (pill-button styling) | B5/R4 |
| `ui/create/CreateMaskedEmailScreen.kt`, `ui/detail/MaskedEmailDetailScreen.kt` | `BackHandler` + discard-changes dialog gdy dirty | B5 |
| `ui/settings/SettingsScreen.kt` | Logout confirm dialog | R4 |
| `res/values/strings.xml` | +6 stringów dialogów (`tools:ignore`, do przetłumaczenia) | B5/R4 |
| `test/.../PurchaseSecurityTest.kt` (nowy) | +5: valid/tampered/foreign-key/blank/malformed | SEC1 |

**SEC1 weryfikacja:** release build zielony, `BuildConfig.PLAY_LICENSE_KEY` niepusty (2048-bit RSA, potwierdzone `openssl`). Pusty klucz (dev/CI) → weryfikacja pominięta z ostrzeżeniem debug. **Do manualnego QA:** realny zakup na internal testing musi wciąż odblokowywać Pro (podpis prawdziwego zakupu jest ważny); sfałszowany — nie.

**Zmiana wizualna (R1):** amber FAB/pill w dark mode ma teraz ciemny tekst (jak pozostałe akcenty), nie parchment. Obejrzyj na urządzeniu.

## Czego NIE ruszano (świadomie, patrz UX_RECOMMENDATIONS.md D)

- R2/R3/R8/R9/R10 — polish/spójność, niskie priorytety.
