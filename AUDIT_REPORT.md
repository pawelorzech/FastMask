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
| `testDebugUnitTest` | 114 PASS | **121 PASS**, 0 porażek |
| `lintDebug` | 0 errorów | 0 errorów |
| `assembleDebug` | SUCCESS | SUCCESS |
| `assembleRelease` | SUCCESS | SUCCESS |
| Kompletność tłumaczeń | 19 lokali × 13 luk | **0 luk**, pilnowane testem |

---

## 7. Ograniczenia audytu

Czego **nie** dało się zweryfikować w tym środowisku:

1. **Zachowanie intentów na realnym Androidzie 11+.** D1 potwierdzone regułami widoczności pakietów, historią gita i odczytem zmergowanego manifestu — ale samo tapnięcie „Kontakt" na telefonie nie zostało wykonane. Pozycja nr 1 manualnego QA.
2. **Cały przepływ Play Billing.** Brak konta testowego i builda na Play; logika zweryfikowana wyłącznie jednostkowo na fake'u `BillingDataSource`.
3. **Biometria i blokada aplikacji.** Wymaga urządzenia z odciskiem/PIN-em; E3 pozostaje niepotwierdzone.
4. **Realny ruch JMAP.** Brak tokenu Fastmail — warstwa sieciowa testowana tylko na fake'ach.
5. **Jakość tłumaczeń.** Tłumaczenia wykonałem samodzielnie. Są poprawne znaczeniowo i gramatycznie, ale **nie sprawdził ich native speaker** — dla 19 języków to realne ryzyko drobnych nienaturalności, zwłaszcza w bn, th, hi, vi.
6. **Brak testów instrumentowanych.** Konfiguracja `androidTest` istnieje, testów UI nie ma; `connectedAndroidTest` nie było uruchamiane (brak emulatora w sesji).
