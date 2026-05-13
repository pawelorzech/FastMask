# Status check FastMask Play Store + następny krok

**Data:** 2026-05-13

## Context

Pytanie Pawła: "co robiliśmy, jak był kolejny krok, sprawdź Fastmaila — jest mail od Google". Cel: zsynchronizować się ze stanem planu A→Z (`Plans/byczku-ja-bym-bardzo-logical-sparrow.md`) i podjąć następny ruch w stronę publikacji FastMask na Google Play.

## Co robiliśmy (stan 2026-05-12 z głównego planu)

- ✅ D-U-N-S Number **427999274** nadany (YESWAS PAWEŁ ORZECH, 8 min po submit)
- ✅ Google Search Console: `orzech.me` Domain property verified (TXT przez Bunny DNS)
- ✅ Play Console website verified (`pawel.orzech.me`)
- ✅ Payments profile YESWAS PAWEŁ ORZECH **utworzony** i zlinkowany do dev account
- ✅ Organization wizard 5 stepów: Account type → Payments → Org info → Contact → Public profile
- ✅ Document upload: CEIDG `Wydruk.pdf` + government photo ID (OnePlus mobile flow)
- ✅ Faza 0–3 i 1.1–1.7 (kod, ikona warm-ink V3, privacy policy, copy, feature graphic) — commits `0abb424`, `dea9f70`, `9d19c43` na `release/1.5.1-play-store`
- 🔄 **Faza 5 — final signed AAB build w tle** (do potwierdzenia czy skończył)
- ⏳ Google review konta Organization — czekaliśmy 2–7 dni

**Kolejny krok wg planu z 12.05:** czekać na email od Google z acceptance Organization → dopiero potem Faza 6 (create app w Play Console) i 7 (Internal Testing).

## Co znalazłem w Fastmailu (świeży mail z dziś 2026-05-13 08:21)

**Od:** Google Play Console &lt;noreply-play-console@google.com&gt;
**Do:** pawelorzech@me.com
**Temat:** "To finish updating your account type in Play Console, link your verified payments profile"

Cytat:
> "your selected new payments profile **has now been successfully verified**, and can be linked to your developer account in Play Console. Until you link your verified payments profile, your developer account will continue to use the information from your existing profile, and your developer account will **remain as a personal account**."

CTA w mailu: `https://play.google.com/console/developers/account/developer-details?tab=aboutYou` → przycisk **"Link payments profile"**.

### Co to znaczy

1. ✅ **Google zaakceptował payments profile YESWAS PAWEŁ ORZECH** — szybciej niż prognozowane 2–7 dni (~24h).
2. ⚠️ **Wpis w planie z 12.05 ("payments profile linked do dev account") był optymistyczny** — profile był wtedy stworzony ale jeszcze nie zlinkowany jako aktywny po weryfikacji. Teraz Google wprost mówi: dopóki nie klikniesz "Link", konto **zostaje osobiste**.
3. 🎯 To **odblokowuje wszystko**: po kliknięciu Link konto zmienia się na Organization (JDG YesWas Paweł Orzech) — odpada wymóg 14-dni Closed Testing, FastMask może iść prosto na Production.

## Co robię dalej — w pełni autonomicznie z Playwright

**Twoja rola: NIC, dopóki Playwright nie utknie na 2FA / hasło / capture / wybór wizualny.** Wszystkie kliki, formy, uploady — ja.

**Krok 1 — Link payments profile (autonomicznie z Playwright, 5 min):**
1. Playwright otwiera persisted session `~/.config/playwright/play-console-session/` (cookie z poprzedniej sesji org-wizarda powinien być żywy)
2. Jeżeli session active → idzie prosto do `https://play.google.com/console/developers/account/developer-details?tab=aboutYou`
3. Jeżeli session wygasł → loguje do `accounts.google.com`, wyświetla email Pawła (`pawelorzech@me.com`) → **PROSZĘ CIĘ TYLKO O HASŁO + 2FA** (jedno pytanie via AskUserQuestion z "Other" do wpisania kodu)
4. Klika **"Link payments profile"** → potwierdza dialog → screenshot stanu po: `play-after-link-payments.png`
5. Sprawdza Fastmail za ~3 min czy przyszedł mail confirmation account_type=Organization
6. Aktualizuje główny plan `Plans/byczku-ja-bym-bardzo-logical-sparrow.md` (Live status: 12.05 → 13.05)

