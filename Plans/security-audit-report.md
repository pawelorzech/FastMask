# FastMask — Security Audit Report

**App:** FastMask Android client for Fastmail Masked Email
**Audit date:** 2026-04-27
**Audited revision:** `4dcd75f` (main, dirty: only IDE/Gradle config files modified — not source)
**Auditor methodology:** OWASP Top 10 / API Top 10, OWASP MASVS, OWASP ASVS L2, CWE Top 25, Android-specific (MASVS-PLATFORM, MASVS-NETWORK, MASVS-STORAGE, MASVS-CRYPTO, MASVS-CODE).
**Scope:** Static review of `app/src/main`, `app/build.gradle.kts`, `AndroidManifest.xml`, `proguard-rules.pro`, GitHub workflows, full git history. No dynamic testing was executed (release APK was not built); all dynamic PoCs are reproducible per-finding.

---

## Executive summary

FastMask is a thin, single-purpose client. The architecture is clean, the attack surface is small (one Activity, INTERNET permission only, no IPC, no WebView, no third-party SDKs), and there are several deliberate good-practice choices (`EncryptedSharedPreferences` with the modern Tink-backed `MasterKey`, HTTPS-only base URLs, no logging via `Log.*`, no analytics/crash reporting, server-confirmed JMAP `updated`/`destroyed` checks).

The audit identified **2 CRITICAL**, **3 HIGH**, **2 MEDIUM**, **2 LOW**, and **3 INFO** issues. The two CRITICAL issues — release builds signed with the public Android debug keystore (F-01) and unconditional HTTP body logging that captures the Bearer token in `logcat` (F-02) — must be fixed before any signed release is shipped. The HIGH issues (no certificate pinning, `allowBackup="true"`, alpha `security-crypto`) materially weaken the token-protection promise made in `SECURITY.md`.

