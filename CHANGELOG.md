# Changelog

All notable changes to FastMask are documented here.
Versions follow [Semantic Versioning](https://semver.org/).

## [1.5.1] — 2026-05-11

### Added
- ProGuard rules for Hilt/Dagger and Jetpack Compose — prevents R8 from stripping DI-generated classes and Composable runtime metadata in release builds.
- `isShrinkResources = true` in the release build type — drops unused drawable/string resources alongside R8 dead-code elimination.
- Privacy Policy hosted at `https://pawelorzech.github.io/FastMask/privacy.html` — required for Google Play submission.
- Google Play store listing copy (English + Polish) in `marketing/copy/`.

### Changed
- `versionCode` 6 → 7, `versionName` "1.5" → "1.5.1".

### Operational
- First release prepared for Google Play distribution (closed testing track).

## [1.5] — 2026-04 (April 2026) — Security hardening

- Removed unconditional HTTP body logging; release builds emit zero network logs.
- Release signing config no longer falls back to the debug keystore.
- `android:allowBackup="false"` — app data excluded from Android cloud backup.
- Network Security Config restricts `api.fastmail.com` to the system CA store.
- Added `FLAG_SECURE` + `filterTouchesWhenObscured` — screens blanked in screenshots/recording.

## [1.4] — 2026-04 (April 2026)

- Complete UI overhaul: warm-ink palette, Instrument Serif headings, JetBrains Mono addresses.
- Sort by activity (`lastMessageAt`) instead of creation date.
- Archive instead of Delete — masks can be restored.
- 20-language localization.
