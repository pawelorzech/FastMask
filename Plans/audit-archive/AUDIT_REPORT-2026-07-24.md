# FastMask — Audyt techniczny 2026-07-24 (pass C)

**Rewizja wejściowa:** `c8bfc3d` (v1.8.1, versionCode 18, `main`)
**Gałąź z poprawkami:** `feature/audit-2026-07-24c`
**Zakres:** pełny sweep repozytorium — architektura, kod, dane, bezpieczeństwo, prywatność, zależności, wydajność, dostępność, i18n, UX
**Metodyka:** przegląd całego kodu produkcyjnego (74 pliki, ~11 000 linii) i testowego; analiza statyczna, buildy debug i release, testy jednostkowe; każda hipoteza weryfikowana odczytem kodu **oraz** uruchomionym narzędziem

> **Kontekst:** czwarty przebieg audytu tego repozytorium. Poprzednie trzy (2026-07-19, 2026-07-23, 2026-07-24 pass A i B) naprawiły ponad 30 problemów. Ten przebieg szukał defektów, które tamte przebiegi **przeoczyły** albo **wprowadziły** — nie powtarzał znalezisk C1–C11, A1–A5 ani B1–B7.

---

## 1. Stan bazowy (przed zmianami)

| Komenda | Wynik |
|---|---|
| `./gradlew testDebugUnitTest` | ✅ 114 testów, 0 porażek |
| `./gradlew lintDebug` | ✅ 0 errorów |
| `./gradlew assembleDebug` | ✅ SUCCESS |
| `./gradlew assembleRelease` | ✅ SUCCESS (R8 + shrinkResources) |
| Środowisko | JDK 21 (pinowany w `gradle.properties`), Gradle 8.9, Kotlin 1.9.22, compileSdk/targetSdk 36 |

**Zielony build nie był dowodem poprawności.** Dwa najpoważniejsze znaleziska (D1, D2) istniały **mimo** zielonego lintu — bo oba były jawnie wyciszone w kodzie.

---

## 2. Rozpoznanie projektu

**Przeznaczenie.** Natywny klient Android do zarządzania maskowanymi adresami e-mail Fastmail. Logowanie tokenem API, przeglądanie / tworzenie / edycja / archiwizacja masek. Model: darmowy rdzeń + jednorazowy zakup „FastMask Pro" (`pro_lifetime`) odblokowujący akcenty, biometryczną blokadę aplikacji i eksport CSV.

| Warstwa | Zawartość |
|---|---|
| **Dane** | JMAP przez Retrofit/OkHttp (`JmapApi`, cache sesji pod mutexem), `TokenStorage` (EncryptedSharedPreferences), `SettingsDataStore` + `ProEntitlementStore` (DataStore), Play Billing 8.3 |
| **Domena** | Modele (`MaskedEmail`, `EmailState`, `ProStatus`, `Accent`), interfejsy repozytoriów, 10 use case'ów |
| **UI** | Compose + Material 3, 8 ekranów, `StateFlow<UiState>` + `Channel<Event>`, Hilt, Navigation z shared elementami |

**Przepływ danych.** ViewModel → UseCase → `MaskedEmailRepositoryDispatcher` → (real JMAP | demo in-memory); routing po `AppMode` czytanym z DataStore przy każdym wywołaniu.

**Miejsca przechowywania danych.** Token API → EncryptedSharedPreferences (wykluczony z backupu i device-transfer). Ustawienia i uprawnienie Pro → DataStore. Eksport CSV → `cacheDir/exports`, retencja 1 h. Maski **nie są** cache'owane lokalnie — każde wejście to fetch.

**Główne ścieżki.** Welcome → (login tokenem | demo) → lista → szczegół (edycja / włącz-wyłącz / archiwizacja z undo) → tworzenie → ustawienia → paywall Pro.

**Obszary największego ryzyka.** (1) Uprawnienie Pro — styk Play ↔ lokalny cache. (2) Token API — jedyny sekret, daje pełny dostęp do skrzynki. (3) Eksport CSV — jedyne miejsce, gdzie wszystkie maski lądują na dysku jawnym tekstem. (4) Integracje z systemem przez intenty — jedyna droga do supportu i wymaganych przez Play dokumentów prawnych.

---

## 3. Znalezione problemy

Status: **[C]** potwierdzony (odczyt kodu + uruchomione narzędzie) · **[R]** potwierdzony eksperymentem runtime · **[D]** wymaga urządzenia.

