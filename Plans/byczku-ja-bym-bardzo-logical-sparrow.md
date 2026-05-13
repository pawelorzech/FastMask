# Plan A→Z: FastMask na Google Play Store

**Owner egzekucji:** Claude (autonomicznie, z Playwright)
**Rola Pawła:** wybory artystyczne, hasła/2FA przy logowaniach, dane JDG, akceptacja akcji zewnętrznych. Nic poza tym.
**SLA (aktualne 2026-05-13):** Organization upgrade FINALIZED dziś o 10:28 (8:28 UTC). Pozostaje: 1–7 dni Google production review (Internal Testing pomijamy lub przelatujemy w 24h bo Organization account = brak wymogu 14-dniowego).
**ETA live na Production:** **2026-05-15 do 2026-05-21** (przyspieszone — payments profile verified w ~24h zamiast 2–7 dni, link dziś rano, AAB gotowy w `app/build/outputs/bundle/release/`).
**Kluczowa zmiana:** zamiast 14-dniowego Closed Testing na koncie osobistym → **upgrade konta do Organization (JDG Pawła Orzecha, nie Cocolab)**. Jednorazowy koszt teraz, ale wszystkie przyszłe apki (LineApp 2.0 etc.) lecą prosto na Production bez 14-dniowego wymogu.

**Live status (2026-05-13):**
- ✅ D-U-N-S Number: **427999274** (YESWAS PAWEŁ ORZECH, nadany 8 min po submit)
- ✅ Google Search Console: orzech.me Domain property verified (TXT przez Bunny DNS API)
- ✅ Play Console website verified (pawel.orzech.me via parent domain)
- ✅ Payments profile YESWAS PAWEŁ ORZECH stworzony i zweryfikowany przez Google (2026-05-13 08:21 UTC)
- ✅ Organization wizard (5 stepów): Account type → Payments → Org info → Contact → Public profile — DONE
- ✅ Document upload: CEIDG `Wydruk.pdf` (Organization) + government photo ID (Authorized representative via OnePlus mobile flow)
- ✅ **Payments profile LINKED → account type = ORGANIZATION** (2026-05-13 08:28 UTC, screenshot `play-after-link-payments.png`, confirmation mail thread `AwCv8-e9t3LN`)
- ✅ Faza 0–3 + 1.1–1.7 (kod + ikona V3 + privacy + copy + feature graphic) DONE i pushed na origin (commits 0abb424, dea9f70, 9d19c43)
- ✅ Faza 5 — signed AAB gotowy: `app/build/outputs/bundle/release/app-release.aab` (6.2 MB, 2026-05-12 10:41)
- ✅ **App utworzony w Play Console** (2026-05-13 08:30 UTC): "FastMask – Fastmail Masks" / com.fastmask / App ID `4975267426879332531`, Free, App. Package name dostępność potwierdzona przez Play Console.
- ✅ **GitHub Pages enabled** dla `pawelorzech/FastMask` (main /docs), URL: `https://pawelorzech.github.io/FastMask/privacy.html`. Build live po ~5 min.
- 🔄 **Faza 6 setup tasks: 7/11 completed + 2 jako draft** (sesja 2026-05-13 10:30-11:06 UTC):
  - ✅ Set privacy policy URL: `https://pawelorzech.github.io/FastMask/privacy.html`
  - ✅ Ads = No
  - ✅ Government apps = No
  - ✅ Financial features = None
  - ✅ Health = None
  - ✅ Content rating = IARC submitted (All Other App Types, all No - email management app)
  - ✅ App category = Productivity
  - ✅ Contact details: email `pawel@orzech.lol`, phone `+48 789 497 469`, website `pawel.orzech.me`
  - 🔄 Data safety (Step 5/5 jako draft): Yes collect Email addresses (Personal info), encryption in transit Yes, accounts created via external Fastmail OAuth-like, data auto-deleted &lt;90 days, purpose: App functionality + Account management. Preview OK. Czeka na **Target audience completion** żeby submit.
  - 🔄 Store listing (jako draft): App name + short + full description z `marketing/copy/en.md` filled. Czeka na **screenshots upload** + **icon-512** + **feature-graphic 1024x500** (asset library blocker — może wymagać click przez UI niedostępny via Playwright).
