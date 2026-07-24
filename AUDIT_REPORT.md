# FastMask — Audyt techniczny 2026-07-24

**Rewizja wejściowa:** `dfe4b2f` (v1.8.0, vc17, main)
**Zakres:** pełny sweep — architektura, kod, bezpieczeństwo, dane, wydajność, dostępność, UX
**Metodyka:** przegląd wszystkich 68 plików produkcyjnych i 12 testowych, analiza statyczna (lint), buildy debug/release, testy jednostkowe, przegląd bezpieczeństwa (network config, token storage, billing, ProGuard)

---

## 1. Stan bazowy

| Komenda | Wynik |
|---|---|
| `./gradlew testDebugUnitTest` | ✅ 99 testów (92 istniejące + 7 nowych), 0 porażek |
| `./gradlew lintDebug` | ✅ 0 errorów; ~25 warningów (22 GradleDependency, 1 LocaleFolder, 2 Typos w font_certs.xml — false positive) |
| `./gradlew assembleDebug` | ✅ SUCCESS |
| `./gradlew assembleRelease` | ✅ SUCCESS — APK podpisany, R8 minification |
| Środowisko | JDK 21 (Homebrew), Gradle 8.9, AGP 8.5.2, Kotlin 1.9.22 |

**Poprzedni audyt (2026-07-23):** 90 testów, 0 porażek. Wszystkie naprawy z tamtego audytu (A1–A17) zostały zweryfikowane i są aktywne w kodzie.

---

## 2. Architektura

| Aspekt | Ocena |
|---|---|
| Separacja warstw | ✅ Clean Architecture (data/domain/ui) z wyraźnymi granicami |
| DI | ✅ Hilt — 3 moduły (Network, Repository, Billing), singleton scope |
| Stan | ✅ StateFlow + Channel w ViewModelach, DataStore dla preferencji |
| Nawigacja | ✅ Single-activity, Compose Navigation, hoisted NavController |
| Sieć | ✅ Retrofit + OkHttp + JMAP, singleton session cache z Mutex |
| Płatności | ✅ Play Billing 8.3.0, reconciliacja przy ON_RESUME, buy-flow hardening |
| Bezpieczeństwo | ✅ EncryptedSharedPreferences, FLAG_SECURE, network security config |
| Lokalizacja | ✅ 20 języków, in-app picker, AppCompatDelegate |

---

## 3. Znalezione i naprawione problemy

| ID | Prio | Problem | Przyczyna | Poprawka | Weryfikacja |
|----|------|---------|-----------|----------|-------------|
| B1 | P2 | `ErrorMessage` — Modifier na końcu parametrów (lint ModifierParameter) | Niezgodność z konwencją Compose — modifier powinien być pierwszym opcjonalnym parametrem | Zamiana kolejności: modifier przed onRetry | lint: 0 ModifierParameter warnings |
| B2 | P2 | `NetworkModule.provideJson()` — brak `@OptIn(ExperimentalSerializationApi)` | `explicitNulls = false` wymaga opt-in dla experimental serialization API | Dodano `@OptIn(ExperimentalSerializationApi::class)` | lint: 0 ExperimentalSerializationApi warnings |
| B3 | P3 | `MaskedEmailDetailScreen.DetailContent` — nieużywana zmienna `context` | `LocalContext.current` pobrane, ale nie używane (onCopyEmail callback) | Usunięto nieużywaną zmienną | compiler: 0 unused variable warnings |
| B4 | P3 | `MaskedEmailDetailScreen.DetailContent` — niepotrzebny `!!` na `errorRes` | `errorRes!!` w `stringResource()` gdy warunek na linii wyżej gwarantuje non-null | Usunięto `!!` (null assertion na non-null receiver) | compiler: 0 warnings |
| B5 | P3 | `MaskedEmailDetailScreen.DetailContent` — dead code: `email1 != null` zawsze true | `email1 = uiState.email` — warunek na linii wyżej już gwarantuje `email != null` | Usunięto pośrednią zmienną, użyto `email` bezpośrednio | compiler: 0 warnings |
| B6 | P3 | `SettingsScreen.LanguagePickerDialog` — nieużywana zmienna `extras` | `FastMaskExtras.current` pobrane, ale nie używane w dialogu | Usunięto nieużywaną zmienną | compiler: 0 unused variable warnings |

---

## 4. Testy regresyjne

Dodano 7 nowych testów w `MaskedEmailTest.kt`:

