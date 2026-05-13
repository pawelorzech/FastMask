# Demo Mode — interaktywny tutorial bez konta Fastmail

**Status:** plan, 2026-05-13
**Cel:** dodać "Try demo" CTA na ekranie startowym + interaktywny tryb z mock data + coach marks tutorial. Ten sam tryb służy: (1) nowym userom jako onboarding, (2) Google Play reviewerowi zamiast test creds, (3) jako źródło screenshotów do Play Store listing.

## Decyzje (zatwierdzone przez Pawła 2026-05-13)

1. **Interaktywny demo** — mocked CRUD w pamięci sesji (utwórz/edytuj/disable/archive). Toast banner: "Demo mode — sign in to save changes".
2. **Coach marks na live UI** (nie carousel). Spotlight overlay + tooltip; 5-6 stepów po pierwszym entry; reusable composable `TutorialOverlay`.
3. **Sekwencja:** Engineer #1 (data layer) → Engineer #2 (UI + coach marks) → screenshots → Play Console finalize.

## Architektura

### A. SettingsDataStore — nowe flagi

Plik: `app/src/main/java/com/fastmask/data/local/SettingsDataStore.kt:20-54`

Dodaj:
- `enum class AppMode { REAL, DEMO }` (w domain/model/, nowy plik `AppMode.kt`)
- `appModeKey: Preferences.Key<String>` = `stringPreferencesKey("app_mode")`, default `"REAL"`
- `tutorialCompletedKey: Preferences.Key<Boolean>` = `booleanPreferencesKey("tutorial_completed")`, default `false`
- Public API:
  - `val appMode: Flow<AppMode>`
  - `val tutorialCompleted: Flow<Boolean>`
  - `suspend fun setAppMode(mode: AppMode)`
  - `suspend fun setTutorialCompleted(done: Boolean)`
  - `fun appModeBlocking(): AppMode` — sync getter dla MainActivity start destination logic

### B. AuthRepository — `isLoggedIn` widzi demo jako logged-in

Plik: `app/src/main/java/com/fastmask/data/repository/AuthRepositoryImpl.kt:26-28`

Zmiana:
```kotlin
override fun isLoggedIn(): Boolean =
    tokenStorage.hasToken() || settingsDataStore.appModeBlocking() == AppMode.DEMO
```
Inject `SettingsDataStore` przez constructor (jest już Singleton scope).

Plik logout `AuthRepositoryImpl.kt:21-24`:
- Po logout, jeśli był w DEMO mode → set `appMode = REAL` (czystka)
- Plus opcjonalnie `tutorialCompleted = false` żeby kolejne demo entry pokazało tutorial od nowa

### C. Repository — DemoMaskedEmailRepository + dispatch

Architektura: **single binding `MaskedEmailRepository` to dispatch wrapper** który deleguje do real lub demo w runtime na podstawie `appMode`. Eliminuje to potrzebę zmiany 4 ViewModeli.

Pliki do utworzenia:
- `app/src/main/java/com/fastmask/data/repository/DemoMaskedEmailRepositoryImpl.kt` (nowy) — in-memory `MutableStateFlow<List<MaskedEmail>>` z `INITIAL_DEMO_MASKS` constant. Implements `MaskedEmailRepository` interface (suspend funkcje, Result<T> jak `MaskedEmailRepositoryImpl`).
- `app/src/main/java/com/fastmask/data/repository/MaskedEmailRepositoryDispatcher.kt` (nowy) — implements `MaskedEmailRepository`, constructor inject:
  - `@Named("real") realRepo: MaskedEmailRepository` (rename existing `MaskedEmailRepositoryImpl`)
  - `@Named("demo") demoRepo: MaskedEmailRepository`
  - `settingsDataStore: SettingsDataStore`
  
  Każda metoda: `if (settingsDataStore.appModeBlocking() == AppMode.DEMO) demoRepo.X() else realRepo.X()`

Plik: `app/src/main/java/com/fastmask/di/RepositoryModule.kt:15-28` — zmień:
```kotlin
@Binds @Singleton @Named("real")
abstract fun bindRealRepo(impl: MaskedEmailRepositoryImpl): MaskedEmailRepository

@Binds @Singleton @Named("demo")
abstract fun bindDemoRepo(impl: DemoMaskedEmailRepositoryImpl): MaskedEmailRepository

@Binds @Singleton
abstract fun bindDispatcher(dispatcher: MaskedEmailRepositoryDispatcher): MaskedEmailRepository
```

