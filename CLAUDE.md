# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK (with ProGuard/R8 minification)
./gradlew assembleRelease

# Run unit tests
./gradlew test

# Run instrumented tests (requires emulator/device)
./gradlew connectedAndroidTest

# Clean build
./gradlew clean
```

APK outputs: `app/build/outputs/apk/`

## Project Configuration

- **SDK**: Compile/Target 34, Min 26
- **JDK**: 17
- **Kotlin**: 1.9.22
- **Package**: `com.fastmask`

## Architecture

Clean Architecture with MVVM pattern. Three distinct layers:

### Data Layer (`data/`)
- `api/` - JMAP protocol integration (Fastmail's native API)
  - `JmapApi.kt` - API client with session caching
  - `JmapService.kt` - Retrofit service interface
  - `JmapModels.kt` - Kotlinx Serialization models
- `local/` - Persistence
  - `TokenStorage.kt` - EncryptedSharedPreferences (lazy-initialized for Hilt compatibility)
  - `SettingsDataStore.kt` - DataStore for language preferences
- `repository/` - Repository implementations

### Domain Layer (`domain/`)
- `model/` - Domain models (`MaskedEmail`, `Language`, `EmailState` enum)
- `repository/` - Abstract interfaces
- `usecase/` - Business logic (Login, Logout, CRUD for masked emails, language settings)

### UI Layer (`ui/`)
- `auth/`, `list/`, `create/`, `detail/`, `settings/` - Feature screens with ViewModels
- `components/` - Reusable composables (MaskedEmailCard, ShimmerEffect, ErrorMessage)
- `navigation/` - Jetpack Navigation with shared element transitions
- `theme/` - Material 3 theming with dynamic colors

## Key Patterns

**State Management**: `StateFlow<UiState>` for reactive state, `SharedFlow<Event>` for one-time events (navigation, logout)

**Dependency Injection**: Hilt with `NetworkModule` (Retrofit, OkHttp, Json) and `RepositoryModule` (repository bindings)

**API Protocol**: JMAP (JSON Mail Access Protocol) with Bearer token auth. Session (accountId, apiUrl) is cached after first call.

**Security**: Tokens stored in EncryptedSharedPreferences using Android Security Crypto library. TokenStorage uses lazy initialization to prevent Hilt injection issues.

## Localization

20 languages supported. String resources in `res/values-*/strings.xml`. In-app language override uses AppCompatDelegate for runtime switching without restart.

## ProGuard/R8

Release builds use minification. Key rules in `app/proguard-rules.pro`:
- Keep Kotlinx Serialization and JMAP models
- Keep Google Tink classes (security-crypto dependency)
- Retrofit and OkHttp configurations

## Commit Message Format

- `Add: new feature description`
- `Fix: bug description`
- `Update: what was changed`
- `Refactor: what was refactored`
