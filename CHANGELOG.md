# Changelog

All notable changes to FastMask are documented here.
Versions follow [Semantic Versioning](https://semver.org/).

## [1.8.2] — 2026-07-24

Fourth audit pass (see `AUDIT_REPORT.md`, `CHANGELOG_AGENT.md`) — 7 fixes plus 3 UX quick wins, no P0. 114 → 124 unit tests.

### Fixed
- **Contact, Privacy Policy and Terms links did nothing on Android 11+.** `resolveActivity()` is package-visibility filtered since API 30 and the manifest declared no `<queries>`, so it reported "no handler" for `mailto:` and `https:` even with apps installed — and the failure was silent. The Privacy/Terms case was a regression introduced in 1.8.1; the Contact case had been broken since January.
- **13 strings appeared in English in all 19 translated locales**, including the sign-out and discard-changes confirmations. They had been added with `tools:ignore="MissingTranslation"`, which silenced Lint.
- **18 locales rendered a raw `%s`** on the mask detail screen: `email_detail_last_message` became a bare label but the translations kept the old "Label: %s" sentence form.
- A failed sign-in no longer clears the pasted API token when the failure is retryable (no network, 429, 5xx). An auth rejection (401/403) still clears it.
- Entering demo mode no longer crashes the app if the settings write fails.

### Changed
- The copy snackbar names the address that was copied.
- A failed CSV export reports the actual cause (network, rate limit, server, or a file-write failure) instead of one generic message.
- The list header uses `<plurals>`, so Polish, Russian and Ukrainian inflect the count correctly.

### Build
- `assembleRelease` / `bundleRelease` now **refuse to produce a signed build without a Play licence key**, which would otherwise ship with purchase signature verification silently disabled. Unsigned CI builds are unaffected.

### Tests
- New `TranslationCompletenessTest`: fails on a missing key in any locale, on an English sentence surviving into a translation, and on a translation whose format arguments disagree with the default. All three were verified by deliberately reintroducing the bugs they guard against.

## [1.8.1] — 2026-07-24

Third audit pass (see `AUDIT_REPORT.md`, `CHANGELOG_AGENT.md`) — 20 fixes, no P0/P1, all P2/P3. 101 → 114 unit tests.

### Security
- **Play purchases are now cryptographically verified** — a purchase unlocks Pro only when Google Play's signature validates against the app's RSA key, so a forged or replayed purchase on a modified device can no longer unlock Pro.
- CSV export neutralizes formula characters even behind leading whitespace (OWASP), and a newly created mask copied from the create screen is flagged sensitive on Android 13+.

### Fixed
- Edits typed in the detail screen are no longer discarded by the reload that follows a save or state toggle.
- The paywall's Buy button can no longer spin forever if Play returns an unusual buy-flow result.
- A rare storage write failure (e.g. full disk) no longer crashes the app on sign-out, language change, or toggles.
- The list no longer fires duplicate refreshes on rapid re-entry.

### Changed
- Mask state is announced to TalkBack on the list (was conveyed by color alone); search field, demo-banner "Sign in", and the create screen's state selector are properly labeled for screen readers.
- Prompt before discarding unsaved edits (create + detail) and before signing out.
- The list distinguishes "no masks yet" from "no matches for this search/filter".
- Archiving shows a progress indicator; prefix validation messages are localizable.
- Dark-theme default accent brightened so accent text meets WCAG AA contrast.

## [1.8.0] — 2026-07-23

Full technical audit (see `AUDIT_REPORT.md`) plus every fix from its backlog.

### Fixed
- **Undo after archiving works again** — the "Archived — Undo" snackbar was dismissed after a single frame by its own consume signal, so the action could never be tapped. It now stays up for ~10 s, and a failed undo shows an error instead of silently keeping the mask archived.
- Undo restores the mask to its **pre-archive state** — a disabled mask no longer comes back accepting mail.
- Billing: a buy-flow update that doesn't contain the Pro purchase no longer downgrades the cached entitlement (only an authoritative Play query may); purchase updates are emitted in listener order.
- Billing: the entitlement is reconciled with Play on every return to the foreground — pending purchases completed in the background unlock without restarting the app, and failed acknowledgements retry well within Play's refund window (also with the monetization kill-switch off).
- App lock: fixed a bypass after background process death on Android 8.0/8.1 (saved state predating the re-lock was trusted); locking no longer destroys the navigation back stack, so a half-typed form survives a lock/unlock cycle.
- Pro accents are legible in the dark theme — brightened per-theme variants (the deep light-theme colors rendered at 1.7–2.8:1 on dark surfaces, with Ink nearly invisible as cursor/FAB).
- CSV export: leading CR/LF are neutralized like other formula lead characters (OWASP); export files are timestamped and only cleaned up after an hour, so a slow share target keeps a valid file.
- Copied masked addresses are flagged sensitive on Android 13+ (no plaintext in the clipboard preview) and the clip label is localized.

### Changed
- Buy, Restore and CSV export show an in-flight spinner instead of looking frozen.
- Pending-payment message explains the expected wait; dialogs that apply choices instantly say "OK" instead of "Cancel" (all 20 languages).
- Accessibility: accent/language pickers announce the current selection (radio semantics), settings toggles are single Switch-role targets, the settings chevron mirrors in RTL, paywall legal links have 48 dp touch targets.
- Stale purchase events no longer replay when reopening the paywall; paywall-closed analytics counts gesture back too.

### Internal
- `androidx.security:security-crypto` 1.1.0-alpha06 → 1.1.0 stable.
- Unit tests 87 → 92; `local.properties` untracked from git.
- `versionCode` 16 → 17, `versionName` "1.7.3" → "1.8.0".

## [1.7.3] — 2026-07-22

### Changed
- **Target API level bumped to 36 (Android 16)** to meet Google Play's requirement taking effect August 31, 2026 (target API must stay within one year of the latest Android release). No behavioural changes for users.

### Internal
- `compileSdk`/`targetSdk` 35 → 36, `android.suppressUnsupportedCompileSdk` 35 → 36 (AGP 8.5.2 / Gradle 8.9 unchanged).
- `versionCode` 15 → 16, `versionName` "1.7.2" → "1.7.3".

## [1.7.2] — 2026-07-19

### Changed
- **New app icon** — an amber domino mask on the warm parchment background, replacing the `@`-with-a-face glyph. All layers regenerated (adaptive foreground, monochrome/themed, legacy + round mipmaps, Play Store 512px); splash screen picks it up automatically.

### Internal
- `versionCode` 14 → 15, `versionName` "1.7.1" → "1.7.2".

## [1.7.1] — 2026-07-19

### Fixed
- CSV export hardening: fields starting with spreadsheet formula characters (`=`, `+`, `-`, `@`, tab) are neutralized with a leading apostrophe (OWASP CSV-injection mitigation), so a mask note never executes as a formula in Excel/Sheets.
- Export cache keeps a single file — previous exports are deleted before writing a new one.

### Internal
- `versionCode` 13 → 14, `versionName` "1.7.0" → "1.7.1". (1.7.0/vc13 reached internal testing only.)

## [1.7.0] — 2026-07-19

### Added
- **FastMask Pro** — an optional, one-time purchase (Google Play Billing 8.3.0) that supports development and unlocks:
  - **Accent themes** — five accents (Amber, Ink, Sage, Plum, Cobalt), all WCAG-AA compliant, applied instantly without restart.
  - **Biometric app lock** — optional fingerprint/credential gate when opening the app; content is never composed behind the lock screen.
  - **CSV export** — share all masks (including archived) as an RFC-4180 CSV via the system share sheet; no storage permissions.
- Pro screen with price fetched from Google Play, purchase, restore, and clear offline/unavailable states. No dark patterns: no timers, no forced paywalls, back always works.
- Terms of use page (`terms.html`) on the project site.

### Unchanged (by design)
- **Every feature that existed before 1.7.0 stays free, forever.** Pro only adds new extras.
- **Still zero tracking** — no analytics SDK was added; the monetization funnel is instrumented locally (debug builds only) and revenue metrics come from Play Console.

### Internal
- Billing architecture: `BillingDataSource` abstraction → `ProRepository` as the single entitlement source of truth; Play is authoritative, DataStore caches the last verified state for offline; acknowledge-before-unlock; pending purchases stay locked; downgrade only on an authoritative empty answer.
- `MONETIZATION_ENABLED` build flag as a kill switch (hides every Pro entry point).
- 36 new unit tests (86 total): purchase/cancel/pending/offline/restore/downgrade/double-tap, paywall gating, CSV escaping.
- New strings localized across all 20 languages.
- `versionCode` 12 → 13, `versionName` "1.6.0" → "1.7.0".

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
