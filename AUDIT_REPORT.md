# AUDIT_REPORT — FastMask, 2026-08-01

**Repozytorium:** `~/Programowanie/FastMask` · **Wersja:** 1.10.1 (versionCode 22)
**Punkt wyjścia:** `main` @ `b3d6dca` · **Gałąź poprawek:** `feature/audit-2026-08-01`
**Przebieg:** szósty audyt. Poprzednie raporty: `Plans/audit-archive/`.

Ten dokument podaje **fakty z lokalizacją i statusem potwierdzenia**. Wnioski, oceny i propozycje produktowe są w `UX_RECOMMENDATIONS.md`; lista zmian w `CHANGELOG_AGENT.md`.

Statusy potwierdzenia używane niżej: **[P]** potwierdzone z kodu lub uruchomionym narzędziem · **[U]** wymaga testu manualnego na urządzeniu · **[H]** hipoteza.

---

## 1. Streszczenie stanu

FastMask to natywna aplikacja Android (Kotlin, Compose, Hilt, JMAP) do zarządzania maskami e-mail Fastmaila. 12 591 linii Kotlina produkcyjnego w 108 plikach, 47 plików testowych, 20 lokalizacji, minSdk 26 / target 36.

Kod jest w bardzo dobrym stanie i widać na nim pięć poprzednich przebiegów: komentarze przy nietrywialnych miejscach tłumaczą nie tylko *co*, ale *jaki błąd* dana linia zamyka, a warstwy są rozdzielone konsekwentnie. Duże obszary sprawdziłem i **nie znalazłem w nich nic do naprawienia** — obsługa tokenu, powierzchnia IPC quick-maska, wiązanie snapshotu z kontem, częściowe odpowiedzi JMAP, cache sesji, escapowanie CSV, rozmiary celów dotykowych, poprawność RTL i kompletność tłumaczeń (233 stringi + 8 plurali × 20 lokalizacji, zero dryfu). Szczegóły w § 6.

Nowe ustalenia tego przebiegu koncentrują się w trzech miejscach, których wcześniejsze audyty nie dotykały:

1. **Repozytorium nie budowało się z czystego klona** — udokumentowana komenda `./gradlew assembleDebug` padała u każdego poza Tobą. To także wyjaśnia brak jakiegokolwiek builda w CI.
2. **Trzy wyścigi na styku „operacja w locie ↔ koniec sesji/ekranu"**, z których jeden łamał obietnicę zapisaną w polityce prywatności.
3. **Poprawka dostępności z poprzedniego audytu okazała się kodem martwym** — warunek, pod którym ogłaszała postęp, nie był spełniony w żadnym miejscu wywołania.

Naprawione: **6 pozycji P1, 3 z P2 i 3 z P3**. Dwie pozycje P2 zostawiłem świadomie nienaprawione (S-3 i A-1) — uzasadnienie przy każdej. Wszystkie bramki jakości przechodzą.

---

## 2. Mapa architektury

**Główne komponenty**

| Warstwa | Zawartość |
|---|---|
| `data/api` | `JmapApi` (cache sesji pod `Mutex`, walidacja `apiUrl`), `JmapService` (Retrofit), modele kotlinx.serialization |
| `data/local` | `TokenStorage` (EncryptedSharedPreferences), `MaskedEmailCache` (EncryptedFile, snapshot offline), `ExportCache` (CSV w cacheDir, jedyne dane w plaintekście), `ProEntitlementStore`, `SettingsDataStore`, `CrashReportingSettings` |
| `data/repository` | Implementacje + `MaskedEmailRepositoryDispatcher` przełączający demo/real per wywołanie |
| `data/billing` | `PlayBillingDataSource`, `PurchaseSecurity` (weryfikacja podpisu RSA) |
| `data/crash` | `FirebaseCrashlyticsReporter` — jedyny plik nazywający SDK Crashlytics |
| `domain` | Modele, interfejsy repozytoriów, use case'y, `ShareRouter`/`SharedLinkParser`, polityki crash-reportingu |
| `ui` | 8 ekranów (welcome, auth, list, create, detail, settings, pro, lock) + `DesignKit`, nawigacja, motyw |
| `quickmask` | Kafelek Quick Settings, skrót, powiadomienie z Undo, `QuickMaskActivity` (nieeksportowana) |

**Przepływ danych.** Token → `TokenStorage` (Keystore) → nagłówek `Authorization` → `api.fastmail.com` (JMAP). Odpowiedź → domena → zapis przelotowy do `MaskedEmailCache` (szyfrowany, związany z kontem przez SHA-256 tokenu) → UI. Brak własnych serwerów.

**Punkty integracji.** Fastmail JMAP · Google Play Billing · Firebase Crashlytics (opt-out) · systemowy BiometricPrompt · FileProvider (udostępnianie CSV).

**Miejsca przechowywania danych.** `fastmask_secure_prefs.xml` (token, szyfrowany) · `files/masked_emails_cache.bin` (+ `cache_staging/`, szyfrowany) · `files/datastore/` (ustawienia, uprawnienie Pro) · `cacheDir/exports/` (**CSV w plaintekście**, retencja 1 h).