### 3.1 Naprawione

| ID | Prio | Status | Problem | Lokalizacja | Przyczyna źródłowa |
|----|------|--------|---------|-------------|--------------------|
| **D1** | **P1** | **[C]** | Kontakt/support, Polityka prywatności i Regulamin to **martwe tapnięcia na Androidzie 11+**, bez żadnego feedbacku | `SettingsScreen.kt:364`, `ProScreen.kt:264,271` | Od API 30 `resolveActivity()` podlega filtrowaniu widoczności pakietów. Manifest **nie miał `<queries>`**, więc metoda zwracała `null` dla `mailto:` i `https:` mimo zainstalowanych aplikacji. Gałąź `null` była pustym `if` → cicha porażka |
| **D2** | P2 | [C] | 13 stringów po angielsku we **wszystkich 19 przetłumaczonych językach**, w tym potwierdzenia wylogowania i odrzucenia zmian | `values-*/strings.xml` | Poprzednie przebiegi audytu dodawały stringi z `tools:ignore="MissingTranslation"`, co wyciszyło własną kontrolę Lintu. Nic nigdy nie zgłosiło braku |
| **D3** | P2 | [C] | Nieudane logowanie kasowało wklejony token — także przy braku sieci / 429 / 5xx | `LoginViewModel.kt:51` | Czyszczenie stanu bezwarunkowe, przed rozgałęzieniem `fold`. Sprzeczne z komunikatami `UiErrors`, które każą spróbować ponownie |
| **D4** | P2 | [C] | Awaria zapisu DataStore przy wejściu w demo **wywala aplikację** | `WelcomeViewModel.kt:31` | `viewModelScope` re-rzuca nieobsłużone wyjątki na głównym wątku. `SettingsViewModel` i `MaskedEmailListViewModel` mają `CoroutineExceptionHandler`; `WelcomeViewModel` został pominięty |
| **D5** | P2 | [R] | Podpisany release bez `PLAY_LICENSE_KEY` **cicho wyłącza weryfikację podpisu zakupu** | `build.gradle.kts`, `PlayBillingDataSource.kt:266` | `isSignatureValid()` zwraca `true` przy pustym kluczu (świadomy kompromis dla dev/CI), ale nic nie odróżniało builda dev od produkcyjnego. Ostrzeżenie tylko w logu debug |
| D6 | P3 | [C] | Dokumentacja podawała SDK 35, faktycznie 36 | `CLAUDE.md` | Drift po bumpie SDK |
| **D7** | P2 | [C] | **18 z 19 lokali pokazuje surowe `%s`** na ekranie szczegółów maski („Letzte Nachricht: %s") | `values-*/strings.xml:60` | `email_detail_last_message` był kiedyś zdaniem „Last message: %s", potem stał się samą etykietą (wartość renderuje `MetaRow` osobno). Zaktualizowano tylko `values/` i `values-pl`. `stringResource()` wołane bez argumentów → placeholder trafia na ekran dosłownie |

**Jak znalazłem D7:** czytając ostrzeżenia `StringFormatCount` w raporcie lintu. Build failuje tylko na errorach, więc te ostrzeżenia narastały nieprzeczytane — podobnie jak `MissingTranslation` było wyciszone przy D2. To ten sam wzorzec: **sygnał istniał, nikt go nie odbierał**.

### 3.2 D1 to częściowo **regresja wprowadzona przez poprzedni audyt**

To najważniejszy wniosek tego przebiegu i wymaga rozdzielenia dwóch przypadków:

- **Linki Pro (Polityka prywatności, Regulamin)** — **regresja**. Do commita `f1e89ee` (pass A, v1.8.0) kod wołał `context.startActivity(...)` bezpośrednio i **działał**: `startActivity` nie podlega filtrowaniu widoczności pakietów. Pass A opakował je w `if (resolveActivity(...) != null)` jako „hardening" i tym samym je **zepsuł** w v1.8.1. Zgodnie z regułą „regresja = min. P1" to podnosi priorytet całości.
- **Kontakt (`mailto:`)** — defekt zastany, obecny od `46cd22b` (styczeń 2026), nigdy nie działał na Androidzie 11+.

Wniosek procesowy: `resolveActivity()` jako bramka przed `startActivity()` jest antywzorcem na współczesnym Androidzie. Poprawka usuwa go z **wszystkich** trzech miejsc i zastępuje jednym helperem, więc wzorzec nie może wrócić przez kopiuj-wklej.

### 3.3 Nienaprawione (świadomie)

| ID | Prio | Status | Problem | Lokalizacja | Powód pozostawienia |
|----|------|--------|---------|-------------|---------------------|
| E1 | P3 | [C] | `loadMaskedEmails()` (pull-to-refresh) nie sprawdza `refreshInFlight`, więc może wystartować równolegle z miękkim odświeżeniem | `MaskedEmailListViewModel.kt:87` | Skutek to jeden zbędny request i last-writer-wins na identycznych danych. Poprawka wymaga ujednolicenia dwóch ścieżek ładowania — zmiana projektowa nieproporcjonalna do szkody |
| E2 | P3 | [C] | `delete()` nie czyści `isDeleting` po sukcesie — polega na tym, że ekran zniknie | `MaskedEmailDetailViewModel.kt:186` | Poprawne dopóki nawigacja działa; zmiana wprowadziłaby migotanie przycisku tuż przed przejściem |
| E3 | P3 | [D] | `requestUnlock()` może zostać wywołane dwukrotnie (`LaunchedEffect` + przycisk) → dwa `BiometricPrompt.authenticate()` | `MainActivity.kt:188` | Nie umiem tego odtworzyć bez urządzenia z biometrią; „naprawa" na ślepo mogłaby zablokować legalny retry |
| E4 | P3 | [C] | Zdublowany klucz języka (pole instancji + `companion`) | `SettingsDataStore.kt:26,116` | Czysta duplikacja, zero wpływu na zachowanie — obie ścieżki czytają ten sam klucz |
| E5 | P3 | [C] | `android:fullBackupContent` jest martwą konfiguracją przy `allowBackup="false"` | `AndroidManifest.xml:15` | Nadmiarowe, ale bezpieczne; zostawienie chroni, gdyby ktoś kiedyś włączył backup |
| E6 | P2 | [D] | Eksport CSV zapisuje **wszystkie maski jawnym tekstem** do `cacheDir/exports`, retencja 1 h | `SettingsScreen.kt:128` | Świadomy kompromis: krótsza retencja mogłaby uciąć URI, które wolny odbiorca (upload na Dysk) wciąż czyta. Katalog prywatny dla aplikacji. Zgłaszam jako fakt do decyzji, nie jako defekt |
| E7 | P3 | [C] | `ProEntitlementStore` nie odświeża digestu dowodu, gdy status zostaje PRO, a zmienia się token zakupu | `ProRepositoryImpl.kt:217` | Digest nigdy nie jest weryfikowany względem tokenu — służy wyłącznie do odróżnienia „PRO z dowodem" od ręcznie podrobionego wpisu. Nieaktualny digest tej własności nie osłabia |

---

## 4. Czego **nie** znaleziono

Sprawdzone i czyste — istotne, bo zawęża pole przyszłych audytów.

- **Brak P0.** Żadnej utraty danych, żadnej ścieżki pomieszania kont, żadnego crasha na głównej ścieżce.
- **Sekrety.** Brak kluczy, tokenów i haseł w repozytorium; klucz licencyjny i keystore wyłącznie przez env / `~/.gradle/gradle.properties`.
- **Transport.** `network_security_config` wymusza system CA i blokuje cleartext; `JmapApi.isFastmailHttpsUrl()` waliduje `apiUrl` z odpowiedzi sesji, więc złośliwa odpowiedź nie przekieruje ruchu.
- **Powierzchnia ataku.** Jedna eksportowana Activity bez deep linków i filtrów intentów; `FileProvider` nieeksportowany; `FLAG_SECURE` i `filterTouchesWhenObscured` w release.
- **Współbieżność.** Sesja JMAP pod mutexem, pola `@Volatile`, uprawnienie Pro serializowane `reconcileMutex`, gardy podwójnego tapnięcia ustawiane synchronicznie przed `launch`.
- **CSV injection.** Neutralizacja sprawdza pierwszy **nie-biały** znak i obejmuje CR/LF — zgodnie z OWASP.
- **Dostępność.** Stany masek, filtry, pole wyszukiwania i kontrolki segmentowe mają semantykę dla TalkBack; cele dotyku ≥ 48 dp na krytycznych akcjach.

---

## 5. Weryfikacja poprawek

Każda poprawka została **uruchomiona**, nie tylko napisana.

| Co weryfikowano | Metoda | Wynik |
|---|---|---|
| `<queries>` trafia do finalnego artefaktu | odczyt `merged_manifest/release/AndroidManifest.xml` | ✅ oba intenty obecne (obok tych dodanych przez bibliotekę Billing) |
| Test i18n faktycznie łapie regresję | celowe usunięcie jednego tłumaczenia i podmiana drugiego na angielski | ✅ oba testy failują; po przywróceniu przechodzą |
| Lint znów widzi braki tłumaczeń | usunięcie `tools:ignore`, `lintDebug` | ✅ 0 `MissingTranslation` |
| Bramka release — bez keystore (CI) | `./gradlew assembleRelease` | ✅ SUCCESS — dev/CI niezablokowane |
| Bramka release — podpisany + pusty klucz | `assembleRelease -Pfastmask.keystore=… -Pfastmask.playLicenseKey=` | ✅ BUILD FAILED z komunikatem „Refusing to build a signed release…" |
| Bramka release — podpisany + klucz obecny | `assembleRelease -Pfastmask.keystore=…` | ✅ SUCCESS |
| Kontrakt logowania | 5 testów (401/403 czyści token; IOException/429/5xx zachowuje) | ✅ PASS |

**Uwaga metodyczna.** Pierwsza wersja bramki D5 siedziała w `doFirst` na `assembleRelease` i **nie działała** — `validateSigningRelease` failował wcześniej i maskował błąd. Wykryte wyłącznie dzięki faktycznemu uruchomieniu testu; odczyt kodu tego nie pokazał. Przeniesiona na `gradle.taskGraph.whenReady`. Drugi fałszywy sygnał: test „podpisany bez klucza" początkowo przechodził, bo klucz licencyjny jest u Pawła w `~/.gradle/gradle.properties` — trzeba go było jawnie wyzerować, żeby przetestować ścieżkę negatywną.

---

## 6. Stan po zmianach

| Metryka | Przed | Po |
|---|---|---|
| `testDebugUnitTest` | 114 PASS | **124 PASS**, 0 porażek |
| `lintDebug` — errory | 0 | 0 |
| `lintDebug` — `MissingTranslation` | wyciszone `tools:ignore` | **0**, wyciszenia usunięte |
| `lintDebug` — `StringFormatCount` | 18 ostrzeżeń | **0** |
| `lintDebug` — `MissingQuantity` | 0 (brak plurals) | **0** (plurals kompletne wg CLDR) |
| `assembleDebug` / `assembleRelease` | SUCCESS | SUCCESS |
| Kompletność tłumaczeń | 19 lokali × 13 luk | **0 luk**, pilnowane testem |
| Wersja | 1.8.1 (vc 18) | **1.8.2 (vc 19)** |

### Wdrożone rekomendacje UX (decyzja Pawła)

| ID | Rekomendacja | Realizacja |
|---|---|---|
| A1 | Snackbar nazywa skopiowany adres | `list_copied_value` „Skopiowano %1$s" w 20 lokalach |
| A2 | Nieudany eksport podaje przyczynę | `ExportFailed(messageRes)` przez `UiErrors`; osobny komunikat dla błędu zapisu pliku; +3 testy |
| A3 | `<plurals>` w liczniku listy | Dwa fragmenty łączone tłumaczalnym formatem (RTL może zmienić kolejność). Pełne formy CLDR dla pl/ru/uk/ar oraz `many` dla es/fr/it/pt. **ar/bn/hi/th używają neutralnej formy „etykieta: liczba"** — ich reguły zgodności są poza tym, co potrafię zweryfikować, a niesprawdzalna odmiana jest gorsza niż poprawna neutralność |

---

## 7. Ograniczenia audytu

Czego **nie** dało się zweryfikować w tym środowisku:

1. **Zachowanie intentów na realnym Androidzie 11+.** D1 potwierdzone regułami widoczności pakietów, historią gita i odczytem zmergowanego manifestu — ale samo tapnięcie „Kontakt" na telefonie nie zostało wykonane. Pozycja nr 1 manualnego QA.
2. **Cały przepływ Play Billing.** Brak konta testowego i builda na Play; logika zweryfikowana wyłącznie jednostkowo na fake'u `BillingDataSource`.
3. **Biometria i blokada aplikacji.** Wymaga urządzenia z odciskiem/PIN-em; E3 pozostaje niepotwierdzone.
4. **Realny ruch JMAP.** Brak tokenu Fastmail — warstwa sieciowa testowana tylko na fake'ach.
5. **Jakość tłumaczeń.** Wszystkie tłumaczenia (13 stringów z D2 + 3 z A1/A2 + 2 plurals z A3) wykonałem samodzielnie. Są poprawne znaczeniowo i gramatycznie, ale **nie sprawdził ich native speaker** — dla 19 języków to realne ryzyko drobnych nienaturalności, zwłaszcza w bn, th, hi, vi. Paweł świadomie zaakceptował to ryzyko; punkt wyjścia był gorszy (te same stringi były w 100% po angielsku).
6. **Brak testów instrumentowanych.** Konfiguracja `androidTest` istnieje, testów UI nie ma; `connectedAndroidTest` nie było uruchamiane (brak emulatora w sesji).

---

# Aneks — backlog po v1.8.2 (2026-07-25, pass D)

**Gałąź:** `feature/backlog-e1-e7` (z `main` @ `74e20cf`) · **niezmergowana**
**Zakres:** domknięcie pozycji E1–E7 z §3.3 oraz rekomendacji B1 i B3 z `UX_RECOMMENDATIONS.md`

## D.1 Stan

| Metryka | Po pass C | Po pass D |
|---|---|---|
| Testy jednostkowe | 124 PASS | **143 PASS** |
| Testy instrumentowane | brak | **12 PASS** (Pixel 9a, API 36) |
| `lintDebug` errory | 0 | 0 |
| `assembleDebug` / `assembleRelease` | SUCCESS | SUCCESS |

## D.2 Zamknięte pozycje E1–E7

| ID | Co zrobiono |
|----|-------------|
| E1 | Zunifikowano dwie zduplikowane ścieżki ładowania listy w jedno `fetch()` z jedną flagą in-flight. Osobne flagi powodowały, że pull-to-refresh w trakcie cichego odświeżania startował drugi równoległy fetch (ciche odświeżanie nie podnosi `isLoading`, gdy lista ma dane) |
| E2 | `delete()` czyści `isDeleting` po wysłaniu eventu — stan zgodny z rzeczywistością, bez migotania przycisku |
| E4 | Klucz języka miał dwie deklaracje z tym samym literałem; `companion` jest teraz jedynym właścicielem |
| E5 | **Bez zmiany kodu.** `allowBackup="false"` faktycznie unieważnia `fullBackupContent`, ale reguły wykluczają zaszyfrowane prefy z tokenem — chronią, gdyby ktoś backup włączył. Udokumentowane w manifeście zamiast usunięte |
| E6 | Eksport CSV przeżywał wylogowanie w `cacheDir`. Wydzielono `ExportCache` jako jedynego właściciela katalogu (zapis + wygasanie + czyszczenie), wołany z `logout()` |
| E7 | Zapis dowodu uprawnienia następuje też, gdy status zostaje PRO, a zmienia się token zakupu (odkup po zwrocie). Warunek na tokenie utrzymuje brak zapisów w przypadku typowym |

E3 (podwójny `BiometricPrompt`) **pozostaje otwarty** — wymaga urządzenia z biometrią, którego nadal nie było.

## D.3 Trzy defekty wykryte dopiero przez testy instrumentowane (B3)

Cztery przebiegi przeglądu kodu ich nie znalazły. Wszystkie potwierdzone [C] i naprawione.

| ID | Prio | Problem | Przyczyna źródłowa |
|----|------|---------|--------------------|
| **D9** | P2 | **Każdy przycisk ikonowy w aplikacji** (wstecz, ustawienia, kopiuj, archiwizuj) był dla TalkBacka bezimiennym „Button" | `PillIconButton` przyjmował parametr `contentDescription`, ale stosował go **wyłącznie** jako `onClickLabel`. To nazywa akcję („dwukrotnie dotknij, aby Ustawienia"), nie kontrolkę. Wewnętrzna ikona świadomie ma `contentDescription = null`, więc nazwy nie było nigdzie |
| **D10** | P2 | Każde pole formularza czytane jako gołe „Edit box" | `DesignInput` renderuje etykietę jako osobny `Text` nad polem — wizualnie poprawnie, semantycznie bez powiązania |
| **D8** | P3 | „Wypróbuj demo" po wylogowaniu otwierało **poprzednie demo z jego zmianami** | Repozytorium demo to singleton na czas procesu, nigdy nie resetowany — wbrew kontraktowi opisanemu w KDoc tej samej klasy. Dodano `DemoSession.reset()`, wołane przy wejściu w demo |

