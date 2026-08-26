# Release checklist

Steps that live outside this repository and therefore cannot be enforced by a test.
Run through it before uploading an AAB.

## Every release

- [ ] `./gradlew testDebugUnitTest lintDebug bundleRelease` green
- [ ] `versionCode` / `versionName` bumped in `app/build.gradle.kts` (a published `versionCode` cannot be reused)
- [ ] `CHANGELOG.md` entry dated
- [ ] Play release notes and store copy match the shipped behavior; screenshots/icon are current
- [ ] Signed `app-release.aab`, `mapping.txt` and native symbols (when present) are archived; verify the upload-key certificate
- [ ] Install the AAB-generated splits with `bundletool` or use an internal test track, then run `connectedDebugAndroidTest` plus a manual launch/create/archive/restore smoke
- [ ] On a Play test build, exercise Billing approve, decline, pending, restore and acknowledgement with a license tester
- [ ] Test both flexible and immediate in-app updates from a lower `versionCode`, including a previously interrupted immediate update
- [ ] Review the Play pre-launch report and Android vitals; check the final AAB for unexpected native `.so` files and 16 KB page-size compatibility
- [ ] Recheck permissions and Play Data safety, define staged-rollout stop criteria and record the rollback version
- [ ] `docs/privacy.md` names the version any newly disclosed processing starts from — GitHub Pages publishes on merge, so the policy goes live before the build does

## When telemetry or a third-party SDK changes

- [ ] **Play Console → App content → Data safety** matches the privacy policy. Since 1.10.0 the app collects *Crash logs* and *Device or other IDs*: collected, not shared, optional (the user can switch it off), purpose "App functionality". A listing that contradicts the policy it links to is a Play policy violation, not just a documentation gap.
- [ ] `app/google-services.json` is present locally before building the release AAB. It is **not** in the repository (`.gitignore`), and the build now skips the Firebase plugins when it is missing — so a release built without it would ship with crash reporting silently inert rather than failing loudly. Confirm the release build log does *not* contain "building without Firebase Crashlytics".
- [ ] **Google Cloud Console → project `fastmask-c03e3` → Credentials**: the Android API key in your local `app/google-services.json` has an Application restriction of "Android apps" for package `com.fastmask` with the release *and* upload signing SHA-1, and an API restriction covering only the Firebase Installations / Crashlytics APIs. The key is public by design (it ships inside the APK); the restriction is the only thing stopping it from being used against this project's billing, and it lives in the console, not in this repo.
- [ ] No billable API (Maps, Places, Translate) is enabled on the Firebase project.
