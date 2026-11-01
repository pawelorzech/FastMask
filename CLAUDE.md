# CLAUDE.md

Last verified: redesign/m3-expressive | 2026-11-01

Android app (Kotlin, Compose, Hilt, Retrofit) for Fastmail Masked Email over JMAP. Package `com.fastmask`, compileSdk 37, targetSdk 36, minSdk 26, AGP 9.5 (built-in Kotlin 2.2.20, Hilt on KSP), Gradle 9.6, JDK 17-21 (see `AGENTS.md` for JDK and `google-services.json` caveats). UI is Material 3 Expressive on `material3:1.5.0-beta02` (pinned above the Compose BOM).

## Commands

```bash
./gradlew assembleDebug                      # debug APK -> app/build/outputs/apk/
./gradlew assembleRelease                    # R8-minified release
./gradlew testDebugUnitTest lintDebug        # fast verify gate, run after every change
./gradlew connectedDebugAndroidTest          # instrumented, needs emulator/device
```

## Architecture

Clean Architecture + MVVM under `app/src/main/java/com/fastmask/`: `data/` (JMAP client with cached session in `api/`, local storage, repositories), `domain/` (models, repository interfaces, use cases), `ui/` (feature screens + ViewModels, Compose; shared M3E pieces in `ui/components/expressive/`), `di/` (Hilt: `NetworkModule`, `RepositoryModule`, billing/crash modules), `quickmask/` (notification/tile quick-create).

- State: `StateFlow<UiState>` for state, `SharedFlow<Event>` for one-shot events (navigation, logout).
- API: JMAP with Bearer token; session (accountId, apiUrl) cached after first call.
- Tokens live in EncryptedSharedPreferences. `TokenStorage` must stay lazily initialized, otherwise Hilt injection breaks.

## Frontend Theme

- **Palette:** Material You. Wallpaper dynamic color on API 31+ (`Accent.DYNAMIC`, the default); Pro seeds Amber/Ink/Sage/Plum/Cobalt are generated schemes in `ui/theme/SeedSchemes.kt` (`bun tools/seed-schemes/gen.ts`), Amber is the < API 31 fallback. Use `MaterialTheme.colorScheme` roles only. Status: Active=primaryContainer, Pending=tertiaryContainer, Off=surfaceContainerHighest, Archived=errorContainer.
- **Type:** Google Sans Flex, bundled variable font (`res/font/google_sans_flex.ttf`, OFL; GMS downloadable fonts do NOT serve it). `*Emphasized` display/headline styles turn the ROND axis up.
- **Shapes:** M3 Expressive shape scale; identity comes from `MaterialShapes` (cookie, clover, sunny, burst) via `MaskAvatar`/`StateMorphAvatar`; grouped rows use `SegmentedRow` (20dp outer / 4dp inner, press morphs).
- **Density:** 16dp gutters, segmented lists with 2dp gaps, 56dp primary buttons, 48dp touch targets.
- **Motion:** `MaterialExpressiveTheme(motionScheme = MotionScheme.expressive())`; springs from `MaterialTheme.motionScheme` only, never `tween`. Nav = spring shared axis + predictive back; list row to detail is a container transform (`Modifier.maskContainer(id)`). Infinite shape morphs must go through Compose infinite-animation APIs so tests/accessibility can idle. Brand mark: Cookie @ icon (`tools/brand/icon.ts`).

## Gotchas

- 20 locales in `res/values-*/strings.xml`; in-app language switch uses AppCompatDelegate (no restart).
- Release uses R8; rules in `app/proguard-rules.pro` keep Kotlinx Serialization/JMAP models, Tink and Retrofit/OkHttp. Any new serialized model needs a keep check on a release build.

## Commit messages

`Add:` / `Fix:` / `Update:` / `Refactor:` prefix, then the description.