**Główne ścieżki użytkownika.** Powitanie → wklejenie tokenu API → lista masek → utworzenie / edycja / archiwizacja z Undo → ustawienia (język, akcent, zamek, crash reporty, eksport, wylogowanie). Poza tym: tryb demo, kafelek/skrót quick-mask, udostępnianie linku do aplikacji, paywall Pro.

**Obszary największego ryzyka.** (1) Token Fastmaila — pełny dostęp do skrzynki. (2) Snapshot masek i eksport CSV jako dane w spoczynku. (3) Ścieżki tworzące maski (podwójne wysłanie = prawdziwa maska na koncie). (4) Odwracalność archiwizacji. (5) Bramka biometryczna.

---

## 3. Stan bazowy (przed zmianami)

Wykonane polecenia i ich dosłowne wyniki. JDK: bundlowany JBR Android Studio 17 — systemowy `java` to OpenJDK 26, na którym Gradle 8.9 **odmawia startu**; to nie jest udokumentowane w repo (patrz D-1).

| # | Polecenie | Wynik |
|---|---|---|
| 1 | `./gradlew test lint` | **BUILD SUCCESSFUL**, 0 błędów lintu, **99 ostrzeżeń** |
| 2 | `./gradlew testDebugUnitTest --rerun-tasks` | **BUILD SUCCESSFUL** — 39 klas, **447 testów, 0 porażek, 0 pominiętych** |
| 3 | `./gradlew assembleRelease` | **BUILD SUCCESSFUL** (R8 + shrink resources, niepodpisany) |
| 4 | `./gradlew connectedDebugAndroidTest` (emulator Pixel 9a, API 36) | **BUILD FAILED** — 19 testów, **1 porażka** |
| 5 | `git clone` → `./gradlew processDebugGoogleServices` | **BUILD FAILED** — `File google-services.json is missing` |

**Porażka w punkcie 4** to `MainFlowsTest#creatingAMaskReturnsToTheListWithTheNewMaskOnIt` z `RootViewWithoutFocusException: Waited for the root of the view hierarchy to have window focus`. To flake środowiskowy emulatora, nie defekt aplikacji — **[P]**: ten sam test przeszedł w każdym z trzech kolejnych przebiegów na tym samym emulatorze, bez żadnej zmiany w kodzie testu ani ekranu tworzenia.

**Rozkład 99 ostrzeżeń lintu:** 27 × `GradleDependency` (nieaktualne zależności), 55 × `Typos` (fałszywe trafienia na base64 w `font_certs.xml` i na `fmu1` w stringach), 9 × `UnusedResources`, 5 × `IconLauncherShape`, 4 × `InlinedApi` (świadome, `POST_NOTIFICATIONS`), 1 × `LocaleFolder`, 1 × `ObsoleteSdkInt`. Żadne nie jest błędem.

> **Zielony build nie jest dowodem, że aplikacja działa poprawnie.** Punkt 5 to pokazuje wprost: punkty 1–4 były zielone u mnie, a repozytorium było niebudowalne dla kogokolwiek innego.

**Propozycja naprawy.** Zachować kolory, dodać drugi kanał do `StateDot` — ten sam rozmiar, ta sama pozycja, bez zmiany układu:

## 4. Znalezione problemy

### P1

---

**B-1 · Czysty klon repozytorium nie buduje się** — status **[P]**, naprawione (`3f306ac`)

*Lokalizacja:* `app/build.gradle.kts:5-6` (przed zmianą), `.gitignore:41`

*Reprodukcja:* `git clone` → `./gradlew assembleDebug`.

*Oczekiwane vs rzeczywiste:* README § Build from Source, `CLAUDE.md` i `AGENTS.md` podają tę komendę jako sposób zbudowania projektu. Rzeczywistość:

```
> Task :app:processDebugGoogleServices FAILED
> File google-services.json is missing. The Google Services Plugin cannot function without it.
```

*Przyczyna źródłowa:* wtyczki `google-services` i `firebase.crashlytics` były aplikowane bezwarunkowo, a `app/google-services.json` jest — słusznie — w `.gitignore` (commit `deab7b0`, 2026-07-31). Plik **nigdy nie był w historii gita**, co sprawdziłem (`git rev-list --all --objects`), więc nie ma tu wycieku klucza; jest wyłącznie zepsuty build.

*Klasyfikacja jako regresja:* przed 1.10.0 nie było wtyczek Firebase, więc czysty klon budował się. Zgodnie z regułą „regresja = min. P1" to P1, mimo że nie dotyka użytkownika końcowego.

*Konsekwencja wtórna:* to wyjaśnia, dlaczego repozytorium **nigdy nie miało joba CI budującego kod** (§ B-2) — taki job padłby przy pierwszym uruchomieniu.

*Poprawka:* wtyczki aplikowane warunkowo. Degradacja jest bezpieczna **konstrukcyjnie, nie przypadkiem**: `FirebaseCrashlyticsReporter` rozwiązuje uchwyt SDK leniwie per wywołanie, a `CrashReportingStartup` już wcześniej łapał `IllegalStateException`, który `getInstance()` rzuca bez domyślnego `FirebaseApp` — ścieżka, którą i tak chodzą ROM-y bez content providerów.

