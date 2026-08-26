# API and release audit — 2026-08-26

Target release: `1.12.0` (`versionCode 25`). The audit covers every external
integration present in the app; it does not include publishing to Google Play.

## Contract matrix

| Integration | Surface used | Result |
|---|---|---|
| Fastmail JMAP | session discovery, Bearer auth, capability/account selection, `MaskedEmail/get`, `MaskedEmail/set` create/update/destroy | Corrected: origins, empty descriptions, nullable deep links, partial create responses, pending default, ambiguous accounts and `rateLimit` |
| Google Play Billing 8 | connect, product query, purchase launch, purchase query/update, signature check, acknowledgement | Corrected: fresh `ProductDetails` before launch and per-product unfetched errors |
| Google Play In-App Updates | update info, flexible/immediate launch, resume, complete | Corrected: Boolean launch result, unconditional resume of an interrupted immediate update and cancellation-only dismissal |
| Firebase Crashlytics | collection preference, queued-report deletion, exception recording | SDK usage is valid; copy now reflects that collection disablement applies on the next app launch |
| Google Fonts provider | downloadable-font request and bundled certificates | No contract discrepancy found |

Primary references:

- Fastmail Masked Email: <https://www.fastmail.com/dev/>
- JMAP Core: <https://www.rfc-editor.org/rfc/rfc8620.html>
- Play Billing integration: <https://developer.android.com/google/play/billing/integrate>
- Play in-app updates: <https://developer.android.com/guide/playcore/in-app-updates/kotlin-java>
- Firebase Crashlytics API: <https://firebase.google.com/docs/reference/android/com/google/firebase/crashlytics/FirebaseCrashlytics>

## Deliberate boundaries

- Fastmail recommends OAuth for distributed apps, but client registration is a
  manual Fastmail process. The existing limited-scope user token flow remains
  functional; OAuth needs a registered client id and is not invented locally.
- Purchase verification is local because FastMask has no backend. It validates
  Play's RSA signature and acknowledgement state, but cannot independently poll
  refunds or voided purchases as a server integration could.
- Billing offer-token support is not added because the current catalog is a
  single backward-compatible lifetime product. Add it before configuring
  multiple purchase options or offers in Play Console.
- `androidx.security:security-crypto` is deprecated. Replacing it safely needs a
  versioned migration of existing encrypted tokens and cache files, plus real
  upgrade/restart tests; it is intentionally not removed during this release.

## Release evidence still requiring Play infrastructure

- License-tester Billing paths: approve, decline, pending, restore and acknowledge.
- Flexible and immediate update paths from a lower `versionCode` on an internal
  track or Internal App Sharing.
- Play pre-launch report, Data safety review and staged-rollout monitoring.
