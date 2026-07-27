# CHANGELOG_AGENT — audyt 2026-07-27

**Gałąź:** `feature/audit-2026-07-27` (z `main`, `be6b291`, v1.10.0 / versionCode 21)
**Charakter zmian:** wyłącznie poprawki defektów i dokumentacji. Żadnej nowej funkcji, żadnej zmiany modelu biznesowego, żadnej migracji frameworka, żadnego masowego bumpu zależności.

> Raporty z poprzedniego przebiegu (2026-07-24) leżą w `Plans/audit-archive/`.

---

## 1. Zmienione pliki

### Kod produkcyjny

| Plik | Co się zmieniło |
|---|---|
| `domain/repository/MaskedEmailRepository.kt` | `deleteMaskedEmail` rozdzielone na `archiveMaskedEmail` (odwracalne) i `destroyMaskedEmail` (nieodwracalne) |
| `data/repository/MaskedEmailRepositoryImpl.kt` | `archiveMaskedEmail` wysyła `MaskedEmail/set update {state: deleted}` zamiast `destroy`; wszystkie metody na `Dispatchers.IO`; cache zapisywany i czytany ze znacznikiem właściciela |
| `data/repository/DemoMaskedEmailRepositoryImpl.kt` | `archiveMaskedEmail` (przerzut stanu) + nowe `destroyMaskedEmail` (usunięcie wpisu) |
| `data/repository/MaskedEmailRepositoryDispatcher.kt` | `current()` jest `suspend` i czyta `appMode.first()` zamiast `runBlocking` |
| `data/repository/AuthRepositoryImpl.kt` | `login()` czyści cache masek i eksporty przed zapisem tokenu |
| `domain/usecase/DeleteMaskedEmailUseCase.kt` → `ArchiveMaskedEmailUseCase.kt` | Rozdzielone na `ArchiveMaskedEmailUseCase` i `DestroyMaskedEmailUseCase` |
| `domain/usecase/QuickMaskCreator.kt` | Bramka zamka bez `isPro()`; Undo woła `DestroyMaskedEmailUseCase` |
| `domain/repository/QuickMaskGuard.kt` | Usunięte `isPro()` |
| `data/repository/QuickMaskGuardImpl.kt` | Usunięta zależność od `ProEntitlementStore` |
| `MainActivity.kt` | `lockAtLaunch` bez `&& cachedPro` |
| `ui/settings/SettingsScreen.kt` | Przełącznik zamka pokazuje stan preferencji, nie uprawnienia |
| `ui/list/MaskedEmailListScreen.kt` | `pendingUndo = null` po zamknięciu snackbara |
| `ui/detail/MaskedEmailDetailViewModel.kt` | Mutacje w `@ApplicationScope`; użycie `ArchiveMaskedEmailUseCase` |
| `ui/create/CreateMaskedEmailViewModel.kt` | Tworzenie w `@ApplicationScope` |
| `data/local/MaskedEmailCache.kt` | Znacznik właściciela w snapshocie; zapis przez plik tymczasowy + `renameTo`; `synchronized` na zapisie i czyszczeniu |
| `data/local/SettingsDataStore.kt` | `runCatching` obejmuje odczyt w `appModeBlocking()`; `ReplaceFileCorruptionHandler` na DataStore `settings` |
| `data/local/ExportCache.kt` | Wydzielone `pruneExpired()`, wołane niezależnie od zapisu |
| `data/billing/PlayBillingDataSource.kt` | Brak klucza licencyjnego = weryfikacja fail-closed poza debugiem |
| `FastMaskApplication.kt` | `pruneExpiredExports()` przy starcie |
| `quickmask/QuickMaskRunner.kt` | `AtomicBoolean` blokujący równoległe tworzenie |

### Zasoby i konfiguracja

| Plik | Co się zmieniło |
|---|---|
| `res/xml/backup_rules.xml` | Dodane wykluczenia snapshotu masek i katalogu DataStore |
| `res/xml/data_extraction_rules.xml` | To samo, dla backupu chmurowego i transferu urządzenia |
| `gradle.properties` | Usunięty `org.gradle.java.home` z absolutną ścieżką do lokalnego JDK |
| `docs/privacy.md` | Sekcja 6 (retencja) opisuje faktyczne zachowanie wylogowania |

