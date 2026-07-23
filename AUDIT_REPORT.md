# FastMask — Audyt techniczny 2026-07-23

**Rewizja wejściowa:** `1febc93` (v1.7.3, vc16, main) · **Branch napraw:** `feature/audit-fixes` (4 commity: `80c9403`, `d94211f`, `3e0e123`, `1104112`)
**Zakres:** pełny sweep z ciężarem na kodzie dodanym po audycie z 2026-07-19 (`Plans/audit-report-2026-07-19.md`): monetyzacja v1.7 (Play Billing 8.3.0, Pro, biometric app lock, CSV export, akcenty), quick-copy + undo archive (1.6.0), target SDK 36.
**Metodyka:** 3 równoległe przeglądy wymiarowe (security / poprawność-dane / UI-UX-a11y-i18n), własny przegląd rdzenia (MainActivity, manifest, konfiguracja, zależności, prywatność), baseline + walidacja buildów i testów, QA manualne na emulatorze (API 36, Pixel 9a AVD) ze zrzutami ekranu, gitleaks.

---

## 1. Stan bazowy (przed zmianami)

| Komenda | Wynik |
|---|---|
| `./gradlew testDebugUnitTest --rerun-tasks` | ✅ 87 testów, 0 porażek |
| `./gradlew lintDebug` | ✅ 0 errorów; 110 warningów (37 Typos, 26 GradleDependency, 20 UnusedResources, 18 StringFormatCount, 5 IconLauncherShape, po 1: PluralsCandidate, ObsoleteSdkInt, LocaleFolder) |
| `./gradlew assembleDebug` | ✅ SUCCESS |
| `./gradlew assembleRelease` | ✅ SUCCESS — APK 3.9 MB, podpisany |
| `gitleaks detect --log-opts="a2daad0..HEAD"` | ✅ 7 commitów, no leaks |
| Środowisko | JBR 21 z Android Studio (`JAVA_HOME`), Gradle 8.9, AGP 8.5.2, Kotlin 1.9.22 |

## 2. Znalezione problemy

Statusy: **[C]** potwierdzony w kodzie · **[C+L]** potwierdzony w kodzie i live na emulatorze · **[H]** hipoteza (ścieżka kodu potwierdzona, trigger zależny od zachowania platformy/Play) · **[M]** wymaga testu manualnego na urządzeniu.