- ⏸️ **2 taski BLOKED na test Fastmail credentials Pawła (jutro):**
  - App access (wymaga test credentials Fastmail dla Google reviewera + instrukcje)
  - Target audience (zależy od App access)

**Screenshots aplikacji** — Paweł zdecydował "skip na razie, do końca", trzeba zrobić zanim submit do Internal Testing.

---

## Context

App `com.fastmask` jest w v1.5, kod jest gotowy, ale nigdy nie była na Google Play. Eksploracja pokazała stan repo:

| Obszar | Stan | Akcja |
|---|---|---|
| Adaptive icon (foreground/background/monochrome) | ✅ jest, ale placeholderowy glif (3 okręgi + 2 paski, #0066CC) | **Wymiana na warm-ink** |
| Splash screen, themes, colors | ✅ skonfigurowane | bez zmian |
| 20 lokalizacji (`locales_config.xml`) | ✅ pełne | bez zmian |
| `signingConfig.release` (env vars `FASTMASK_KEYSTORE/STORE_PWD/KEY_ALIAS/KEY_PWD`) | ✅ poprawnie poza repo | użyć jako **upload key** w Play App Signing |
| `minifyEnabled=true`, ProGuard rules dla Retrofit/OkHttp/Tink/kotlinx.serialization | ⚠️ brak reguł dla **Hilt** i **Compose** | dodać |
| `shrinkResources` | ❌ brak | dodać `true` w release |
| Crash reporting (Sentry/Crashlytics) | ❌ brak | **dodać Sentry** (rekomendacja, oddzielny gate przed produkcją) |
| Privacy policy | ⚠️ tylko fragment w README + SECURITY.md, brak osobnego URL | wystawić `docs/privacy.md` przez GitHub Pages |
| Screenshots, feature graphic, hi-res icon | ❌ brak | wygenerować |
| Permissions w manifeście | ✅ tylko `INTERNET` | bez zmian |
| Tests | ❌ 0 plików | poza scope tej publikacji |

**Decyzje:**
- Konto: **upgrade osobistego post-2023 → Organization na JDG Pawła Orzecha** (nie Cocolab). Wymaga D-U-N-S + weryfikacji firmy przez Google. **Eliminuje** wymóg 12 testerów × 14 dni dla tej i wszystkich kolejnych apek.
- Ikona: **warm-ink, spójnie z v1.5 UI** (kremy/atrament/ochra)
- Signing: **Play App Signing + upload key** (rekomendowany default)
- Tor: **Internal Testing (24h sanity) → Production** (bez Closed Testing — Organization account omija wymóg)
- **Strategia czasowa: zrównoleglenie.** D-U-N-S i Google verification trwają 4–6 tyg w tle. W tym samym czasie wykonujemy fazy 1–5 (ikona, kod, copy, screenshots, AAB build) tak, by w momencie acceptance Organization mieć wszystko gotowe do upload.

---

## Plan wykonania (11 faz)

### Faza 0 — Pre-flight: katalogi, name check, trademark (samodzielnie, 30 min)

0.1 — Założyć katalog `marketing/` w repo (gitignore'owany dla draftów, commitowane tylko finalne assety):
- `marketing/icon/` — warianty PNG 1024×1024
- `marketing/play/` — feature graphic, hi-res icon, screenshots
- `marketing/copy/` — pl + en short/full description

0.2 — Sprawdzić **dostępność nazwy "FastMask"** na Play (Playwright na `play.google.com/search?q=FastMask` — czy istnieje już aplikacja o tej nazwie). Plan B jeśli zajęte / Google flaguje: `Mask Manager for Fastmail` jako display name na Play, package zostaje `com.fastmask`.

0.3 — Walidacja **trademark Fastmail** — w opisie i FAQ na liście Play wyraźnie:
> "FastMask is an unofficial, open-source client. Not affiliated with or endorsed by Fastmail Pty Ltd."

Bez tego risk rejection.

---

### Faza A — D-U-N-S Number registration (autonomicznie, 2–4 tyg wait)

**Dane firmy (już znane z memory `pawel-profile.md`):**
- Nazwa działalności: **YesWas** (JDG Paweł Orzech)
- Imię i nazwisko: **Paweł Orzech**
- Adres: **ks. Teofila Boguckiego 3A/65, 01-508 Warszawa**
- Telefon: **+48 789 497 469**
- Email: **pawel@orzech.me**
- Forma opodatkowania: ryczałt (PIT-28)

**Do pobrania w A.0:** NIP + REGON — odczytane z publicznego rejestru CEIDG przez `aplikacja.ceidg.gov.pl` (Playwright headless) lub bezpośrednio curl na API CEIDG (`datastore.ceidg.gov.pl`) po imieniu i nazwisku + adresie.

A.0 — Lookup NIP/REGON z CEIDG (read-only, 2 min, zero zaangażowania Pawła).

A.1 — Zgłoszenie do **Dun & Bradstreet (Bisnode Polska)**: `https://www.dnb.com/duns-number/lookup.html` → "Get a D-U-N-S Number" → formularz dla JDG (sole proprietorship). Wybrać opcję **"For Apple/Google verification"** (przyspiesza, free tier).

A.2 — D-U-N-S potwierdzony: **2–4 tygodnie** standardowo (czasem 5 dni roboczych dla "expedited" za free, jeśli zaznaczysz "Apple Developer enrollment" purpose).

A.3 — W okresie czekania: **fazy 1–5 lecą równolegle** (ikona, kod, copy, screenshots, AAB).

A.4 — Daily check email Pawła (Playwright lub via Fastmail MCP) na potwierdzenie D-U-N-S.

---

### Faza B — Play Console: upgrade do Organization (po otrzymaniu D-U-N-S, 1–2 tyg wait)

B.1 — Playwright otwiera `play.google.com/console` na istniejącym koncie osobistym Pawła. **Konto ma już 1 opublikowaną apkę (zgodnie z odpowiedzią) — robimy in-place upgrade, listing tej apki migruje automatycznie pod Organization** (potwierdzone w Google Play Help: "Switching account type doesn't affect existing apps; they migrate to the new organization profile"). Jeżeli Google jednak zablokuje upgrade — fallback: nowe konto Organization za $25 dla FastMask, stare osobiste zostaje z poprzednią apką.

B.2 — Settings → **Developer account** → "Update your account type" → wybór **Organization** → wprowadzenie:
- Legal business name (zgodne z CEIDG i D-U-N-S)
- D-U-N-S Number
- Adres
- Telefon
- Tax ID (NIP)
- Website (`pawel.orzech.me` lub `github.com/pawelorzech/FastMask`)
- Email firmowy
- Skan dokumentu tożsamości Pawła (wymóg dla osoby reprezentującej JDG — Paweł skanuje dowód i wrzuca interaktywnie do prompta)

B.3 — Google weryfikuje:
- D-U-N-S match
- Adres firmowy (potencjalnie list weryfikacyjny pocztą — `1–2 tyg`)
- Numer telefonu (SMS lub call)
- Email firmowy (link weryfikacyjny)

B.4 — Daily monitoring statusu w Play Console: email check + ekran "Developer account verification status".

B.5 — Po acceptance: konto pokazuje "Organization" + brand JDG na publicznym profilu developera w sklepie Play.

---

### Faza 1 — Ikona (warm-ink) (60–90 min)

1.1 — **Generacja przez Gemini Nano Banana API** (model `gemini-2.5-flash-image-preview`):
- 6 wariantów, każdy 1024×1024, format PNG transparentny + PNG na kremowym tle
- Prompt skeleton (4 wariacje promptu × 1–2 generacje):
  > "App icon for 'FastMask', a privacy-focused masked email manager. Style: editorial, warm-ink palette — cream background `#F6F0E4`, ink navy `#1A2238` glyph, single deep-ochra accent `#B8722D`. Glyph: stylized domino/masquerade mask silhouette merged subtly with envelope or '@' symbol. Minimalist, geometric, recognizable at 48dp. Vector-friendly flat shapes, no gradients, no text. Pinterest-tier design, Stripe/Linear aesthetic, NOT generic AI clipart. Square 1024×1024."
- Storage: `marketing/icon/v1_*.png`

1.2 — **Mockup w gridzie ikon Android** (Playwright screenshot z `https://romannurik.github.io/AndroidAssetStudio/icons-launcher.html` lub własny HTML mockup): 6 ikon × na Pixel home screen wallpaper → pokazuję Pawłowi side-by-side.

1.3 — **Wybór przez Pawła** (jedno pytanie `AskUserQuestion` z preview, 6 opcji).

1.4 — **Konwersja zwycięzcy → Android adaptive icon assets:**
- Trzymamy XML-vector adaptive (Android 8+) — wygenerować przez **Android Asset Studio CLI** lub ręcznie SVG → Android Vector Drawable. Pliki:
  - `app/src/main/res/drawable/ic_launcher_foreground.xml` (108×108 dp, glif zajmuje środkowe 72×72 dp safe zone)
  - `app/src/main/res/drawable/ic_launcher_background.xml` (cream `#F6F0E4`)
  - `app/src/main/res/drawable/ic_launcher_monochrome.xml` (ink-navy glif jako pełnia, dla Android 13+ themed icons)
- Fallback rasteryzacja do `mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher.webp` + `ic_launcher_round.webp` przez `cwebp` (dla starszych launcherów < API 26 wciąż używane).

1.5 — **Splash screen update:** `windowSplashScreenBackground` z `#0066CC` → `#F6F0E4` (kremowy), `windowSplashScreenAnimatedIcon` zostaje `ic_launcher_foreground`. Wpływa na pierwsze wrażenie po starcie aplikacji.

1.6 — **Hi-res Play Store icon 512×512** (PNG, bez przezroczystości — Play wymaga full square z tłem) → `marketing/play/icon-512.png`.

1.7 — **Build sanity check:** `./gradlew assembleDebug` + screenshot z emulatora że nowa ikona się renderuje w launcherze.

---

### Faza 2 — Release-readiness fixes (45 min)

2.1 — **ProGuard rules** (`app/proguard-rules.pro`) — dodać:
```proguard
# Hilt
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.lifecycle.HiltViewModel
-keepclasseswithmembers class * { @dagger.hilt.* <methods>; }
-keep @dagger.hilt.android.HiltAndroidApp class *
-keep class hilt_aggregated_deps.** { *; }

# Compose
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }
-keepclassmembers class * { @androidx.compose.runtime.Composable *; }
```

2.2 — **`shrinkResources = true`** w `buildTypes.release`.

2.3 — **Bump wersji:** `versionCode 6 → 7`, `versionName "1.5" → "1.5.1"` (skoro dotyka ProGuard).

2.4 — **Sentry SDK** (rekomendacja, ale zapytam Pawła w faza 0 czy chce):
- `io.sentry:sentry-android:7.x` w deps
- `sentry.properties` z DSN (env var w CI)
- `Sentry.init` w `Application.onCreate`
- Ten gate: jeśli Paweł nie chce — pomijamy, ale wtedy production bez visibility.

2.5 — **Smoke test release buildu** (lokalnie z env vars):
```bash
./gradlew bundleRelease
bundletool build-apks --bundle=app/build/outputs/bundle/release/app-release.aab \
  --output=fastmask.apks --mode=universal
bundletool install-apks --apks=fastmask.apks
```
Plus uruchomienie na emulatorze + 3 ekrany click-through (login → list → detail) żeby potwierdzić że R8 nic nie wykasował.

---

### Faza 3 — Privacy policy + store copy (45 min)

3.1 — **`docs/privacy.md`** (źródło: pierwsze 5 punktów z README "Privacy & Security" + SECURITY.md + GDPR boilerplate):
- Co jest zbierane: API token Fastmaila (lokalnie, EncryptedSharedPreferences) — nigdy nie opuszcza urządzenia poza ruchem do `api.fastmail.com`
- Co NIE jest zbierane: telemetria, analytics, identyfikatory urządzenia
- Komu udostępniane: nikomu poza Fastmailem (target API)
- Retencja: do momentu logout
- Dane dzieci: app oznaczona 18+, nie zbiera danych dzieci
- Kontakt: pawel@orzech.me
- (Jeśli włączymy Sentry) Sentry zbiera anonymized crash data — disclosure

3.2 — **GitHub Pages enable** dla repo (Settings → Pages → main branch /docs). URL będzie: `https://pawelorzech.github.io/FastMask/privacy.html`. Konwertujemy markdown → minimalny HTML przy commitcie.

3.3 — **Store listing copy** (EN + PL — pozostałe 18 języków deferred):
- **App name (50 chars):** `FastMask – Fastmail Masks` (lub plan B z fazy 0.2)
- **Short description (80 chars EN):** `Manage Fastmail masked emails. Open-source, private, no tracking.`
- **Short description (80 chars PL):** `Zarządzaj maskami e-mail Fastmail. Open-source, prywatne, bez śledzenia.`
- **Full description (~1500 chars)** — bazuje na README, dodaje:
  - Lista features (8 punktów z README tabeli)
  - Privacy positioning ("Zero tracking. Zero analytics. Zero servers in the middle.")
  - Wymóg Fastmail account (z linkiem)
  - "Unofficial, open-source. Not affiliated with Fastmail."
  - 20 languages
  - MIT license + GitHub link
- **Whatsnew (500 chars):** v1.5.1 — Security hardening, new icon, Play Store debut.

3.4 — Sklepowa kategoria: **Productivity**. Tagi: email, privacy, fastmail, masked, alias.

---

### Faza 4 — Screenshots (60 min)

4.1 — **Emulator setup:** Pixel 6 (1080×2400) i Pixel Tablet (2560×1600), API 34, język EN + PL, light mode.

4.2 — **Seed data:** stworzyć test account na Fastmailu (Paweł podaje credentials interaktywnie) z 8–10 maskami w różnych stanach (active, disabled, archived) i historią aktywności, żeby lista wyglądała żywo.

4.3 — **Capture przez `adb exec-out screencap`** — 6 ekranów × 2 lokalizacje (EN+PL) × 2 device size = 24 zrzuty. Ekrany:
1. Lista masek z aktywnością (hero shot)
2. Detail z toolbarem akcji
3. Create new mask
4. Search/filter z wynikami
5. Settings (z 20 językami highlighted)
6. Empty/login screen (clean first impression)

4.4 — **Feature graphic 1024×500** (Gemini Nano Banana → mock-up telefonu z naszą ikoną + tagline "Quiet place for your masked addresses"). Cream tło + ink-navy text + serif Instrument Serif feel.

4.5 — Output: `marketing/play/screenshots/{phone,tablet}/{en,pl}/*.png`, plus `feature-graphic-1024x500.png`.

---

### Faza 5 — Keystore weryfikacja + AAB final (30 min)

5.1 — **Confirm Paweł ma keystore** (env vars `FASTMASK_KEYSTORE` itd.). Jeżeli nie istnieje → wygenerować nowy:
```bash
keytool -genkey -v -keystore fastmask-upload.jks -alias fastmask-upload \
  -keyalg RSA -keysize 4096 -validity 10000
```
Backup w `~/Documents/Obsidian Vault/Praca/FastMask/keys/` (poza repo, sensitive-files-protected). Paweł notuje hasło.

5.2 — **Final release build** z bumped wersją:
```bash
export FASTMASK_KEYSTORE=...; export FASTMASK_STORE_PWD=...; \
export FASTMASK_KEY_ALIAS=...; export FASTMASK_KEY_PWD=...
./gradlew clean bundleRelease
```
Output: `app/build/outputs/bundle/release/app-release.aab`.

5.3 — Sanity: rozmiar AAB <50 MB, AAB validation przez `bundletool validate`.

5.4 — Commit zmian (faza 1–3) na branchu `release/1.5.1-play-store`, PR, merge → tag `v1.5.1`.

---

### Faza 6 — Play Console: app creation (Playwright, 30 min)

6.1 — Playwright otwiera `https://play.google.com/console`. Paweł loguje się (hasło + 2FA — czekam aż wpisze). Kontekst persystowany w `~/.config/playwright/play-console-session/`.

6.2 — **Create app:**
- App name: `FastMask – Fastmail Masks`
- Default language: English (United States)
- App or game: App
- Free or paid: Free
- Declarations: I comply with Play Developer Program Policies + US export laws

6.3 — **Main store listing:**
- App name, short description, full description (z fazy 3.3)
- Upload: icon 512×512, feature graphic 1024×500, screenshots (8 phone + 4 tablet × EN), per-language wariant PL (jeśli Play pozwala dla starting set)
- Category: Productivity
- Tags: select 5
- Email kontaktowy: `pawel@orzech.me`
- Privacy policy URL: `https://pawelorzech.github.io/FastMask/privacy.html`

6.4 — **App content:**
- Privacy policy: ⬆ ten URL
- App access: "All functionality available without restrictions" — ALE w sekcji "Are parts restricted?" zaznaczyć "Sign-in required" i podać test credentials (test Fastmail account z fazy 4.2)
- Ads: No
- Content rating: wypełnić IARC questionnaire (10 pytań — `Reference: 1) No violence 2) No sexuality 3) No gambling 4) No drugs 5) No user-generated content shared 6) No location 7) No personal info 8) No purchases 9) No social features 10) Email management functionality`)
- Target audience: 18+ (managing emails, prywatność)
- News app: No
- COVID-19 app: No
- Data safety form (kluczowe):
  - **Collected & used:** Email addresses (the masked aliases you create), Account-bound auth tokens
  - **Collection purposes:** App functionality
  - **Sharing:** None (data goes only to Fastmail API which user authenticates with directly)
  - **Encryption in transit:** Yes (HTTPS only, certificate pinning to system CA)
  - **Encryption at rest:** Yes (EncryptedSharedPreferences)
  - **User can request deletion:** Yes (Logout + uninstall purges)
- Government app: No
- Financial features: No

6.5 — **Pricing & distribution:**
- Free
- Countries: select all (≈173)
- Devices: phones + tablets, exclude Wear/TV/Auto/ChromeOS for v1

---

### Faza 7 — Play App Signing enroll + Internal Testing (30 min — po acceptance Organization z fazy B)

7.1 — **Enroll w Play App Signing** — Play Console → Setup → App signing → "Use Google's automated key generation" lub "Use a key from a Java keystore" → upload PEM z naszego upload keystore. Google generuje **app signing key** (trzymany u Google) i my zostajemy z **upload key**.

7.2 — **Create Internal Testing track** (24h sanity, bez 14-dniowego wymogu — Organization account omija):
- Tracks → Internal testing → Create track
- Testers: do 100 emaili, my dodajemy 2–4: Paweł, ewentualnie 1–2 znajomych. Cel: smoke test na różnych urządzeniach.
- Release name: `1.5.1 (7)`
- Release notes (PL + EN): "Pierwsza wersja FastMask. Krótki internal sanity przed publicznym launch."

7.3 — **Upload AAB:** drag-and-drop `app-release.aab`. Play Console weryfikuje signing, package, supported devices, manifests.

7.4 — **Review & rollout:** Submit do Internal Testing. Google review <24h. Po acceptance — opt-in link aktywny.

7.5 — **Smoke check 24h:** Paweł instaluje na fizycznym Pixelu, klika 6-step flow, raportuje "OK" lub blockery. Bugfix loop jeśli trzeba.

---

### Faza 8 — Production submission (30 min)

8.1 — Play Console → Production track → Create new release.

8.2 — **"Promote from Internal" + Same AAB** (jeden build dla wszystkich tracków = brak dryfu wersji).

8.3 — Wybór **staged rollout 20% → 50% → 100%** (3-dniowe ramp-up).

8.4 — Final review submit. Google production review **1–7 dni** (typowo 2–3 dni dla nowych apek z Organization account; szybciej niż osobiste).

8.5 — Po acceptance — app live na `play.google.com/store/apps/details?id=com.fastmask`.

8.6 — Update README z badgem Play Store + linkiem. Mastodon/Twitter post (Paweł zatwierdza tekst).

---

### Faza 9 — Post-launch (week 1, daily 5 min)

9.1 — Daily check: install volume, crash-free %, recenzje.

9.2 — Respond do recenzji <72h (Paweł zatwierdza tekst).

9.3 — Po tygodniu: retrospective + plan v1.6 (tłumaczenia store listingu na pozostałe 18 języków).

9.4 — **Wartość Organization na przyszłość:** następna apka (LineApp 2.0 release na Play, kolejne side-projects) idzie **prosto na Production**, bez 14-dniowego Closed Testing. Koszt jednorazowy z fazy A+B zwraca się przy drugiej apce.

---

## Critical files (do edycji w execute)

| Plik | Zmiana |
|---|---|
| `app/src/main/res/drawable/ic_launcher_foreground.xml` | nowy glif warm-ink |
| `app/src/main/res/drawable/ic_launcher_background.xml` | tło `#F6F0E4` cream |
| `app/src/main/res/drawable/ic_launcher_monochrome.xml` | nowy mono glif |
| `app/src/main/res/mipmap-{m..xxxh}dpi/ic_launcher.webp` | rasteryzacja fallback |
| `app/src/main/res/values/themes.xml` + `values-v31/themes.xml` | splash bg `#F6F0E4` |
| `app/proguard-rules.pro` | + Hilt + Compose rules |
| `app/build.gradle.kts` | `shrinkResources=true`, version bump, (opcjonalnie) Sentry dep |
| `app/src/main/java/com/fastmask/App.kt` (lub `Application`) | (opcjonalnie) `Sentry.init` |
| `docs/privacy.md` + `docs/privacy.html` | nowy plik privacy policy |
| `marketing/play/**` | nowy katalog z assets |
| `README.md` | + Play Store badge + screenshots z fazy 4 |
| `CHANGELOG.md` | wpis v1.5.1 |

## Reused / istniejące rzeczy

- `signingConfigs.release` z env vars — **zostaje**, używamy jako upload key
- `network_security_config.xml`, `backup_rules.xml`, `data_extraction_rules.xml` — **zostają**, wartość argumentów do data safety form
- `locales_config.xml` z 20 językami — **zostaje**, AAB obsługuje per-language splits z automatu
- Strings.xml z `login_subtitle` ("A quiet place for your masked addresses." / "Spokojne miejsce dla ukrytych adresów.") — **bazowy tagline dla feature graphic + store listing**
- `SECURITY.md` + sekcja "Privacy & Security" w README — **bazowa treść privacy policy**

## Verification (end-to-end checklist)

Każdy z poniższych musi przejść zielono przed Production:

1. [ ] `./gradlew bundleRelease` builduje bez warningów i wytwarza AAB <50 MB
2. [ ] Universal APK z AAB instaluje się na emulatorze + fizycznym Pixelu (Paweł użyczy)
3. [ ] Po instalacji: ikona warm-ink renderuje się w launcherze (jasny i ciemny system)
4. [ ] Splash screen pokazuje kremowe tło + nowy glif (Android 12+)
5. [ ] Login → list → create → detail → settings → logout: 6-step click-through bez crashu w release buildzie z R8
6. [ ] Sentry (jeśli włączony) raportuje testowy `captureMessage` z urządzenia testowego
7. [ ] Privacy policy URL `https://pawelorzech.github.io/FastMask/privacy.html` zwraca 200 i jest czytelny mobile
8. [ ] Play Console "Pre-launch report" zielony (Google odpala app na farmie urządzeń)
9. [ ] Data safety form zaakceptowany przez Google bez warnings
10. [ ] Content rating: IARC certyfikat wygenerowany
11. [ ] 12+ testerów ma status "Installed" w Closed Testing przez ≥14 ciągłych dni
12. [ ] Crash-free sessions ≥99.5% w okresie testing (próg arbitralny, raczej zgubione warningi niż blockery)
13. [ ] Production promotion zaakceptowany przez Google review
14. [ ] App widoczny na `play.google.com/store/apps/details?id=com.fastmask`
15. [ ] Install z Play działa, app uruchamia się, login do Fastmail przechodzi

---

## Open Questions / Risks

**Q1: Konto Play — upgrade istniejącego osobistego (✅ zdecydowane)**
- Konto ma 1 aktywną apkę. Robimy **in-place upgrade**: konto + istniejąca apka migrują pod Organization (JDG Pawła). Brand publiczny developera zmienia się z osoby na nazwę JDG.
- Risk: Google może w specyficznych przypadkach zablokować upgrade z aktywnym listingiem. Fallback: jeżeli odmowa → nowe Organization za $25 dla FastMask, stara apka zostaje na osobistym.

**Q2: D-U-N-S Number — aplikujemy free (✅ zdecydowane)**
- Faza A startuje od submisji wniosku do Bisnode/D&B. Czas oczekiwania 2–4 tyg w tle, w tym czasie fazy 1–5 (ikona, kod, copy, screenshots, AAB) lecą równolegle.

**Q3: Sentry przed produkcją — TAK czy NIE?**
- Recommendation: **TAK.** Bez crash reportingu nie wiesz, że apka się sypie u userów dopóki nie dostaniesz 1-gwiazdkowych recenzji. Sentry free tier (5k events/mies.) wystarczy na long ogon FastMaska.
- Domyślne zachowanie planu: dodajemy Sentry. Powiedz "stop" jeżeli nie.

**Q4: Nazwa "FastMask" vs Fastmail trademark**
- Risk: średni. "Fast" + "mask" semantically suggests Fastmail. Play może flagować, Fastmail (firma) może wysłać DMCA.
- Mitygacja: w opisie, na ekranie Login i w privacy policy wyraźny disclaimer. Plan B `Mask Manager for Fastmail` jako display name jeżeli pierwszy submit zostanie odrzucony — package id nie zmieniamy.
- Powiedz mi w fazie A jeżeli chcesz proaktywnie zmienić display name na "Mask Manager for Fastmail" (bezpieczniej).

**Q5: Test credentials dla Google review**
- Google reviewer musi móc się zalogować do appki. Plan: utworzyć dedykowany Fastmail test account (Paweł podaje email/hasło interaktywnie w fazie 4.2), podajemy w Play Console "App access".
- Alternatywnie: konto z trial Fastmail (30 dni) wystarczy do review (3 dni).

**Q6: 2FA Google podczas Playwright login**
- Paweł musi być przy sesji przez pierwsze ~5 minut pierwszego logowania do Play Console. Po tym session cookie persystowany.

**Q7: Co jeżeli D-U-N-S nie przyjdzie w 4 tyg?**
- D-U-N-S free tier może utknąć — fallback: D-U-N-S "expedited" za $229 USD (5 dni roboczych gwarantowane).
- Drugi fallback: po 4 tyg czekania, jeżeli desperacja → zostawiamy plan Organization na drugą apkę, FastMask wrzucamy mimo wszystko na osobiste z 14-dniowym Closed Testing.
- Decyzja: dam Ci znać po 3 tyg jeżeli nic nie przychodzi.

**Q8: Co z LineApp 2.0 i deadline'em demo 30.04?**
- Według TELOS deadline LineApp Demo 30.04 już minął (dzisiaj 2026-05-11). FastMask publikacja nie wpływa na ten torcz — to side-project. Plan tej apki idzie niezależnie od LineApp 2.0.

---

## Capabilities/skills do wykorzystania w execute

- `Media` skill — generacja ikony przez Gemini Nano Banana, mockup feature graphic
- `Engineer` agent — implementacja ProGuard rules, version bump, Sentry init
- `frontend-design` skill (jeśli potrzebny redesign privacy.html / store landing)
- `playwright` MCP tools — Play Console automation
- `Plan` agent (Phase 8 reviews przed Production promotion) — review przed pchnięciem live
- `simplify` po zmianach w `app/build.gradle.kts` i `proguard-rules.pro` — quality gate

## Po zatwierdzeniu planu

1. Zapytam Cię w 1–2 follow-upach: nazwa Plan A vs Plan B, Sentry TAK/NIE, lista testerów źródło
2. Tworzę gałąź `release/1.5.1-play-store`
3. Start fazą 1 (ikona) — pierwsze artefakty wracają do Ciebie do akceptacji w ~30 min