---

## 2. Zmiany zachowania widoczne dla użytkownika

Trzy zmiany są odczuwalne. Reszta jest niewidoczna, dopóki coś nie pójdzie źle.

**1. „Archiwizuj maskę" faktycznie archiwizuje.** Przed zmianą przycisk wysyłał JMAP `destroy`. Po zmianie wysyła `state: deleted`. **Konsekwencja do sprawdzenia na koncie:** zarchiwizowane maski powinny teraz pojawiać się pod filtrem „Archived" i w webowym Fastmailu pod „Review deleted masked addresses" — czyli tam, gdzie UI od początku obiecywał, że będą. Wcześniej najprawdopodobniej znikały bezpowrotnie. Undo ze snackbara powinno je przywracać.

**2. Zamek aplikacji nie rozbraja się po utracie Pro.** Jeśli miałeś włączony zamek i uprawnienie Pro wygasło, aplikacja startowała odblokowana, ale blokowała się po powrocie z tła, a Ustawienia pokazywały przełącznik jako wyłączony. Teraz wszystkie cztery miejsca czytają tę samą preferencję. Wyłączenie zamka nadal nie wymaga Pro (ochrona przed zablokowaniem się na zewnątrz).

**3. Kafelek Quick Settings nie tworzy wielu masek naraz.** Trzykrotne szybkie tapnięcie tworzyło trzy prawdziwe maski, z których cofnąć dało się tylko ostatnią. Teraz kolejne tapnięcia w trakcie tworzenia są ignorowane.

**Zmiana jednorazowa, cicha:** przy pierwszym uruchomieniu po aktualizacji offline'owy snapshot masek zostanie odrzucony (nie ma znacznika właściciela) i odtworzony przy pierwszym udanym pobraniu. Bez sieci przy pierwszym starcie lista będzie pusta zamiast pokazać stare dane — jednorazowo.

---

## 3. Dodane testy

**Jednostkowe (442 → 446, wszystkie zielone):**

| Test | Co pilnuje |
|---|---|
| `MaskedEmailRepositoryImplTest.archive flips state to deleted and never destroys` | Archiwizacja nie może sięgnąć po `destroy` |
| `MaskedEmailRepositoryImplTest.destroy is the only path that reaches jmap destroy` | I odwrotnie — Undo kafelka nie może zamienić się w archiwizację |
| `MaskedEmailRepositoryImplTest.the cache is read back under the same owner it was written with` | Zapis i odczyt cache'u używają tego samego znacznika |
| `MaskedEmailRepositoryImplTest.a different token asks the cache for a different owner` | Dwa konta nie dzielą znacznika |
| `MaskedEmailRepositoryImplTest.a successful fetch is written through to the cache` | *(rozszerzony)* zapis musi nieść niepusty znacznik |
| `ExportCacheTest.pruneExpired ages out old exports without writing a new one` | Retencja działa bez kolejnego eksportu |

**Instrumentowane (16 → 19):**

| Test | Co pilnuje |
|---|---|
| `MaskedEmailCacheTest.aSnapshotWrittenByOneAccountIsInvisibleToAnother` | Snapshot konta A nie odpowiada kontu B |
| `MaskedEmailCacheTest.aSnapshotFromBeforeOwnersExistedIsTreatedAsAbsent` | Plik po aktualizacji degraduje do „brak cache", nie do cudzych danych |
| `MaskedEmailCacheTest.aStrandedTempFileDoesNotDestroyTheLiveSnapshot` | Przerwany zapis nie kasuje dobrego snapshotu |

