# FastMask Security Audit — Plan

## Context

FastMask is an Android app (Kotlin/Compose, Hilt, JMAP) that manages Fastmail Masked Email aliases. Auth is a single Bearer API token stored in `EncryptedSharedPreferences`. The app handles credentials and PII (masked email aliases that proxy real inboxes), so a compromise enables enumeration of a user's identities and full takeover of their masked-email surface (create, disable, delete).

Goal of this audit: produce a finding-by-finding report with PoCs, severity, fixes, and verification steps. Output is read-only analysis + recommended patches; no code is changed in this audit pass. Methodology follows the user's prompt (OWASP MASVS / Top 10 / ASVS L2 + CWE Top 25 + Android-specific concerns).

---

## Scope (recon, already mapped)

**Stack:** Android Kotlin 1.9.22, Compose (BOM 2024.09.00), Hilt 2.50, Retrofit 2.9.0 + OkHttp 4.12.0, kotlinx-serialization 1.6.2, androidx.security-crypto 1.1.0-alpha06, datastore-preferences 1.0.0. compileSdk/targetSdk 34, minSdk 26, JDK 17.

**Architecture:** Clean Architecture — `data/` (api, local, repository), `domain/` (model, repository, usecase), `ui/` (auth, list, create, detail, settings). Single Activity (`MainActivity`), single Application (`FastMaskApplication`).

**Trust boundaries:**
- Device ↔ Fastmail JMAP API (`https://api.fastmail.com/jmap/session` + `/jmap/api/`) — only network entry point.
- App ↔ User keystore (Android KeyStore via `MasterKey` AES-256-GCM).
- App ↔ OS (`Intent.ACTION_SENDTO` mailto handler, no other IPC).

**Sensitive data:**
- Fastmail API token (long-lived, full account access scope).
- Masked email aliases + descriptions + linked domains/URLs (PII, behavioral profile).

**External deps (deploy):** Fastmail JMAP only. No analytics, no crash reporting, no third-party SDKs that phone home. CI = two Claude Code GitHub Actions workflows, no release pipeline.

**Entry/attack surface:**
- Single launcher Activity (`exported=true`, MAIN/LAUNCHER only).
- INTERNET permission only.
- No deep links, no custom schemes, no `<provider>`, no `<receiver>`, no `<service>`.
- No WebView, no `addJavascriptInterface`, no `file://` / `content://` handling.
- One outgoing intent: `mailto:` with `resolveActivity()` check.

---

## Confirmed findings (verified by reading code)

These were spotted in recon and confirmed against the source. Each becomes a finding card in the audit report.

### F-01 [CRITICAL] Release builds signed with default Android debug keystore
- **File:** `app/build.gradle.kts:26-33`
- **Evidence:** `signingConfigs.create("release")` points at `~/.android/debug.keystore` with `storePassword="android"`, `keyAlias="androiddebugkey"`, `keyPassword="android"`.
- **Class:** CWE-321 (Use of Hard-coded Cryptographic Key) / MASVS-CODE-2.
- **Impact:** Any actor can re-sign the APK with the public debug keystore, defeating signature-based update integrity and allowing trivial trojanized rebuilds. Play Store would reject; sideload distribution loses authenticity guarantee.
- **Fix:** Move signing to a real keystore loaded from Gradle properties / env (`signingConfigs.create("release") { storeFile = file(System.getenv("FASTMASK_KEYSTORE")); storePassword = System.getenv("FASTMASK_STORE_PWD"); ... }`), keep keystore out of git, document in `SECURITY.md`. Until then, do not publish a release APK.
- **Verify:** `apksigner verify --print-certs app-release.apk` shows non-debug subject; `keytool -list` on keystore confirms a non-`androiddebugkey` alias.

### F-02 [CRITICAL] HTTP body logging in all builds leaks Bearer token + masked email contents
- **File:** `app/src/main/java/com/fastmask/di/NetworkModule.kt:35-36`
- **Evidence:** `HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }` is added to the singleton OkHttp client unconditionally — there is no `BuildConfig.DEBUG` guard and the release build keeps the same interceptor.
- **Class:** CWE-532 (Insertion of Sensitive Information into Log File) / MASVS-STORAGE-2.
- **Impact:** Every JMAP request/response is written to `logcat`, including `Authorization: Bearer <token>` and the full set of masked emails (addresses, descriptions, linked URLs). On Android 11+ logcat is sandboxed per-app, but: (a) an attacker with adb access (USB debugging enabled, work device, MDM) gets the token; (b) device-vendor diagnostic loggers, bug reports (`adb bugreport`), and crash uploaders capture this; (c) any future Crashlytics/Sentry-style integration will capture it automatically.
- **Fix:** Gate the interceptor on `BuildConfig.DEBUG` and downgrade level to `NONE` for release; redact the `Authorization` header even in debug via `setLevel(HEADERS)` + `redactHeader("Authorization")`.
- **PoC:** `adb logcat | grep -iE 'authorization|bearer|maskedemail'` while exercising login + list.
- **Verify:** Build release APK, install, exercise app, run `adb logcat -d | grep -iE 'bearer|MaskedEmail/'` — must be empty.