Wait — multiple `@Binds` dla tego samego interface = ambiguity bez qualifier. Dispatcher musi mieć **brak qualifiera** (default), a oba implementacje mają qualifier. Sprawdzić czy Hilt akceptuje.

**Alternatywa prostsza:** zaimplementować dispatcher jako `@Provides` (nie `@Binds`) w obiekcie Companion module — programowo skomponuj.

### D. Demo data — `INITIAL_DEMO_MASKS`

Plik: `app/src/main/java/com/fastmask/data/demo/DemoMaskedEmails.kt` (nowy)

10 masek pokrywających range stanów. Realistyczne nazwy + opisy:

| # | email | description | domain | state | received | last activity |
|---|-------|-------------|--------|-------|----------|---------------|
| 1 | quiet.harbor942@fastmask.com | Amazon | amazon.com | ENABLED | 247 | dziś |
| 2 | blue.morning315@fastmask.com | Stripe receipts | stripe.com | ENABLED | 89 | wczoraj |
| 3 | calm.river078@fastmask.com | Notion | notion.so | ENABLED | 34 | 3 dni temu |
| 4 | gentle.bridge501@fastmask.com | Vercel deployments | vercel.com | ENABLED | 156 | tydzień temu |
| 5 | warm.silk862@fastmask.com | Dropbox trial | dropbox.com | ENABLED | 12 | 2 tyg. temu |
| 6 | bright.echo284@fastmask.com | Newsletter signup | substack.com | ENABLED | 73 | 5 dni temu |
| 7 | clever.path619@fastmask.com | Hetzner | hetzner.com | ENABLED | 8 | miesiąc temu |
| 8 | swift.cloud447@fastmask.com | Groupon | groupon.com | DISABLED | 2103 | 4 mies. temu |
| 9 | mellow.frost792@fastmask.com | Old Airbnb account | airbnb.com | DELETED (archived) | 56 | 6 mies. temu |
| 10 | sharp.flame138@fastmask.com | Quick test | — | ENABLED | 0 | nigdy |

Każda maska to `MaskedEmail` data class (sprawdzić shape w `domain/model/MaskedEmail.kt`).

### E. WelcomeScreen — nowy ekran przed LoginScreen

Plik: `app/src/main/java/com/fastmask/ui/welcome/WelcomeScreen.kt` (nowy katalog `ui/welcome/`)

Layout (Compose, Material 3, design system z LoginScreen):
- Ikona FastMask + headline ("FastMask")
- Tagline ("A quiet place for your masked addresses." — istnieje w strings)
- Spacer
- **Primary CTA** "Sign in with Fastmail" → nav do `LOGIN`
- **Secondary CTA** (TextButton lub OutlinedButton) "Try demo" → setAppMode(DEMO) + setTutorialCompleted(false) + LoginSuccess event → nav `EMAIL_LIST`
- Privacy note small text (link do privacy.html, optional)

ViewModel: `WelcomeViewModel.kt` z `enterDemoMode()` które:
1. `settingsDataStore.setAppMode(AppMode.DEMO)`
2. `settingsDataStore.setTutorialCompleted(false)`
3. Emit `WelcomeEvent.EnterDemo` → nav idzie do `EMAIL_LIST`

### F. Navigation update

Plik: `app/src/main/java/com/fastmask/ui/navigation/FastMaskNavHost.kt`

- Dodaj `NavRoutes.WELCOME` na początku
- Start destination logic w `MainActivity.kt:43-47`:
  ```kotlin
  val start = when {
      authRepository.isLoggedIn() -> NavRoutes.EMAIL_LIST
      else -> NavRoutes.WELCOME  // było: LOGIN
  }
  ```
- WelcomeScreen → po "Sign in" idzie do LOGIN; po "Try demo" idzie do EMAIL_LIST z popUpTo(WELCOME, inclusive=true)
- LOGIN screen zostaje bez zmian; "Back" z LOGIN wraca do WELCOME

### G. Demo banner

Plik: `app/src/main/java/com/fastmask/ui/components/DemoBanner.kt` (nowy)

