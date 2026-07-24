# FastMask — Audyt techniczny 2026-07-24 (pass B)

**Rewizja wejściowa:** `f1e89ee` (v1.8.0, vc17, `main`)
**Gałąź z poprawkami:** `feature/audit-2026-07-24b`
**Zakres:** pełny sweep — architektura, kod, współbieżność, bezpieczeństwo, dane, wydajność, dostępność, UX
**Metodyka:** przegląd wszystkich ~70 plików produkcyjnych i 15 testowych; trzy równoległe pod-audyty (współbieżność/stan, security/dane, UX/dostępność) z niezależną weryfikacją każdego znaleziska bezpośrednio w kodzie; analiza statyczna (lint), buildy debug/release, testy jednostkowe.

> **Kontekst:** to trzeci przebieg audytu tego repo. Poprzednie dwa (2026-07-23 i 2026-07-24 pass A) naprawiły 11+ problemów (C1–C11: fix JDK 26, browser resolveActivity, cooldown refresh, undo deep-link, guard pendingUndo, CoroutineExceptionHandler na ApplicationScope, blocking DataStore w onCreate, mapowanie 429/5xx, CSV formula injection, sensitive clipboard, billing hardening). Ten przebieg szukał **nowych/pozostałych** defektów, nie powtarzał C1–C11 ani znanych A1–A5.

---

## 1. Stan bazowy (przed zmianami)

| Komenda | Wynik |
|---|---|
| `./gradlew testDebugUnitTest` | ✅ 101 testów, 0 porażek |
| `./gradlew lintDebug` | ✅ 0 errorów; 108 warningów (benign: IconLauncherShape ×5, GradleDependency, LocaleFolder) |
| `./gradlew assembleRelease` | ✅ SUCCESS (R8/ProGuard, unsigned bez keystore env) |
| Środowisko | JDK pinowany na openjdk@21 przez `gradle.properties` (`org.gradle.java.home`), Gradle 8.9, Kotlin 1.9.22 |

**Zielony build nie jest dowodem poprawności aplikacji** — poniższe znaleziska pochodzą z ręcznego przeglądu kodu, nie z bramek jakości.

---

## 2. Ogólna ocena

Kod jest **dojrzały i dobrze zbudowany**, z realną obroną w głąb: proces-token chroniący przed obejściem app-locka na API < 28, downgrade Pro tylko na autorytatywną odpowiedź Play, kompletna neutralizacja CSV, session-mutex w JMAP, ack-retry w billingu, redakcja nagłówków w logach. Nie znaleziono **żadnego P0/P1** — spójne z dwoma wcześniejszymi rzetelnymi audytami. Wszystkie nowe znaleziska to **P2/P3**: hardening współbieżności, drobne luki UX/i18n oraz jedna niekompletność w dostępności.

---

## 3. Znalezione i naprawione problemy (ten przebieg)

Status potwierdzenia: **[C]** potwierdzony przez czytanie kodu · **[P]** prawdopodobny, wymaga runtime.

