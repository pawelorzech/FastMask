# AGENTS.md

## Build & Test

Requires JDK 17–21 — the Gradle 8.11.1 wrapper does not run on Java 26, so a machine whose
default `java` is 22+ must point `JAVA_HOME` at a supported JDK (Android Studio's
bundled JBR is one). Machine-specific paths belong in `~/.gradle/gradle.properties`,
never in the repo.

`app/google-services.json` is not in the repository and is not required: without
it the Firebase plugins are skipped and the app builds with crash reporting inert.

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

## Development

Open the project in Android Studio and run the `app` configuration on an emulator
or a device (API 26+). Android Studio supplies its own JDK, so no `JAVA_HOME`
setup is needed there.

From the command line, `./gradlew connectedDebugAndroidTest` runs the instrumented
suite against a booted emulator; `./gradlew testDebugUnitTest lintDebug` is the
fast pre-commit gate.
