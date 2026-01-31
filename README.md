<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.webp" width="100" alt="FastMask Logo">
</p>

<h1 align="center">FastMask</h1>

<p align="center">
  <strong>Native Android app for managing Fastmail masked emails</strong>
</p>

<p align="center">
  <a href="https://github.com/pawelorzech/FastMask/releases/latest">
    <img src="https://img.shields.io/github/v/release/pawelorzech/FastMask?style=flat-square" alt="Latest Release">
  </a>
  <a href="https://github.com/pawelorzech/FastMask/blob/main/LICENSE">
    <img src="https://img.shields.io/github/license/pawelorzech/FastMask?style=flat-square" alt="License">
  </a>
  <a href="https://developer.android.com/about/versions/oreo">
    <img src="https://img.shields.io/badge/API-26%2B-brightgreen?style=flat-square" alt="API 26+">
  </a>
  <a href="https://kotlinlang.org">
    <img src="https://img.shields.io/badge/Kotlin-100%25-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin">
  </a>
</p>

<p align="center">
  <a href="#features">Features</a> •
  <a href="#screenshots">Screenshots</a> •
  <a href="#installation">Installation</a> •
  <a href="#setup">Setup</a> •
  <a href="#tech-stack">Tech Stack</a> •
  <a href="#architecture">Architecture</a> •
  <a href="#contributing">Contributing</a>
</p>

---

## About

FastMask is a native Android application that lets you manage your [Fastmail](https://www.fastmail.com) masked email addresses directly from your phone. Masked emails are disposable addresses that forward to your real inbox, helping you protect your privacy and reduce spam.

## Features

| Feature | Description |
|---------|-------------|
| **View All Masks** | Browse your masked emails in a clean, searchable list |
| **Create New** | Generate new masked addresses with custom descriptions |
| **Enable/Disable** | Toggle masks on or off without deleting them |
| **Edit Details** | Update description, domain, and URL associations |
| **Quick Copy** | One-tap copy to clipboard |
| **Delete** | Remove masks you no longer need |
| **Search & Filter** | Find specific masks instantly |
| **Material You** | Dynamic theming that adapts to your wallpaper |

## Screenshots

<p align="center">
  <i>Screenshots coming soon</i>
</p>

<!--
<p align="center">
  <img src="docs/screenshots/list.png" width="200" alt="Email List">
  <img src="docs/screenshots/create.png" width="200" alt="Create Email">
  <img src="docs/screenshots/detail.png" width="200" alt="Email Detail">
</p>
-->

## Installation

### Download APK

1. Go to the [Releases](https://github.com/pawelorzech/FastMask/releases/latest) page
2. Download the latest APK file
3. Enable "Install from unknown sources" if prompted
4. Install the APK

### Build from Source

```bash
# Clone the repository
git clone https://github.com/pawelorzech/FastMask.git
cd FastMask

# Build debug APK
./gradlew assembleDebug

# Or build release APK
./gradlew assembleRelease
```

The APK will be generated in `app/build/outputs/apk/`

## Requirements

- Android 8.0 (API 26) or higher
- Fastmail account with API access

## Setup

### 1. Create a Fastmail API Token

1. Log in to [Fastmail](https://www.fastmail.com)
2. Navigate to **Settings** → **Privacy & Security** → **Integrations** → **API tokens**
3. Click **New API token**
4. Name it (e.g., "FastMask")
5. Select scope: **Masked Email** (read/write)
6. Copy the generated token

### 2. Log in to FastMask

1. Open the app
2. Paste your API token
3. Tap "Log in"

Your token is stored securely using Android's EncryptedSharedPreferences.

## Tech Stack

| Category | Technology |
|----------|------------|
| **Language** | [Kotlin](https://kotlinlang.org/) 100% |
| **UI Framework** | [Jetpack Compose](https://developer.android.com/jetpack/compose) |
| **Design System** | [Material 3](https://m3.material.io/) with dynamic theming |
| **DI** | [Hilt](https://dagger.dev/hilt/) |
| **Networking** | [Retrofit](https://square.github.io/retrofit/) + [OkHttp](https://square.github.io/okhttp/) |
| **Serialization** | [Kotlinx Serialization](https://github.com/Kotlin/kotlinx.serialization) |
| **Async** | [Coroutines](https://kotlinlang.org/docs/coroutines-overview.html) + [Flow](https://kotlinlang.org/docs/flow.html) |
| **API Protocol** | [JMAP](https://jmap.io/) (Fastmail's native protocol) |

## Architecture

The app follows **Clean Architecture** principles with **MVVM** pattern:

```
app/
├── data/                    # Data layer
│   ├── api/                 # JMAP API service & models
│   ├── local/               # Secure token storage
│   └── repository/          # Repository implementations
│
├── domain/                  # Business logic layer
│   ├── model/               # Domain models
│   ├── repository/          # Repository interfaces
│   └── usecase/             # Use cases
│
├── di/                      # Dependency injection modules
│
└── ui/                      # Presentation layer
    ├── auth/                # Login screen
    ├── list/                # Masked email list
    ├── create/              # Create new mask
    ├── detail/              # View/edit mask details
    ├── components/          # Reusable UI components
    ├── navigation/          # Navigation setup
    └── theme/               # Material 3 theming
```

## Privacy & Security

- **Local Storage Only**: Your API token is stored locally using Android's [EncryptedSharedPreferences](https://developer.android.com/reference/androidx/security/crypto/EncryptedSharedPreferences)
- **Direct API Communication**: The app communicates directly with Fastmail's servers - no intermediary servers
- **No Tracking**: Zero analytics, telemetry, or data collection
- **Open Source**: Full source code available for audit

## Contributing

Contributions are welcome! Here's how you can help:

1. **Fork** the repository
2. **Create** a feature branch (`git checkout -b feature/amazing-feature`)
3. **Commit** your changes (`git commit -m 'Add amazing feature'`)
4. **Push** to the branch (`git push origin feature/amazing-feature`)
5. **Open** a Pull Request

### Development Setup

1. Install [Android Studio](https://developer.android.com/studio) (latest stable)
2. Clone the repository
3. Open the project in Android Studio
4. Sync Gradle and run on an emulator or device

### Code Style

- Follow [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use meaningful commit messages
- Write tests for new features when applicable

## Changelog

### v1.1 (January 2026)
- **Fixed**: ProGuard/R8 minification crash with `ParameterizedType` casting error
- **Improved**: Added proper ProGuard rules for Google Tink (security-crypto dependency)
- **Stability**: Release builds now work correctly with code minification enabled

### v1.0 (Initial Release)
- Manage Fastmail masked emails
- Create, edit, enable/disable, and delete masks
- Material 3 dynamic theming
- Secure token storage with EncryptedSharedPreferences

## Roadmap

- [ ] Add screenshots to README
- [ ] Biometric authentication option
- [ ] Widget for quick mask creation
- [ ] Export/import functionality
- [ ] Dark/light mode toggle
- [ ] Localization support

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- [Fastmail](https://www.fastmail.com) for their excellent email service and JMAP API
- [JMAP](https://jmap.io/) for the open standard specification
- The Android and Kotlin communities for amazing tools and libraries

---

<p align="center">
  Made with Kotlin and Jetpack Compose
</p>

<p align="center">
  <a href="https://github.com/pawelorzech/FastMask/issues">Report Bug</a> •
  <a href="https://github.com/pawelorzech/FastMask/issues">Request Feature</a>
</p>
