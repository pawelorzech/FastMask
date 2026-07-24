# CHANGELOG_AGENT.md — 2026-07-24

## Zmienione pliki

### Build & Config
| Plik | Zmiana |
|------|--------|
| `gradle.properties` | Dodano `org.gradle.java.home` — fix dla JDK 26 niekompatybilności z Kotlin 1.9.22 |

### Source — fixes
| Plik | Zmiana |
|------|--------|
| `app/src/main/java/com/fastmask/ui/pro/ProScreen.kt` | Dodano `resolveActivity` check przed `startActivity` dla linków privacy/terms (P2: crash na urządzeniach bez przeglądarki) |
| `app/src/main/java/com/fastmask/ui/navigation/FastMaskNavHost.kt` | Fallback na `currentBackStackEntry` gdy `previousBackStackEntry` jest null (P2: utrata undo po deep-linku) |
| `app/src/main/java/com/fastmask/ui/list/MaskedEmailListScreen.kt` | 30s cooldown na refresh (P2: unikanie nadmiarowych zapytań API); guard `pendingUndo == null` (P2: drugie szybkie archiwum nie nadpisuje undo); import `mutableLongStateOf` |
| `app/src/main/java/com/fastmask/ui/components/DesignInput.kt` | Uproszczony martwy `when` → `if`; `Spacer(padding)` → `Spacer(width)` |
| `app/src/main/java/com/fastmask/di/BillingModule.kt` | Dodano `CoroutineExceptionHandler` do `ApplicationScope` (P2: ochrona przed crash z tła) |
| `app/src/main/java/com/fastmask/FastMaskApplication.kt` | Blocking DataStore read przeniesiony na IO dispatcher (P2: główny wątek blokowany w `onCreate`) |
| `app/src/main/java/com/fastmask/ui/common/UiErrors.kt` | Dodano mapowanie HTTP 429 → `error_rate_limit`, 5xx → `error_server` (P3) |

### Resources
| Plik | Zmiana |
|------|--------|
| `app/src/main/res/values/strings.xml` | Dodano `error_rate_limit`, `error_server` |
| `app/src/main/res/values-*/strings.xml` | Dodano `error_rate_limit`, `error_server` (19 języków, en placeholder) |

### Tests
| Plik | Zmiana |
|------|--------|
| `app/src/test/java/com/fastmask/ui/common/UiErrorsTest.kt` | Dodano testy dla 429 i 5xx; zaktualizowano test "server errors use fallback" |
| `app/src/test/java/com/fastmask/ui/detail/MaskedEmailDetailViewModelTest.kt` | Dodano `@file:OptIn(ExperimentalCoroutinesApi::class)` |
| `app/src/test/java/com/fastmask/ui/list/MaskedEmailListViewModelTest.kt` | Dodano `@file:OptIn(ExperimentalCoroutinesApi::class)` |
| `app/src/test/java/com/fastmask/ui/auth/LoginViewModelTest.kt` | Dodano `@file:OptIn(ExperimentalCoroutinesApi::class)` |

---

## Wykonane poprawki (szczegóły)

### C1 — P0: Java 26 incompatibility
- **Przyczyna:** Kotlin 1.9.22 nie parsuje wersji Java > 21
- **Objaw:** `java.lang.IllegalArgumentException: 26.0.1`
- **Poprawka:** `gradle.properties` → `org.gradle.java.home=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`
- **Weryfikacja:** `./gradlew assembleDebug` → SUCCESS bez ręcznego `JAVA_HOME`

### C2 — P2: Crash na brak przeglądarki (privacy/terms)
- **Przyczyna:** `Intent(ACTION_VIEW)` bez `resolveActivity` check
- **Objaw:** `ActivityNotFoundException` → crash aplikacji
- **Poprawka:** Sprawdzenie `intent.resolveActivity(context.packageManager) != null`

