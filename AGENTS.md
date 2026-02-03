# AGENTS.md

## Build & Test

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