**Usunięty test:** `QuickMaskCreatorTest.the lock preference without Pro does not block quick creation` — kodyfikował naprawiany defekt, powołując się na uzasadnienie („taka sama koniunkcja, jakiej używa MainActivity"), które było nieprawdziwe. W jego miejscu jest komentarz wyjaśniający, dlaczego zniknął. Regresję blokuje teraz kompilator: `QuickMaskGuard` nie wystawia już uprawnienia, więc koniunkcji nie da się napisać bez ponownego dodania metody i uzasadnienia jej.

---

## 4. Potencjalne regresje

Uczciwie: rozdzielenie archiwizacji od usuwania to najpoważniejsza zmiana w tym zestawie i **nie została zweryfikowana na prawdziwym koncie Fastmail** — nie mam tokenu, a testy potwierdzają tylko, że wysyłamy `update {state: deleted}`, nie to, co Fastmail z tym robi.

| Ryzyko | Dlaczego mimo to uważam zmianę za bezpieczną |
|---|---|
| Zarchiwizowane maski zaczną pojawiać się pod „Archived" i podbijać licznik chipa | To jest zamierzone zachowanie: enum `EmailState.DELETED`, filtr „Archived" i etykieta `state_deleted` istnieją w kodzie od dawna i nie miały czym się zapełnić z poziomu aplikacji |
| Fastmail mógłby odrzucić `state: deleted` | Wtedy archiwizacja zwróci błąd „Nie udało się zarchiwizować" zamiast po cichu skasować maskę. Głośna porażka jest lepsza od cichej utraty |
| Mutacje w `@ApplicationScope` mogą trafić do martwego ViewModelu | Nie mogą: `async { }.await()` — żądanie żyje w scope aplikacji, oczekiwanie w `viewModelScope`, więc zniszczenie ekranu przerywa tylko oczekiwanie |
| `withContext(IO)` odsłania wyścig zapisu cache'u | Dlatego atomowy zapis i `synchronized` weszły w tym samym zestawie zmian, nie później |
| Odrzucenie snapshotu bez znacznika przy aktualizacji | Jednorazowa degradacja do stanu sprzed funkcji offline; nic nie jest tracone poza jednym pobraniem |
| `./gradlew` z gołego terminala przestanie działać | Świadoma decyzja Pawła (2026-07-27): repo zostaje przenośne, budujesz przez alias z `JAVA_HOME` wskazującym JBR z Android Studio |

---

## 5. Do ręcznego QA (nie da się tego sprawdzić bez konta i telefonu)

Kolejność od najważniejszego.

1. **Archiwizacja na prawdziwym koncie.** Załóż maskę testową → zarchiwizuj z ekranu szczegółów → wróć na listę → tapnij „Cofnij" w snackbarze. Maska powinna wrócić do stanu sprzed archiwizacji. Potem zarchiwizuj drugą i sprawdź w webowym Fastmailu, czy leży pod „Review deleted masked addresses".
2. **Filtr „Archived".** Po powyższym chip „Archived" powinien mieć niezerowy licznik.
3. **Drugie Undo z rzędu.** Zarchiwizuj maskę A, poczekaj aż snackbar zniknie, zarchiwizuj maskę B. Snackbar z Undo musi pojawić się za drugim razem (wcześniej nie pojawiał się nigdy po pierwszym).
4. **Undo po obrocie.** Zarchiwizuj, poczekaj aż snackbar zniknie, obróć telefon. Nie może wyskoczyć „zombie" snackbar dla dawno zarchiwizowanej maski.
5. **Kafelek Quick Settings.** Tapnij trzy razy szybko. Ma powstać **jedna** maska, jedno powiadomienie, Undo ma ją cofnąć.
6. **Zamek aplikacji.** Włącz (wymaga Pro) → wyjdź i wróć → prompt biometryczny. Zabij proces i uruchom ponownie → prompt musi pojawić się też przy zimnym starcie.
7. **Wylogowanie i zalogowanie na to samo konto.** Lista musi się odbudować; brak sieci zaraz po zalogowaniu = pusta lista, nie cudze dane.
8. **Tworzenie maski + natychmiastowe cofnięcie.** Wypełnij formularz, „Utwórz", od razu wstecz. Maska powinna powstać, a nie zawisnąć jako sierota bez adresu.
9. **Eksport CSV (Pro).** Wyeksportuj, poczekaj godzinę, zrestartuj aplikację, sprawdź że plik zniknął z `cacheDir/exports`.
10. **Płynność listy.** Przy ~265 maskach pull-to-refresh powinien być zauważalnie gładszy — szyfrowany zapis snapshotu zszedł z wątku głównego.