*Weryfikacja:* klon bez pliku → `assembleDebug` **BUILD SUCCESSFUL**; zainstalowany APK **startuje i zostaje wznowiony** na emulatorze API 36 (`topResumedActivity=com.fastmask/.MainActivity`, proces żywy, zero `FATAL`/`Firebase` w logcat). Build z plikiem nadal rejestruje `processDebugGoogleServices` i zadania mapowania Crashlytics.

---

**B-2 · Brak jakiegokolwiek builda w CI, przy aktywnym Dependabocie** — status **[P]**, naprawione

*Lokalizacja:* `.github/workflows/` (dwa pliki, oba to boty Claude), `.github/dependabot.yml`

*Fakt:* w całej historii repozytorium istniały tylko `claude.yml` i `claude-code-review.yml` (`git log --all --name-only -- .github/workflows/`). Żaden workflow nigdy nie kompilował kodu ani nie uruchamiał testów. Jednocześnie Dependabot jest skonfigurowany na cotygodniowe PR-y dla `gradle` i `github-actions` (limit 5 + 5), a w repo leży 9 lokalnych gałęzi `dependabot/*`.

*Konsekwencja:* bump zależności trafiał do przeglądu bez żadnego dowodu, że się w ogóle kompiluje.

*Poprawka:* `.github/workflows/build.yml` — `testDebugUnitTest`, `lintDebug`, `assembleRelease` na push do `main` i każdy PR, akcje przypięte do SHA (zgodnie z konwencją istniejących workflowów), artefakty raportów. Build release'u jest tam celowo: pokrywa R8, reguły keep dla serializacji/Retrofita/Tinka oraz bramkę odmawiającą podpisanego release'u bez klucza licencyjnego.

*Uwaga:* ten job stał się możliwy dopiero po B-1.

---

**B-3 · Uszkodzony `pro_entitlement` zapętla crash u płacącego użytkownika** — status **[P]**, naprawione (`b0bfe7e`)

*Lokalizacja:* `data/local/ProEntitlementStore.kt:16` (brak handlera), `data/repository/ProRepositoryImpl.kt:234` (niechroniony zapis), `MainActivity.kt:140` (goły `lifecycleScope.launch`)

*Reprodukcja:* uszkodzić `files/datastore/pro_entitlement.preferences_pb` (przerwany zapis, pełny dysk) na urządzeniu z wykupionym `pro_lifetime`. Start → odczyt rzuca, jest złapany, status degraduje do FREE → `ON_RESUME` → `refresh()` → Play zwraca zakup → `newStatus = PRO ≠ FREE` → `store.write` → `CorruptionException` → nieprzechwycony w `lifecycleScope` → **proces ginie. Przy każdym starcie.**

*Przyczyna źródłowa:* audyt A3 dodał `ReplaceFileCorruptionHandler` do `SettingsDataStore`, a jego własny opis przyczyny brzmiał „brak `corruptionHandler` na **obu** DataStore'ach". Handler dostał tylko jeden.

*Dlaczego to boli tylko płacących:* użytkownik FREE uzgadnia ten sam status z tym samym `null`-owym tokenem, więc zapis nie następuje. Jedyne wyjście to wyczyszczenie danych aplikacji, co niszczy też token i snapshot masek.

