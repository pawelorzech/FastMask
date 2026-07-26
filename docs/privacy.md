---
layout: default
title: FastMask Privacy Policy
description: Privacy policy for the FastMask Android app
permalink: /privacy.html
---

# Privacy Policy

**Last updated: 2026-07-26**
**Effective date: 2026-07-26**

This Privacy Policy explains how the FastMask Android application ("FastMask", "the app", "we", "our") handles your personal information. FastMask is an unofficial, open‑source client for [Fastmail](https://www.fastmail.com) masked email addresses. It is not affiliated with or endorsed by Fastmail Pty Ltd.

## 1. Data Controller

The data controller responsible for this app is:

- **Paweł Orzech** (sole proprietorship "YesWas")
- Email: [pawel@orzech.me](mailto:pawel@orzech.me)
- Tax ID (NIP): 8741734171
- REGON: 146400491
- Registered in CEIDG (Polish Central Registration and Information on Business)

## 2. What Data the App Processes

The first five rows below are the data FastMask needs in order to work as a Fastmail masked‑email manager. The sixth, crash diagnostics, is not needed for the app to work — it is optional, and you can switch it off; it is listed here because it is the only data the app sends to anyone other than Fastmail and Google Play.

| Data | Where it is stored | Why it is processed |
|------|-------------------|---------------------|
| **Fastmail API token** | Locally on your device, encrypted via Android `EncryptedSharedPreferences` (AES‑256‑GCM for values, AES‑256‑SIV for keys) | To authenticate JMAP API calls to `api.fastmail.com` on your behalf |
| **Masked email addresses, descriptions, domain/URL associations, activity timestamps** | In memory while the app runs; persisted only on Fastmail's servers | To display, create, edit, and search your masked emails |
| **Selected language** | Locally on your device via Android `DataStore` | To remember your language preference between sessions |
| **App preferences (accent theme, app-lock on/off)** | Locally on your device via Android `DataStore` | To remember your personalization choices |
| **FastMask Pro entitlement** | Locally on your device via Android `DataStore` — a status flag plus a SHA‑256 digest of the Google Play purchase token (never the token itself, never payment data) | To keep Pro features working offline; Google Play remains the source of truth and is re-checked at every app start |
| **Crash diagnostics** — stack trace of the crash, device model, Android version, app version, and the identifiers Firebase generates for the installation (Firebase installation ID and Crashlytics installation UUID) | Sent to **Firebase Crashlytics**, a Google service, when the app crashes. Your on/off choice is stored locally via Android `DataStore` | To find and fix the bugs that make the app crash, and to tell how many people a given crash affects. **This is on by default and you can switch it off** in Settings → *Crash reports* |

### Crash reporting in detail

Crash reporting is **enabled by default**, including on existing installations that update to this version.

**To turn it off:** open **Settings** in the app and switch off **Crash reports**. The change takes effect immediately, without restarting the app — collection stops at once, and any reports already queued on your device but not yet sent are deleted. You can switch it back on at any time in the same place.

**What a crash report never contains.** The app is built so that it *cannot* attach your data to a report: the only calls it makes to the Crashlytics SDK are "turn collection on/off" and "delete unsent reports". It sets no user ID, no custom keys, and no log messages. Concretely, a report never includes:

- your masked email addresses,
- mask descriptions, associated domains, or forwarding URLs,
- your Fastmail API token or any credential,
- your own email address or Fastmail account name,
- the content of any email.

**No behavioural analytics.** Google Analytics for Firebase is deliberately **not** included in the app. Crashlytics is used for crash diagnostics only — there is no screen tracking, no event tracking, no advertising identifier, and no user profiling.

**Development builds never report.** Debug builds of the app send nothing to Crashlytics, regardless of this setting.

We do **not** collect or process any of the following:

- Analytics, telemetry, or usage statistics (no Google Analytics, no advertising or attribution SDK)
- Device identifiers (advertising ID, IMEI, MAC address)
- Location, contacts, photos, microphone, or any other phone resource
- Your Fastmail account password (the app never sees it — only an API token you generate yourself)
- Payment or card data (purchases are processed entirely by Google Play; the app only receives a purchase confirmation)
- Biometric data (the optional app lock uses Android's system `BiometricPrompt`; authentication happens inside the operating system and the app never accesses fingerprint or face data)

**CSV export** (a Pro feature) is generated locally, on your explicit request, and handed to the app *you* choose in the share sheet — nothing is uploaded anywhere by FastMask.

## 3. Legal Basis for Processing (GDPR)

For users in the European Economic Area, two different legal bases apply, depending on the data:

**a) App functionality — Article 6(1)(b) GDPR, performance of a contract.** The API token, masked‑email data, language and app preferences, and the Pro entitlement are processed because doing so is strictly necessary to provide the masked‑email management functionality you requested by installing the app and logging in.

**b) Crash diagnostics — Article 6(1)(f) GDPR, legitimate interests.** Crash reports are *not* necessary to perform the contract: the app works whether or not they are sent. They are processed on the basis of our legitimate interest in keeping the app stable, secure, and free of defects, and in fixing crashes that would otherwise leave users unable to reach their masked emails. We have weighed that interest against your interests and rights and consider it proportionate, because the reports are limited to technical crash data, contain none of the content described in section 2, are not used for profiling, advertising, or any decision about you, and are not combined with behavioural analytics (Google Analytics is not present in the app).

