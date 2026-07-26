# Release checklist

Steps that live outside this repository and therefore cannot be enforced by a test.
Run through it before uploading an AAB.

## Every release

- [ ] `./gradlew testDebugUnitTest lintDebug assembleRelease` green
- [ ] `versionCode` / `versionName` bumped in `app/build.gradle.kts` (a published `versionCode` cannot be reused)
- [ ] `CHANGELOG.md` entry dated
- [ ] `docs/privacy.md` names the version any newly disclosed processing starts from — GitHub Pages publishes on merge, so the policy goes live before the build does

## When telemetry or a third-party SDK changes

- [ ] **Play Console → App content → Data safety** matches the privacy policy. Since 1.10.0 the app collects *Crash logs* and *Device or other IDs*: collected, not shared, optional (the user can switch it off), purpose "App functionality". A listing that contradicts the policy it links to is a Play policy violation, not just a documentation gap.
- [ ] **Google Cloud Console → project `fastmask-c03e3` → Credentials**: the Android API key committed in `app/google-services.json` has an Application restriction of "Android apps" for package `com.fastmask` with the release *and* upload signing SHA-1, and an API restriction covering only the Firebase Installations / Crashlytics APIs. The key is public by design; the restriction is the only thing stopping it from being used against this project's billing, and it lives in the console, not in this repo.
- [ ] No billable API (Maps, Places, Translate) is enabled on the Firebase project.