Composable, observable `appMode` from SettingsDataStore via Hilt. Pokazuje się tylko gdy `appMode == DEMO`:
- Wysokość ~40dp, kolor `MaterialTheme.colorScheme.tertiaryContainer` (subtelne)
- Tekst: "Demo mode — changes won't be saved" + small "Sign in" link
- Click sign-in → setAppMode(REAL) → clearToken (no-op bo i tak nie ma) → nav do LOGIN

Wstaw w 3 ekranach:
- `MaskedEmailListScreen`: jako top banner pod toolbar
- `MaskedEmailDetailScreen`: jako top banner pod toolbar
- `CreateMaskedEmailScreen`: jako top banner

### H. TutorialOverlay — coach marks

Plik: `app/src/main/java/com/fastmask/ui/components/TutorialOverlay.kt` (nowy)

API:
```kotlin
@Composable
fun TutorialOverlay(
    steps: List<TutorialStep>,
    visible: Boolean,
    onComplete: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
)

data class TutorialStep(
    val title: String,
    val description: String,
    val targetBounds: Rect,  // pos & size na ekranie
    val tooltipPosition: TooltipPosition  // TOP, BOTTOM, AUTO
)
```

Implementacja:
- `Box(Modifier.fillMaxSize())` zawierający `Canvas(scrim)` z `clearBlend(targetBounds)` (rectangular cutout)
- `Surface` z tooltipem (title + description + "Next" / "Skip" / "Done")
- Animation: AnimatedVisibility, ~300ms cross-fade między stepami
- Brak external library — proste Compose canvas + offsets

Implementacja w MaskedEmailListScreen:
- Po composition: `LaunchedEffect(appMode, tutorialCompleted) { if (appMode == DEMO && !tutorialCompleted) showTutorial = true }`
- Stepy hard-coded z `stringResource(R.string.tutorial_step_1_title)` etc.
- 5 stepów (uproszczone z 6):
  1. **Lista**: "This is your masked emails. Each one is a real forwarding address that protects your inbox."
  2. **Search bar**: "Search across all your masks by name or domain."
  3. **Filter chips**: "Filter by Active / Off / Archived."
  4. **Tap card** (autoclose+navigate): "Tap a mask to see message stats and copy the address."
  5. **FAB (+)**: "Tap here to create a new mask for any service."
- Po stepie 5: `setTutorialCompleted(true)`, hide overlay

Implementacja `targetBounds`: każdy element używa `Modifier.onGloballyPositioned { coords -> tutorialBounds[i] = coords.boundsInRoot() }`. ViewModel/state holder zbiera te bounds, przekazuje do `TutorialOverlay`.

### I. SettingsScreen — wyjście z demo

Plik: `app/src/main/java/com/fastmask/ui/settings/SettingsScreen.kt`

Dodaj w demo mode:
- Sekcja "Account" pokazuje "Demo mode" + button "Sign in with Fastmail" → setAppMode(REAL) + clearToken + nav LOGIN
- "Reset tutorial" button (useful do screenshotów) — opcjonalne

### J. Strings (EN + PL na start, reszta deferred)

Plik: `app/src/main/res/values/strings.xml` (base / EN), `values-pl/strings.xml`

Nowe klucze:
- `welcome_signin_cta` = "Sign in with Fastmail" / "Zaloguj się przez Fastmail"
- `welcome_demo_cta` = "Try demo" / "Wypróbuj demo"
- `welcome_tagline` = (reuse `login_subtitle` jeśli pasuje)
- `demo_banner_text` = "Demo mode — changes won't be saved" / "Tryb demo — zmiany nie są zapisywane"
- `demo_banner_signin` = "Sign in" / "Zaloguj"
- `tutorial_step_1_title` = "Your masked emails" / "Twoje maskowane adresy"
- `tutorial_step_1_body` = "Each address forwards mail to your inbox while hiding your real email." / "Każdy adres przekierowuje wiadomości do twojej skrzynki, ukrywając prawdziwy email."
- (analogicznie 2-5)
- `tutorial_next` = "Next" / "Dalej"
- `tutorial_skip` = "Skip" / "Pomiń"
- `tutorial_done` = "Got it" / "Rozumiem"
- `settings_signin_with_fastmail` = "Sign in with Fastmail" / "Zaloguj się przez Fastmail"
- `settings_demo_section` = "Account" / "Konto"

Pozostałe 18 lokalizacji — dodać jako placeholder z komentarzem `<!-- TODO: translate -->` lub zostawić bez wpisu (Android fallback do default EN).

## Pliki do utworzenia

