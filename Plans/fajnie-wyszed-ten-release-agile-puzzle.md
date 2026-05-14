# Plan: Fix ikony FastMask (safe-zone + czarne rogi) → nowy build do Play

## Context

Release v1.5.1 jest w drodze do Google Play. Paweł zauważył dwa wizualne defekty ikony, które trzeba pilnie poprawić i wypuścić nowy build:

1. **Brak safe-zone w ikonie launchera** — glif `@` w warstwie foreground sięga krawędzi płótna (0% marginesu). Adaptive icon Androida maskuje ikonę do okręgu/squircle'a (widoczne 72dp ze 108dp, safe-zone 66dp), więc glif jest przycinany na krawędziach i wygląda na "wciśnięty".
2. **Czarne fragmenty na ekranie logowania** — `WelcomeScreen` renderuje `R.drawable.ic_launcher_foreground` bezpośrednio (88dp). Cztery rogi tego PNG to **nieprzezroczysta czerń `(0,0,0,255)`** zamiast przezroczystości → na kremowym tle widać czarne narożniki. Ten sam asset jest też animowaną ikoną splash screena.

### Diagnoza (zweryfikowana odczytem pikseli)

- `app/src/main/res/drawable-*/ic_launcher_foreground.png` (5 gęstości: 108/162/216/324/432 px) — glif `@` (navy `~(25,43,58)`) na przezroczystości, ALE 4 rogi = opaque black. Glif wypełnia ~100% płótna.
- `app/src/main/res/drawable-*/ic_launcher_monochrome.png` (5 gęstości) — ten sam problem: czarny glif + czarne rogi, brak safe-zone.
- `marketing/icon/v3-foreground-1024.png` — master foregroundu, ten sam defekt.
- `marketing/icon/v3-monochrome-1024.png` — master monochrome, ten sam defekt.
- `marketing/icon/v3-source-1024.png` i `marketing/play/icon-512.png` — wersja złożona (kremowe tło + glif + czarne rogi). Ikona Play Store ma czarne rogi → źle wygląda po zaokrągleniu przez Google.
- Architektura adaptive-icon jest **poprawna**: `background` = pełny kremowy `#F6F0E4`, `foreground` = sam glif na przezroczystości. Naprawiamy tylko zawartość PNG-ów, nie XML-e.
- Glif navy `(25,43,58)` ≠ czysta czerń `(0,0,0)` — rogi da się bezpiecznie usunąć flood-fillem z 4 narożników (glif jest oddzielony od rogów pasem przezroczystości, więc flood-fill go nie ruszy).
- `minSdk = 26` → legacy `mipmap-*/ic_launcher.png` i `ic_launcher_round.png` nie są nigdy używane na API 26+ (wygrywa `mipmap-anydpi-v26`). Regeneracja opcjonalna (porządek), nie wymagana funkcjonalnie.

**Podejście:** czysto geometryczna naprawa istniejących assetów (flood-fill rogów → przezroczystość, bounding-box + przeskalowanie glifu do ~66% safe-zone, recenter). BEZ regeneracji przez Gemini — prompt już prosił o "15% safe-zone margin" i model to zignorował, więc to niewiarygodne.

## Files to modify

### Skrypt naprawczy (nowy, jednorazowy)
- `marketing/icon/fix-icon-safezone.py` — skrypt PIL: wejście = master 1024, wyjście = czyste mastery + wszystkie gęstości. Trzymany w repo dla powtarzalności (obok `draft/generate.sh`).

### Assety ikony (regenerowane przez skrypt)
- `marketing/icon/v3-foreground-1024.png` — czyszczenie rogów + safe-zone
- `marketing/icon/v3-monochrome-1024.png` — j.w.
- `marketing/icon/v3-source-1024.png` — kremowy kwadrat (rogi → krem) + glif w safe-zone
- `marketing/play/icon-512.png` — pełne kremowe tło 512×512 bez alfy, glif w safe-zone (Google sam zaokrągla)
- `app/src/main/res/drawable-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher_foreground.png` — 5× przeskalowany glif na przezroczystości (108/162/216/324/432 px)
- `app/src/main/res/drawable-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher_monochrome.png` — 5× sylwetka w safe-zone
- (opcjonalnie) `app/src/main/res/mipmap-*/ic_launcher.png` + `ic_launcher_round.png` — legacy, dla porządku