| ID | Prio | Status | Problem | Lokalizacja | Przyczyna źródłowa | Poprawka |
|----|------|--------|---------|-------------|--------------------|----------|
| CC1 | P2 | [C]/[P] | Niezłapany wyjątek z zapisu DataStore w `viewModelScope.launch` → crash na głównym wątku | `SettingsViewModel` (6 miejsc), `MaskedEmailListViewModel.markTutorialCompleted`, `DemoBannerViewModel.exitDemoMode` | `viewModelScope` re-rzuca nieobsłużone wyjątki; brak try/catch wokół `DataStore.edit`/`logout` (IOException przy dysku/korupcji) | Dodano `CoroutineExceptionHandler` do zapisów; nawigacyjne eventy (logout, exitDemo) nie odpalają przy przerwanym zapisie |
| CC2 | P2 | [P] | Spinner „Kup" na paywallu może wisieć w nieskończoność | `ProViewModel.buy` + `ProRepositoryImpl.handlePurchases` | `purchaseInFlight` kasowany **wyłącznie** przez event; dwie gałęzie buy-flow (`return@withLock` gdy brak SKU; `ProStatus.FREE`) nie emitowały żadnego eventu | Obie gałęzie emitują teraz `ProPurchaseEvent.Failed` — każdy terminalny wynik buy-flow kasuje spinner |
| CC3 | P3 | [C] | Reload po zapisie/toggle nadpisuje pola edytowane w trakcie pisania | `MaskedEmailDetailViewModel.loadEmail` | Sukces `saveChanges`/`updateState` wołał `loadEmail()`, który bezwarunkowo reseeduje `edited*` z serwera | Dodano `loadEmail(resetEdits = Boolean)`; reload po akcji przekazuje `false` i zachowuje wpisany tekst |
| CC4 | P3 | [C] | Double-checked locking na nie-`@Volatile` polach sesji JMAP (widoczność międzywątkowa) | `JmapApi.cachedSession/cachedAccountId` | Fast-path w `ensureSession` i lock-free `getAccountId`/`getApiUrl` czytają pola pisane na innym wątku IO bez happens-before | Oznaczono oba pola `@Volatile` |
| CC5 | P3 | [P] | Dwa wywołania `refreshMaskedEmails` w tej samej klatce startują dwa fetche | `MaskedEmailListViewModel` | Guard `if (isLoading)` ustawiany dopiero w korutynie i tylko gdy lista pusta; `loadMaskedEmails` bez guarda | Dodano synchroniczny `refreshInFlight` + guard `isLoading` na `loadMaskedEmails` |
| SEC2 | P2(low) | [P] | Neutralizacja CSV sprawdza tylko pierwszy znak → bypass przez wiodące spacje (`" =HYPERLINK(...)"`) | `ExportMasksUseCase.csvEscaped` | Importery, które trymują białe znaki przed ewaluacją, wykonają formułę | Sprawdzanie na pierwszym **nie-białym** znaku (`trimStart().firstOrNull()`) |
| SEC3 | P3 | [C] | Kopiowanie nowo utworzonego maska pomija `EXTRA_IS_SENSITIVE` | `CreateMaskedEmailScreen` (lokalny `copyToClipboard`) | Ekran miał własną funkcję bez flagi sensitive, niespójną ze współdzielonym helperem | Usunięto lokalną funkcję, użyto `com.fastmask.ui.common.copyToClipboard` (flaguje sensitive na API 33+) |
| B1 | P2 | [C] | Stan maska na liście sygnalizowany **tylko kolorem** — TalkBack nie ogłasza stanu, daltoniści nie odróżniają | `MaskedEmailListScreen.MaskRow` + `DesignKit.StateDot` | `StateDot` to goła kolorowa kropka bez semantyki | `StateDot` przyjmuje opcjonalny `contentDescription`; lista przekazuje etykietę stanu (reużyte przetłumaczone `state_*`) |
| B2 | P2 | [C] | Komunikaty walidacji prefixu zahardkodowane po angielsku pod w pełni przetłumaczonym formularzem | `CreateMaskedEmailViewModel.onPrefixChange` | Literały `String` zamiast res-id | `prefixError: String?` → `prefixErrorRes: Int?`; dodano 2 stringi (patrz §5) |
| B3 | P2 | [C] | Błąd ładowania szczegółu = ślepy zaułek (brak retry) | `MaskedEmailDetailScreen.ErrorMessage` | Renderował tytuł+treść bez akcji; `loadEmail()` jest w pełni re-runowalny | Dodano przycisk Retry (jak w liście) |
| B4 | P2 | [C] | Pusty stan nie odróżnia „brak masków" od „brak wyników filtra" — podpowiada tworzenie mimo istniejących masków | `MaskedEmailListScreen` | `EmptyBlock()` pokazywany dla każdego `filteredEmails.isEmpty()` | Rozgałęzienie: `NoMatchesBlock()` gdy maski istnieją ale odfiltrowane |
| B6 | P2 | [C] | Archiwizacja bez feedbacku in-flight — ekran wygląda na zawieszony | `MaskedEmailDetailScreen` | Przycisk delete znika przy `isDeleting`, nic nie pokazuje progresu | Spinner w top-barze gdy `isDeleting` |
| B7 | P3 | [C] | „Zapisz" aktywny dla edycji samych spacji → martwy tap (VM robi no-op) | `MaskedEmailDetailScreen` | UI liczył `hasChanges` na surowych stringach, VM na trymowanych | UI liczy `hasChanges` na `.trim()` — zgodnie z VM |
| R5 | P3 | [C] | Pole wyszukiwania bez etykiety dla TalkBack | `MaskedEmailListScreen.SearchField` | `BasicTextField` tylko z wizualnym placeholderem | Dodano `semantics { contentDescription }` |
| R6 | P3 | [C] | „Sign in" w demo-bannerze: tap-target < 48dp, brak roli Button | `DemoBanner` | Goły `clickable` `Text` | `heightIn(min = 48.dp)` + `role = Role.Button` |
| R7 | P3 | [C] | Segmented control (initial state) bez semantyki selekcji | `CreateMaskedEmailScreen.StateSegmented` | `clickable` zamiast `selectable` | `selectable(selected, role = Role.RadioButton)` |

---

## 4. Testy

