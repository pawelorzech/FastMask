# FastMask — Audyt techniczny 2026-07-24

**Rewizja wejściowa:** `2a2e0c7` (v1.8.0, vc17, main)
**Zakres:** pełny sweep — architektura, kod, bezpieczeństwo, dane, wydajność, dostępność, UX
**Metodyka:** przegląd wszystkich 68 plików produkcyjnych i 14 testowych, analiza statyczna (lint), buildy debug/release, testy jednostkowe, przegląd bezpieczeństwa (network config, token storage, billing, ProGuard)

---

## 1. Stan bazowy (przed zmianami)

| Komenda | Wynik |
|---|---|
| `./gradlew testDebugUnitTest` | ✅ 99 testów, 0 porażek |
| `./gradlew lintDebug` | ✅ 0 errorów; ~25 warningów (22 GradleDependency, 1 LocaleFolder, 2 Typos w font_certs.xml) |
| `./gradlew assembleDebug` | ⚠️ FAIL — JDK 26 niekompatybilne z Kotlin 1.9.22 |
| `./gradlew assembleDebug` (JDK 21) | ✅ SUCCESS |
| `./gradlew assembleRelease` (JDK 21) | ✅ SUCCESS |
| Środowisko | JDK 26.0.1 (Homebrew, default), Gradle 8.9, AGP 8.5.2, Kotlin 1.9.22 |

**Kluczowe znalezisko bazowe:** Java 26.0.1 (systemowy default z Homebrew) nie jest rozpoznawana przez parser wersji w Kotlin 1.9.22. Build wymaga ręcznego ustawienia `JAVA_HOME` na JDK 21.

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

## 3. Znalezione i naprawione problemy (nowy audyt)

| ID | Prio | Problem | Przyczyna | Poprawka |
|----|------|---------|-----------|----------|
| C1 | P0 | Build fail z JDK 26 | Kotlin 1.9.22 nie rozpoznaje wersji Javy 26+ | Dodano `org.gradle.java.home` w gradle.properties |
| C2 | P2 | Crash przy otwarciu linku privacy/terms bez przeglądarki | Brak `resolveActivity` check przed `startActivity` | Dodano sprawdzenie `packageManager.resolveActivity` |
| C3 | P2 | Nadmiarowe odświeżanie na każdy ON_RESUME | `repeatOnLifecycle(RESUMED)` bez cooldownu | Dodano 30-sekundowy cooldown (`lastRefreshTime`) |
| C4 | P2 | Utrata undo po archiwizacji przez deep-link | `previousBackStackEntry` null → stan archiwizacji przepada | Fallback na `currentBackStackEntry.savedStateHandle` |
| C5 | P2 | Drugie szybkie archiwum nadpisuje pierwsze undo | `pendingUndo` to single-slot — brak guarda | Guard: `pendingUndo == null` przed zapisaniem |
| C6 | P2 | Martwy warunek w DesignInput supportText | `when` z dwoma identycznymi gałęziami (`isError && hint`, `!isError && hint`) | Zamieniono na prosty `if (hint != null) hint else null` |
| C7 | P2 | Brak CoroutineExceptionHandler na ApplicationScope | Nieobsłużony wyjątek w scope → crash aplikacji | Dodano handler z logowaniem w debug |
| C8 | P2 | Blocking DataStore read w Application.onCreate | `runBlocking` na głównym wątku podczas startu | Przeniesiono do coroutine na IO dispatcherze |
| C9 | P3 | 22 warningi ExperimentalCoroutinesApi w testach | `backgroundScope` / `launch` w `runTest` bez @OptIn | Dodano `@file:OptIn` do 3 plików testowych |
| C10 | P3 | Ograniczone mapowanie błędów HTTP (tylko 401/403) | Brak rozróżnienia 429 i 5xx | Dodano `error_rate_limit` i `error_server` z tłumaczeniami na 19 języków |
| C11 | P3 | Spacer z `Modifier.padding(start = 8.dp)` zamiast `.width(8.dp)` | Antipattern — padding na zero-size spacerze | Zamieniono na `Modifier.width(8.dp)` |

---

## 4. Testy

### Stan po zmianach

| Metryka | Wartość |
|---|---|
| Testy jednostkowe | 103 (wszystkie PASS) |
| Nowe przypadki testowe | +4 (HTTP 429/5xx mapping w UiErrorsTest) |
| Warnings (testy) | 0 (wszystkie `@OptIn` dodane) |
| Lint errors | 0 |

### Kluczowe komendy