### C3 — P2: Nadmiarowe odświeżanie sieci
- **Przyczyna:** Każdy ON_RESUME → `refreshMaskedEmails()` bez limitu
- **Objaw:** Każde wysunięcie notification shade → zapytanie API
- **Poprawka:** 30-sekundowy cooldown (`lastRefreshTime`)

### C4 — P2: Utrata undo przy deep-link
- **Przyczyna:** `previousBackStackEntry` może być null
- **Objaw:** Archiwizacja przez deep-link → brak undo snackbara na liście
- **Poprawka:** Fallback na `currentBackStackEntry.savedStateHandle`

### C5 — P2: Race condition na podwójne archiwum
- **Przyczyna:** `pendingUndo` to pojedynczy slot
- **Objaw:** Drugie szybkie archiwum nadpisuje undo dla pierwszej maski
- **Poprawka:** Guard `pendingUndo == null` przed zapisaniem

### C6 — P2: Martwy kod w DesignInput
- **Przyczyna:** `when` z identycznymi gałęziami
- **Objaw:** Mylący kod — sugeruje różne zachowanie dla error/non-error
- **Poprawka:** Prosty `if (hint != null) hint else null`

### C7 — P2: Brak CoroutineExceptionHandler
- **Przyczyna:** `ApplicationScope` bez handlera wyjątków
- **Objaw:** Dowolny nieobsłużony wyjątek w scope → crash
- **Poprawka:** Dodano handler z `Log.e` w debug mode

### C8 — P2: Blokujący IO w Application.onCreate
- **Przyczyna:** `runBlocking` na głównym wątku podczas startu aplikacji
- **Objaw:** Wydłużony cold start; flagowane przez Play Vitals
- **Poprawka:** Przeniesiono do `CoroutineScope(IO).launch`

### C9 — P3: Ostrzeżenia ExperimentalCoroutinesApi
- **Przyczyna:** Użycie `backgroundScope` / `launch` w `runTest` bez opt-in
- **Objaw:** 22 warningi podczas `./gradlew test`
- **Poprawka:** `@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)` w 3 plikach

### C10 — P3: Ograniczone mapowanie błędów HTTP
- **Przyczyna:** Tylko IOException i 401/403 miały dedykowane komunikaty
- **Objaw:** 429/5xx pokazywały generic fallback
- **Poprawka:** Dodano `error_rate_limit` i `error_server` z placeholderami EN w 19 językach

### C11 — P3: Antipattern Spacer
- **Przyczyna:** `Spacer(Modifier.padding(start = 8.dp))` zamiast `.width(8.dp)`
- **Objaw:** Semantycznie niepoprawny kod (padding na zero-size komponencie)
- **Poprawka:** `Spacer(Modifier.width(8.dp))` + dodano import

---

## Potencjalne regresje

| Ryzyko | Poziom | Mitigacja |
|--------|--------|-----------|
| C8: Język nieodtworzony przed pierwszym Activity | Niskie | Splash screen maskuje opóźnienie; `AppCompatDelegate.setApplicationLocales` działa nawet jeśli wywołane później |
| C3: Zbyt rzadkie odświeżanie | Niskie | 30s cooldown to konserwatywny limit; pull-to-refresh zawsze działa natychmiast |
| C5: Zablokowanie undo przy szybkim użyciu | Niskie | Poprawka chroni undo pierwszej maski; drugie archiwum czeka na konsumpcję pierwszego undo |

---

## Wymagane manualne QA

- [ ] Sprawdź cooldown refresh: archiwizuj maskę na detail → wróć na listę → maska powinna zniknąć po ~250ms (refresh na RESUME)
- [ ] Sprawdź deep-link: otwórz detail maski przez zewnętrzny link → archiwizuj → sprawdź czy snackbar działa na liście
- [ ] Sprawdź podwójne archiwum: archiwizuj 2 maski szybko → pierwsza powinna mieć undo
- [ ] Sprawdź linki privacy/terms na Pro screen
- [ ] Sprawdź błędy 429 i 5xx (wymaga mock API)
- [ ] Sprawdź przywracanie języka po restarcie z czyszczonymi danymi