| Metryka | Przed | Po |
|---|---|---|
| Testy jednostkowe | 101 (PASS) | **109 (PASS)** |
| Nowe przypadki | — | +8 |
| Lint errors | 0 | 0 |
| Nowe warningi | — | 0 (dodano `@file:OptIn` do testu create) |

**Nowe testy regresyjne:**
- `MaskCsvTest`: neutralizacja formuły z wiodącą spacją; benign leading-whitespace nietknięty (SEC2).
- `CreateMaskedEmailViewModelTest`: prefix unicode → `prefix_chars` res; prefix >64 → `prefix_length` res (B2).
- `MaskedEmailDetailViewModelTest`: edycja w trakcie przetrwa reload po toggle; initial load wciąż seeduje pola (CC3).
- `MaskedEmailListViewModelTest`: dwa refresh w tej samej klatce = jeden fetch (CC5).
- `ProRepositoryImplTest`: buy-flow OK bez naszego SKU wciąż emituje event terminalny (CC2).

### Komendy
```bash
./gradlew testDebugUnitTest   # 109/109 pass
./gradlew lintDebug           # 0 errors
./gradlew assembleRelease     # SUCCESS (R8)
```

---

## 5. Zmiany w zasobach (i18n)

Dodano 4 nowe stringi do `values/strings.xml`, oznaczone `tools:ignore="MissingTranslation"` (staged-translation idiom — nie globalne wyłączenie lintu):
`create_email_error_prefix_length`, `create_email_error_prefix_chars`, `email_list_no_matches`, `email_list_no_matches_sub`.

**Do zrobienia:** przetłumaczyć te 4 klucze na 19 pozostałych języków. Do tego czasu wyświetlają się po angielsku (fallback) — tak samo jak wcześniej dla B2 (były zahardkodowanym angielskim), więc bez regresji. B1/R5 reużywają istniejące, przetłumaczone klucze `state_*` / `email_list_search_placeholder`.

---

## 6. Znaleziska NIE naprawione (świadomie) — patrz UX_RECOMMENDATIONS.md

| ID | Prio | Problem | Powód niewdrożenia |
|----|------|---------|--------------------|
| SEC1 | P2 | Brak kryptograficznej weryfikacji podpisu zakupów Play (`Purchase.signature`) — możliwy bypass Pro na zrootowanym urządzeniu z hookowanym Play | **Wymaga klucza publicznego RSA z Play Console.** Wpisanie pustego/złego klucza = fail-closed, blokada wszystkich realnych zakupów. Standardowy trade-off dla IAP bez backendu. Implementacja opisana w rekomendacjach — czeka na klucz od Pawła |
| R1 | P2 | Domyślny amber `#A8530F` jako **mały tekst** w dark mode ma kontrast ≈3.39:1 (< AA 4.5:1) | Zmiana koloru brandu = decyzja wizualna/produktowa. Zmierzone i opisane w rekomendacjach z propozycją rozjaśnienia. Nie zmieniam brandu jednostronnie |
| B5 | P2 | Utrata niezapisanych edycji przy back (create + detail) bez ostrzeżenia | Dialog potwierdzenia dodaje tarcie do częstej akcji = decyzja produktowa; nie wdrażam nowego flow UX jednostronnie |
| R4 | P3 | Wylogowanie bez potwierdzenia (mniej destrukcyjne archiwum ma dialog) | Jw. — tarcie vs bezpieczeństwo, do decyzji |
| R2, R3, R8, R9, R10 | P3 | Chaining klawiatury; blokujący snackbar create; label „…" na przyciskach loading; martwe komponenty; brak `<plurals>` | Polish/spójność — w rekomendacjach |

Znane z poprzednich audytów (nadal aktualne, poza zakresem tego przebiegu): A1–A5 (tutorial bounds<5, tutorial step3 pozycyjnie, export orphan files, buy no-op gdy context≠Activity, package-keep ProGuard).

---

## 7. Ograniczenia audytu

- **Play Billing:** buy/pending/refund i bypass podpisu (SEC1) wymagają internal testing na urządzeniu — CC2 zweryfikowany przez czytanie kodu + test jednostkowy repo, nie runtime.
- **App lock:** biometryka niemożliwa na emulatorze.
- **Dostępność:** brak automatycznego skanu TalkBack; znaleziska a11y z analizy kodu (semantyka, tap-targets, kontrast policzony ręcznie).
- **Instrumented tests:** 0 plików `androidTest` — brak E2E UI (rekomendacja jak w poprzednich audytach).
- **CC1/CC5/CC2:** trigger wymaga rzadkich warunków runtime (I/O fail, race, edge Play) — potwierdzenie kodowe, poprawki + testy dodane.
