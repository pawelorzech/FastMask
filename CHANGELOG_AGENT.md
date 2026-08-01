# CHANGELOG_AGENT — audyt 2026-08-01

**Gałąź:** `feature/audit-2026-08-01` (z `main`, `b3d6dca`, v1.10.1 / versionCode 22)
**Charakter zmian:** wyłącznie poprawki defektów, testy regresyjne i dokumentacja. Żadnej nowej funkcji, żadnej migracji frameworka, żadnego masowego bumpu zależności, żadnej zmiany modelu biznesowego.

> Raporty z poprzedniego przebiegu (2026-07-27) leżą w `Plans/audit-archive/`.

Każda poprawka behawioralna ma test regresyjny, i **każdy taki test został uruchomiony przeciwko staremu kodowi, żeby potwierdzić, że tam pada**. Test, który przechodzi w obie strony, nie pilnuje niczego — a ten przebieg istnieje między innymi dlatego, że poprzedni dowiózł dokładnie taki przypadek (patrz B-6 w `AUDIT_REPORT.md`).

---

## 1. Commity

| Commit | Temat |
|---|---|
| `3f306ac` | Fix: let a clean clone build without the Firebase config |
| `b0bfe7e` | Fix: a corrupt entitlement store must not crash-loop a paying user |
| `45da219` | Fix: let an Undo outlive the screen that started it |
| `ef008f4` | Fix: stop a sign-out being undone by an in-flight fetch |
| `289d8dd` | Fix: make the screen-reader progress announcement actually fire |
| `165468f` | Fix: harden the task stack, the undo path and the backup net |

Plus commit domykający: poprawka `CancellationException` w `MainActivity`, nowy workflow CI i trzy raporty.

---

## 2. Zmienione pliki

### Build i konfiguracja repozytorium

| Plik | Co się zmieniło |
|---|---|
| `app/build.gradle.kts` | Wtyczki `google-services` i `firebase.crashlytics` aplikowane **tylko** gdy istnieje `app/google-services.json`. Blok `configure<CrashlyticsExtension>` w buildType `debug` osłonięty tą samą flagą — `configure<T>` rzuca, gdy wtyczki nie zaaplikowano. Build bez wtyczek loguje komunikat, żeby nie dało się go pomylić z pełnym |
| `.github/workflows/build.yml` | **Nowy.** Testy jednostkowe, lint i nieopisany build release na każdy push do `main` i każdy PR |
| `README.md` | Wymóg JDK 17–21 i opcjonalność `google-services.json` |
| `AGENTS.md` | To samo, plus usunięty stub `TODO: Document how to run the app` |
| `Plans/release-checklist.md` | Nowy punkt: potwierdź, że `google-services.json` był na miejscu przy buildzie release — brak pliku nie jest już błędem builda |
| `docs/privacy.md` | Korekta sekcji 2 (patrz § 4) |

### Dane i pamięć lokalna

| Plik | Co się zmieniło |
|---|---|
| `data/local/MaskedEmailCache.kt` | Licznik generacji podbijany przez `clear()` pod tym samym zamkiem; `write()` przyjmuje generację złapaną przed pobraniem i porzuca zapis unieważniony. Parametr `generation` **celowo bez wartości domyślnej** |
| `data/local/ExportCache.kt` | Ten sam mechanizm; `write()` rzuca, gdy sesja się skończyła |
| `data/local/ProEntitlementStore.kt` | Dodany `ReplaceFileCorruptionHandler`, tak jak w `SettingsDataStore` |
| `data/repository/MaskedEmailRepositoryImpl.kt` | Łapie generację cache'u **przed** wywołaniem sieciowym i przekazuje do zapisu |
| `data/repository/ProRepositoryImpl.kt` | Zapis uprawnienia osłonięty; `lastPersistedToken` przesuwa się tylko po sukcesie, więc porażka jest ponawiana; `CancellationException` przepuszczany dalej |

### UI