Where processing rests on legitimate interests, **you have the right to object at any time under Article 21(1) GDPR**. In this app that right is implemented directly as a switch: turning off **Settings → Crash reports** stops the processing immediately and deletes reports still pending on your device. You do not have to give a reason, and you do not have to contact us — although you may, using the address in section 12, and we will act on it.

We do not rely on consent (Article 6(1)(a)) for crash diagnostics, and none of the processing described in this policy involves special categories of data under Article 9 GDPR or automated decision‑making under Article 22 GDPR.

## 4. Data Sharing and Recipients

We never sell or rent your personal data, and we do not share it for advertising or profiling. Data leaves your device only in the three cases below.

**1. Fastmail.** The app's main network destination is `api.fastmail.com` (Fastmail's JMAP API), reached **directly from your device** over HTTPS/TLS. The app uses Android's Network Security Config to restrict trust for that hostname to the system certificate authority store.

**2. Google — crash diagnostics (processor).** If crash reporting is on, crash reports are sent to **Firebase Crashlytics**, operated by **Google LLC** (1600 Amphitheatre Parkway, Mountain View, CA 94043, USA) and, for users in the EEA, **Google Ireland Limited** (Gordon House, Barrow Street, Dublin 4, Ireland). Google acts as our **processor** for this data under the Firebase Data Processing and Security Terms, and processes it on our documented instructions. Applicable terms:

- Firebase privacy and data‑handling documentation: <https://firebase.google.com/support/privacy>
- Firebase Data Processing and Security Terms: <https://firebase.google.com/terms/data-processing-terms>
- Google privacy policy: <https://policies.google.com/privacy>

No other third‑party SDK sends data anywhere: the app contains no analytics, advertising, attribution, or A/B‑testing service.

**3. Google Play — purchases (independent controller).** If you buy the optional **FastMask Pro** in-app purchase, the transaction is handled by **Google Play** (Google LLC / Google Ireland Ltd.), acting as an independent data controller under its own privacy policy: <https://policies.google.com/privacy>. FastMask receives only a purchase confirmation — never your payment details.

Your Fastmail account, including all masked emails, is governed by Fastmail's own privacy policy: <https://www.fastmail.com/about/privacy/>.

## 5. International Data Transfers

We operate no servers of our own, so we transfer nothing ourselves. Two destinations may nonetheless involve a transfer outside the European Economic Area:

- **Fastmail.** Where your masked emails and account data are stored is determined entirely by Fastmail's infrastructure and is subject to their privacy practices.
- **Crash diagnostics.** If crash reporting is on, crash reports are sent to Google's infrastructure and **may be stored and processed on servers outside the EEA, including in the United States**. Google states that such transfers are covered by the safeguards in the Firebase Data Processing and Security Terms, which incorporate the European Commission's Standard Contractual Clauses (Article 46(2)(c) GDPR): <https://firebase.google.com/terms/data-processing-terms>. Turning off **Settings → Crash reports** stops this transfer.

## 6. Data Retention

