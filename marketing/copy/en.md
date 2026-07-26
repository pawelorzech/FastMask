# Google Play Store listing — English (default)

## App name (max 50 chars)
**FastMask – Fastmail Masked Email**
(38 chars)

## Short description (max 80 chars)
**Manage Fastmail masked email addresses. Open-source, private, no ad tracking.**
(77 chars)

## Full description (max 4000 chars)

FastMask is a fast, private, native Android client for managing your Fastmail masked email addresses.

**Masked emails** are disposable forwarding addresses that protect your real inbox. Use a different one for every website, track who leaked your data, and switch off any address that gets abused — without giving up your real email anywhere.

FastMask brings the masked-email features that already exist on Fastmail's web app to your phone, in a calm, distraction-free interface designed for one thing: getting in, copying or creating an address, and getting back to whatever you were doing.

⸻ FEATURES ⸻

• View all your masks in a clean list, sorted by latest activity (not creation date)
• Create new masks with custom descriptions and domain associations
• Enable, disable, or archive masks without losing the address
• Edit description, domain, and forwarding URL
• One-tap copy to clipboard with subtle haptic feedback
• Search and filter by Active / Off / Archived
• Quick stats: how many messages each mask has received, when the last one arrived
• Settings screen with language picker, crash-report switch, contact link, and logout

⸻ DESIGN ⸻

• Warm-ink palette — parchment cream and ink navy, not the usual blue
• Instrument Serif headings, Inter Tight body, JetBrains Mono for addresses
• Light and dark mode, both following your system setting
• Smooth shared-element transitions between list and detail
• Subtle, restrained motion — never in the way

⸻ PRIVACY & SECURITY ⸻

• No tracking, no analytics, no ads. There is no Google Analytics in this app, no advertising ID, no event or screen tracking, no user profiling.
• Your Fastmail API token is encrypted on-device using EncryptedSharedPreferences
• The app talks directly to api.fastmail.com over HTTPS — no servers in the middle
• Network Security Config pins trust to the system CA store
• Release builds log nothing about your network traffic
• Screens are FLAG_SECURE — the token never leaks to screenshots or screen recording
• Crash reports: on by default, off in one tap. If the app crashes, it sends Google Firebase Crashlytics the stack trace, your device model, Android version and app version — so the bug can be found and fixed. It never sends your masked addresses, descriptions, associated domains, API token, email address, or the contents of any message. A report only leaves on a real crash — but while reporting is on, the SDK also registers the install with Google and fetches its own configuration when the app starts, sending the installation ID, device model and Android version with no crash involved. Don't want any of it? Settings → Crash reports → off. Both stop immediately and anything still queued is deleted.
• Open source under MIT license — verify everything for yourself. The Crashlytics SDK is called from exactly one file, and a test fails the build if that ever changes.

⸻ LANGUAGES ⸻

Full UI translations: English, Polish, Spanish, German, French, Italian, Portuguese, Dutch, Russian, Ukrainian, Turkish, Arabic, Hindi, Bengali, Chinese (Simplified), Japanese, Korean, Vietnamese, Thai, Indonesian.

⸻ REQUIREMENTS ⸻

• Android 8.0 (API 26) or higher
• A Fastmail account with API token access (any paid plan)

To get started, create an API token in Fastmail Settings → Privacy & Security → Integrations → API tokens (scope: Masked Email, read/write), paste it into FastMask once, and you're in.

⸻ NOT AFFILIATED WITH FASTMAIL ⸻

FastMask is an unofficial, community-built client. It is not affiliated with or endorsed by Fastmail Pty Ltd. "Fastmail" is a trademark of Fastmail Pty Ltd, used here only to describe the service this app integrates with.

⸻ OPEN SOURCE ⸻

Source code, issue tracker, and release history:
https://github.com/pawelorzech/FastMask

Released under the MIT License. Pull requests welcome.

## What's new (max 500 chars) — v1.5.1
**v1.5.1 — Play Store launch**

• Brand new icon and splash screen — warm-ink palette
• Tightened R8 rules — slightly smaller install size
• Stable release artifact, suitable for production
• Same calm, private, zero-tracking app you'd expect

(287 chars)

## What's new (max 500 chars) — crash reporting release

**Crash reports — on by default, off in one tap**

• New: if the app crashes, it can send the stack trace, device model and app version to Firebase Crashlytics so the bug gets fixed
• Never sends your masks, descriptions, domains, token or email
• Still no analytics and no ad tracking
• Don't want it? Settings → Crash reports → off. Stops right away.
• Full detail in the updated privacy policy

(392 chars, full field)

## Category, tags, contact

- **Category:** Productivity
- **Tags (pick 5):** email · privacy · fastmail · masked · alias
- **Email:** pawel@orzech.me
- **Website:** https://pawelorzech.github.io/FastMask/
- **Privacy policy:** https://pawelorzech.github.io/FastMask/privacy.html
