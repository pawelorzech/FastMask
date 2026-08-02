<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png" width="112" alt="FastMask app icon">
</p>

<h1 align="center">FastMask</h1>

<p align="center">
  <strong>A privacy-first Android client for Fastmail Masked Email.</strong><br>
  Create, find, edit, pause, archive, and restore masked addresses without opening Fastmail in a browser.
</p>

<p align="center">
  <a href="https://github.com/pawelorzech/FastMask/actions/workflows/build.yml"><img src="https://img.shields.io/github/actions/workflow/status/pawelorzech/FastMask/build.yml?branch=main&style=flat-square&label=build" alt="Build status"></a>
  <a href="https://github.com/pawelorzech/FastMask/releases/latest"><img src="https://img.shields.io/github/v/release/pawelorzech/FastMask?style=flat-square" alt="Latest packaged release"></a>
  <a href="https://github.com/pawelorzech/FastMask/blob/main/LICENSE"><img src="https://img.shields.io/github/license/pawelorzech/FastMask?style=flat-square" alt="MIT License"></a>
  <a href="https://developer.android.com/about/versions/oreo"><img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white" alt="Android 8.0 or newer"></a>
  <a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/Kotlin-2.2.20-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin 2.2.20"></a>
</p>

<p align="center">
  <a href="#what-it-does">Features</a> ·
  <a href="#getting-started">Getting started</a> ·
  <a href="#privacy-and-security">Privacy</a> ·
  <a href="#building-the-project">Build</a> ·
  <a href="#architecture">Architecture</a>
</p>

> [!NOTE]
> FastMask is an independent, open-source project. It is not affiliated with or endorsed by Fastmail.

## What it does

FastMask talks directly to Fastmail's JMAP API using a token limited to the **Masked Email** permission. The current source tree builds app version **1.10.1** (`versionCode 22`); downloadable GitHub releases may trail the source.

### Core features

- Browse masks in a searchable list, sorted by latest activity.
- Filter by **All**, **Active**, **Off**, or **Archived**, with live counts.
- Create a random address or choose an optional prefix, site/domain, note, URL, and initial state.
- Copy an address from the list or detail screen.
- Edit metadata and switch a mask between Active and Off.
- Archive a mask so new mail bounces, then restore it with Undo.
- Keep reading the last successful, encrypted account snapshot while offline.
- Share a link or text to FastMask to open a pre-filled creation form.
- Create a mask from a Quick Settings tile or long-press launcher shortcut; the address is copied to the clipboard and the confirmation notification offers Undo.
- Explore the complete UI in a local demo mode without a Fastmail token. Demo changes are never saved.
- Use the app in 20 languages, including RTL support, with an in-app language picker.
- Follow the system light/dark theme with an accessible warm-ink design system.

### FastMask Pro

FastMask Pro is an optional one-time Google Play purchase. Existing core functionality stays free; Pro adds:

- five accent themes: Amber, Ink, Sage, Plum, and Cobalt;
- an optional biometric/device-credential app lock;
- CSV export of all masks through Android's system share sheet.

The Pro surface can be disabled at build time with the monetization kill switch. A signed release build also requires a Play licensing public key so purchase signatures cannot be accepted without verification.

## Getting started

### Requirements

- Android 8.0 (API 26) or newer;
- a Fastmail account with access to Masked Email;
- a Fastmail API token with the **Masked Email** permission.

### Create a Fastmail API token