| ID | Prio | Status | Problem | Lokalizacja | Naprawa |
|----|------|--------|---------|-------------|---------|
| A1 | P1 | [C+L] | Snackbar „Archived — Undo" kasował sam siebie: `onArchivedConsumed()` wołane jako pierwsza instrukcja efektu kluczowanego na konsumowanej wartości SavedStateHandle → zapis null → rekompozycja → anulowanie `showSnackbar` w zawieszeniu → snackbar znikał po 1 klatce, Undo nieosiągalne. Regresja funkcji z 1.6.0 (`14c9eea`). | `MaskedEmailListScreen.kt:127-137` | ✅ `80c9403` — id latchowane do lokalnego stanu przed konsumpcją; duration Short→Long. Zweryfikowane live: snackbar widoczny po 5 s, Undo przywraca maskę. |
| A2 | P2 | [C] | Buy-flow listener traktowany jako autorytatywny: `onPurchasesUpdated(OK, lista bez pro_lifetime)` → downgrade do FREE + nadpisanie cache — wbrew własnemu kontraktowi klasy („downgrade tylko z autorytatywnej odpowiedzi"). Listener raportuje zakupy TEGO update'u, nie stan konta. | `ProRepositoryImpl.kt:83-85→178-204` | ✅ `d94211f` — buy-flow z pustą listą nie dotyka stanu; downgrade wyłącznie z `queryPurchases`. + test regresyjny. |
| A3 | P2 | [C] | Brak re-query zakupów na ON_RESUME; `refresh()` tylko w onCreate i za gate'em `MONETIZATION_ENABLED`. Skutki: PENDING dokończony w tle nie odblokowuje Pro do restartu; nieudany `acknowledge()` retryowany zbyt rzadko (Play auto-refunduje po ~3 dniach); kill-switch=false odcinał także acknowledgment już opłaconego zakupu. | `MainActivity.kt:110-113` | ✅ `d94211f` — `refresh()` na każde ON_RESUME, bez gate'a kill-switcha (kill-switch chowa entry pointy, nie rekoncyliację). |
| A4 | P2 | [C]/[M] | Bypass app-locka po process death w tle na API 26–27: przed API 28 `onSaveInstanceState` biegnie PRZED `onStop`, więc bundle niesie `locked=false` sprzed re-locka; restore ufał bundle'owi bezwarunkowo. | `MainActivity.kt:103-107,134-142,167-170` | ✅ `d94211f` — token procesu w bundle'u; bundle z obcego procesu = wymuszony świeży unlock. Test na fizycznym API 26/27 niedostępny w tym środowisku. |
| A5 | P2 | [C] | Każdy cykl lock/unlock niszczył NavController + back stack + ViewModele ekranów (warunkowa kompozycja) — po odblokowaniu user zawsze lądował na starcie, niedopisany formularz create / niezapisana edycja przepadały. Utrata wpisanych danych. | `MainActivity.kt:149-160` | ✅ `d94211f` — `rememberNavController()` wyniesiony nad gate; NavHost nadal niekomponowany za lockiem (własność „content not composed" zachowana). Nawigacja zweryfikowana live. |
| A6 | P2 | [C] | Akcenty Pro nieczytelne w dark mode: jeden kolor na oba motywy; na ciemnych tłach Ink 1.74:1, Plum 2.09, Cobalt 2.14, Sage 2.78 (poniżej nawet 3:1 non-text) — kursor/FAB/ikony/linki niemal niewidoczne dla płacących użytkowników. | `AccentColors.kt:12-19`, `Theme.kt:76-78` | ✅ `3e0e123` — `Accent.color(darkTheme)`/`onColor(darkTheme)`: dark-warianty ≥6.3:1 na DarkSurface/DarkBg z ciemnym tekstem na akcencie ≥5.9:1 (policzone WCAG). Amber (klasyczny default) celowo bez zmian. |
| A7 | P2 | [C] | Picker akcentów i języka bez semantics selekcji (TalkBack czyta identyczne wiersze bez stanu — powtórzony gap A16 z poprzedniego audytu); SettingsToggleRow z zagnieżdżonymi clickable = 2 focus-targety na jedno ustawienie. | `SettingsScreen.kt:503-535,598-629,430-468` | ✅ `3e0e123` — `selectable(Role.RadioButton)` na obu pickerach (domyka też A16-leftover), `toggleable(Role.Switch)` + `Switch(onCheckedChange=null)`. Zweryfikowane live (renderowanie bez regresji). |
| A8 | P2 | [C+L] | Cichy błąd Undo: `restoreMask` bez `onFailure` — przy błędzie sieci maska zostawała w archiwum bez żadnego komunikatu. | `MaskedEmailListViewModel.kt:56-61` | ✅ `80c9403` — błąd trafia do bannera błędu listy. + test regresyjny. |
| A9 | P3 | [C] | CSV: zbiór neutralizacji formuł bez `\r`/`\n` jako znaków wiodących (OWASP: część parserów zdejmuje wiodące znaki kontrolne i wykonuje resztę; pole `\r=HYPERLink(...)` było quotowane, ale nie neutralizowane). | `ExportMasksUseCase.kt:42` | ✅ `1104112` + 1 test. |
| A10 | P3 | [C+L] | Chevron „w głąb" w ustawieniach nie mirrorował się w RTL (wskazywał wstecz po arabsku). `AutoMirrored.ChevronRight` nie istnieje w icons z BOM 2024.09 — mirror ręczny w miejscu renderu. | `SettingsScreen.kt` (SettingsRow trailing) | ✅ `3e0e123`. Zweryfikowane live po arabsku. |
| A11 | P3 | [C] | Schowek: kopiowany adres maski bez `EXTRA_IS_SENSITIVE` (Android 13+ pokazuje go plaintextem w podglądzie schowka) + label hardcoded EN. | `Clipboard.kt:13` | ✅ `1104112` — flaga sensitive na API 33+, label z zasobów. |
| A12 | P3 | [C] | `local.properties` śledzone w gicie wbrew `.gitignore:35` (dziś tylko sdk.dir, ale to plik, do którego Android Studio potrafi dopisać signing config). | repo root | ✅ `80c9403` — `git rm --cached`. |
| A13 | P3 | [C] | `security-crypto` przypięte do 1.1.0-alpha06, a 1.1.0 **stable** jest dostępne (ta sama linia API MasterKey.Builder — udokumentowany trade-off z F-05 do domknięcia). | `app/build.gradle.kts:140` | ✅ `1104112`. Boot + TokenStorage init zweryfikowane live na emulatorze. |
| A14 | P3 | [C] | Wąski race re-locka na zimnym starcie: obserwator ON_STOP kluczowany na `lockActive` (czeka na async proStatus) — okno unlock→background bez re-locka. | `MainActivity.kt:134-142` | ✅ `d94211f` — obserwator kluczowany na fladze DataStore, od pierwszej klatki. |
| A15 | P3 | [C] | Flash domyślnego amberu na zimnym starcie u userów Pro z akcentem (initial FREE/DEFAULT do czasu seedu). | `MainActivity.kt:116-120` | ✅ `d94211f` — snapshot cache (accent+entitlement) czytany w istniejącym bloku IO jako initial. Wyjątek: refund w trakcie sesji zostawia akcent do restartu (kosmetyczne, samonaprawialne — udokumentowane w komentarzu). |
| A16 | P3 | [C] | Kolejność emisji purchase updates niegwarantowana (`scope.launch{emit}` per callback — dwa szybkie callbacki mogły się wyprzedzić). | `PlayBillingDataSource.kt:69-70` | ✅ `d94211f` — synchroniczny `tryEmit` z wątku listenera. |
| A17 | P3 | [C] | Linki prawne na paywallu: ~30 dp touch target, bez roli (wymagane prawnie linki na ekranie płatności). | `ProScreen.kt:340-350` | ✅ `3e0e123` — 48 dp + `Role.Button`, wizual bez zmian. |
| A18 | P3 | [C] | Buy/Restore bez feedbacku in-flight (billing sheet potrafi wstawać 1–3 s; przycisk wygląda na zamrożony). | `ProScreen.kt:221-249`, `DesignKit.kt` (PillButton) | ⏸ backlog (dotyka wspólnego PillButton — osobna, mała zmiana z QA wizualnym; rekomendacja #1 w UX_RECOMMENDATIONS) |
| A19 | P3 | [C] | Stale event zakupu: buforowany Channel w repo singleton — event z zamkniętego paywalla odpala się przy następnej wizycie (nawet po dniach). | `ProRepositoryImpl.kt:54-55` | ⏸ backlog (stan entitlement poprawny, tylko stary snackbar; fix = drenaż kanału przy wejściu) |
| A20 | P3 | [H] | `ensureConnected` po timeoucie może zawołać `startConnection` na kliencie w stanie CONNECTING → sztuczny błąd DEVELOPER. | `PlayBillingDataSource.kt:81-111` | ⏸ backlog (wymaga potwierdzenia zachowania biblioteki; auto-reconnect 8.x łagodzi) |
| A21 | P3 | [C] | Wipe katalogu exportów przy każdym eksporcie może unieważnić URI trzymane przez wolnego odbiorcę share (np. upload do Drive w toku). | `SettingsScreen.kt:114-121` | ⏸ backlog (nazwy z timestampem + kasowanie po wieku) |
| A22 | P3 | [C] | Undo przywraca maskę DISABLED jako ENABLED (udokumentowana decyzja — spójna z un-delete Fastmaila; semantycznie „undo" obiecuje stan sprzed). | `MaskedEmailListViewModel.kt:51-55` | ⏸ decyzja produktowa Pawła |
| A23 | P3 | [C] | Export bez feedbacku postępu (fetch sieciowy przed share sheet; `exportInFlight` prywatny, nie w UI state) + rotacja w trakcie handlera ShareCsv gubi eksport [M]. | `SettingsViewModel.kt:150-164`, `SettingsScreen.kt:111-136` | ⏸ backlog (rekomendacja B) |
| A24 | P3 | [C] | Gesture-back na paywallu omija `onClosed()` → PAYWALL_CLOSED niedoliczane (analytics tylko debug-log, waga minimalna). | `ProScreen.kt:120-124` | ⏸ backlog (przenieść do `onCleared()`) |
| A25 | P3 | [C] | Dialogi: „Cancel" tam, gdzie nic się nie anuluje (wybór działa natychmiast); klucz `settings_accent_locked` reużyty dla lock/export. | `SettingsScreen.kt:180-184,539-545,587-593` | ⏸ backlog (2 nowe klucze × 20 locale — razem z następną partią tłumaczeń) |

**Uwagi bez zmian kodu (świadome trade-offy, do wiedzy):**
- Lokalny „proof" entitlementu (SHA-256 tokenu zakupu) nie jest realnym anti-tamper — `read()` sprawdza tylko niepustość; root/adb może sforgować PRO offline. Rekoncyliacja z Play przy każdym starcie ogranicza to do trybu offline i funkcji lokalnych. Akceptowalne dla aplikacji bez backendu; docstring w `ProEntitlementStore.kt` lekko przecenia ochronę.
- App lock = `BIOMETRIC_WEAK|DEVICE_CREDENTIAL`, bez CryptoObject — gate prywatności (shoulder-surf / zgubiony telefon), nie ochrona danych at-rest. To poprawny wybór (wymagany pairing dla non-crypto prompt); nie reklamować jako szyfrowanie.
- Kontrast klasycznego amberu jako tekst na ciemnych tłach (3.12:1) — pre-existing default, decyzja designowa (analogicznie do A20 z poprzedniego audytu); propozycja w UX_RECOMMENDATIONS.

## 3. Weryfikacja po zmianach

| Sprawdzenie | Wynik | Tryb |
|---|---|---|
| `./gradlew testDebugUnitTest` | ✅ **90 testów, 0 porażek** (87 + 3 regresyjne) | automatyczny |
| `./gradlew lintDebug` | ✅ 0 errorów | automatyczny |
| `./gradlew assembleDebug` / `assembleRelease` | ✅ SUCCESS (release z R8, podpisany) | automatyczny |
| Boot na security-crypto 1.1.0 (TokenStorage/EncryptedSharedPreferences init) | ✅ welcome screen, 0 FATAL w logcat | manualny (emulator API 36) |
| Undo archiwizacji end-to-end (demo): archive → snackbar trwa → Undo → maska aktywna | ✅ | manualny (screenshoty) |
| Nawigacja po hoistingu NavControllera (lista→detail→back, settings) | ✅ | manualny |
| Settings po zmianach toggleable/selectable, picker języka | ✅ renderowanie i działanie bez regresji | manualny |
| RTL (arabski): chevrony wskazują „w głąb" | ✅ | manualny (screenshot) |
| Kontrasty dark-wariantów akcentów | ✅ policzone (WCAG relative luminance): INK 7.46, SAGE 7.85, PLUM 6.90, COBALT 7.71 na DarkSurface | obliczeniowy |
| Lock: process-token na API 26/27, hoisted-nav przez cykl lock/unlock, billing (buy/pending/restore) | — | **nieweryfikowalne w tym środowisku** (brak AVD 26/27, brak biometrii na emulatorze, Play billing wymaga license testera na urządzeniu — checklist w odpowiedzi końcowej) |

## 4. Ograniczenia audytu

- Ścieżki Play Billing przetestowane jednostkowo (fake'i) i statycznie; realne buy/pending/refund wymagają internal testing na urządzeniu (pworzech@gmail.com jest license testerem).
- App lock niemożliwy do przejścia na emulatorze bez skonfigurowanej biometrii; zmiany w tym obszarze pokryte review + kompilacją, nie testem runtime.
- Brak AVD API 26/27 — [DEFERRED-VERIFY] z poprzedniego audytu nadal otwarty, teraz obejmuje też fix A4.
- Dark-mode akcentów nie obejrzano na ekranie (wymaga Pro); wartości kontrastów policzone, nie ocenione wizualnie.

## 5. Zależności (stan)

26 warningów GradleDependency — świadomy backlog z poprzedniego audytu (AGP 8.5.2/Kotlin 1.9.22/Compose BOM 2024.09; podbicie = osobny PR z pełną weryfikacją). W tym audycie podbito wyłącznie `security-crypto` (alpha→stable, ta sama linia API). Billing 9.1.0 dostępny, ale 8.3.0 spełnia wymóg Play „8+" — nie ruszano (proporcjonalność).