| ID | Severity | Title |
| --- | --- | --- |
| [F-01](#f-01-critical) | CRITICAL | Release build signed with public Android debug keystore |
| [F-02](#f-02-critical) | CRITICAL | `HttpLoggingInterceptor` at BODY level logs Bearer token + masked-email PII to `logcat` in every build |
| [F-03](#f-03-high) | HIGH | No certificate pinning on Fastmail JMAP traffic |
| [F-04](#f-04-high) | HIGH | `android:allowBackup="true"` with brittle exclude-list (deny-list, not allow-list) |
| [F-05](#f-05-high) | HIGH | `androidx.security:security-crypto` pinned to alpha (`1.1.0-alpha06`) — handles token AES-GCM |
| [F-06](#f-06-medium) | MEDIUM | No `FLAG_SECURE` — token-paste field, masked-email list and details appear in screenshots, screen recording, Recents thumbnail |
| [F-07](#f-07-medium) | MEDIUM | Server-supplied JMAP `apiUrl` cached and used without origin validation |
| [F-08](#f-08-low) | LOW | API token retained in `LoginUiState.token` after successful login |
| [F-09](#f-09-low) | LOW | `SECURITY.md` advertises "certificate pinning is recommended for production builds" — the production build does not implement it |
| [F-10](#f-10-info) | INFO | `.idea/` and `.gradle/config.properties` tracked in git despite `.gitignore` |
| [F-11](#f-11-info) | INFO | GitHub Actions pinned to moving tag `@v4`/`@v1` instead of commit SHA |
| [F-12](#f-12-info) | INFO | Token-paste field has no tapjacking protection (`filterTouchesWhenObscured`) |

**Remediation roadmap (ordered):**

1. **Block release** until F-01 (signing) and F-02 (logging) are fixed.
2. **Within first patch release** — F-03 (cert pinning), F-04 (`allowBackup="false"`), F-05 (downgrade to stable `security-crypto 1.0.0`).
3. **Next minor** — F-06 (`FLAG_SECURE`), F-07 (`apiUrl` host validation).
4. **Backlog** — F-08, F-09 (sync `SECURITY.md`), F-10 (clean `git rm --cached`), F-11 (pin actions to SHA), F-12 (tapjacking flag).

Severity rubric used:

- **CRITICAL** — direct, single-step path to API-token compromise or full account takeover under realistic conditions.
- **HIGH** — token compromise possible under common-but-specific conditions (MITM with user-installed CA, device backup, library bug).
- **MEDIUM** — sensitive-data exposure in narrower conditions (screen capture, post-MITM persistence).
- **LOW** — defense-in-depth gap; exploitation requires chained prerequisites.
- **INFO** — hygiene / documentation / supply-chain.

---

## Findings

### F-01 [CRITICAL]

**Title:** Release build signed with public Android debug keystore
**File:** `app/build.gradle.kts:26-33`
**CWE / MASVS:** CWE-321 (Use of Hard-coded Cryptographic Key); MASVS-CODE-2 (signing integrity).
**Class:** Build / Code integrity.

#### Evidence

```kotlin
// app/build.gradle.kts:26-33
signingConfigs {
    create("release") {
        storeFile = file("${System.getProperty("user.home")}/.android/debug.keystore")
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
    }
}
```

The `release` build type is wired to this config (`buildTypes.release.signingConfig = signingConfigs.getByName("release")` at `app/build.gradle.kts:38`). The keystore is the standard Android SDK debug keystore, the credentials are documented public defaults (`android`/`androiddebugkey`/`android`), and the keystore itself is generated identically on every developer's machine.

#### Impact

The Android debug keystore is not a secret. Any third party can:

1. Take a published `app-release.apk`, repackage it with malicious code (e.g., exfiltrate the Bearer token to an attacker-controlled server), and re-sign it with their own copy of the debug keystore — producing an APK that the OS sees as an "update" of the original (matching signature → automatic install on `adb install -r`, or pushed via fake-update social engineering).
2. Trivially fail Google Play's signing requirements (Play Store rejects debug-signed APKs), so the only viable distribution channel becomes sideload — where the lack of signature authenticity matters most.
3. Defeat any future hardening that relies on signature checks (e.g., a server-side check on a client-cert hash, or the OS' update-signature check that would normally protect users from tampered updates).

This is a single-step root-of-trust compromise. Severity is CRITICAL because the very first signed release ships with no integrity guarantee.

#### Proof of Concept

```bash
./gradlew :app:assembleRelease
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
# Subject: CN=Android Debug, O=Android, C=US
# SHA-256 digest matches the universal default-debug-keystore digest documented at
# https://developers.google.com/android/guides/client-auth — i.e. globally non-unique.
```

#### Fix

1. Generate a real release keystore offline (kept out of git):
   ```bash
   keytool -genkeypair -v \
     -keystore fastmask-release.jks -alias fastmask \
     -keyalg RSA -keysize 4096 -validity 10000 \
     -storetype JKS
   ```
2. Replace the signing config to read from environment / Gradle properties (no secrets in `build.gradle.kts`):
   ```kotlin
   // app/build.gradle.kts
   signingConfigs {
       create("release") {
           val keystoreFile = System.getenv("FASTMASK_KEYSTORE")
               ?: project.findProperty("fastmask.keystore") as String?
           if (keystoreFile != null) {
               storeFile = file(keystoreFile)
               storePassword = System.getenv("FASTMASK_STORE_PWD")
                   ?: project.property("fastmask.storePassword") as String
               keyAlias = System.getenv("FASTMASK_KEY_ALIAS")
                   ?: project.property("fastmask.keyAlias") as String
               keyPassword = System.getenv("FASTMASK_KEY_PWD")
                   ?: project.property("fastmask.keyPassword") as String
           }
       }
   }
   buildTypes {
       release {
           isMinifyEnabled = true
           if (System.getenv("FASTMASK_KEYSTORE") != null
               || project.hasProperty("fastmask.keystore")) {
               signingConfig = signingConfigs.getByName("release")
           }
           // Otherwise: leave unsigned; require explicit signing by CI/Play App Signing.
           proguardFiles(
               getDefaultProguardFile("proguard-android-optimize.txt"),
               "proguard-rules.pro",
           )
       }
   }
   ```
3. Prefer Google Play App Signing (upload-key model) so the repository never sees the production-signing key.
4. Hold any already-distributed release APKs as compromised; rotate by publishing a new build under the new signing identity (users will need to uninstall/reinstall).

#### Verification

```bash
unset FASTMASK_KEYSTORE
./gradlew :app:assembleRelease
# Build either produces an unsigned APK (preferred) or fails with a clear
# "no signing config" error. The previous debug-key fallback is gone.

FASTMASK_KEYSTORE=$PWD/fastmask-release.jks FASTMASK_STORE_PWD=... \
FASTMASK_KEY_ALIAS=fastmask FASTMASK_KEY_PWD=... ./gradlew :app:assembleRelease
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
# Subject: CN=FastMask Release, ... (your identity, NOT "Android Debug")
```

#### Defense in depth

- Add a CI gate (`apksigner verify --print-certs ... | grep -v "Android Debug"`).
- Document the build/release process in `CONTRIBUTING.md` (where the signing keystore lives, who has access, rotation policy).
- Consider Play Integrity API for runtime tamper detection.

---

### F-02 [CRITICAL]

**Title:** `HttpLoggingInterceptor` at BODY level logs Bearer token and masked-email PII to `logcat` in every build
**File:** `app/src/main/java/com/fastmask/di/NetworkModule.kt:32-45`
**CWE / MASVS:** CWE-532 (Insertion of Sensitive Information into Log File); MASVS-STORAGE-2 (no sensitive data in logs).
**Class:** Logging.

#### Evidence

```kotlin
// app/src/main/java/com/fastmask/di/NetworkModule.kt:32-45
@Provides
@Singleton
fun provideOkHttpClient(): OkHttpClient {
    val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY      // <-- always BODY, no DEBUG guard
    }
    return OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
}
```

`Level.BODY` writes the full request line, all request headers (including `Authorization: Bearer <token>`), the request body, the full response status/headers, and the response body. The interceptor is added unconditionally — `BuildConfig.DEBUG` is never checked, and there is no `redactHeader` call.

#### Impact

Every JMAP request — `getSession`, `MaskedEmail/get`, `MaskedEmail/set` (create/update/destroy) — emits to `logcat` (and therefore to any pipeline that ingests `logcat`):

- The full Bearer token (long-lived Fastmail API token, full account scope), once per call.
- All masked-email aliases, descriptions, linked URLs/domains, and state changes.

Concrete attacker scenarios:

1. **adb / USB-debugging access.** Work-managed device, lost device with USB debugging on, repair shop, MDM, dev pairing. `adb logcat -d | grep -i bearer` returns the live token.
2. **Bug reports.** `adb bugreport`, vendor diagnostic uploaders (e.g., Samsung's "Send error report"), and crash forwarders bundle `logcat` and may upload to OEM/cloud.
3. **Future telemetry SDKs.** If Crashlytics, Sentry, Bugsnag, or any custom logger is added later, it will pick up these messages by default and ship them to a third-party server.
4. **Co-resident apps with `READ_LOGS` (Android < 4.1, or rooted devices, or system apps).** Rare on modern devices but real on the long tail (minSdk = 26 covers Android 8.0 onward).

The token grants full Fastmail account access for masked-email management; revocation requires manual user action in the Fastmail web UI.

Severity is CRITICAL because the bar to extraction is low (`adb logcat`) and the prize is full token compromise.

#### Proof of Concept

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -c
# launch app, paste a token, press Continue
adb logcat -d | grep -iE 'bearer|maskedemail|api/' | head -20
# You will see:
# Authorization: Bearer fmu1-xxxxxxxxxx-...
# {"using":["urn:ietf:params:jmap:core","https://www.fastmail.com/dev/maskedemail"], ...}
# alias_xxxxxxxxx@fastmail.com  description: "github.com"  state: enabled  ...
```

The same is true on `assembleRelease` because the interceptor has no `BuildConfig.DEBUG` gate.

#### Fix

In `app/build.gradle.kts` ensure `buildFeatures { buildConfig = true }` (already present). Then:

```kotlin
// app/src/main/java/com/fastmask/di/NetworkModule.kt
import com.fastmask.BuildConfig

@Provides
@Singleton
fun provideOkHttpClient(): OkHttpClient {
    val builder = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)

    if (BuildConfig.DEBUG) {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.HEADERS
            redactHeader("Authorization")
            redactHeader("Cookie")
            redactHeader("Set-Cookie")
        }
        builder.addInterceptor(logging)
    }

    return builder.build()
}
```

Rationale:

- `BuildConfig.DEBUG` is `false` for the `release` build type → release ships with no logging interceptor at all.
- In debug, `Level.HEADERS` is sufficient for diagnosing 4xx/5xx without dumping JMAP bodies (which contain user PII in plaintext).
- `redactHeader("Authorization")` ensures the token is replaced with `**` even when developing.

#### Verification

```bash
./gradlew :app:assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
adb logcat -c
# launch app, paste token, exercise list
adb logcat -d | grep -iE 'bearer|fmu1-|alias_|MaskedEmail/' | wc -l
# Expected: 0
```

Add a unit test asserting that the `OkHttpClient` produced in a `BuildConfig.DEBUG = false` context has zero `HttpLoggingInterceptor` instances among its `interceptors`.

#### Defense in depth

- Strip `Log.*` classes in release with a ProGuard rule (`-assumenosideeffects class android.util.Log { *; }`) so any future careless logging is silently dropped from release.
- If telemetry is ever added, configure a custom `Logger` that explicitly redacts `Authorization`, `Cookie`, and any token-shaped string (`fmu1-…`).

---

### F-03 [HIGH]

**Title:** No certificate pinning on Fastmail JMAP traffic
**File:** `app/src/main/java/com/fastmask/di/NetworkModule.kt:32-45`; absent `res/xml/network_security_config.xml`; `AndroidManifest.xml:7-19` (no `android:networkSecurityConfig` attribute).
**CWE / MASVS:** CWE-295 (Improper Certificate Validation, default-trust); MASVS-NETWORK-2.
**Class:** Network.

#### Evidence

The OkHttp client builder has no `.certificatePinner(...)` call. There is no Network Security Config XML referenced by the manifest. Android's default behavior on `targetSdk = 28+` is: (a) cleartext disabled by default — good; (b) **system CA store and user-installed CAs** are trusted (until `targetSdk = 24+` fully restricts user CAs, but the default config still trusts the system store).

The Bearer token is long-lived and not auto-rotated by the app.

#### Impact

A user-installed CA — common via corporate MDM, "free wifi" captive profiles on iOS-derived enterprise tooling, Burp/mitmproxy CA on a developer device, or a malicious profile pushed via social engineering — allows transparent MITM of `api.fastmail.com`. The first JMAP request after setup leaks the Bearer token.

Once captured, the attacker has full masked-email management for the account. `SECURITY.md:42` already acknowledges this: *"Certificate pinning is recommended for production builds."* That recommendation has not been implemented.

#### Proof of Concept

```bash
mitmproxy --mode regular --listen-port 8080
# In Android Settings → Wi-Fi → proxy → host=<mitm host>, port=8080
# Settings → Security → Encryption & credentials → Install a certificate (CA)
# install mitm CA as a "user CA"

# Launch FastMask, paste a token. mitmproxy shows:
# GET https://api.fastmail.com/jmap/session
# Authorization: Bearer fmu1-...
```

#### Fix

Add a Network Security Config that pins the SPKI hash of `api.fastmail.com`'s leaf and intermediate certificates, and restricts trust anchors to the system store (excluding user-installed CAs).

```xml
<!-- app/src/main/res/xml/network_security_config.xml -->
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <domain-config cleartextTrafficPermitted="false">
        <domain includeSubdomains="false">api.fastmail.com</domain>

        <!-- Replace with the actual SPKI sha256 of Fastmail's current
             intermediate (primary) and a backup (fallback root or alternate
             intermediate). Compute via:
               openssl s_client -connect api.fastmail.com:443 -showcerts </dev/null \
                 | openssl x509 -pubkey -noout \
                 | openssl pkey -pubin -outform DER \
                 | openssl dgst -sha256 -binary | base64
        -->
        <pin-set expiration="2027-01-01">
            <pin digest="SHA-256">REPLACE_WITH_PRIMARY_SPKI_HASH</pin>
            <pin digest="SHA-256">REPLACE_WITH_BACKUP_SPKI_HASH</pin>
        </pin-set>

        <trust-anchors>
            <certificates src="system"/>
        </trust-anchors>
    </domain-config>
</network-security-config>
```

```xml
<!-- AndroidManifest.xml — add to <application ...> -->
android:networkSecurityConfig="@xml/network_security_config"
```

Operational note: pin two hashes (current + backup), set an explicit `expiration` you can observe via dependency-bot, and document the rotation procedure (Fastmail's intermediate may rotate). Build a small CI job that fetches the live SPKI weekly and alerts on change.

#### Verification

With user CA installed (as in PoC) and the new config in place, FastMask login fails at TLS:

```bash
adb logcat -d | grep -iE 'CertificatePinner|SSLHandshakeException|Pin verification failed'
# javax.net.ssl.SSLPeerUnverifiedException: Certificate pinning failure!
```

Without MITM, the app continues to work normally.

#### Defense in depth

- Combine pinning with `ConnectionSpec.RESTRICTED_TLS` (TLS 1.2+, modern ciphers only) on the OkHttp client.
- Future: implement token rotation flow (`POST /jmap/session` re-issuance) so a one-shot leak has a bounded blast radius.

---

### F-04 [HIGH]

**Title:** `android:allowBackup="true"` with brittle deny-list rules
**Files:**
- `app/src/main/AndroidManifest.xml:9, 12, 10` (`allowBackup`, `fullBackupContent`, `dataExtractionRules`)
- `app/src/main/res/xml/backup_rules.xml`
- `app/src/main/res/xml/data_extraction_rules.xml`

**CWE / MASVS:** CWE-200 (Information Exposure); MASVS-STORAGE-1 (no sensitive data in shared/world-readable storage), MASVS-STORAGE-3 (data extraction rules).
**Class:** Storage / Backup.

#### Evidence

```xml
<!-- AndroidManifest.xml -->
<application
    android:allowBackup="true"
    android:dataExtractionRules="@xml/data_extraction_rules"
    android:fullBackupContent="@xml/backup_rules"
    ...>
```

```xml
<!-- backup_rules.xml -->
<full-backup-content>
    <exclude domain="sharedpref" path="fastmask_secure_prefs.xml"/>
</full-backup-content>
```

```xml
<!-- data_extraction_rules.xml -->
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="sharedpref" path="fastmask_secure_prefs.xml"/>
    </cloud-backup>
    <device-transfer>
        <exclude domain="sharedpref" path="fastmask_secure_prefs.xml"/>
    </device-transfer>
</data-extraction-rules>
```

These are exclude-lists — every other file under `data/data/com.fastmask/` (DataStore preferences, future files) is implicitly included.

#### Impact

The `EncryptedSharedPreferences` file is excluded by name, and the actual encryption key lives in the Android KeyStore (not part of any backup) — so the encrypted blob alone is useless. That part is fine. However:

1. **Future-leak risk.** If a second encrypted prefs file is added or `EncryptedSharedPreferences` is migrated to a different name, it will be silently included in backups until the rules are updated. Allow-lists fail safe; deny-lists fail open.
2. **`adb backup` on dev devices.** On emulators and on rooted/dev-mode devices, `adb backup com.fastmask` produces a `.ab` archive containing the (encrypted) prefs blob and any auto-backed-up DataStore prefs (`settings.preferences_pb`). This is mostly low-value (language code), but confirms the backup pipeline is live.
3. **Cloud account compromise.** If the user's Google account is compromised, the attacker can restore the app on a new device and recover any non-excluded data. Today that's only the language preference — but the surface is unnecessary.
4. **`SECURITY.md` says "No sensitive data is stored in plain text" and "No data is sent to third-party servers"** — Google cloud backup is a third-party server; cloud backup of any future prefs would silently break that promise.

#### Proof of Concept

```bash
adb shell bmgr backupnow com.fastmask
# (or, on Android < 12 with a transport configured: adb backup com.fastmask -f /tmp/fm.ab)

adb shell run-as com.fastmask ls -la /data/data/com.fastmask/files /data/data/com.fastmask/shared_prefs
# Confirms the files exist; backup engine sees them per the deny-list rules.
```

#### Fix

Switch to `android:allowBackup="false"` (the simplest correct answer for an app that has nothing it would benefit from backing up — the user's tokens and aliases are recoverable by re-authenticating to Fastmail):

```xml
<!-- AndroidManifest.xml -->
<application
    android:allowBackup="false"
    android:dataExtractionRules="@xml/data_extraction_rules"
    ...>
    <!-- you can remove android:fullBackupContent at this point -->
```

If a future product decision wants backup, switch `data_extraction_rules.xml` to an explicit allow-list:

```xml
<data-extraction-rules>
    <cloud-backup disableIfNoEncryptionCapabilities="true">
        <include domain="sharedpref" path="settings"/>
        <!-- Do NOT include fastmask_secure_prefs.xml -->
    </cloud-backup>
    <device-transfer>
        <include domain="sharedpref" path="settings"/>
    </device-transfer>
</data-extraction-rules>
```

#### Verification

```bash
adb shell bmgr backupnow com.fastmask
# expected: "Backup not allowed for com.fastmask" (allowBackup=false)
adb backup com.fastmask -f /tmp/fm.ab
# expected: empty/zero-byte archive
```

#### Defense in depth

- Re-run this check whenever a new file is added under `data/data/com.fastmask/`.
- Add a lint rule (e.g., custom Detekt rule or an Android Lint flag) that fails CI when `allowBackup` is set to true without an explicit allow-list.

---

### F-05 [HIGH]

**Title:** `androidx.security:security-crypto` pinned to alpha
**File:** `app/build.gradle.kts:110`
**CWE / MASVS:** CWE-1104 (Use of Unmaintained Third-Party Components); MASVS-CRYPTO-2 (use of standard, well-maintained libraries).
**Class:** Crypto / Dependency.

#### Evidence

```kotlin
// app/build.gradle.kts:110
implementation("androidx.security:security-crypto:1.1.0-alpha06")
```

`androidx.security:security-crypto` is the library wrapping Tink behind `MasterKey` + `EncryptedSharedPreferences` (used in `app/src/main/java/com/fastmask/data/local/TokenStorage.kt:5-6, 16-26`). Version `1.1.0-alpha*` was published in 2022, has not been promoted to beta or stable since, and the entire library line is effectively dormant as of the audit date. Stable line is `1.0.0`.

#### Impact

Token storage — the highest-value secret in the app — depends on an alpha-marked library with:

- No security-fix SLA (it is alpha; unmaintained alphas are how known issues become unpatched issues).
- Known migration friction between alpha versions in this line; if a future Android version subtly breaks the alpha implementation (key wrapping, key versioning), the app will silently start failing or, worse, fall back to default behavior.
- Most dependency-vulnerability scanners exclude alpha versions from CVE matching, so a CVE filed against `1.1.x` may not be flagged for a project pinning `-alpha06`.

This is not an exploitable vulnerability today. It is a structural risk that materially weakens the strongest claim in `SECURITY.md`: *"Encryption uses AES-256-GCM for values and AES-256-SIV for keys."*

#### Proof of Concept

```bash
./gradlew :app:dependencies --configuration releaseRuntimeClasspath \
  | grep security-crypto
# androidx.security:security-crypto:1.1.0-alpha06
```

Inspect AndroidX release notes — no stable promotion; check `https://maven.google.com/web/index.html?#androidx.security:security-crypto` for activity.

#### Fix

Move to the stable line:

```kotlin
// app/build.gradle.kts
implementation("androidx.security:security-crypto:1.0.0")
```

The public API surface used by `TokenStorage` (`MasterKey.Builder`, `EncryptedSharedPreferences.create` with `AES256_SIV` / `AES256_GCM` schemes) is identical across `1.0.0` and `1.1.0-alpha06`, so no `TokenStorage.kt` changes should be required. Run a full re-install + login + relaunch test post-upgrade to confirm stored tokens are still readable.

If `1.0.0` proves limiting in the future, the maintained alternative is to write tokens via DataStore (or plain files) protected by Tink's `AeadFactory.getAead` keyed by a key wrapped in the Android KeyStore — explicit but stable.

#### Verification

```bash
./gradlew :app:dependencies --configuration releaseRuntimeClasspath \
  | grep security-crypto
# expect: androidx.security:security-crypto:1.0.0
```

Smoke test:
1. Install the current build, log in, force-stop, then upgrade to the new build (`adb install -r`) and relaunch.
2. App should land on the email list (token still readable) without prompting for re-login.

#### Defense in depth

- Add a one-time migration path: on first launch after upgrade, attempt to read the token; on `SecurityException`/decryption failure, force re-login rather than crashing. (Today, decryption failure surfaces as an exception with no recovery UI.)
- Add `dependencyCheckAnalyze` (OWASP Dependency-Check Gradle plugin) to CI so future alpha pins surface immediately.

---

### F-06 [MEDIUM]

**Title:** No `FLAG_SECURE` — token-paste field, masked-email list and details appear in screenshots, screen recording, and Recents thumbnails
**Files:** `app/src/main/java/com/fastmask/MainActivity.kt:28-58`; no `FLAG_SECURE` reference anywhere under `app/src/`.
**CWE / MASVS:** CWE-200; MASVS-PLATFORM-3 (sensitive screens not exposed).
**Class:** UI / Platform.

#### Evidence

`grep -rn "FLAG_SECURE" app/src` returns nothing. `MainActivity.onCreate` does not set window flags. Compose screens (`LoginScreen`, `MaskedEmailListScreen`, detail/create screens) use `Scaffold` without any window-level flag manipulation.

#### Impact

The token-paste input on `LoginScreen` (visible while the user is typing/pasting their long-lived API token, including via Android's clipboard preview) and the `MaskedEmailListScreen`/`MaskedEmailDetailScreen` (which show every alias address, description, and linked URL — a behavioral profile of the user) appear in:

- Screenshots taken via system gestures.
- Screen recording (Android 11+ built-in recorder).
- Screen-share via casting or third-party apps.
- The Recents/multitasking thumbnail (visible when the user briefly switches apps).
- Android 14 Partial Screen Sharing (an attacker-installed app can request a per-app share scope).

Token compromise via screenshot is unlikely (the field uses `PasswordVisualTransformation` so the token is dotted by default), but the show-token toggle (`var showToken by remember { mutableStateOf(false) }`) reveals the cleartext token; if a screenshot is taken while toggled visible, the token is captured. Aliases and descriptions are always plaintext.

#### Proof of Concept

1. Launch FastMask, log in, switch to Recents — the email list is rendered in the Recents thumbnail.
2. On the login screen, tap "show token", then `adb shell screencap /sdcard/cap.png` — the token appears in cleartext.

#### Fix

Add `FLAG_SECURE` to the single window in `MainActivity` (covers all Compose screens since the app has one Activity):

```kotlin
// app/src/main/java/com/fastmask/MainActivity.kt
import android.view.WindowManager

override fun onCreate(savedInstanceState: Bundle?) {
    val splashScreen = installSplashScreen()
    super.onCreate(savedInstanceState)

    window.setFlags(
        WindowManager.LayoutParams.FLAG_SECURE,
        WindowManager.LayoutParams.FLAG_SECURE,
    )

    splashScreen.setKeepOnScreenCondition { !isReady }
    enableEdgeToEdge()
    // ...
}
```

If the app later wants to allow screenshots on innocuous screens (e.g., Settings) for support purposes, scope the flag per-composable:

```kotlin
@Composable
fun SecureWindow(content: @Composable () -> Unit) {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window ?: return@DisposableEffect onDispose {}
        window.setFlags(FLAG_SECURE, FLAG_SECURE)
        onDispose { window.clearFlags(FLAG_SECURE) }
    }
    content()
}
```

#### Verification

- `adb shell screencap` while on `LoginScreen` → produces black/blank PNG.
- Recents thumbnail of FastMask shows blank/app-icon placeholder rather than UI content.

#### Defense in depth

- For the show-token toggle, auto-revert after 10 seconds (`LaunchedEffect(showToken) { delay(10_000); showToken = false }`).
- Consider clearing the clipboard after a successful login if the token was pasted.

---

### F-07 [MEDIUM]

**Title:** Server-supplied JMAP `apiUrl` cached and used without origin validation
**File:** `app/src/main/java/com/fastmask/data/api/JmapApi.kt:20-40` (`cachedSession`, `getApiUrl`); usages at `:60, :100, :140, :167`.
**CWE / MASVS:** CWE-918 (SSRF, client-side variant); CWE-601 (Open Redirect; here as token-exfil endpoint redirect); MASVS-NETWORK-1.
**Class:** Network / Trust boundary.

#### Evidence

```kotlin
// JmapApi.kt:20-40
private var cachedSession: JmapSession? = null
private var cachedAccountId: String? = null

suspend fun getSession(token: String): Result<JmapSession> = runCatching {
    val authHeader = "Bearer $token"
    jmapService.getSession(authHeader = authHeader).also {
        cachedSession = it
        cachedAccountId = it.primaryAccounts["https://www.fastmail.com/dev/maskedemail"]
            ?: it.primaryAccounts.values.firstOrNull()
    }
}

fun getApiUrl(): String = cachedSession?.apiUrl ?: JmapService.FASTMAIL_API_URL
```

Every method (`getMaskedEmails`, `createMaskedEmail`, `updateMaskedEmail`, `deleteMaskedEmail`) calls `jmapService.executeMethod(url = getApiUrl(), authHeader = "Bearer $token", ...)`. There is no validation that `cachedSession.apiUrl` is HTTPS or that its host is `api.fastmail.com` (or `*.fastmail.com`).

#### Impact

The session response itself comes over TLS from `api.fastmail.com`, so under normal conditions `apiUrl` is honest. However:

- An attacker who briefly MITMs the **session call** (window of opportunity: until cert pinning F-03 is implemented) can substitute `apiUrl: "https://attacker.example/jmap"`. The cached value persists for the lifetime of the `JmapApi` singleton and across all subsequent JMAP method calls. Even after the MITM window closes, the token continues to be sent — over TLS — to the attacker's server.
- Combined with F-03, this turns a transient MITM into a permanent (until app restart) credential-exfiltration channel.
- `cachedSession` is cleared only via `clearSession()` on logout; there is no expiration or re-validation.

#### Proof of Concept

Mock test (no MITM required to reproduce the vulnerable path):

```kotlin
// Replace JmapService with a stub that returns an attacker-controlled apiUrl
val stub = object : JmapService { /* getSession returns JmapSession(
    apiUrl = "https://attacker.example/jmap/api/", primaryAccounts = mapOf(...))
   executeMethod observes the URL it's called with  */ }
val api = JmapApi(stub, Json.Default)
api.getSession("real-token").getOrThrow()
api.getMaskedEmails("real-token")
// stub records: executeMethod was called with url="https://attacker.example/jmap/api/"
//               authHeader="Bearer real-token"
```

#### Fix

Validate `apiUrl` (and `downloadUrl`/`uploadUrl` if ever consumed) immediately after parsing the session, and reject the session entirely on mismatch:

```kotlin
// JmapApi.kt
import java.net.URI

suspend fun getSession(token: String): Result<JmapSession> = runCatching {
    val authHeader = "Bearer $token"
    val session = jmapService.getSession(authHeader = authHeader)
    require(isFastmailHttpsUrl(session.apiUrl)) {
        "Untrusted apiUrl in session response: ${session.apiUrl}"
    }
    cachedSession = session
    cachedAccountId = session.primaryAccounts["https://www.fastmail.com/dev/maskedemail"]
        ?: session.primaryAccounts.values.firstOrNull()
    session
}

private fun isFastmailHttpsUrl(raw: String): Boolean {
    val uri = runCatching { URI(raw) }.getOrNull() ?: return false
    if (!uri.scheme.equals("https", ignoreCase = true)) return false
    val host = uri.host?.lowercase() ?: return false
    return host == "api.fastmail.com" || host.endsWith(".fastmail.com")
}
```

(If a stricter policy is preferred, pin to `api.fastmail.com` only.)

#### Verification

Add a unit test that supplies a `JmapService` test double returning `apiUrl = "https://attacker.example/jmap/"` and asserts that `getSession()` returns `Result.failure` with `IllegalArgumentException` and that `cachedSession` remains null.

#### Defense in depth

- Reduce session TTL: clear `cachedSession` on app backgrounding or after N minutes.
- Pin `api.fastmail.com` in the Network Security Config (F-03) — this fix and that fix are complementary, not redundant.

---

### F-08 [LOW]

**Title:** API token retained in `LoginUiState.token` after successful login
**File:** `app/src/main/java/com/fastmask/ui/auth/LoginViewModel.kt:42-47, 22, 28-30, 34, 61-65`
**CWE / MASVS:** CWE-316 (Cleartext Storage of Sensitive Information in Memory).
**Class:** Memory hygiene.

#### Evidence

```kotlin
// LoginViewModel.kt:32-58 (excerpt)
fun login() {
    val token = _uiState.value.token.filterNot { it.isWhitespace() }
    if (token.isBlank()) { ...; return }
    viewModelScope.launch {
        _uiState.update { it.copy(isLoading = true, error = null) }
        loginUseCase(token).fold(
            onSuccess = {
                _uiState.update { it.copy(isLoading = false) }   // <-- token stays
                _events.emit(LoginEvent.LoginSuccess)
            },
            onFailure = { error ->
                _uiState.update {
                    it.copy(isLoading = false, error = error.message ?: "Login failed")
                }                                                 // <-- token stays
            },
        )
    }
}
```

`LoginUiState.token` is never reset; the ViewModel survives `LoginScreen` recomposition and is cleared only when the ViewModel is destroyed (typically when the back stack drops the login route, which doesn't happen on success — the app navigates forward but the ViewModel scope may still resolve later).

#### Impact

The cleartext token sits in heap memory after login. Realistic exploit paths are narrow on stock Android (process isolation prevents another app from reading the heap), but:

- A heap dump (debug build, developer-mode profile capture) contains the token in cleartext.
- A future `rememberSaveable` refactor that mistakenly saves the token would persist it through `Bundle` (and `onSaveInstanceState` writes it to disk during process death).
- Compose UI Inspector (Layout Inspector via Android Studio) can read state values when the app is debuggable.

Severity LOW because exploitation requires either a debug build or a chained code change.

#### Fix

Clear the token from `_uiState` immediately after the login attempt resolves, regardless of outcome:

```kotlin
viewModelScope.launch {
    _uiState.update { it.copy(isLoading = true, error = null) }
    val outcome = loginUseCase(token)
    _uiState.update { it.copy(token = "", isLoading = false) }  // always clear
    outcome.fold(
        onSuccess = { _events.emit(LoginEvent.LoginSuccess) },
        onFailure = { error ->
            _uiState.update { it.copy(error = error.message ?: "Login failed") }
        },
    )
}
```

#### Verification

Unit test:
```kotlin
@Test
fun `token cleared from state after login attempt`() = runTest {
    val vm = LoginViewModel(fakeLoginUseCaseAlwaysSucceeds)
    vm.onTokenChange("fmu1-secret")
    vm.login()
    advanceUntilIdle()
    assertEquals("", vm.uiState.value.token)
}
```

#### Defense in depth

- Use `CharArray` rather than `String` for the token in the input field; zero it on submit. (Android `EditText`/Compose `TextField` doesn't natively support `CharArray`; this is a higher-effort change.)
- Disable Layout Inspector / Compose tooling in release (already implicit because release isn't debuggable, but worth verifying as part of CI).

---

### F-09 [LOW]

**Title:** `SECURITY.md` advertises certificate pinning as recommended; production build does not implement it
**File:** `SECURITY.md:39-43`; tied to F-03.
**Class:** Documentation / Policy.

#### Evidence

```markdown
### Network Security
- All communication with Fastmail uses HTTPS/TLS
- Certificate pinning is recommended for production builds
- No data is sent to third-party servers
```

The current production build (debug-keystore-signed; this is itself F-01) ships without pinning. The SECURITY.md statement reads as a present-tense capability of the app to a casual reader.

#### Impact

Trust mismatch. A user who reads `SECURITY.md` to evaluate whether to entrust their Fastmail token to FastMask comes away with an inflated sense of network security. Once F-03 is fixed, this finding closes automatically.

#### Fix

Two options:

1. (Preferred) Implement F-03 and rewrite the bullet to state the present:
   *"Certificate pinning is enforced for `api.fastmail.com` via Android's Network Security Config; pin rotation is documented in CONTRIBUTING.md."*
2. Until F-03 lands, rewrite the bullet to be honest:
   *"All communication with Fastmail uses HTTPS/TLS. Certificate pinning is planned (see issue #N); the current build relies on the system CA store, and a user-installed root CA could intercept traffic."*

#### Verification

`grep "Certificate pinning is recommended" SECURITY.md` → no result post-fix.

---

### F-10 [INFO]

**Title:** `.idea/` and `.gradle/config.properties` tracked in git despite `.gitignore`
**Files:** `git ls-files .idea/ .gradle/` returns 12 tracked entries; `.gitignore:30, 46-47` lists `.gradle/`, `.idea/`, `*.iml`.
**Class:** Hygiene.

#### Evidence

```
$ git ls-files .gradle/ .idea/
.gradle/config.properties
.idea/.gitignore
.idea/AndroidProjectSystem.xml
.idea/appInsightsSettings.xml
.idea/caches/deviceStreaming.xml
.idea/compiler.xml
.idea/deploymentTargetSelector.xml
.idea/gradle.xml
.idea/migrations.xml
.idea/misc.xml
.idea/runConfigurations.xml
.idea/vcs.xml
```

Files were committed before `.gitignore` exclusions took effect. Dirty tree at audit time also shows ongoing churn in these files (`git status` lists `.idea/caches/deviceStreaming.xml`, `.idea/gradle.xml`, `.idea/misc.xml`, `.gradle/config.properties` as modified).

#### Impact

- Not security-critical: contents observed are JDK paths, IDE module configuration, GradleHome cache fingerprints. No tokens, no signing keys.
- However: `.idea/deploymentTargetSelector.xml` and `.idea/caches/deviceStreaming.xml` can leak personal device IDs, emulator names, or development-environment fingerprints over time. Treat as a privacy nit.
- IDE config drift between developers creates noisy diffs and can mask legitimate changes during code review.

#### Fix

```bash
git rm -r --cached .idea/ .gradle/
git commit -m "chore: untrack IDE/Gradle local config (already in .gitignore)"
```

If a small subset of `.idea/` (e.g., `.idea/codeStyles/`, `.idea/inspectionProfiles/`) is genuinely team-shared, re-add only those files explicitly and add a `.idea/!keep` documentation note.

#### Verification

```bash
git ls-files .gradle/ .idea/  # expected: empty output
```

---

### F-11 [INFO]

**Title:** GitHub Actions pinned to moving tags (`@v4`, `@v1`) instead of commit SHAs
**Files:** `.github/workflows/claude.yml:29, 35`; `.github/workflows/claude-code-review.yml:30, 36`.
**Class:** Supply chain.

#### Evidence

```yaml
- uses: actions/checkout@v4              # claude.yml:29, claude-code-review.yml:30
- uses: anthropics/claude-code-action@v1 # claude.yml:35, claude-code-review.yml:36
```

Both workflows have read-only repository permissions plus `id-token: write` and consume `secrets.CLAUDE_CODE_OAUTH_TOKEN`. Triggers are PR comments / PR events — *not* `pull_request_target`, which is correct.

#### Impact

A compromise of the upstream `actions/checkout` or `anthropics/claude-code-action` repository (or a maintainer account that retags `v1`) would execute attacker code in the workflow with access to:

- The repository contents (read-only, but the OAuth token is in env).
- `id-token: write` (OIDC token issuance — primarily useful for cloud-provider federation; only a real risk if combined with a federated trust relationship).
- `CLAUDE_CODE_OAUTH_TOKEN` (Anthropic auth — narrow blast radius, but still a credential).

Because this repo doesn't run secrets-bearing deploy steps, the practical impact is bounded. `pull_request_target` is not used, so PR authors cannot inject code into the trusted workflow context. This is mostly defensive hygiene.

#### Fix

Pin to commit SHAs and document the version comment:

```yaml
- uses: actions/checkout@b4ffde65f46336ab88eb53be808477a3936bae11  # v4.1.1
- uses: anthropics/claude-code-action@<sha>                         # v1.x
```

Use Dependabot for actions to track upstream updates:

```yaml
# .github/dependabot.yml
version: 2
updates:
  - package-ecosystem: "github-actions"
    directory: "/"
    schedule: { interval: "weekly" }
```

#### Verification

`grep -E "uses: .+@[a-f0-9]{40}" .github/workflows/*.yml` should match every `uses:` line.

---

### F-12 [INFO]

**Title:** Token-paste field has no tapjacking protection
**File:** `app/src/main/java/com/fastmask/ui/auth/LoginScreen.kt` (uses `DesignInput`/`OutlinedTextField`); `app/src/main/AndroidManifest.xml` has no `android:filterTouchesWhenObscured` on the activity.
**CWE / MASVS:** CWE-1021 (Improper Restriction of Rendered UI Layers); MASVS-PLATFORM-1.
**Class:** UI / Platform.

#### Evidence

No call to `setFilterTouchesWhenObscured(true)` anywhere; no `filterTouchesWhenObscured="true"` in the manifest or layouts. Compose-equivalent is `Modifier.semantics { filterTouchesWhenObscured = true }` on the relevant nodes — not used.

#### Impact

A malicious overlay app (drawn over other apps permission, or an accessibility overlay on older devices) could trick the user into typing/pasting their token into a transparent field. Modern Android requires explicit user grant of `SYSTEM_ALERT_WINDOW` and the OS shows warnings, so this requires the user to fall for the install + permission prompt. Lowest realistic severity.

#### Fix

```kotlin
// MainActivity.onCreate
window.decorView.filterTouchesWhenObscured = true
```

Or per-composable in Compose:

```kotlin
Modifier.semantics { filterTouchesWhenObscured = true }
// applied to the LoginScreen container
```

#### Verification

Manual: install a test overlay app (e.g., the AOSP `OverlayApp` sample), grant overlay permission, position over the login token field, attempt to interact — taps should be dropped while the overlay is visible.

---

## What was checked and found clean (no findings)

These were inspected and considered safe in the current revision; documenting them prevents a future audit from re-checking the same surface and helps the team see what changed if these areas regress.

- **`Log.*` / `println` usage in app code** — `grep -rn "Log\." app/src` returns zero matches. (HTTP body logging via OkHttp is the only logging channel — handled in F-02.)
- **Token stored at rest** — `EncryptedSharedPreferences` with `MasterKey.KeyScheme.AES256_GCM`, `PrefKeyEncryptionScheme.AES256_SIV`, `PrefValueEncryptionScheme.AES256_GCM` (`TokenStorage.kt:16-26`). Backed by Android KeyStore. Correct usage, modulo the alpha library version (F-05).
- **Token validated before persistence** — `AuthRepositoryImpl.login` calls `jmapApi.getSession(token)` and only invokes `tokenStorage.saveToken(token)` on success (`AuthRepositoryImpl.kt:15-19`). No invalid token is ever persisted.
- **Logout cleanup** — `AuthRepositoryImpl.logout` calls both `tokenStorage.clearToken()` and `jmapApi.clearSession()` (`AuthRepositoryImpl.kt:21-24`). Cached `accountId` is also cleared (`JmapApi.kt:264-267`).
- **JMAP server-confirmation** — `parseSetResponseUpdated` explicitly verifies the server returned the id under `updated`, defending against silent no-op responses (`JmapApi.kt:237-240`). `parseSetResponseDestroyed` and `parseSetResponseCreated` similarly check `notDestroyed` / `notCreated` before treating the call as successful.
- **Token visual masking** — `LoginScreen` uses `PasswordVisualTransformation` by default; the show/hide toggle is in-memory only.
- **Permissions** — `AndroidManifest.xml` declares only `INTERNET`. No `READ_/WRITE_EXTERNAL_STORAGE`, no contact/calendar/SMS permissions, no `SYSTEM_ALERT_WINDOW`, no `BIND_ACCESSIBILITY_SERVICE`.
- **Exposed components** — single Activity (`MainActivity`, exported with MAIN/LAUNCHER only). No `<provider>`, `<receiver>`, or `<service>`. No `intent-filter` with deep-link schemes or custom data.
- **`debuggable` flag** — not set in manifest; defaults to `false` for `release` (`build.gradle.kts` doesn't override).
- **Cleartext traffic** — `usesCleartextTraffic` not set; default for `targetSdk = 28+` is `false`. No `http://` URLs anywhere in code (`grep -rn "http://" app/src` returns only XML namespace declarations).
- **WebView / IPC / external schemes** — none. Single outgoing intent (`Intent.ACTION_SENDTO` with `mailto:`) uses `resolveActivity()` before launching (`SettingsScreen.kt:149-159` per recon).
- **Analytics / telemetry / crash reporting** — none. No Firebase, no Crashlytics, no Sentry, no Bugsnag, no Mixpanel, no AppCenter. Confirmed by `grep -rn "firebase\|crashlytics\|sentry\|bugsnag" app/build.gradle.kts app/src` returning zero matches.
- **ProGuard rules** — `proguard-rules.pro` correctly preserves kotlinx-serialization generated classes, Retrofit suspend continuations, and Tink internals. No unintentional reflection retention.
- **Git history** — manually scanned all 22 commits via `git log --all -p`; the only matches for "password"/"token"/"secret" are: (a) the F-01 debug-keystore credentials, (b) `KEY_API_TOKEN = "api_token"` (a key name, not a secret), (c) `PasswordVisualTransformation` Compose imports, (d) `secrets.CLAUDE_CODE_OAUTH_TOKEN` GitHub Actions reference. No leaked tokens in any historical revision.
- **`gradle.properties`** — contains only JVM args, AndroidX flag, Kotlin code style, R-class flag. No secrets.
- **`local.properties`** — gitignored (`.gitignore:35`). Not in `git ls-files`.
- **Token rotation defense (timing-attack)** — token is only used as a Bearer header value; no string comparison on-device, so timing oracles are not a concern.

---

## Tools the auditor would have run if available

The following tools were not installed on the audit host (`gitleaks`, `trufflehog`, `osv-scanner`, `semgrep`, `apkanalyzer` all reported `not found`). Their results would have been incorporated as additional sub-findings under existing F-NNs or as new INFO findings. The team should run them as part of the remediation pass:

```bash
brew install gitleaks trufflehog osv-scanner semgrep
brew install --cask android-platform-tools  # apkanalyzer

gitleaks detect --source . --log-opts="--all"
trufflehog git file://. --since-commit=HEAD~500
osv-scanner -L app/build.gradle.kts
semgrep --config p/security-audit --config p/kotlin --config p/owasp-top-ten app/src/main

./gradlew :app:assembleRelease
apkanalyzer manifest print app/build/outputs/apk/release/app-release.apk
apkanalyzer dex packages app/build/outputs/apk/release/app-release.apk | head
```

Add an OWASP Dependency-Check Gradle plugin run as a CI step (`./gradlew dependencyCheckAnalyze`). Consider Detekt with the `detekt-rules-libraries` ruleset to lint future regressions.

---

## Recommended follow-up

1. **Open one issue per finding** in the FastMask GitHub repo, labeled by severity. Cross-link to this document.
2. **Block release of any signed APK** until F-01 + F-02 are fixed. Tag the next release as `v1.5-security`.
3. **Run the dynamic verification commands** (in each F-NN's "Verification" section) on the patched build before publishing.
4. **Update `SECURITY.md`** to reflect the post-fix reality (F-09).
5. **Add CI checks**: signing-cert verification, OWASP Dependency-Check, gitleaks pre-commit hook, action-pin verification.

---

*End of report.*
