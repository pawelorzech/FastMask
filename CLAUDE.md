# CLAUDE.md

Last verified: 60b798c | 2026-10-26

Android app (Kotlin, Compose, Hilt, Retrofit) for Fastmail Masked Email over JMAP. Package `com.fastmask`, SDK compile/target 36, min 26, JDK 17-21 (see `AGENTS.md` for JDK and `google-services.json` caveats), Kotlin 2.2.20.

## Commands

```bash
./gradlew assembleDebug                      # debug APK -> app/build/outputs/apk/
./gradlew assembleRelease                    # R8-minified release
./gradlew testDebugUnitTest lintDebug        # fast verify gate, run after every change
./gradlew connectedDebugAndroidTest          # instrumented, needs emulator/device
```

## Architecture

Clean Architecture + MVVM under `app/src/main/java/com/fastmask/`: `data/` (JMAP client with cached session in `api/`, local storage, repositories), `domain/` (models, repository interfaces, use cases), `ui/` (feature screens + ViewModels, Compose, Material 3 dynamic colors), `di/` (Hilt: `NetworkModule`, `RepositoryModule`, billing/crash modules), `quickmask/` (notification/tile quick-create).

- State: `StateFlow<UiState>` for state, `SharedFlow<Event>` for one-shot events (navigation, logout).
- API: JMAP with Bearer token; session (accountId, apiUrl) cached after first call.
- Tokens live in EncryptedSharedPreferences. `TokenStorage` must stay lazily initialized, otherwise Hilt injection breaks.

## Gotchas

- 20 locales in `res/values-*/strings.xml`; in-app language switch uses AppCompatDelegate (no restart).
- Release uses R8; rules in `app/proguard-rules.pro` keep Kotlinx Serialization/JMAP models, Tink and Retrofit/OkHttp. Any new serialized model needs a keep check on a release build.

## Commit messages

`Add:` / `Fix:` / `Update:` / `Refactor:` prefix, then the description.