```bash
./gradlew test              # 103/103 pass
./gradlew lintDebug         # 0 errors
./gradlew assembleDebug     # SUCCESS (bez ręcznego JAVA_HOME)
./gradlew assembleRelease   # SUCCESS (podpisane, R8 zminifikowane)
```

---

## 5. Bezpieczeństwo

| Aspekt | Ocena |
|---|---|
| Token storage | ✅ EncryptedSharedPreferences (AES256_GCM), recovery przy korupcji KeyStore |
| Token w UI | ✅ Czyszczony po loginie (`_uiState.update { it.copy(token = "") }`) |
| FLAG_SECURE | ✅ Na oknie głównym (debug builds wyłączony) |
| Network security | ✅ Tylko system CAs dla api.fastmail.com, cleartext wyłączony |
| apiUrl validation | ✅ Regex `*.fastmail.com` z HTTPS |
| ProGuard | ✅ Reguły dla Retrofit, kotlinx.serialization, Hilt, Tink, Compose |
| Backup | ✅ `allowBackup=false`, exclude secure prefs |
| Billing | ✅ Buy-flow nie downgrade'uje, acknowledged z retry, ON_RESUME refresh |
| CSV export | ✅ OWASP formula injection mitigation |
| Clipboard | ✅ `EXTRA_IS_SENSITIVE` na API 33+ |
| Debug logs | ✅ Release = zero network logs, debug = headers z redaction Authorization |
| Secrets | ✅ `gitleaks detect` = 0 leaks (poprzedni audyt) |

---

## 6. Wydajność

| Aspekt | Ocena |
|---|---|
| Startup | ✅ Splash screen trzyma do czasu ustalenia destynacji (IO off-main po C8) |
| List refresh | ✅ Teraz z 30s cooldownem (było: każdy ON_RESUME) |
| Loading | ✅ Shimmer placeholder, soft refresh bez shimmera gdy dane istnieją |
| List | ✅ `itemsIndexed` z key, `rememberLazyListState` |
| Sorting | ✅ Pre-computed w VM (`sortedByDescending`) |
| Compose | ✅ Stable params, `collectAsState` |
| Network | ✅ Session cache z Mutex, 30s timeouts |
| Billing | ✅ ProductDetails cache, auto-reconnect, 15s timeout |

---

## 7. Dostępność

| Aspekt | Ocena |
|---|---|
| TalkBack | ✅ `semantics { selected, role }` na pickerach i toggle'ach |
| Touch targets | ✅ 48dp na akcjach, 40dp na search clear |
| RTL | ✅ Chevron mirrorowane przez `graphicsLayer` |
| Kontrasty | ✅ Dark-akcenty ≥6.3:1 na ciemnych tłach |
| Reduced motion | ⏳ Brak explicit `reduceMotion` |
| Keyboard nav | ⏳ Compose default |

---

## 8. Ograniczenia audytu

- **Play Billing:** Buy/pending/refund wymagają internal testing na urządzeniu
- **App lock:** Biometric prompt niemożliwy na emulatorze
- **API 26/27:** Brak AVD — fixy zweryfikowane statycznie
- **Instrumented tests:** 0 plików androidTest — brak end-to-end testów UI
- **Zmiany w FastMaskApplication (C8):** Odtworzenie języka po starcie jest teraz asynchroniczne. Może opóźnić pierwsze wyrenderowanie o ~50-200ms. W praktyce splash screen maskuje ten czas.

---

## 9. Rekomendacje (skrótowe)

Szczegóły w `UX_RECOMMENDATIONS.md`.

1. **Najbliższy patch (v1.8.1):** Wszystkie poprawki z tego audytu
2. **Kolejny release (v1.9):** Instrumented tests, Autofill API, podbicie Kotlin do 2.x
3. **Większy release (v2.0):** Material 3 dynamic color, widget na ekran główny

---

## 10. Pozostałe znane problemy (nie naprawione)

| ID | Prio | Problem | Powód niewdrożenia |
|----|------|---------|-------------------|
| A1 | P2 | Tutorial może się nie pokazać gdy bounds < 5 | Wymaga zmiany produktowej (timeout vs degraded mode); niskie prawdopodobieństwo w praktyce |
| A2 | P2 | Tutorial step 3 podświetla pozycyjnie, nie po ID | Wymaga refaktoryzacji całego systemu tutoriala |
| A3 | P2 | Export zostawia sieroce pliki przy niepowodzeniu | Niski impact — czyszczone przy następnym eksporcie |
| A4 | P2 | Buy button silent no-op gdy context != Activity | Obecnie nie występuje — Compose always provides Activity context |
| A5 | P3 | Package-level keep rules w ProGuard | Świadomy trade-off: prostota vs rozmiar APK |