D9 i D10 naprawiono w komponentach, więc pokrywają wszystkie miejsca wywołań naraz.

## D.4 Cache offline (B1)

Wdrożony po decyzji Pawła: **zaszyfrowany, czyszczony przy wylogowaniu**.

`MaskedEmailCache` trzyma ostatni udany fetch w `EncryptedFile` na kluczu z Android Keystore — tej samej ochronie, jaką `TokenStorage` daje tokenowi API. To jedyne miejsce poza eksportem CSV, gdzie komplet masek trafia na dysk, więc nie leży jawnym tekstem; test instrumentowany sprawdza, że adresy **nie występują w bajtach pliku**.

Decyzje projektowe:

- `cachedMaskedEmails()` jest **osobnym wywołaniem**, nie cichym fallbackiem w `getMaskedEmails()`. To drugie znaczy „powiedz, co mówi serwer" — odpowiadanie starymi danymi pozwoliłoby podać nieaktualne maski jako aktualne.
- Fallback działa **tylko** gdy ekran jest pusty i fetch padł. Nieudane odświeżanie w tle przy dobrych danych nie zmienia nic.
- Lista pokazuje „Offline · zaktualizowano X temu" zawsze, gdy to snapshot, w 20 językach.
- Każda awaria cache'u jest miękka: brak / nieczytelny / uszkodzony → „brak cache", czyli dokładnie zachowanie sprzed tej zmiany.