### F-03 [HIGH] No certificate pinning on Fastmail JMAP traffic
- **File:** `app/src/main/java/com/fastmask/di/NetworkModule.kt:34-45`
- **Evidence:** OkHttp client has no `CertificatePinner`, no Network Security Config XML present, system trust store used.
- **Class:** CWE-295 (Improper Certificate Validation, weak default) / MASVS-NETWORK-2.
- **Impact:** A user-installed CA (corporate MDM, malicious profile, "free wifi" captive) can MITM the long-lived API token. Fastmail tokens do not rotate automatically.
- **Fix:** Add `res/xml/network_security_config.xml` pinning `api.fastmail.com` to the SPKI of the leaf or intermediate cert (with backup pin); reference it via `android:networkSecurityConfig` in the manifest. Or use OkHttp `CertificatePinner` directly. Track pin rotation.
- **Verify:** Run app behind mitmproxy with proxy CA installed as user CA → JMAP requests fail TLS once pinning is on.

### F-04 [HIGH] `android:allowBackup="true"` enables auto-backup of app data
- **File:** `app/src/main/AndroidManifest.xml:9-12`
- **Evidence:** `allowBackup="true"`, with `backup_rules.xml` and `data_extraction_rules.xml` excluding only `fastmask_secure_prefs.xml`.
- **Class:** CWE-200 (Information Exposure) / MASVS-STORAGE-1.
- **Impact:** The encryption key for `EncryptedSharedPreferences` lives in the Android KeyStore and is **not** part of backup; the encrypted blob alone is not directly useful. But: (a) the DataStore preferences (`settings`) and any future non-encrypted file are auto-backed-up by default; (b) the exclude list is path-based and brittle — adding a second encrypted prefs file in the future would silently leak it; (c) backup can be triggered via `adb backup` on Android < 12 if device is unlocked + USB debugging on.
- **Fix:** `allowBackup="false"` (simplest, app has no backup needs). If backup is desired later, switch to a strict allow-list in `data_extraction_rules.xml` (not exclude-list).
- **Verify:** `adb shell bmgr backupnow com.fastmask` on a debug build returns `Backup not allowed`.

### F-05 [HIGH] `androidx.security:security-crypto` pinned to alpha
- **File:** `app/build.gradle.kts:110`
- **Evidence:** `implementation("androidx.security:security-crypto:1.1.0-alpha06")`. This is a 2022 alpha and the line `1.1.0-alpha*` has known issues with key versioning. The current stable line is `1.0.0`; `1.1.0-alpha06` was deprecated in favor of `1.1.0-alpha07` and the project is effectively unmaintained as of 2024.
- **Class:** CWE-1104 (Use of Unmaintained Third-Party Components).
- **Impact:** Token storage relies on an alpha library handling AES-GCM key wrapping; future Android minor versions may break it without a fix. Also blocks dependency-vulnerability tooling (most scanners flag alpha as ineligible).
- **Fix:** Pin to `1.0.0` stable, or migrate to a maintained alternative (DataStore + Tink, or Android KeyStore + manual AES-GCM wrap). Test that existing tokens are decryptable post-upgrade, otherwise plan a forced re-login on first launch.
- **Verify:** `./gradlew dependencies | grep security-crypto` shows `1.0.0`; smoke-test: install old build, log in, upgrade to new build, confirm token still readable.

### F-06 [MEDIUM] Sensitive screens not protected from screen capture
- **Files:** `app/src/main/java/com/fastmask/MainActivity.kt`, `ui/auth/LoginScreen.kt`, `ui/list/MaskedEmailListScreen.kt`, `ui/detail/*.kt`, `ui/create/*.kt`.
- **Evidence:** No `WindowManager.LayoutParams.FLAG_SECURE` set on any window. (To confirm in audit: `grep -r FLAG_SECURE app/src`.)
- **Class:** CWE-200 / MASVS-PLATFORM-3.
- **Impact:** The token-paste field on login and the masked-email list (addresses, linked services) appear in: Recents thumbnail, screenshots, screen recording, screen-share, Android 14 partial screen sharing.
- **Fix:** In `MainActivity.onCreate`, set `window.setFlags(FLAG_SECURE, FLAG_SECURE)`. If only some screens should be protected, toggle per-composable via a `DisposableEffect` that adds/removes the flag.
- **Verify:** Pull Recents → app preview is blanked; `adb shell screencap` returns black image while sensitive screen is foreground.