**Krok 2 — Status AAB (autonomicznie, 30s):**
- `git log --oneline release/1.5.1-play-store -10` + `ls -la app/build/outputs/bundle/release/`
- Jeżeli AAB jest → idę do Kroku 3
- Jeżeli nie ma → `./gradlew clean bundleRelease` z env vars keystore (JDK 17 z Android Studio JBR per memory), czekam, weryfikuję rozmiar &lt;50 MB

**Krok 3 — Faza 6: Create app w Play Console (autonomicznie z Playwright, 30 min):**
- Klika "Create app" → wypełnia nazwę "FastMask – Fastmail Masks", language EN-US, App+Free, declarations checked
- Wgrywa: icon 512×512 (`marketing/play/icon-512.png` lub z `play-icon-final.png`), feature graphic 1024×500, screenshots phone+tablet × EN+PL
- Wypełnia copy z Fazy 3.3 (short/full description PL+EN)
- Privacy policy URL, contact email
- Data safety form (auto-wypełniam wg planu 6.4)
- Content rating IARC (10 pytań — auto)
- Pricing & distribution: Free, ~173 krajów, phones+tablets
- **Tylko proszę Cię, jeżeli:** test Fastmail credentials potrzebne dla "App access" (Google reviewer login) — jeden AskUserQuestion z email+hasło test accounta

**Krok 4 — Faza 7: Internal Testing (autonomicznie):**
- Enroll w Play App Signing
- Create Internal Testing track, testers = `pawelorzech@me.com`, `pawel@orzech.me`
- Upload AAB, submit do review
- Czekam na Google &lt;24h, monitoruję Fastmail
- Po acceptance: opt-in link aktywny, **proszę Cię** tylko o smoke test na fizycznym Pixelu (10 min Twojego czasu)

**Krok 5 — Faza 8: Production (autonomicznie, po Twoim "ok smoke test passed"):**
- Promote from Internal, staged rollout 20%
- Submit do production review
- Monitoring Fastmail na acceptance (1–7 dni, typowo 2–3 dla Organization)
- Po accept: README badge + post draft (Mastodon/Twitter — pokażę Ci tekst do akceptacji przed pchnięciem)

## Punkty kontaktu z Tobą (tylko te)

1. **Krok 1.3** — hasło Google + 2FA do Play Console (jeżeli session wygasł)
2. **Krok 3** — test Fastmail credentials dla Google reviewer (jeżeli nie mamy zapisanych)
3. **Krok 4.5** — smoke test na fizycznym Pixelu (10 min) + "ok / blocker X"
4. **Krok 5.4** — akceptacja tekstu posta o launch (jeżeli chcesz to ogłosić)

Każdy inny case (Google odrzuca AAB, R8 strip, content rating warning, etc.) → debug autonomicznie, raportuję wynik.

## Pliki do zaktualizowania po linkowaniu

- `Plans/byczku-ja-bym-bardzo-logical-sparrow.md` — sekcja Live status (12.05 → 13.05), wpis: "✅ Payments profile linked, account type = Organization (potwierdzone screenshotem 2026-05-13)"

## Verification (jak będę wiedział że krok się udał)

1. Po kliknięciu "Link payments profile": widoczny stan "Account type: Organization" + brand "YESWAS PAWEŁ ORZECH" na ekranie Developer details
2. Screenshot zapisany do `play-after-link-payments.png` w repo (consistent z istniejącym naming `play-after-*.png`)
3. Mail od Google Play Console potwierdzający zmianę account type (sprawdzę Fastmaila ponownie ~5 min po linku)

## Open questions (przed startem)

Brak. Jadę.
