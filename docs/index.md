---
layout: default
title: FastMask - Fastmail Masked Email Manager for Android
description: Native Android app for managing Fastmail masked emails. Create, view, edit, and delete masked email addresses with Material 3 design.
---

# FastMask

**Native Android app for managing Fastmail masked emails**

[![Latest Release](https://img.shields.io/github/v/release/pawelorzech/FastMask?style=flat-square)](https://github.com/pawelorzech/FastMask/releases/latest)
[![License](https://img.shields.io/github/license/pawelorzech/FastMask?style=flat-square)](https://github.com/pawelorzech/FastMask/blob/main/LICENSE)
[![API 26+](https://img.shields.io/badge/API-26%2B-brightgreen?style=flat-square)](https://developer.android.com/about/versions/oreo)

---

## Download

<a href="https://github.com/pawelorzech/FastMask/releases/latest" style="display: inline-block; padding: 12px 24px; background-color: #6750A4; color: white; text-decoration: none; border-radius: 8px; font-weight: bold;">Download Latest APK</a>

Or view all releases on [GitHub](https://github.com/pawelorzech/FastMask/releases)

---

## What is FastMask?

FastMask lets you manage your [Fastmail](https://www.fastmail.com) masked email addresses directly from your Android phone.

**Masked emails** are disposable addresses that forward to your real inbox. They help you:
- Protect your real email from spam
- Track which services share your email
- Easily disable addresses if they get compromised

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

## Requirements

- Android 8.0 (API 26) or higher
- Fastmail account with API access

## Quick Start

### 1. Install the App

Download the APK from [Releases](https://github.com/pawelorzech/FastMask/releases) and install it.

### 2. Create a Fastmail API Token

1. Log in to [Fastmail](https://www.fastmail.com)
2. Go to **Settings** → **Privacy & Security** → **Integrations** → **API tokens**
3. Click **New API token**
4. Name it "FastMask"
5. Select scope: **Masked Email** (read/write)
6. Copy the token

### 3. Log In

Open FastMask, paste your token, and tap "Log in".

---

## Privacy & Security

- **Secure Storage**: Your API token is encrypted using Android's EncryptedSharedPreferences
- **Direct Connection**: The app talks directly to Fastmail - no middleman servers
- **No Tracking**: Zero analytics or data collection
- **Open Source**: [Full source code](https://github.com/pawelorzech/FastMask) available for review

---

## Tech Stack

- **Kotlin** - 100% Kotlin codebase
- **Jetpack Compose** - Modern declarative UI
- **Material 3** - Latest Material Design with dynamic theming
- **JMAP** - Fastmail's native API protocol

---

## Contributing

FastMask is open source and welcomes contributions!

- [View Source Code](https://github.com/pawelorzech/FastMask)
- [Report a Bug](https://github.com/pawelorzech/FastMask/issues/new?template=bug_report.md)
- [Request a Feature](https://github.com/pawelorzech/FastMask/issues/new?template=feature_request.md)

---

## License

FastMask is released under the [MIT License](https://github.com/pawelorzech/FastMask/blob/main/LICENSE).

---

<p style="text-align: center; color: #666; margin-top: 40px;">
Made with Kotlin and Jetpack Compose
</p>