*Poprawka:* handler korupcji (naprawa przyczyny) plus osłonięcie zapisu (każda inna awaria dysku degraduje do nieaktualnego cache'u zamiast crasha). `lastPersistedToken` przesuwa się tylko po sukcesie, więc porażka jest ponawiana, a nie zapisana jako zrobiona. `CancellationException` przepuszczany.

*Weryfikacja:* dwa testy regresyjne, **oba potwierdzone jako padające przeciwko niechronionemu zapisowi**.

---

**B-4 · Undo archiwizacji było jedyną mutacją nadal w `viewModelScope`** — status **[P]**, naprawione (`45da219`)

*Lokalizacja:* `ui/list/MaskedEmailListViewModel.kt:63-75`

*Reprodukcja:* zarchiwizuj maskę → snackbar „Undo" → tapnij Undo → natychmiast wyjdź z aplikacji, gdy `MaskedEmail/set {state: enabled}` jest w locie.

*Oczekiwane vs rzeczywiste:* maska powinna wrócić do stanu sprzed archiwizacji. Faktycznie: wpis nawigacji jest zdejmowany, ViewModel czyszczony, `viewModelScope` anulowany, OkHttp anuluje połączenie — maska zostaje zarchiwizowana, a gałąź `.onFailure`, która istnieje właśnie po to, żeby cicha porażka nie przeszła niezauważona, siedzi w tej samej anulowanej korutynie i nigdy nie biegnie. Jeśli żądanie zdążyło dojść do Fastmaila — maska wraca na koncie, ale lista nigdy się nie przeładowuje.

*Przyczyna źródłowa:* audyt A6 przeniósł mutacje na `@ApplicationScope` w `CreateMaskedEmailViewModel` i `MaskedEmailDetailViewModel`. To miejsce zostało pominięte — akurat to, którego całą obietnicą jest odwracalność.

*Poprawka:* żądanie w `appScope`, czekanie w ViewModelu — dokładnie ten podział, którego używają pozostałe ekrany.

*Weryfikacja:* test prowadzi oba zakresy na **osobnych schedulerach**, więc sprawdza własność zakresu, a nie timing; potwierdzony jako padający przeciwko staremu wywołaniu.

---

**B-5 · Wylogowanie cofane przez pobieranie w locie** — status **[P]**, naprawione (`ef008f4`)

*Lokalizacja:* `data/repository/MaskedEmailRepositoryImpl.kt:44-49`, `data/repository/AuthRepositoryImpl.kt:38-48`

*Reprodukcja:* pull-to-refresh na wolnym łączu → Ustawienia → Wyloguj, zanim odpowiedź dotrze.

*Oczekiwane vs rzeczywiste:* `logout()` kasuje snapshot; korutyna w locie wznawia się i **ponownie szyfruje pełną listę masek na dysk**, gdzie zostaje bezterminowo (czyści ją dopiero następne logowanie).

*Dlaczego to jest P1, a nie porządkowa drobnica:* `docs/privacy.md` § 6 wymienia „the offline snapshot of your masked addresses" wśród rzeczy **usuwanych przy wylogowaniu**. To była złamana obietnica z dokumentu prawnego, nie tylko nieporządek. Plik nie jest czytelny dla innego konta — znacznik właściciela to blokuje — więc nie ma tu wycieku między kontami.

*Przyczyna źródłowa:* token czytany na wejściu, potem sieć, potem bezwarunkowy zapis przelotowy, bez ponownego sprawdzenia, czy sesja jeszcze istnieje. **Anulowanie nie mogło tego zamknąć**: między odpowiedzią a zapisem nie ma punktu zawieszenia, więc nie ma czego wywłaszczyć.

*Ten sam kształt, wyższa stawka — eksport CSV.* `ui/settings/SettingsScreen.kt:137-150` zapisywał CSV z korutyny composable'a, która mogła przeżyć `exportCache.clear()`. To jedyna kopia danych konta na urządzeniu **bez szyfrowania**. Dodatkowo composable konstruował własny `ExportCache(context)`, więc jakakolwiek bramka per-instancja i tak by nie zadziałała — sprzątanie przy wylogowaniu działało na wstrzykniętym singletonie, a zapis na innym obiekcie.

*Poprawka:* licznik generacji w obu cache'ach, podbijany przez `clear()` pod tym samym zamkiem, który bierze zapis; zapisujący łapie generację przed pobraniem, a zapis unieważniony jest porzucany. `generation` **nie ma wartości domyślnej** — domyślna czytałaby wartość bieżącą, czyli dokładnie ten przypadek, który bramka ma łapać, i pozwalałaby nowemu miejscu wywołania wypisać się z bramki przez niewiedzę. Zapis eksportu przeniesiony do `SettingsViewModel`, który trzyma wstrzyknięty singleton.

*Weryfikacja:* pokrycie JVM dla obu bramek i dla tego, że generacja jest czytana przed pobraniem, plus dwa testy instrumentalne na prawdziwym szyfrowanym pliku.

---

**B-6 · Ogłoszenie postępu dla czytnika ekranu było kodem martwym** — status **[P]**, naprawione (`289d8dd`)

*Lokalizacja:* `ui/components/DesignKit.kt:261-266`

*Fakt:* `PillButton` ogłaszał postęp tylko gdy `loading && loadingDescription != null`. **Żadne miejsce wywołania w aplikacji nie ustawiało obu naraz:**

| Miejsce | Przekazuje |
|---|---|
| `auth/LoginScreen.kt:211` | tylko `loadingDescription` |
| `create/CreateMaskedEmailScreen.kt:259` | tylko `loadingDescription` |
| `detail/MaskedEmailDetailScreen.kt:303`, `:388` | tylko `loadingDescription` |
| `pro/ProScreen.kt:247`, `:266` | tylko `loading` |

Cztery ekrany sygnalizowały postęp przez `enabled = !isLoading` plus własny spinner, więc `loading` zostawało `false`. String `state_working`, przetłumaczony na 20 języków, obsługiwał gałąź, która nie mogła się wykonać. Użytkownik czytnika ekranu dostawał wyłącznie przycisk cicho przechodzący w stan `disabled`.

*Przyczyna źródłowa:* poprawka z commita `d1e3edd` („Fix: make the app usable with a screen reader and low vision") nie miała testu, więc nic nie zauważyło, że jest nieosiągalna.

*Przy okazji:* `PillButton` — jedyny przycisk w aplikacji (logowanie, tworzenie, zapis, archiwizacja, ponów, oba przyciski dialogów, zakup, przywracanie, odblokowanie) — **nie miał `Role.Button`**. Poprzedni przebieg ustawił rolę w pięciu liściach i przeoczył komponent, przez który wszystkie przechodzą. To samo dotyczyło `DesignCard`, czyli każdego wiersza maski na liście.

*Poprawka:* `loading` jest jedyną flagą zajętości — komponent posiada spinner, blokuje się i ogłasza, z fallbackiem na ogólny string zamiast milczenia. Miejsca wywołań tracą własne spinnery i zbędne `!isLoading` w `enabled`.

*Weryfikacja:* `PillButtonSemanticsTest` sprawdza rolę, ogłoszenie i **brak fałszywego ogłoszenia w spoczynku** (bez tego trzeciego testu pierwszy przechodziłby dla komponentu ogłaszającego bezwarunkowo). Dwa z trzech potwierdzone jako padające przeciwko starej semantyce.

---

### P2

**S-1 · Przejęcie zadania (StrandHogg) na API 26–30** — status **[P]** z manifestu / **[U]** dla samego exploita, naprawione (`165468f`)

`AndroidManifest.xml:96-118` — `MainActivity` jest eksportowana, `launchMode` standard, bez `taskAffinity` i bez `allowTaskReparenting="false"`; potwierdzone w scalonym manifeście release'u. Dowolna zainstalowana aplikacja mogła zadeklarować aktywność z `android:taskAffinity="com.fastmask"` i `allowTaskReparenting="true"`; przy następnym starcie system przenosi ją na szczyt zadania FastMaska i to ją widzi użytkownik. Ekran wart podszycia to ekran logowania — jedno pole, w które wkleja się token API dający pełny dostęp do skrzynki. Żadna z istniejących obron nie działa: `FLAG_SECURE` chroni przed zrzutami, nie przed nałożonym zadaniem, a `LockScreen` komponuje się *za* aktywnością atakującego. Android 12+ blokuje wstawianie zadań między UID-ami, więc ekspozycja to API 26–30 — w zakresie przy `minSdk = 26`. `QuickMaskActivity` ustawiała pustą afinity; aktywność trzymająca pole tokenu nie. Naprawione przez `android:taskAffinity=""` na `<application>`; **[P]** obecne w scalonym manifeście release'u, suite instrumentalny przechodzi.

**S-2 · Undo quick-maska nieidempotentne i bez bramki** — status **[P]**, naprawione (`165468f`)

`quickmask/QuickMaskRunner.kt:109-123` — `launchUndo` nie miało `AtomicBoolean`, w odróżnieniu od `launchCreate`, które dostało je w poprzednim audycie. `notifier.cancel` biegnie wewnątrz korutyny, więc przycisk Undo zostaje na ekranie jeszcze przez dziesiątki milisekund. Dwa broadcasty → dwa `destroy` na to samo id: pierwszy się udaje, drugi dostaje `notFound`, co `JmapApi` raportuje jako porażkę, a wynikowe „Undo failed" nadpisuje prawdziwe „Undone" w tym samym slocie powiadomienia. **Użytkownik jest informowany, że maska nadal jest na koncie, gdy już jej nie ma** — groźniejsze z dwóch możliwych kłamstw. Bramka jest per-id (dwie różne maski w sekwencji to poprawny scenariusz) i zwalnia się po porażce, żeby nigdy nie stała się powodem odmowy późniejszej próby.

*Znalezione przy pisaniu testów:* `QuickMaskRunner` miał `Dispatchers.IO` zaszyte na sztywno, więc asertywne testy ścigały się z prawdziwą pulą wątków i zachowania, którego ta klasa pilnuje, **nie dało się w ogóle przypiąć testem**. Dispatcher jest teraz wstrzykiwany przez `@IoDispatcher` (Hilt nie honoruje domyślnych argumentów Kotlina na konstruktorze `@Inject`, więc kwalifikator, nie wartość domyślna).

*Przy okazji:* `crashGuard` (`:40-44`) wołał `createInFlight.set(false)` dla **każdego** throwable'a we współdzielonym zakresie, więc wyjątek z korutyny Undo zwalniał bramkę trwającego tworzenia. Poprawione.

**S-3 · „Dowód" uprawnienia Pro nie daje odporności na manipulację** — status **[P]**, **nie naprawione świadomie**

`data/local/ProEntitlementStore.kt:32-44` — digest jest sprawdzany wyłącznie na niepustość (`isNullOrEmpty`), nigdy porównywany z tokenem zakupu, nie ma HMAC-a ani powiązania z Keystore. To zwykły boolean z dodatkowym polem, które spełnia dowolna wartość; KDoc klasy („Not a plain user-editable boolean… tamper") obiecuje więcej, niż robi kod. Na zrootowanym urządzeniu wystarczy wpisać `status="PRO"`, `proof="0"`, a na urządzeniu bez Play `refresh()` zwraca `UNAVAILABLE` i nigdy nie degraduje statusu — Pro zostaje odblokowane na stałe. Wpływ wyłącznie monetyzacyjny: żadne dane użytkownika nie są odsłonięte, wymaga roota, `allowBackup="false"` zamyka drogę przez adb restore. Zgłoszone, bo klasa dokumentuje ochronę, której nie implementuje. Poprawka to HMAC nad `status|proofDigest` kluczem z Keystore.

**D-1 · Dokumentacja procesu budowania rozmija się z rzeczywistością** — status **[P]**, naprawione

Trzy pliki doktryny agentów (`CLAUDE.md`, `AGENTS.md`, README) podawały `./gradlew assembleDebug` bez wzmianki, że (a) potrzebny jest JDK 17–21, bo Gradle 8.9 odmawia startu na nowszym — a systemowy `java` na tej maszynie to OpenJDK 26 — i (b) build wymagał pliku spoza repozytorium. `AGENTS.md` zawierał też stub `TODO: Document how to run the app from Android Studio/emulator once confirmed`. `Plans/release-checklist.md` odwoływał się do „the Android API key **committed in** `app/google-services.json`", co przestało być prawdą 2026-07-31. Zgodnie z zasadą „doktryna to kod" traktuję to jako defekt, nie kosmetykę: instrukcja, której wykonanie kończy się błędem, kosztuje każdego nowego kontrybutora tyle samo co zepsuty skrypt.

**A-1 · Stan maski na liście rozróżnialny wyłącznie kolorem** — status **[P]**, **nie naprawione świadomie**

`ui/list/MaskedEmailListScreen.kt:654` — jedynym wskaźnikiem stanu w wierszu jest `StateDot`. Policzone kontrasty **między wypełnieniami kropek**: jasny archived/pending `#7D3D1E` vs `#6B4C0D` = **1.04:1**; ciemny enabled/pending `#B8D49A` vs `#E6C576` = **1.02:1**; jasny enabled/disabled = 1.39:1. Różnicuje je wyłącznie odcień — luminancja jest praktycznie identyczna, więc w symulacji dichromatycznej zlewają się całkowicie. Każda kropka jest dobrze widoczna na swojej karcie (5,5–10,3:1), po prostu nie jest **rozpoznawalna**. To potwierdzona niezgodność z WCAG 1.4.1 (poziom A). Ścieżka czytnika ekranu jest pokryta (`stateContentDescription`), ekran szczegółów też (`StatePill` ma etykietę tekstową) — luka dotyczy widzącego użytkownika z zaburzeniem widzenia barw. Nie naprawiłem tego tutaj, bo lekarstwo zmienia język wizualny najczęściej przewijanego ekranu, a to decyzja projektowa, nie naprawa defektu. Konkretna propozycja: `UX_RECOMMENDATIONS.md` § A1.

**A-2 · Jedna para statusowa poniżej AA** — status **[P]**, naprawione (`289d8dd`)

`ui/theme/Color.kt:36-37` — `LightOffInk #6B6450` na `LightOffBg #E3DCC9` = **4,31:1**, przy renderowaniu etykiety pigułki „off" w ok. 11sp, czyli bez prawa do ulgi dla dużego tekstu. Zmienione na `#645E4B` = **4,73:1**. Wszystkie pozostałe pary już przechodziły (jasny 5,82–6,25:1, ciemny 5,31–7,74:1).

### P3

**C-1 · `MainActivity` połykał `CancellationException`** — status **[P]**, naprawione

`MainActivity.kt:173` — `catch (e: Exception)` wokół startowego I/O. `CancellationException` dziedziczy po `IllegalStateException`, więc Activity zniszczona lub odtwarzana w trakcie tego odczytu miała anulowanie **wyrzucone do kosza**, a korutyna szła dalej i wołała `setContent` na martwej Activity. Poprawione przez przepuszczenie anulowania. Nie zaobserwowałem z tego crasha — **[U]** co do praktycznego skutku — ale to złamanie strukturalnej współbieżności i dokładnie ten sam wzorzec, który `CrashReportingStartup` w tym samym repo obsługuje poprawnie.

**C-2 · Reguły backupu nie pokrywały kopii roboczej snapshotu** — status **[P]**, naprawione (`165468f`)

`res/xml/backup_rules.xml:16`, `data_extraction_rules.xml:10,15` wykluczały `masked_emails_cache.bin` w korzeniu `filesDir`, ale nie `cache_staging/masked_emails_cache.bin`, gdzie `MaskedEmailCache` stage'uje ten sam pełny zbiór masek przed `renameTo`. Śmierć procesu między zapisem a przemianowaniem zostawia tam kompletną listę. Bez skutku dzisiaj (`allowBackup="false"`, treść szyfrowana kluczem, który i tak nie jest backupowany), ale te pliki istnieją wyłącznie jako siatka bezpieczeństwa na dzień, w którym ta flaga wróci — więc siatka musi pokrywać wszystko, co własny komentarz w tym pliku zresztą deklaruje.

**C-3 · `docs/privacy.md` przeczył sam sobie** — status **[P]**, naprawione

Tabela w sekcji 2 opisywała maski jako trzymane „in memory while the app runs; persisted only on Fastmail's servers", podczas gdy sekcja 6 obiecywała skasowanie lokalnego snapshotu przy wylogowaniu. Snapshot jest realny (`MaskedEmailCache`), szyfrowany i związany z kontem. Sekcja 2 mówi to teraz wprost, z datą i notką o korekcie. **To korekta dokumentu, nie zmiana aplikacji** — żadne nowe dane nie są zbierane i nic nowego nie opuszcza urządzenia. Deklaracja Play Data Safety nie wymaga zmiany: przechowywanie wyłącznie lokalne nie jest „collection".

---

## 5. Problemy zgłoszone i **odrzucone** po weryfikacji

Zgodnie z zasadą „nie przedstawiaj hipotez jako potwierdzonych błędów" — te zostały podniesione w trakcie audytu i po sprawdzeniu **nie są defektami**:

| Zgłoszenie | Dlaczego odrzucone |
|---|---|
| Pusty udany fetch niszczy snapshot offline (proponowany próg „nie zastępuj N zerem") | Pusta lista jest **poprawną** odpowiedzią: użytkownik, który skasował ostatnią maskę, nie może widzieć jej dalej offline. Próg zamieniłby wynik poprawny na nieaktualny. Realnym defektem byłoby potraktowanie **nieudanego** pobrania jako pustego — ta ścieżka już zostawia cache w spokoju, co pokrywa istniejący test |
| `values-id` powinno być `values-in`, bo Indonezyjski się nie załaduje (ostrzeżenie lintu `LocaleFolder`) | **[U]**, ale silnie przemawia za poprawnością: `LocaleListCompat.forLanguageTags("id")` → `Locale.forLanguageTag("id")`, a `toLanguageTag()` kanonizuje legacy `in` z powrotem do `id`, więc `LocaleList.toLanguageTags()` szuka `id`. Ostrzeżenie lintu to porada dla urządzeń sprzed API 21; `minSdk` to 26. Potwierdziłem, że aapt2 zapisuje zasoby aplikacji pod kwalifikatorem `id` (dump APK), więc na pewno **nie ma tu podwójnego zapisu**. Zostawiam jako pozycję do sprawdzenia ręcznego, nie jako defekt |
| Martwe wpisy `\t`, `\r`, `\n` w `FORMULA_LEAD_CHARS` (`ExportMasksUseCase.kt`) | Nieosiągalne przez `trimStart().firstOrNull()`, bo `trimStart` usuwa właśnie te znaki. Nieszkodliwe, nie defekt |

---

## 6. Obszary sprawdzone i **czyste**

Warte odnotowania, żeby następny przebieg nie audytował ich od zera.

- **Obsługa tokenu.** Token trafia do dokładnie sześciu miejsc, wszystkie to `tokenStorage.getToken()` w `MaskedEmailRepositoryImpl`, i opuszcza aplikację wyłącznie jako `@Header("Authorization")`. Nie ma go w żadnym extra intentu, logu, powiadomieniu, trasie nawigacji, `SavedStateHandle` ani `rememberSaveable` (zero wystąpień `rememberSaveable` w całym drzewie).
- **Logowanie sieciowe.** `di/NetworkModule.kt:43-51` — interceptor dodawany tylko pod `BuildConfig.DEBUG`, poziom `HEADERS` (bez ciał), `Authorization`/`Cookie`/`Set-Cookie` redagowane.
- **Prywatność crash-reportingu.** `CrashReporter` wystawia wyłącznie dwa przełączniki — nie ma API user-id / custom-key / log, więc żadne miejsce wywołania nie ma **czym** wyciec. `CrashReportingStartup` poprawnie odmawia zamiany stanu „nieczytelny" w decyzję.
- **Weryfikacja zakupów.** `PurchaseSecurity.verify` zawodzi zamknięcie na każdej ścieżce błędu; release bez klucza licencyjnego odrzuca każdy zakup; bramka na grafie zadań nadal odmawia zbudowania podpisanego release'u bez klucza.
- **IPC.** `QuickMaskActivity` nieeksportowana **i** bramkowana akcją; `QuickMaskUndoReceiver` nieeksportowany; kafelek za `BIND_QUICK_SETTINGS_TILE`; wszystkie cztery `PendingIntent` to `FLAG_IMMUTABLE` z jawnymi celami. `ACTION_SEND` nie tworzy maski — tylko wypełnia formularz, który użytkownik musi zatwierdzić.
- **Zamek aplikacji.** Nie znalazłem obejścia przez rotację (token procesu poprawnie odrzuca bundle z martwego procesu), „ostatnie aplikacje" (`FLAG_SECURE` w release), udostępnianie (`ShareRoute.WaitForUnlock`, a `consumes()` odmawia porzucenia czekającego share'a) ani kafelek.
- **Wiązanie snapshotu z kontem.** `owner = SHA-256(token)` zapisywany, porównywany przy odczycie, `null` traktowany jako „nie mój"; `clear()` przy wylogowaniu **i** przy logowaniu.
- **Częściowe odpowiedzi JMAP.** Wszystkie trzy — `notCreated`, `notUpdated`, `notDestroyed` — są czytane, a każda ścieżka **pozytywnie potwierdza** skutek zamiast zakładać sukces. Nic nie jest po cichu gubione.
- **Cache sesji JMAP.** Poprawny double-checked locking pod `Mutex` z polami `@Volatile`, `apiUrl` walidowany przed przypisaniem.
- **Escapowanie CSV.** RFC 4180 plus neutralizacja formuł badana na **pierwszym nie-białym** znaku, więc `" =HYPERLINK(...)"` też jest łapane.
- **Cele dotykowe.** Nie znalazłem ani jednego klikalnego elementu poniżej 48dp.
- **Skalowanie tekstu.** Zero stałych wysokości `.dp` na kontenerach tekstu.
- **Akcje wyłącznie gestowe.** Nie ma żadnych — zero `combinedClickable`, `onLongClick`, swipe-to-dismiss w całym drzewie.
- **RTL.** Zero `Absolute`/`Left`/`Right`/`paddingLeft`, ikony `AutoMirrored`, `ChevronRight` ręcznie lustrzany, `list_stats` z przestawionymi argumentami w `values-ar`.
- **i18n.** 233 stringi + 8 plurali we wszystkich 20 lokalizacjach, zero dryfu; kategorie liczby mnogiej CLDR poprawne (ar `zero/one/two/few/many/other`, pl/ru/uk `one/few/many/other`).
- **Sekrety.** Żaden keystore, klucz licencyjny, `local.properties` ani `google-services.json` nie jest śledzony przez gita, ani nigdy nie był w historii.

---

## 7. Stan po zmianach

| Bramka | Wynik | Sposób weryfikacji |
|---|---|---|
| `./gradlew testDebugUnitTest` | **459 testów, 0 porażek** (było 447) | automatycznie |
| `./gradlew lintDebug` | **0 błędów, 99 ostrzeżeń** — bez zmian względem bazy | automatycznie |
| `./gradlew assembleRelease` | **BUILD SUCCESSFUL** (R8) | automatycznie |
| `./gradlew connectedDebugAndroidTest` | **24 testy, 0 porażek** (było 19 z 1 porażką) | automatycznie, emulator Pixel 9a API 36 |
| Build z czystego klona | **BUILD SUCCESSFUL** | automatycznie, osobny klon bez `google-services.json` |
| Start aplikacji bez Firebase | Activity wznowiona, proces żywy, zero `FATAL` | automatycznie, logcat + `dumpsys activity` |
| `taskAffinity` w scalonym manifeście release'u | obecne | automatycznie, odczyt scalonego manifestu |

**Zweryfikowane automatycznie:** wszystkie powyższe, plus każdy test regresyjny uruchomiony przeciwko staremu kodowi w celu potwierdzenia, że tam pada.

**Wymaga weryfikacji manualnej:** § 8.

**Nieweryfikowalne w tym środowisku:** rzeczywisty przepływ zakupu Play (wymaga konta testera i podpisanego builda), realny exploit StrandHogg (wymaga urządzenia API ≤30 i złośliwej aplikacji), wysyłka raportu Crashlytics (wymaga podpisanego release'u i prawdziwego crasha), rozdzielczość lokalizacji indonezyjskiej na urządzeniu.

---

## 8. Checklista QA manualnego przed publikacją

Uporządkowana według ryzyka wprowadzonego przez ten audyt.

1. **Zamek i zadania.** Włącz zamek biometryczny → wyjdź → wróć: prompt się pojawia. Sprawdź „ostatnie aplikacje" — aplikacja pojawia się raz, z poprawną nazwą i ikoną (zmiana `taskAffinity`). Obróć ekran przy podniesionym zamku.
2. **Udostępnianie do FastMaska.** Udostępnij link z przeglądarki → formularz tworzenia wypełnia się. Powtórz przy włączonym zamku: najpierw prompt, formularz dopiero po odblokowaniu. Powtórz po obrocie: share nie odtwarza się drugi raz.
3. **Undo archiwizacji.** Zarchiwizuj maskę, tapnij Undo, **natychmiast wyjdź z aplikacji**. Wróć: maska ma stan sprzed archiwizacji.
4. **Eksport CSV a wylogowanie.** Z Pro: tapnij Eksport i szybko się wyloguj. Oczekiwane: komunikat o niepowodzeniu eksportu, **brak** pliku w cache. Osobno: normalny eksport nadal otwiera arkusz udostępniania z poprawną treścią.
5. **Przyciski w trakcie pracy.** Zaloguj, Utwórz, Zapisz i Włącz/Wyłącz — sprawdź, że w trakcie żądania przycisk jest zablokowany i pokazuje spinner (teraz po lewej stronie etykiety) i że **nie da się go kliknąć dwa razy**.
6. **TalkBack.** Włącz i przejdź: powitanie → logowanie → lista → szczegóły → ustawienia. Każdy przycisk ogłasza się jako „przycisk", a akcja w toku jest ogłaszana. **Sprawdź szczególnie pola edycyjne** (prefiks, domena, URL, opis, wyszukiwarka) — czy TalkBack czyta ich zawartość, czy tylko etykietę; podejrzenie z § UX D7 wymaga urządzenia.
7. **Quick mask.** Kafelek → maska utworzona, powiadomienie z Undo. **Tapnij Undo dwa razy szybko**: dokładnie jeden komunikat, mówiący prawdę.
8. **Pro.** Zakup w torze testowym, przywracanie na drugim urządzeniu, zachowanie w trybie samolotowym (Pro nie znika).
9. **Indonezyjski.** Ustawienia → język → Indonesia. Interfejs przechodzi na indonezyjski (weryfikacja odrzuconego zgłoszenia z § 5).
10. **Release.** Zbuduj AAB **z obecnym `app/google-services.json`** i potwierdź, że log **nie zawiera** „building without Firebase Crashlytics".

---

## 9. Ograniczenia tego audytu

- Wszystkie testy na emulatorze Pixel 9a API 36. Nie testowałem na fizycznym urządzeniu ani na API 26–30, gdzie leży ekspozycja S-1.
- Nie testowałem przeciwko prawdziwemu kontu Fastmail — cała weryfikacja przez tryb demo, testy jednostkowe i analizę kodu. Zachowanie prawdziwego API JMAP przy błędach częściowych jest sprawdzone z kodu, nie z ruchu sieciowego.
- Nie weryfikowałem przepływu Play Billing końcowo-do-końca.
- Nie uruchamiałem TalkBacka. Ustalenia dostępności pochodzą z kodu i z policzonych kontrastów; punkt 6 checklisty jest tego konsekwencją, a podejrzenie o `contentDescription` na polach edycyjnych pozostaje **[U]**.
- Nie audytowałem zależności pod kątem znanych CVE — brak lockfile'a i skanera w tym repo; 27 ostrzeżeń `GradleDependency` mówi tylko o nieaktualności, nie o podatności.