1. Sign in to [Fastmail on the web](https://app.fastmail.com/settings/security/tokens).
2. Open **Settings → Privacy & Security**.
3. In **Connected apps & API tokens**, choose **Manage API tokens**.
4. Create a new token and select only **Masked Email**.
5. Copy the token before closing the Fastmail dialog.

FastMask's sign-in screen includes the same walkthrough, a direct link to Fastmail's token settings, and a paste action. Tokens without the required scope are rejected with a specific error.

### Install an APK

1. Open [GitHub Releases](https://github.com/pawelorzech/FastMask/releases/latest).
2. Download the APK attached to the release.
3. Allow installation from that source if Android asks.
4. Install FastMask and paste the API token.

The packaged release can lag behind the version on `main`. To run the current source, build a debug APK locally.

## Privacy and security

FastMask is designed around a narrow data path:

```text
FastMask on your device ── JMAP over HTTPS ── Fastmail
          │
          └── optional crash diagnostics ── Firebase Crashlytics
```

- **No intermediary backend:** mask operations go directly to Fastmail.
- **Encrypted local secrets:** the API token uses `EncryptedSharedPreferences`; the offline snapshot uses `EncryptedFile`. Both are backed by Android Keystore.
- **Account-scoped offline data:** cached masks are bound to the account and removed on sign-out.
- **No cloud backup:** Android backup and device-transfer backup are disabled for app data.
- **Hardened transport:** cleartext traffic is disabled, Fastmail API traffic trusts system certificate authorities only, and server-provided JMAP URLs are restricted to Fastmail hosts before receiving the token.
- **Protected release UI:** release builds block screenshots, screen recording, Recents previews, and obscured-touch interactions.
- **Sensitive clipboard data:** Android 13+ is told that copied masked addresses are sensitive.
- **No ads or behavioural analytics:** there is no analytics SDK or screen/event tracking in production.
- **Optional crash reports:** configured release builds can send technical crash diagnostics to Firebase Crashlytics. Collection is on by default, can be disabled in **Settings → Crash reports**, never runs in debug builds, and the app has no API for attaching masks, descriptions, domains, email addresses, or tokens to reports.
- **Explicit plaintext export:** CSV export is a Pro action with a privacy confirmation. Exports live in app cache, are shared with a one-time URI grant, and are cleaned up after one hour.

See the full [privacy policy](https://pawelorzech.github.io/FastMask/privacy.html) and [security policy](SECURITY.md). Please report vulnerabilities privately using the process in `SECURITY.md`, not a public issue.

## Building the project

### Prerequisites

- Android Studio with Android SDK 36;
- JDK 17–21. Android Studio's bundled JBR is suitable;
- no Firebase project for a normal local or CI build.

The project supports JDK 17–21; in particular, the Gradle 8.11.1 wrapper does not run on Java 26. If your system Java is 22 or newer, point `JAVA_HOME` at a supported JDK or put `org.gradle.java.home` in your personal `~/.gradle/gradle.properties`. Never commit a machine-specific JDK path.

```bash
git clone https://github.com/pawelorzech/FastMask.git
cd FastMask

# Fast pre-commit gate
./gradlew testDebugUnitTest lintDebug

# Debug APK
./gradlew assembleDebug

# Minified, unsigned release smoke build
./gradlew assembleRelease
```

APK outputs are written to `app/build/outputs/apk/`.

`app/google-services.json` is intentionally absent from the repository. When it is missing, the Firebase Gradle plugins are skipped and crash reporting is inert; the rest of the app builds and runs normally. Maintainers can add their own Firebase configuration outside version control for instrumented release builds.

### Tests

```bash
# JVM unit tests
./gradlew testDebugUnitTest

# Android lint
./gradlew lintDebug

# Instrumented tests on a booted emulator/device
./gradlew connectedDebugAndroidTest

# Full Gradle test lifecycle
./gradlew test
```

CI runs unit tests, lint, and a minified release build for every pull request. The test suite covers JMAP mapping, authentication and scope validation, repositories, offline encryption flows, share routing, Quick Mask and Undo policies, billing verification, CSV hardening, translations, privacy boundaries, ViewModels, accessibility semantics, and end-to-end Compose flows.

### Signed release configuration

Release signing material belongs outside the repository. FastMask reads it from environment variables or personal Gradle properties:

| Purpose | Environment variable | Gradle property |
|---|---|---|
| Keystore path | `FASTMASK_KEYSTORE` | `fastmask.keystore` |
| Store password | `FASTMASK_STORE_PWD` | `fastmask.storePassword` |
| Key alias | `FASTMASK_KEY_ALIAS` | `fastmask.keyAlias` |
| Key password | `FASTMASK_KEY_PWD` | `fastmask.keyPassword` |
| Play licensing public key | `FASTMASK_PLAY_LICENSE_KEY` | `fastmask.playLicenseKey` |

A release without signing configuration is left unsigned for external signing. A signed `assembleRelease` or `bundleRelease` fails early when the Play licensing key is missing.

## Architecture

FastMask is a single-module Android application using layered Clean Architecture and MVVM:

```text
app/src/main/java/com/fastmask/
├── data/       JMAP/Retrofit API, encrypted storage, billing, crash reporting,
│               demo data, and repository implementations
├── domain/     models, repository contracts, use cases, share/crash policies
├── ui/         Compose screens, navigation, ViewModels, theme, accessibility
├── quickmask/  Quick Settings tile, launcher shortcut, notifications, Undo
└── di/         Hilt dependency graph and dispatcher bindings
```

| Area | Implementation |
|---|---|
| Language/toolchain | Kotlin 2.2.20, Java 17 bytecode, Gradle 8.11.1, AGP 8.10.1 |
| UI | Jetpack Compose, Material 3 primitives, adaptive layouts, custom design system |
| State/navigation | ViewModels, Kotlin Coroutines and Flow, Navigation Compose |
| Dependency injection | Hilt 2.58 |
| Networking | Retrofit 3, OkHttp 4, Kotlinx Serialization, Fastmail JMAP |
| Persistence | AndroidX Security Crypto, DataStore Preferences |
| Monetization | Google Play Billing 8.3.0 with RSA purchase verification |
| Diagnostics | Optional Firebase Crashlytics; Firebase Analytics is not included |

The server is the source of truth. Local storage is limited to the API token, preferences, verified Pro entitlement, temporary exports, and the encrypted last-known-good mask snapshot.

## Contributing

Contributions are welcome. Before opening a pull request:

1. Read [CONTRIBUTING.md](CONTRIBUTING.md).
2. Branch from `main`.
3. Keep user-facing text localized across all supported languages.
4. Run `./gradlew testDebugUnitTest lintDebug`.
5. Run instrumented tests when changing navigation, Compose semantics, storage encryption, or device integrations.

For user-visible changes, include screenshots or a short recording and explain what was tested on a real device or emulator.

## Project documentation

- [Changelog](CHANGELOG.md) — complete release history
- [Contributing guide](CONTRIBUTING.md) — workflow and coding expectations
- [Security policy](SECURITY.md) — threat model and vulnerability reporting
- [Privacy policy](https://pawelorzech.github.io/FastMask/privacy.html) — data handling for users
- [Release checklist](Plans/release-checklist.md) — maintainer release process

## License

FastMask is available under the [MIT License](LICENSE).

---

<p align="center">
  Built with Kotlin, Jetpack Compose, and the Fastmail JMAP API.
</p>