| Plik | Co się zmieniło |
|---|---|
| `ui/components/DesignKit.kt` | `PillButton` dostaje `Role.Button` i ogłasza postęp zawsze, gdy ustawiono `loading`, z fallbackiem na ogólny string. `DesignCard` — `Role.Button` na klikalności |
| `ui/auth/LoginScreen.kt`, `ui/create/CreateMaskedEmailScreen.kt`, `ui/detail/MaskedEmailDetailScreen.kt` | Przekazują `loading` zamiast własnego spinnera; z `enabled` znika zbędne `!isLoading` |
| `ui/pro/ProScreen.kt` | Oba przyciski dostają `loadingDescription` |
| `ui/list/MaskedEmailListViewModel.kt` | `restoreMask` wysyła żądanie w `@ApplicationScope` |
| `ui/settings/SettingsViewModel.kt`, `SettingsScreen.kt` | Zapis CSV przeniesiony z composable'a do ViewModelu, który trzyma wstrzyknięty singleton `ExportCache`. `SettingsEvent.ShareCsv` niesie teraz `File` |
| `ui/theme/Color.kt` | `LightOffInk` przyciemniony `#6B6450` → `#645E4B` |

### Platforma i IPC

| Plik | Co się zmieniło |
|---|---|
| `AndroidManifest.xml` | `android:taskAffinity=""` na `<application>` |
| `res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml` | Wykluczenie `cache_staging/` |
| `quickmask/QuickMaskRunner.kt` | Bramka Undo per-id; `crashGuard` nie zwalnia już flagi tworzenia; dispatcher wstrzykiwany przez nowy `@IoDispatcher` |
| `di/BillingModule.kt` | Deklaracja i dostarczenie `@IoDispatcher` |
| `MainActivity.kt` | Przepuszcza `CancellationException` z odczytu startowego |

---

## 3. Testy

**Jednostkowe 447 → 459 (+12). Instrumentalne 19 → 24 (+5).**

| Test | Czego pilnuje |
|---|---|
| `ProRepositoryImplTest` · a failing entitlement write does not propagate out of refresh | pętli crashy u płacącego użytkownika |
| `ProRepositoryImplTest` · a failed entitlement write is retried on the next reconciliation | tego, że porażka jest ponawiana, a nie tylko przeżyta |
| `MaskedEmailListViewModelTest` · restoreMask issues the request on the application scope | Undo przeżywającego zniszczenie ekranu |
| `MaskedEmailRepositoryImplTest` · the cache write is stamped with the generation captured before the fetch | tego, że generacja jest czytana **przed** pobraniem — na tym polega cała bramka |
| `ExportCacheTest` · an export whose fetch outlived a sign-out is never written | CSV w plaintekście po wylogowaniu |
| `ExportCacheTest` · an export that started after the sign-out is written normally | tego, że bramka nie strzela za często |
| `SettingsViewModelTest` · an export whose fetch outlived a sign-out is abandoned | to samo, przez cały ViewModel |
| `MaskedEmailCacheTest` (instr.) · aWriteFromASessionThatHasSinceEndedIsDropped | snapshotu po wylogowaniu, na prawdziwym szyfrowanym pliku |
| `MaskedEmailCacheTest` (instr.) · aWriteFromTheCurrentSessionStillLands | tego, że bramka nie strzela za często |
| `PillButtonSemanticsTest` (instr., nowy plik) · 3 testy | roli, ogłoszenia postępu i **braku** fałszywego ogłoszenia w spoczynku |
| `QuickMaskUndoIdempotenceTest` (nowy plik) · 5 testów | podwójnego tapnięcia Undo, zakresu per-id, zwolnienia po porażce, zamknięcia powiadomienia |

`FakeMaskedEmailRepository` dostał hook `beforeGet`, żeby test mógł wpleść coś w trwające pobieranie.

---

## 4. Zmiany zachowania

To są zmiany, które użytkownik albo Ty możecie zauważyć. Reszta jest wewnętrzna.

