# Improvements report

## Najważniejsze zmiany UX/UI

- **Wejście do demo nie dawało feedbacku i mogło wyglądać jak martwy przycisk.** Oba entry pointy pokazują teraz postęp dokładnie na wybranym CTA, blokują ponowne wysłanie i po błędzie pokazują lokalizowany komunikat z możliwością ponowienia. Logowanie pozostaje dostępne po nieudanym wejściu do demo, a wpisany token nie jest tracony.
- **Login pokazywał ten sam loader na niewłaściwej akcji.** Stan operacji rozróżnia teraz logowanie i demo, więc spinner pojawia się tylko na przycisku, który użytkownik nacisnął.
- **Systemowe wklejenie mogło wprowadzić bardzo duży tekst do maskowanego pola tokenu.** Każda ścieżka edycji tokenu korzysta teraz z tego samego czyszczenia i limitu, nie tylko dedykowany przycisk Paste.
- **Formularze nie korzystały konsekwentnie z klawiatury platformy.** Prefix używa klawiatury ASCII, domena i URL klawiatury URI, a akcje Next/Done prowadzą kolejno przez pola także na ekranie edycji.
- **Czyszczenie wyszukiwarki miało target 40 dp.** Target został zwiększony do platformowego minimum 48 dp i zabezpieczony testem UI.
- Wykonano drugi pass wizualny na emulatorze przy skali fontu 150%. Lista i formularz tworzenia zachowują hierarchię, czytelność i dostęp do akcji bez clippingu.

## Performance

- **Demo cold start inicjalizował Tink i Android Keystore mimo braku tokenu.** Sprawdzenie trybu demo odbywa się teraz przed wejściem do szyfrowanego magazynu tokenu, dzięki czemu koszt nie leży na krytycznej ścieżce startu demo.
- **Każdy znak wyszukiwania ponownie sortował całą listę.** Lista jest sortowana raz po pobraniu lub odczycie cache; filtrowanie zachowuje tę kolejność. Zapytanie jest ograniczone do 256 znaków, więc duże wklejenie nie powoduje nieograniczonej pracy na głównym wątku.
- Zastąpiono przestarzałą konfigurację `kotlinOptions.jvmTarget` aktualnym `compilerOptions`, pozostawiając jeden jawny toolchain JVM 17.

## Bugs

- Wszystkie operacje JMAP zachowują teraz `CancellationException`. Zamknięcie ekranu lub timeout nie jest już zamieniany w zwykły `Result.failure`, po którym anulowana operacja mogła jeszcze zmienić stary stan UI.
- Quick Mask również nie połyka anulowania. Anulowany create/undo nie publikuje spóźnionego komunikatu o błędzie, a blokady double-tap są zwalniane, więc akcję można bezpiecznie ponowić.
- Nieudana aktywacja demo nie nawiguje do częściowo ustawionej sesji, nie zostawia spinnera i nie kończy się cichym brakiem reakcji.
- Rapid tap na wejściu do demo jest odrzucany synchronicznie, zanim druga korutyna lub drugi event nawigacji zdążą wystartować.

## Architecture/refactoring

- Stan logowania opisuje konkretną operację (`LOGIN` / `ENTER_DEMO`) zamiast jednego niejednoznacznego booleana.
- Welcome ma mały, jawny `UiState` dla progress/error zamiast cichego `CoroutineExceptionHandler`.
- Wspólny wrapper suspend w warstwie JMAP centralizuje zasadę: błędy domenowe wracają jako `Result`, anulowanie pozostaje anulowaniem.
- Istniejący podział Compose + ViewModel + use case + repository pozostał bez zmian; refaktoryzacje były ograniczone do miejsc z mierzalnym lub produktowym skutkiem.

## Testy

- `testDebugUnitTest`: **498/498**, 0 failures, 0 errors, 0 skipped.
- `connectedDebugAndroidTest` na emulatorze API 35: **44/44**, 0 failures, 0 errors, 0 skipped.
- `lintDebug`: **0 issues**.
- `assembleDebug`: sukces.
- `assembleRelease` z R8/lint vital: sukces; APK **4,1 MB**.
- `git diff --check`: sukces.
- Manualnie: fresh start, welcome → demo → tutorial → lista → tworzenie; ponowne uruchomienia demo; lista i formularz przy font scale 150%.

Dodane regresje obejmują: anulowanie JMAP i Quick Mask, retry po anulowaniu, nieudane i wielokrotne wejście do demo, właściwy loader logowania/demo, limit wklejania tokenu i wyszukiwania, zachowanie sortowania podczas filtrowania, ominięcie Keystore w demo, target 48 dp oraz Next/Done w edycji szczegółów.

## Before/After

Pomiar wykonano tym samym poleceniem `am force-stop` + `am start -W`, na tym samym emulatorze API 35 i debug buildzie, w istniejącej sesji demo. Pierwszy start bezpośrednio po instalacji nie jest wliczony.

| Metryka | Przed | Po | Zmiana |
| --- | ---: | ---: | ---: |
| Cold start, mediana | 2229 ms (4 poprawne próby) | 1688 ms (5/5 COLD) | **-541 ms / -24,3%** |
| Zakres cold start | 1735–2890 ms | 1494–1744 ms | mniejszy rozrzut |
| Sortowania przy zmianie query | 1 na każdą zmianę | 0 | usunięte z hot path |
| Maks. długość query | bez limitu | 256 znaków | ograniczona praca wejścia |
| APK release | 4,1 MB | 4,1 MB | bez wzrostu |
| Testy JVM | 487 | 498 | +11 regresji |
| Testy UI | 43 | 44 | +1 flow regresyjny |

Frame time/jank, pamięć i request latency nie mają wiarygodnego pomiaru before/after, więc nie są estymowane.

## Remaining limitations

- Realne logowanie, błędy 401/403/429/5xx i operacje na koncie Fastmail nie zostały wykonane bez produkcyjnego/testowego tokenu; warstwa błędów i anulowania jest pokryta testami jednostkowymi.
- Zakupy wymagają konta Play/test tracku, a biometria i Quick Settings tile wymagają pełnej weryfikacji na fizycznym urządzeniu.
- Manualny TalkBack, API 26, tablet/foldable i split-screen nie były dostępne w tej sesji; test wykonano na telefonicznym emulatorze API 35 oraz przy font scale 150%.
- AndroidX Security Crypto używane przez istniejący szyfrowany cache/token store jest oznaczone jako deprecated. Migracja formatu danych bez testów upgrade/downgrade mogłaby wylogować użytkowników lub utracić cache, dlatego nie została wykonana w tym zakresie.
- AGP 8.10.1 emituje ostrzeżenia zgodności z Gradle 10. Równoległe uruchomienie lint i kompilacji release raz trafiło na znany wyścig brakującego stubu KAPT; osobne, sekwencyjne `assembleRelease` i `lintDebug` zakończyły się zielono.