```
app/src/main/java/com/fastmask/
├── domain/model/AppMode.kt                              # NEW enum
├── data/local/SettingsDataStore.kt                       # EDIT: dodaj appMode + tutorialCompleted
├── data/repository/AuthRepositoryImpl.kt                 # EDIT: isLoggedIn(), logout()
├── data/repository/DemoMaskedEmailRepositoryImpl.kt      # NEW
├── data/repository/MaskedEmailRepositoryDispatcher.kt    # NEW (router)
├── data/demo/DemoMaskedEmails.kt                         # NEW (mock data)
├── di/RepositoryModule.kt                                # EDIT: qualifiers
├── ui/welcome/WelcomeScreen.kt                           # NEW
├── ui/welcome/WelcomeViewModel.kt                        # NEW
├── ui/components/DemoBanner.kt                           # NEW
├── ui/components/TutorialOverlay.kt                      # NEW
├── ui/navigation/FastMaskNavHost.kt                      # EDIT: dodaj WELCOME
├── ui/list/MaskedEmailListScreen.kt                      # EDIT: banner + tutorial integration
├── ui/detail/MaskedEmailDetailScreen.kt                  # EDIT: banner
├── ui/create/CreateMaskedEmailScreen.kt                  # EDIT: banner
├── ui/settings/SettingsScreen.kt                         # EDIT: demo sign-in button
├── MainActivity.kt                                       # EDIT: start destination logic
└── (strings.xml + values-pl/strings.xml)                 # EDIT: nowe klucze
```

## Engineer dispatch plan

**Engineer #1 — Data layer (Task #8):**
- Cel: po Engineerze build kompiluje, app w demo mode pokazuje 10 mock masek na liście, można je tworzyć/edytować/disable/archive (in-memory), logout wraca do welcome.
- Zakres: A, B, C, D + minimalna F (nav skeleton)
- Verification: ręczne uruchomienie z hard-coded `appMode = DEMO` w MainActivity (override), sprawdzenie że lista pokazuje 10 masek, CRUD działa, logout czyści.

**Engineer #2 — UI layer (Task #9):**
- Cel: WelcomeScreen jest pierwszym ekranem, "Try demo" działa, banner pokazuje się w demo, tutorial z 5 coach marks działa na MaskedEmailListScreen po pierwszym entry.
- Zakres: E, F (pełne), G, H, I, J
- Verification: full user flow — fresh install → WelcomeScreen → "Try demo" → tutorial 5 stepów → list z 10 maskami + banner → "Sign in" w settings → LOGIN screen.

## Screenshots (Task #10)

Po Engineer #2:
- Pixel 6 emulator (1080×2400, API 34), Pixel Tablet (2560×1600)
- Język EN + PL via Settings → Language
- 6 zrzutów per locale per device = ~24 plików:
  1. WelcomeScreen (hero — pokazuje obie CTA)
  2. Lista masek (po tutorialu, demo banner u góry)
  3. Detail screen (rich activity stats)
  4. Create mask form
  5. Search + filter (pokazuje active/disabled/archived)
  6. Settings (z language picker + "Sign in" button)
- Output: `marketing/play/screenshots/{phone,tablet}/{en,pl}/<num>-<screen>.png`

## Play Console implications (Task #11)

Po wgraniu screenshotów:
- **App access** → "All functionality available without any access restrictions" (demo = no creds needed for Google reviewer)
- **Target audience** → 18+, declarations OK
- **Data safety** → submit (już draft)
- **Store listing** → upload icon 512, feature graphic, 8 phone screenshots (EN default, PL i18n)

Cały Faza 6 zamyka się 11/11.

## Verification end-to-end

1. [ ] `./gradlew assembleDebug` kompiluje
2. [ ] `./gradlew test` zielony
3. [ ] Fresh install → WelcomeScreen, dwa CTA
4. [ ] "Try demo" → list z 10 maskami + tutorial 5 stepów + banner
5. [ ] Tap mask → detail; create mask → in-memory append; disable → status zmienia się
6. [ ] Settings → "Sign in with Fastmail" → LOGIN; login z prawdziwym tokenem → real data
7. [ ] Logout z real → wraca do Welcome (nie Login); demo flag czysty
8. [ ] adb screencap × 24 screenów (po Engineer #2)
9. [ ] Play Console: app access "no restrictions" accepted, store listing assets uploaded