- The API token, language, and other local preferences remain on your device until you log out of FastMask, clear the app's data, or uninstall the app — whichever happens first. At that point they are removed.
- We have no servers and therefore retain no copy of your data ourselves.
- Masked email entries themselves live in your Fastmail account. Their retention is governed by Fastmail.
- **Crash reports:** Google states that "Firebase Crashlytics keeps crash stack traces, extracted minidump data, and associated identifiers (including Crashlytics Installation UUIDs and Firebase installation IDs) for 90 days before starting the process of removing it from live and backup systems" (source: <https://firebase.google.com/support/privacy>). Reports captured on your device but not yet uploaded are deleted immediately when you switch crash reporting off.

## 7. Your Rights

To the extent applicable law (such as the GDPR or the UK GDPR) grants you rights over your personal data, you have the right to access, rectify, erase, restrict, port, or object to processing of your data, and the right to lodge a complaint with a supervisory authority.

Because we have no servers, you can exercise the deletion and rectification rights yourself at any time:

- **Delete locally stored data:** Log out inside FastMask, or uninstall the app, or clear the app's data in Android system settings.
- **Modify, delete, or export your masked emails:** Use Fastmail directly — those entries are stored in your Fastmail account.

### Right to object to crash diagnostics (Article 21 GDPR)

Crash reports are the one thing in this app processed on the basis of legitimate interests, so they are the one thing you can object to. Exercise that right in either of these ways:

1. **In the app (immediate, no reason needed):** **Settings → Crash reports → off.** Collection stops at once, and reports still queued on your device are deleted. This is the fastest and most complete route, and it takes effect without a restart.
2. **By email:** write to [pawel@orzech.me](mailto:pawel@orzech.me). Because reports carry no user identifier, we cannot pick your individual reports out of Crashlytics on request — which is also why option 1 exists: it stops the processing at the source. Reports already uploaded are deleted by Google under the 90‑day retention described in section 6.

For any other questions or to exercise your rights regarding the data processed by the app, contact [pawel@orzech.me](mailto:pawel@orzech.me).

## 8. Security

- All network communication uses HTTPS/TLS.
- The Android `EncryptedSharedPreferences` API protects the API token at rest using a hardware‑backed key when available.
- Release builds disable HTTP request/response logging.
- `android:allowBackup="false"` is set in the app manifest — your token cannot be picked up by Android's automatic cloud backup.
- The login and detail screens use `FLAG_SECURE` and `filterTouchesWhenObscured`, so the token is not visible in screenshots, the recent‑apps overview, or screen‑capture flows.
- The app embeds no third‑party analytics or advertising SDK. The only third‑party SDK that sends anything off the device is Firebase Crashlytics, described in sections 2–6; it is limited in code to switching collection on and off and deleting unsent reports, so no app data can be attached to a report.

No security measure is perfect. If you believe you have found a vulnerability, please follow the responsible disclosure process described in [`SECURITY.md`](https://github.com/pawelorzech/FastMask/blob/main/SECURITY.md).

## 9. Children's Privacy

FastMask is not directed at children under 18, and we do not knowingly process data from children. The app is rated 18+ on Google Play.

## 10. Changes to This Policy

We may update this policy from time to time. Material changes will be announced through a new app release and reflected in the "Last updated" date at the top of this document. The current version is always available at <https://pawelorzech.github.io/FastMask/privacy.html>.

**Change on 2026-07-26:** the app gained optional crash reporting via Firebase Crashlytics, on by default and switchable off in Settings. Earlier versions of this policy stated that the app sent no crash reports; that was accurate for those versions and is no longer accurate for this one. Sections 2 through 7 describe exactly what is now sent, to whom, on what legal basis, and how to stop it.

## 11. Open Source

FastMask is open source under the [MIT License](https://github.com/pawelorzech/FastMask/blob/main/LICENSE). You can inspect the full source code, the network security config, and the dependency list at <https://github.com/pawelorzech/FastMask> — including every claim made above about crash reporting. The Crashlytics SDK is reachable from exactly one file (`FirebaseCrashlyticsReporter.kt`), and an automated test in the repository fails the build if any other file starts calling it.

## 12. Contact

- Email: [pawel@orzech.me](mailto:pawel@orzech.me)
- GitHub Issues: <https://github.com/pawelorzech/FastMask/issues>
