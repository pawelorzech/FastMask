---
layout: default
title: FastMask Privacy Policy
description: Privacy policy for the FastMask Android app
permalink: /privacy.html
---

# Privacy Policy

**Last updated: 2026-05-11**
**Effective date: 2026-05-11**

This Privacy Policy explains how the FastMask Android application ("FastMask", "the app", "we", "our") handles your personal information. FastMask is an unofficial, open‑source client for [Fastmail](https://www.fastmail.com) masked email addresses. It is not affiliated with or endorsed by Fastmail Pty Ltd.

## 1. Data Controller

The data controller responsible for this app is:

- **Paweł Orzech** (sole proprietorship "YesWas")
- Email: [pawel@orzech.me](mailto:pawel@orzech.me)
- Tax ID (NIP): 8741734171
- REGON: 146400491
- Registered in CEIDG (Polish Central Registration and Information on Business)

## 2. What Data the App Processes

FastMask processes only the minimum data needed to function as a Fastmail masked‑email manager:

| Data | Where it is stored | Why it is processed |
|------|-------------------|---------------------|
| **Fastmail API token** | Locally on your device, encrypted via Android `EncryptedSharedPreferences` (AES‑256‑GCM for values, AES‑256‑SIV for keys) | To authenticate JMAP API calls to `api.fastmail.com` on your behalf |
| **Masked email addresses, descriptions, domain/URL associations, activity timestamps** | In memory while the app runs; persisted only on Fastmail's servers | To display, create, edit, and search your masked emails |
| **Selected language** | Locally on your device via Android `DataStore` | To remember your language preference between sessions |

We do **not** collect or process any of the following:

- Analytics, telemetry, or usage statistics
- Crash reports (no Crashlytics, no Sentry, no third‑party SDK)
- Device identifiers (advertising ID, IMEI, MAC address)
- Location, contacts, photos, microphone, or any other phone resource
- Your Fastmail account password (the app never sees it — only an API token you generate yourself)

## 3. Legal Basis for Processing (GDPR)

For users in the European Economic Area, the legal basis for processing the data above is **Article 6(1)(b) GDPR — performance of a contract**: we process your data because it is strictly necessary to provide the masked‑email management functionality you requested by installing the app and logging in.

## 4. Data Sharing and Recipients

We do not share, sell, rent, or otherwise transfer your personal data to third parties.

The only network destination contacted by the app is `api.fastmail.com` (Fastmail's JMAP API), which is reached **directly from your device** over HTTPS/TLS. The app uses Android's Network Security Config to restrict trust for that hostname to the system certificate authority store.

Your Fastmail account, including all masked emails, is governed by Fastmail's own privacy policy: <https://www.fastmail.com/about/privacy/>.

## 5. International Data Transfers

Because the app talks only to Fastmail and stores everything else locally on your device, transfer of your personal data outside your country is determined entirely by Fastmail's infrastructure and is subject to their privacy practices.

## 6. Data Retention

- The API token and language preference remain on your device until you log out of FastMask, clear the app's data, or uninstall the app — whichever happens first. At that point they are removed.
- We have no servers and therefore retain no copy of your data.
- Masked email entries themselves live in your Fastmail account. Their retention is governed by Fastmail.

## 7. Your Rights

To the extent applicable law (such as the GDPR or the UK GDPR) grants you rights over your personal data, you have the right to access, rectify, erase, restrict, port, or object to processing of your data, and the right to lodge a complaint with a supervisory authority.

Because we have no servers, you can exercise the deletion and rectification rights yourself at any time:

- **Delete locally stored data:** Log out inside FastMask, or uninstall the app, or clear the app's data in Android system settings.
- **Modify, delete, or export your masked emails:** Use Fastmail directly — those entries are stored in your Fastmail account.

For any other questions or to exercise your rights regarding the small amount of data processed locally by the app, contact [pawel@orzech.me](mailto:pawel@orzech.me).

## 8. Security

- All network communication uses HTTPS/TLS.
- The Android `EncryptedSharedPreferences` API protects the API token at rest using a hardware‑backed key when available.
- Release builds disable HTTP request/response logging.
- `android:allowBackup="false"` is set in the app manifest — your token cannot be picked up by Android's automatic cloud backup.
- The login and detail screens use `FLAG_SECURE` and `filterTouchesWhenObscured`, so the token is not visible in screenshots, the recent‑apps overview, or screen‑capture flows.
- The app does not embed any third‑party analytics, advertising, or crash‑reporting SDK.

No security measure is perfect. If you believe you have found a vulnerability, please follow the responsible disclosure process described in [`SECURITY.md`](https://github.com/pawelorzech/FastMask/blob/main/SECURITY.md).

## 9. Children's Privacy

FastMask is not directed at children under 18, and we do not knowingly process data from children. The app is rated 18+ on Google Play.

## 10. Changes to This Policy

We may update this policy from time to time. Material changes will be announced through a new app release and reflected in the "Last updated" date at the top of this document. The current version is always available at <https://pawelorzech.github.io/FastMask/privacy.html>.

## 11. Open Source

FastMask is open source under the [MIT License](https://github.com/pawelorzech/FastMask/blob/main/LICENSE). You can inspect the full source code, the network security config, and the proof that no third‑party tracking SDK is bundled at <https://github.com/pawelorzech/FastMask>.

## 12. Contact

- Email: [pawel@orzech.me](mailto:pawel@orzech.me)
- GitHub Issues: <https://github.com/pawelorzech/FastMask/issues>
