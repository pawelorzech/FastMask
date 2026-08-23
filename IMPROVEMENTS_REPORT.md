# Improvements report

Poprzedni raport (przebieg 2026-07-27) zarchiwizowany w
`Plans/audit-archive/IMPROVEMENTS_REPORT-2026-07-27.md`.

## Najważniejsze zmiany UX/UI

- **Ekran szczegółów otwierał się spinnerem i dociągał CAŁE konto.** Każde
  otwarcie (i każdy reload po zapisie/przełączeniu stanu) wykonywał pełny
  `MaskedEmail/get` wszystkich masek, a przy okazji ponownie szyfrował i
  zapisywał cały snapshot offline. Teraz: stale-while-revalidate — ekran od
  razu renderuje maskę z zaszyfrowanego snapshotu (tę, którą użytkownik właśnie
  widział na liście), a świeże dane przychodzi jednym `get` z `ids: [id]`.
  Gdy odświeżenie się nie uda, a treść już jest na ekranie, błąd degraduje się
  do inline komunikatu nad przyciskiem Save zamiast wyrzucać użytkownikowi
  zawartość z oczów i podmieniać na pełnoekranowy błąd.
- **Tutorial demo otwierał się na OSTATNIM kroku przy drugiej sesji demo.**
  Overlay zostaje skomponowany między pokazaniami, a indeks kroku przeżywał
  poprzednie przejście — użytkownik, który wyszedł z demo i wrócił, widział
  kartę „Done" zamiast kroku 1. Reset do kroku 1 przy każdym pokazaniu.
- **Szybkie dwa tapnięcia w „Sign in" na banerze demo nawigowały dwukrotnie.**
  `DemoBannerViewModel.exitDemoMode` był jedyną mutującą ścieżką bez
  synchronicznego guarda in-flight (wzorzec z reszty aplikacji). Dodany, z
  zwolnieniem na każdym wyniku — także po nieudanym zapisie.
- **Etykiety mono renderowały „İ" na tureckich/azerskich urządzeniach.**
  `MonoLabel`/`MonoEyebrow` używały `uppercase()` z locale domyślnym
  („sign in" → „SİGN İN"). Teraz `Locale.ROOT`, spójnie z resztą kodu.

## Performance

- **Szczegóły: najdroższy odczyt aplikacji obsługiwał najmniejszy widok.**
  Pełny fetch konta + write-through całego zaszyfrowanego snapshotu przy
  każdym otwarciu/reloadzie → jeden mały `MaskedEmail/get` z `ids`, bez
  zapisu cache (snapshot dogania przy następnym pełnym syncu listy).
- **Wpisywanie w wyszukiwarkę przerysowywało wszystkie widoczne wiersze.**
  Lambdy `onClick`/`onCopy` tworzone w `itemsIndexed` łapały `email` wiersza,
  więc każdy keystroke dawał każdemu wierszowi nowe instancje i bił skipping
  Compose. Teraz jedna stabilna para callbacków (maska jako parametr,
  `rememberUpdatedState` dla nawigacji). Dodatkowo: etykieta czasu
  relative zapamiętywana per wiersz (znika lookup zasobów + plurals na każdą
  rekompozycję), a cztery przejścia po liście na liczniki filtrów liczą się
  raz na zmianę danych (`remember(emails)`), nie przy każdym keystroke.
- **Cold start: inicjalizacja Tink/Keystore nie blokuje już odczytów
  DataStore.** Sprawdzenie tokenu (`isLoggedIn` → EncryptedSharedPreferences)
  startuje pierwsze i nakłada się na odczyty DataStore pod splashem zamiast
  czekać w kolejce za nimi.
- **Martwa praca w modelu domenowym usunięta.** `formattedCreatedAt`/
  `formattedLastMessageAt` formatowały daty (2 × DateTimeFormatter × N masek
  × każdy fetch) i nigdzie nie były czytane — zmodel, formatter, pola.

## Bugs

- Tutorial demo: druga sesja startowała na ostatnim kroku (opisane wyżej).
- Podwójna nawigacja z banera demo przy rapid tap (opisane wyżej).
- Turecki „İ" w etykietach mono (opisane wyżej).
- `QuickMaskRunner.undoneIds` rósł nieograniczenie przez życie procesu
  (singleton; każdy udany undo latchował id na zawsze). Teraz LRU z limitem
  256 — gwarancja guarda bez wycieku.
- Usunięty przedawniony TODO w strings.xml (20 locale jest przetłumaczonych).