### Wersja i dokumentacja
- `app/build.gradle.kts` (linie 17–18) — `versionCode = 11`, `versionName = "1.5.2"`
- `CHANGELOG.md` — nowa sekcja na górze (wpis o naprawie ikony; przy okazji CHANGELOG jest nieaktualny — brak wpisu o vc8→10 i targetSdk 35)
- `README.md` — jeśli zawiera numer wersji, zaktualizować

### Bez zmian (zweryfikowane)
- `mipmap-anydpi-v26/ic_launcher.xml` + `ic_launcher_round.xml` — architektura warstw OK
- `WelcomeScreen.kt` — po naprawie assetu glif (~66% płótna) wyrenderuje się czysto w boxie 88dp, bez czarnych rogów. Rozmiar 88dp zostaje (glif będzie optycznie mniejszy, zgodnie z intencją safe-zone).
- `themes.xml` — splash używa `ic_launcher_foreground`, naprawiony asset jest dla splasha bardziej poprawny.

## Implementation steps

1. **Napisać `marketing/icon/fix-icon-safezone.py`** — funkcje:
   - `flood_fill_corners(img)` — z każdego z 4 narożników flood-fill po połączonych pikselach near-black opaque (`R,G,B < 15, A > 250`) → `alpha = 0`. Usuwa też semi-transparentne czarne piksele AA na granicy.
   - `glyph_bbox(img)` — bounding box pikseli z `alpha > 10` po czyszczeniu rogów.
   - `rescale_to_safezone(img, target_ratio=0.66)` — wytnij glif do bboxa, przeskaluj tak, by większy wymiar = `target_ratio × rozmiar płótna`, wklej wycentrowany na przezroczyste płótno docelowe.
   - Eksport: foreground 5 gęstości, monochrome 5 gęstości, mastery 1024, `v3-source-1024` (glif na pełnym kremowym kwadracie), `icon-512` (jw., 512×512, bez kanału alfa).
2. **Uruchomić skrypt**, wygenerować wszystkie assety.
3. **Zweryfikować assety PIL-em** — rogi `alpha=0`, bbox glifu ≈ 66% płótna, brak `(0,0,0,255)` poza monochrome glyph, `icon-512` bez alfy.
4. **Bump wersji** w `app/build.gradle.kts` → `versionCode = 11`, `versionName = "1.5.2"`.
5. **Zaktualizować `CHANGELOG.md`** — nowa sekcja `## [1.5.2]` na górze (naprawa ikony) i README jeśli zawiera numer wersji.
6. **Build AAB** — `JAVA_HOME=/Applications/Android Studio.app/Contents/jbr/Contents/Home ./gradlew clean bundleRelease`. Skopiować artefakt do `marketing/play/fastmask-1.5.2-release.aab`.
7. **Commit + push** na `release/1.5.1-play-store`. Format commita: `Fix: app icon safe-zone + transparent corners (v1.5.2, vc11)`.
8. Upload AAB do Play Console robi Paweł (pipeline z plików `play-*.yml`).

**Zakres potwierdzony:** doprowadzić do AAB + commit + push. Upload do Play robi Paweł.

## Verification

- **Piksele:** `python3` + PIL — dla każdego `ic_launcher_foreground.png`: wszystkie 4 rogi `alpha == 0`, bbox niezerowej alfy ≈ 64–68% wymiaru. Dla `icon-512.png`: brak kanału alfa, 512×512, rogi kremowe.
- **Build:** `./gradlew clean assembleDebug` przechodzi bez błędów (zasoby się kompilują).
- **Wizualnie na urządzeniu/emulatorze:** `adb install -r` debug APK, następnie:
  - Launcher: ikona `@` ma widoczny kremowy margines, nic nie przycięte na krawędziach maski.
  - `WelcomeScreen`: glif `@` wyśrodkowany, **brak czarnych narożników**, czyste kremowe tło.
  - Splash screen: ikona renderuje się czysto.
  - Screenshot Welcome screen do porównania z `marketing/play/screenshots/phone/*/01-welcome.png`.
- **Android Studio (opcjonalnie):** podgląd `mipmap-anydpi-v26/ic_launcher.xml` w edytorze pokazuje glif w safe-zone.
