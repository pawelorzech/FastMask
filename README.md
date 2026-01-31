# FastMask

A native Android app for managing Fastmail masked emails. Create, view, edit, and manage your masked email addresses directly from your Android device.

## Features

- **View Masked Emails** - Browse all your Fastmail masked email addresses in a clean, organized list
- **Create New Masks** - Generate new masked email addresses with optional descriptions and domain associations
- **Enable/Disable** - Toggle masked emails on or off without deleting them
- **Edit Details** - Update description, associated domain, and URL for any masked email
- **Copy to Clipboard** - Quickly copy email addresses with one tap
- **Delete** - Remove masked emails you no longer need
- **Search** - Filter your masked emails to find what you need
- **Material You** - Modern Material 3 design with dynamic theming support

## Screenshots

*Coming soon*

## Requirements

- Android 8.0 (API 26) or higher
- Fastmail account with API access

## Installation

### From GitHub Releases

1. Download the latest APK from the [Releases](https://github.com/pawelorzech/FastMask/releases) page
2. Enable "Install from unknown sources" for your browser or file manager
3. Open the APK file to install

### Build from Source

1. Clone the repository:
   ```bash
   git clone https://github.com/pawelorzech/FastMask.git
   ```

2. Open the project in Android Studio

3. Build and run on your device or emulator

## Setup

1. Create a Fastmail API token:
   - Log in to [Fastmail](https://www.fastmail.com)
   - Go to **Settings** → **Privacy & Security** → **Integrations** → **API tokens**
   - Click **New API token**
   - Give it a name (e.g., "FastMask")
   - Select the scope: **Masked Email** (read/write)
   - Copy the generated token

2. Open FastMask and paste your API token to log in

## Tech Stack

- **Kotlin** - 100% Kotlin codebase
- **Jetpack Compose** - Modern declarative UI
- **Material 3** - Latest Material Design components
- **Hilt** - Dependency injection
- **Coroutines & Flow** - Asynchronous programming
- **Retrofit + OkHttp** - Network communication
- **Kotlinx Serialization** - JSON parsing
- **JMAP Protocol** - Fastmail's native API

## Architecture

The app follows Clean Architecture principles with MVVM pattern:

```
app/
├── data/           # Data layer (API, repositories)
├── domain/         # Business logic (use cases, models)
├── di/             # Dependency injection modules
└── ui/             # Presentation layer (screens, viewmodels)
```

## Privacy

- Your API token is stored securely using Android's EncryptedSharedPreferences
- The app communicates directly with Fastmail's API - no third-party servers
- No analytics or tracking

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

This project is open source. See the [LICENSE](LICENSE) file for details.

## Acknowledgments

- [Fastmail](https://www.fastmail.com) for their excellent JMAP API
- [JMAP](https://jmap.io) specification for masked emails