## Architecture/refactoring

- Nowy `GetMaskedEmailUseCase` + `MaskedEmailRepository.getMaskedEmail(id)`
  (interfejs, impl JMAP, demo, dispatcher router, fake testowy). JMAP `get`
  z `ids` w `JmapApi` współdzieli parsowanie ze ścieżką pełnej listy.
- Usunięte martwe okablowanie SharedTransition: `SharedTransitionLayout`
  opakowywał całą nawigację i przepinał scope do listy i szczegółów, ale
  `sharedElement`/`sharedBounds` nie istnieje nigdzie w kodzie. Mniejszy drzewo
  kompozycji, mniej opt-inów do eksperymentalnych API.
- `MaskedEmailDetailViewModel`: dwuetapowe ładowanie SWR z synchronicznym
  guarda `loadInFlight` (retry double-tap nie ustawia dwóch wyścigowych
  fetchy); semantyka `resetEdits` zachowana 1:1.

## Testy

- `testDebugUnitTest`: **504/504**, 0 failures (było 498; +6 regresji:
  seed z cache przed odpowiedzią sieci, degradacja błędu do inline przy
  widocznej treści, single-get zamiast pełnej listy, brak maski na serwerze,
  kształt żądania `ids` w JMAP, mapowanie nieznane id → `success(null)`).
- `lintDebug`: **0 errors**, 101 warnings.
- `assembleRelease` (R8): sukces, APK 4,37 MB.
- `connectedDebugAndroidTest`: **42/44** na emulatorze API 35. Dwa padające
  testy (`creatingAMaskReturnsToTheListWithTheNewMaskOnIt`,
  `archivingAMaskOffersAnUndoThatPutsItBack` — licznik chipa nie aktualizuje
  się po powrocie z innego ekranu) reprodukują się **identycznie na
  niemodyfikowanym main** (zweryfikowane: stash → run → te same 2). To
  problem istniejący przed tą sesją; poprzednia sesja raportowała 44/44, więc
  podejrzane są zmiany środowiska emulatora albo dependabotowe bumpy
  (activity-compose/coroutines) z ostatnich tygodni. Emulator został zajęty
  przez inną sesję przed root-cause.
- Uwaga procesowa: podczas tej sesji jedna „optymalizacja" (kotwica
  lastRefreshTime na „teraz", usuwająca drugi fetch przy otwarciu) została
  cofnięta po analizie testów — refresh na powrót do listy jest mechanizmem,
  którym lista dowiaduje się o maskach utworzonych/zarchiwizowanych na innych
  ekranach; jego usunięcie psuje aktualizację liczników. Guard `fetchInFlight`
  i tak deduplikuje nakładające się fetchy przy zimnym starcie.

## Before/After

Pomiary latency (startup, frame times) nie zostały zebrane — emulator stał się
niedostępny w trakcie sesji. Poniżej zmiany strukturalne, weryfikowalne w kodzie:

| Metryka | Przed | Po |
| --- | --- | --- |
| Zapytanie sieciowe otwarcia szczegółów | 1 × pełny `MaskedEmail/get` konta | 1 × `get` z `ids: [id]` |
| Zapis cache przy otwarciu szczegółów | szyfrowanie + zapis całego snapshotu | brak |
| Prace per keystroke wyszukiwania | rekompozycja wszystkich wierszy + 4 × count + plurals per wiersz | brak (stabilne callbacki, remember) |
| Formatowania daty per fetch listy | 2 × N (martwe, nigdy niewyświetlane) | 0 |
| Cold start (ścieżka splasha) | Tink/Keystore sekwencyjnie po DataStore | równolegle |
| Testy JVM | 498 | 504 |
| Lint | 0 errors / 111 warnings (07-27) | 0 errors / 101 warnings |

## Remaining limitations

- Dwa przedistniejące failure w instrumented suite (patrz Testy) — wymagają
  wyłącznego emulatora do root-cause; reprodukują się na czystym main.
- Prawdziwe logowanie do Fastmail i operacje na produkcyjnym API nie były
  wykonywane (brak tokenu); kształt żądania single-get pokryty testem
  jednostkowym względem kontraktu JMAP.
- Before/after startup i frame times niezmierzone (kontencja emulatora).
- Fonty wciąż doładowywane z Google Fonts provider (FOUT przy pierwszym
  uruchomieniu bez GMS-cache). Bundling fontów rozważony i odrzucony w tej
  sesji: wymaga weryfikacji wizualnej i ~1 MB assetów.
