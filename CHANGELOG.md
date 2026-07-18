# Changelog

All notable changes to FastMask are documented here.
Versions follow [Semantic Versioning](https://semver.org/).

## [1.6.0] — 2026-07-19

### Added
- **Quick-copy on the list** — a copy button on every mask card copies the address straight to the clipboard, no need to open the mask first.
- **Undo archive** — archiving a mask now shows an "Undo" snackbar on the list; one tap restores it.

### Fixed
- Editing a mask and clearing the note, domain, or URL now actually clears it (was silently reverted by the server).
- Rapid double-taps on Create/Login/Save/Archive no longer fire duplicate requests (could create duplicate masks).
- Corrupted secure storage (encryption keyset) no longer crash-loops the app on launch — it recovers by requiring a fresh sign-in.
- One-time navigation events (e.g. after login/create) are no longer lost when the screen rotates mid-request.
- The "Active" filter now matches its count (includes pending masks).
- Archiving now verifies the server confirmed the change instead of assuming success.
- Network and auth errors show clear, localized messages instead of raw technical text.

### Changed
- App startup does its storage work off the main thread for a smoother launch.
- Muted text (timestamps, labels) darkened/lightened to meet WCAG AA contrast.
- Larger touch targets on icon buttons; filter chips announce their selected state to screen readers.

### Localization
- Completed translations across all 20 languages (previously ~34% of strings fell back to English in 18 locales); fixed Chinese never resolving.

### Internal
- Added a unit-test suite (50 tests) covering JMAP parsing, sort/filter, error mapping, and the fixes above.
- `versionCode` 11 → 12, `versionName` "1.5.2" → "1.6.0".

## [1.5.2] — 2026-05-14

### Fixed
- App icon foreground now respects the Android adaptive-icon safe zone — the `@` glyph was bleeding to the canvas edges and got clipped by launcher masks; it is now scaled to ~64% of the canvas with proper margin.
- Opaque-black corners removed from the icon foreground PNGs — they showed as black fragments on the Welcome screen and splash screen, where the drawable is rendered unmasked. Corners are now fully transparent.
- Monochrome (themed) icon layer regenerated with the same safe-zone treatment.
- Play Store icon (`marketing/play/icon-512.png`) rebuilt full-bleed on the cream background with no black corners.

### Changed
- `versionCode` 10 → 11, `versionName` "1.5.1" → "1.5.2".

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