### F-07 [MEDIUM] JMAP `apiUrl` from session response is used without origin check
- **File:** `app/src/main/java/com/fastmask/data/api/JmapApi.kt:34, 60, 100, 140, 167`
- **Evidence:** `getApiUrl()` returns `cachedSession?.apiUrl` (server-supplied) and is passed verbatim to `jmapService.executeMethod(url = ...)` for every subsequent request. There is no check that the URL is HTTPS or that its host is `*.fastmail.com` / `api.fastmail.com`.
- **Class:** CWE-918 (SSRF, client-side) / CWE-601 (Open Redirect, but for token exfil).
- **Impact:** Conceptually low because the session response itself comes over TLS from `api.fastmail.com` (so a compromised `apiUrl` already implies Fastmail compromise or a successful TLS MITM — see F-03). But defense-in-depth: an attacker who briefly MITMs the session call can pin all subsequent traffic — including the Bearer token — to their own host even after the MITM window closes, because `cachedSession` survives.
- **Fix:** After parsing session, validate `apiUrl.startsWith("https://") && URI(apiUrl).host.endsWith(".fastmail.com")` (or equals `api.fastmail.com`). Reject and clear cache otherwise.
- **Verify:** Mock JmapService to return `{"apiUrl": "https://attacker.example/jmap"}` → `getMaskedEmails()` throws before sending the token.

### F-08 [LOW] Token retained in `LoginUiState` after successful login
- **File:** `app/src/main/java/com/fastmask/ui/auth/LoginViewModel.kt:22, 29` (per recon, to re-verify in audit)
- **Class:** CWE-316 (Cleartext Storage of Sensitive Info in Memory).
- **Impact:** Minor — heap dump, debug breakpoint, or future Compose state snapshotting (e.g., `rememberSaveable` that gets bundled) could capture it. Process isolation makes this hard to exploit on stock Android.
- **Fix:** Clear `_uiState.value.token` (set to empty string) immediately after the login coroutine resolves, regardless of outcome.
- **Verify:** Add a unit test asserting `state.token.isEmpty()` post-success.

---

## Additional checks to perform during audit (not yet executed)

These were not in the recon pass; the audit must run them.

1. **Static / dependency scan:**
   - `./gradlew dependencyUpdates` (or Gradle Versions Plugin) — flag every outdated lib.
   - OWASP Dependency-Check (`dependencyCheckAnalyze`) or `osv-scanner -L app/build.gradle.kts` — list CVEs.
   - Trivy or Grype against the built APK (`trivy fs app/build/outputs/apk/release`).
   - Semgrep with `p/security-audit` and `p/kotlin` rulesets across `app/src/main`.
   - `detekt` with `formatting` + `style` + a security ruleset.
2. **Secret scan over full git history:**
   - `gitleaks detect --source . --log-opts="--all"`
   - `trufflehog git file://. --since-commit=HEAD~500`
   - Manually inspect `.idea/`, `.gradle/config.properties` (currently dirty in git status — see what changed).
3. **Manifest deep dive:**
   - Re-confirm all components (no Provider, Receiver, Service).
   - Check `tools:targetApi`, `enableOnBackInvokedCallback`, `usesCleartextTraffic` (default false on targetSdk 28+, but verify), `android:debuggable` (must be unset).
4. **Manifest + APK static analysis:**
   - `apkanalyzer` or MobSF on the release APK to confirm there are no surprise components.
   - `aapt dump badging` + `aapt dump permissions`.
5. **Reverse-engineer release APK:**
   - Build release, decompile with `jadx`, verify token never appears in plaintext strings, ProGuard mapping is meaningful, `BuildConfig.DEBUG=false`.
6. **Dynamic / runtime tests:**
   - mitmproxy with user CA installed → confirm F-03 (currently traffic is interceptable).
   - mitmproxy with system CA only (Android 7+ default) → baseline.
   - Run app on rooted emulator with Frida hook on `EncryptedSharedPreferences` to confirm token cannot be lifted without root + KeyStore access.
   - `adb backup com.fastmask` and inspect tar contents (F-04).
   - Recents/screenshot test (F-06).
   - Tapjacking: overlay an attacker app on top of `LoginScreen` and confirm the input field's behavior (no `filterTouchesWhenObscured`).
