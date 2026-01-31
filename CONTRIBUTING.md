# Contributing to FastMask

First off, thank you for considering contributing to FastMask! It's people like you that make FastMask a great tool for managing Fastmail masked emails.

## Code of Conduct

By participating in this project, you agree to maintain a respectful and inclusive environment for everyone.

## How Can I Contribute?

### Reporting Bugs

Before creating bug reports, please check existing issues to avoid duplicates. When you create a bug report, include as many details as possible:

- **Use a clear and descriptive title**
- **Describe the exact steps to reproduce the problem**
- **Describe the behavior you observed and what you expected**
- **Include your Android version and device model**
- **Include screenshots if applicable**

### Suggesting Features

Feature suggestions are welcome! Please:

- **Use a clear and descriptive title**
- **Provide a detailed description of the proposed feature**
- **Explain why this feature would be useful**
- **Include mockups or examples if possible**

### Pull Requests

1. Fork the repository
2. Create a feature branch from `main`:
   ```bash
   git checkout -b feature/your-feature-name
   ```
3. Make your changes
4. Test your changes thoroughly
5. Commit with a meaningful message:
   ```bash
   git commit -m "Add: description of your changes"
   ```
6. Push to your fork:
   ```bash
   git push origin feature/your-feature-name
   ```
7. Open a Pull Request

## Development Setup

### Prerequisites

- [Android Studio](https://developer.android.com/studio) (latest stable version)
- JDK 17 or higher
- Android SDK with API 34

### Building the Project

```bash
# Clone your fork
git clone https://github.com/YOUR_USERNAME/FastMask.git
cd FastMask

# Open in Android Studio or build via command line
./gradlew assembleDebug
```

### Running Tests

```bash
# Run unit tests
./gradlew test

# Run instrumented tests (requires emulator or device)
./gradlew connectedAndroidTest
```

## Code Style

### Kotlin

- Follow [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use meaningful names for variables, functions, and classes
- Keep functions small and focused
- Use Kotlin idioms (scope functions, extension functions, etc.)

### Compose

- Keep composables small and reusable
- Use `remember` and `derivedStateOf` appropriately
- Follow the [Compose API guidelines](https://android.googlesource.com/platform/frameworks/support/+/androidx-main/compose/docs/compose-api-guidelines.md)

### Architecture

- Follow Clean Architecture principles
- Keep the data, domain, and UI layers separate
- Use use cases for business logic
- ViewModels should only contain UI state logic

### Commit Messages

Use clear, descriptive commit messages:

- `Add: new feature description`
- `Fix: bug description`
- `Update: what was changed`
- `Refactor: what was refactored`
- `Docs: documentation changes`

## Project Structure

```
app/src/main/java/com/fastmask/
├── data/           # Data layer (API, storage, repository implementations)
├── domain/         # Domain layer (models, repository interfaces, use cases)
├── di/             # Hilt dependency injection modules
└── ui/             # UI layer (screens, viewmodels, components, theme)
```

## Questions?

Feel free to open an issue with the `question` label if you have any questions about contributing.

Thank you for contributing!