1. **Pozycja spinnera na czterech przyciskach.** Zaloguj, Utwórz i dwa przyciski ekranu szczegółów pokazują spinner **po lewej** od etykiety, nie po prawej — bo `PillButton` przejął go na siebie. Tak już wyglądały przyciski paywalla.
2. **Pigułka statusu „off" jest odrobinę ciemniejsza** w motywie jasnym.
3. **Eksport CSV może teraz zakończyć się błędem** tam, gdzie wcześniej się udawał — konkretnie gdy użytkownik wyloguje się w trakcie pobierania. O to właśnie chodzi w tej poprawce.
4. **Build bez `app/google-services.json` przechodzi** zamiast padać i daje aplikację z martwym raportowaniem crashy. Release zbudowany przez pomyłkę bez tego pliku pojedzie bez instrumentacji, zamiast się nie zbudować — dlatego doszedł punkt w checkliście, a build wypisuje o tym komunikat.
5. **`docs/privacy.md` sekcja 2** mówi teraz, że zaszyfrowany snapshot listy masek leży na urządzeniu. To korekta dokumentu, nie zmiana aplikacji: snapshot istnieje od czasu listy offline, a sekcja 6 już wcześniej obiecywała jego usunięcie przy wylogowaniu. Nie zbierasz nowych danych i nic nowego nie opuszcza urządzenia. **Play Console → Data safety nie wymaga zmiany** — przechowywanie wyłącznie lokalne nie jest „collection", a deklarowany zbiór (crash logs, device IDs) się nie zmienił.

---

## 5. Potencjalne regresje — na co patrzeć

- **Task affinity.** `android:taskAffinity=""` zmienia sposób identyfikacji zadania aplikacji. Suite instrumentalny przechodzi (start, nawigacja, przepływy demo), ale zachowanie w „ostatnich aplikacjach" i wejście przez udostępnianie warto przejść ręcznie — checklist w `AUDIT_REPORT.md` § 8.
- **Zmiana `PillButton` dotyka każdego przycisku w aplikacji.** Tryb awarii to przycisk, który przestaje się blokować w trakcie pracy. W czterech miejscach z `enabled` zniknęło `!isLoading`, bo komponent robi to sam; gdyby ktoś kiedyś to cofnął, przyciski staną się podwójnie klikalne.
- **`ExportCache.write` teraz rzuca** zamiast zawsze zwracać plik. Jedyny wołający to obsługuje, ale każdy nowy musi.
- **Raportowanie crashy zależy od pliku spoza repozytorium.** Build nie wymusza już jego obecności; checklist release'u jest teraz jedyną bramką.

---

## 6. Czego świadomie nie zmieniłem

- **Pusty udany fetch nadal nadpisuje snapshot.** Rozważane jako kandydat na próg „nie zastępuj N masek zerem". Odrzucone: pusta lista jest poprawną odpowiedzią — użytkownik, który skasował ostatnią maskę, nie może dalej widzieć jej offline — a próg zamieniłby wynik poprawny na nieaktualny. Realnym defektem byłoby potraktowanie **nieudanego** pobrania jako pustego, a ta ścieżka już zostawia cache w spokoju (pokryte istniejącym testem).
- **Cyfrowy „dowód" w `ProEntitlementStore` nie jest odporny na manipulację.** Jego KDoc obiecuje więcej, niż robi kod: digest jest sprawdzany tylko na niepustość, więc na zrootowanym urządzeniu da się przypiąć PRO offline. Porządna poprawka to HMAC na kluczu z Keystore. Opisane w `AUDIT_REPORT.md` (S-3), nie naprawione — wymaga roota, nie odsłania danych użytkownika, a Play pozostaje autorytatywny, kiedy tylko jest osiągalny.
- **Stan maski na liście nadal rozróżnialny wyłącznie kolorem.** Potwierdzona niezgodność z WCAG 1.4.1 z policzonymi współczynnikami (1.02:1 między dwiema kropkami stanu). Nie naprawione tutaj, bo lekarstwo zmienia język wizualny najczęściej przewijanego ekranu — to decyzja projektowa, nie naprawa defektu. Pierwsza pozycja w `UX_RECOMMENDATIONS.md`, z konkretną propozycją.