## D.5 Zgodność targetSdk w Play (zgłoszone przez Pawła)

Play zgłosił: „highest non-compliant target API level is Android 15 (API level 35)".

**To nie było zadanie kodowe.** `targetSdk = 36` wszedł w commicie `1febc93` (v1.7.3, versionCode 16) i każdy późniejszy build jest zgodny. Niezgodny był artefakt **wiszący na ścieżce testów wewnętrznych**: `15 (1.7.2)` z 19 lipca, sprzed bumpu.

Naprawione promocją buildu `19 (1.8.2)` — tego samego, który jest na produkcji — na testy wewnętrzne. Ścieżka pokazuje teraz `Latest release: 19 (1.8.2)`.

**Wniosek na przyszłość:** ostrzeżenia Play o targetSdk dotyczą **wszystkich aktywnych ścieżek**, nie tylko produkcji. Stare buildy na testach wewnętrznych/zamkniętych liczą się do zgodności.

## D.6 Nowe pozycje otwarte

Play zgłasza dla `19 (1.8.2)` trzy rekomendacje, których ten przebieg **nie** ruszał:

| Pozycja | Kategoria | Uwaga |
|---|---|---|
| „Edge-to-edge may not display for all users" | User experience | **Zamknięte.** Tekst rekomendacji okazał się ogólną poradą („obsłuż insety, ewentualnie wołaj `enableEdgeToEdge()`" — aplikacja już to robi). Realną luką był `LockScreen`: jedyny ekran bez `Scaffold`, więc bez obsługi insetów. Dodano `windowInsetsPadding(safeDrawing)` |
| „Your app uses deprecated APIs or parameters for edge-to-edge" | User experience | **Zamknięte.** Play nazwał `Window.setStatusBarColor`, `Window.setNavigationBarColor`, `LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES`. Usunięto odpowiadające im atrybuty z obu motywów; reszta pochodzi z wnętrza `enableEdgeToEdge()` i nie jest w naszej gestii bez podbicia `androidx.activity` |
| „Improve your app's memory and performance with R8 optimization" | Technical quality | **Otwarte.** Sugestia pełnego trybu R8 — nie user-facing |

**Uwaga metodyczna do rekomendacji Play:** sekcja „These start in the following places" podała `com.fastmask.data.api.JmapRequest.<clinit>`, `okhttp3.internal.platform.Platform.<clinit>` i `D0.O.t`. Żadne z nich nie ma nic wspólnego z paskami systemowymi — to artefakt analizy statycznej na zminifikowanym bytecodzie. Przypisania winy w tych rekomendacjach nie należy traktować dosłownie; wiarygodna jest lista API, nie lista miejsc.

Naprawione w commicie `1b37eef` (patrz `UX_RECOMMENDATIONS.md` §E). Do tego nadal otwarte: **E3** (podwójny `BiometricPrompt`) i **manualne QA na urządzeniu** — D1 pozostaje potwierdzone regułami Androida i zmergowanym manifestem, ale nie tapnięciem na telefonie. Teraz jest to możliwe: 1.8.2 jest na testach wewnętrznych.