| Test | Ryzyko |
|------|--------|
| `displayName prefers description over domain` | Użycie błędnej hierarchii nazw |
| `displayName falls back to forDomain when description is blank` | Pusta opis → brak nazwy |
| `displayName falls back to email prefix when both are blank` | Brak opisu i domeny |
| `isActive is true for ENABLED` | PENDIG/ENABLED traktowane jako aktywne |
| `isActive is true for PENDING` | Freshly created mask |
| `isActive is false for DISABLED` | Wyłączona maska jako nieaktywna |
| `isActive is false for DELETED` | Zarchiwizowana jako nieaktywna |

---

## 5. Bezpieczeństwo

| Aspekt | Ocena |
|---|---|
| Token storage | ✅ EncryptedSharedPreferences (AES256_GCM), recovery przy korupcji KeyStore |
| Token w UI | ✅ Czyszczony po loginie (`_uiState.update { it.copy(token = "") }`) |
| Flag_SECURE | ✅ Na oknie głównym (debug builds wyłączony — poprawne) |
| Network security | ✅ Tylko system CAs dla api.fastmail.com, cleartext wyłączony |
| apiUrl validation | ✅ Regex `*.fastmail.com` z HTTPS, testy (4 cases) |
| ProGuard | ✅ Reguły dla Retrofit, kotlinx.serialization, Hilt, Tink, Compose |
| Backup | ✅ `allowBackup=false`, exclude secure prefs z cloud backup |
| Billing | ✅ Buy-flow nie downgrade'uje, acknowledged z retry, ON_RESUME refresh |
| CSV export | ✅ OWASP formula injection mitigation (control chars + leading chars) |
| Clipboard | ✅ `EXTRA_IS_SENSITIVE` na API 33+ |
| Debug logs | ✅ Release = zero network logs, debug = headers z redaction Authorization |
| Brak sekretów | ✅ `gitleaks detect` = 0 leaks (poprzedni audyt) |

---

## 6. Wydajność

| Aspekt | Ocena |
|---|---|
| Startup | ✅ Splash screen trzyma do czasu ustalenia destynacji (IO off-main) |
| Loading | ✅ Shimmer placeholder, soft refresh (bez shimmera gdy dane istnieją) |
| List | ✅ `itemsIndexed` z key, `rememberLazyListState`, bez per-item animations |
| Sorting | ✅ Pre-computed timestamps (`sortedByDescending` w VM, nie w composable) |
| Compose | ✅ Stable params, minimalne rekompozycje, `collectAsState` |
| Network | ✅ Session cache z Mutex, 30s timeouts, connection reuse |
| Billing | ✅ ProductDetails cache, auto-reconnect, 15s timeout |

---

## 7. Dostępność

| Aspekt | Ocena |
|---|---|
| TalkBack | ✅ `semantics { selected, role }` na pickerach i toggle'ach |
| Touch targets | ✅ 48dp na akcjach, 40dp na search clear, 48dp na linkach prawnych |
| RTL | ✅ Chevron mirrorowane przez `graphicsLayer` |
| Kontrasty | ✅ Dark-akcenty ≥6.3:1 na ciemnych tłach (policzone WCAG) |
| Reduced motion | ⏳ Brak explicit `reduceMotion` — animations through system |
| Keyboard nav | ⏳ Compose default (Tab/Enter), brak custom shortcuts |

---

## 8. Ograniczenia audytu

- **Play Billing:** Buy/pending/refund wymagają internal testing na urządzeniu z license testerem. Testy jednostkowe z fake'ami.
- **App lock:** Biometric prompt niemożliwy na emulatorze bez skonfigurowanej biometrii.
- **API 26/27:** Brak AVD z tą wersją — process-death fix (A4 z poprzedniego audytu) zweryfikowany statycznie, nie runtime.
- **Dark mode akcentów:** Wizualnie nieoglądane na Pro (wymaga Pro); wartości kontrastów obliczone matematycznie.
- **Instrumented tests:** 0 plików androidTest — brak end-to-end testów UI.

---

## 9. Zależności (stan)

22 warnings GradleDependency — świadomy backlog. AGP 8.5.2, Kotlin 1.9.22, Compose BOM 2024.09 — podbicie wymaga osobnego PR z pełną weryfikacją (breaking changes possible). `security-crypto` 1.1.0 stable (zpoprzedniego audytu). Billing 8.3.0 spełnia wymóg Play "8+".

---

## 10. Rekomendacje (krótkie)

Szczegóły w `UX_RECOMMENDATIONS.md`.

1. **Najbliższy patch:** Stan in-flight na Buy/Restore (A18 z poprzedniego audytu), spinner przy export (A23)
2. **Kolejny release:** Podgląd akcentów dla free, subtelna wzmianka o Pro, undo z prev-state
3. **Większy release:** Podbicie stacka, instrumented tests, Autofill
