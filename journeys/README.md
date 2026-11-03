# Journeys

Natural-language UI tests ([Android CLI journeys](https://developer.android.com/tools/agents/android-cli/journeys)). An agent evaluates each `<action>` against a running emulator via the `android-cli` skill.

## Run

Ask your agent: "run journeys/welcome-screen.xml with the android-cli skill". Results go to `journeys/results/<name>.md` with screenshots (gitignored).

Run in this order, from a fresh install (`adb shell pm clear com.fastmask`):

| Order | Journey | Starts from |
|---|---|---|
| 1 | `welcome-screen.xml` | fresh install |
| 2 | `login-token-screen.xml` | welcome screen, returns to it |
| 3 | `demo-masks-list.xml` | welcome screen, ends in the demo list |
| 4 | `demo-create-mask.xml` | demo list (after journey 3) |

No journey needs a Fastmail token or network: nothing submits the token, and the demo is local.

## Build and install

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew assembleDebug
android emulator start fastmask_p9 --headless
adb uninstall com.fastmask   # only if a differently signed build is installed
android run --apks=app/build/outputs/apk/debug/app-debug.apk
```

Stop the emulator afterwards: `adb -s <serial> emu kill`.