7. **Logic checks (read-only):**
   - JMAP method-call IDs: `add(JsonPrimitive("0"))` is a static client-call-id — fine for single-call requests but verify no multi-call assumption.
   - `parseSetResponseUpdated` correctly verifies `updated.containsKey(id)` (good — already defends against silent no-op responses).
   - `clearSession` is called on logout (`grep -r clearSession`).
   - Token comparison / validation has no timing oracle (only used as a Bearer header — N/A).
8. **CI/CD review:**
   - `.github/workflows/claude.yml` and `claude-code-review.yml`: confirm no `pull_request_target` with PR-author code execution, no untrusted secret echo, no `actions/checkout` of untrusted ref + later token use.
9. **Privacy:**
   - Confirm no third-party SDK exfiltration (already none, but recheck after dependency scan).
   - SECURITY.md / privacy claims vs reality.

---

## Out of scope

- Server-side JMAP / Fastmail account security.
- Physical device attacks beyond Android KeyStore guarantees.
- Social engineering of the user (token-paste workflow inherently requires the user to obtain a token from Fastmail's web UI).
- Performance or non-security code quality.

---

## Output format (per finding)

Each finding rendered as a card in the final report:

```
## F-NN [SEVERITY] <Title>

**File(s):** path:line
**CWE / MASVS:** ...
**Class:** Auth / Crypto / Network / Storage / Logging / Build / etc.

### Evidence
<code snippet + explanation>

### Impact
<concrete attacker scenario, who can exploit, prerequisites>

### Proof of Concept
<minimal repro: adb command, curl, mitmproxy script, etc.>

### Fix
<exact code change with file path>

### Verification
<command + expected output that proves the fix>

### Defense in depth
<additional layers beyond the direct fix>
```

Severity scale: **CRITICAL** (token compromise, RCE, full account takeover), **HIGH** (token compromise under realistic conditions), **MEDIUM** (sensitive data exposure under specific conditions), **LOW** (defense-in-depth gap), **INFO** (hygiene).

---

## Critical files (reference list)

- `app/build.gradle.kts` — signing, deps, build config
- `app/src/main/AndroidManifest.xml` — permissions, components, backup
- `app/src/main/java/com/fastmask/di/NetworkModule.kt` — OkHttp/Retrofit wiring (logging, no pinning)
- `app/src/main/java/com/fastmask/data/api/JmapApi.kt` — token usage, session caching, `apiUrl` trust
- `app/src/main/java/com/fastmask/data/api/JmapService.kt` — endpoint constants
- `app/src/main/java/com/fastmask/data/local/TokenStorage.kt` — encrypted prefs
- `app/src/main/java/com/fastmask/data/local/SettingsDataStore.kt` — DataStore (non-encrypted)
- `app/src/main/java/com/fastmask/data/repository/AuthRepositoryImpl.kt` — login flow
- `app/src/main/java/com/fastmask/ui/auth/LoginViewModel.kt` — token in memory
- `app/src/main/java/com/fastmask/MainActivity.kt` — single Activity, FLAG_SECURE candidate
- `app/proguard-rules.pro` — minification rules
- `app/src/main/res/xml/backup_rules.xml`, `data_extraction_rules.xml`
- `.github/workflows/*.yml` — CI

---

## Verification (how to run the audit end-to-end)

The audit itself is read-only. The verification step is producing the final report and reproducing each F-NN PoC:

```bash
# Static
./gradlew dependencies dependencyUpdates
./gradlew dependencyCheckAnalyze   # if plugin added
gitleaks detect --source . --log-opts="--all"
trufflehog git file://. --since-commit=HEAD~500
semgrep --config p/security-audit --config p/kotlin app/src/main

# Build artifact analysis
./gradlew assembleRelease
apkanalyzer manifest print app/build/outputs/apk/release/app-release.apk
jadx -d /tmp/fastmask-jadx app/build/outputs/apk/release/app-release.apk

# Dynamic
adb install -r app-release.apk
adb logcat -c && adb logcat | tee /tmp/fm.log &
# ... exercise login + list ...
grep -iE 'bearer|MaskedEmail/' /tmp/fm.log   # expect HITS today, NONE after F-02 fix

mitmproxy --mode regular -p 8080
# point device proxy at host, install mitm CA as user CA
# expect: today, all JMAP captured cleanly (F-03); after pinning, TLS fails

adb shell bmgr backupnow com.fastmask     # F-04 today: succeeds
```

Report goes to `Plans/security-audit-report.md` (separate file, written in audit phase) with all findings expanded per the format above, plus an executive summary and a remediation roadmap (CRITICAL → HIGH → MEDIUM → LOW).
