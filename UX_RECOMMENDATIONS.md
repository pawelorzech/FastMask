# UX_RECOMMENDATIONS — FastMask, 2026-08-01

Ten dokument zawiera **oceny i propozycje**. Czyste fakty z audytu są w `AUDIT_REPORT.md`, lista zmian w `CHANGELOG_AGENT.md`. Poprzednie rekomendacje: `Plans/audit-archive/UX_RECOMMENDATIONS-2026-07-27.md`.

Scoring 1-5 dla **Impact / Effort / Confidence / Risk**, `Priority = Impact × Confidence ÷ Effort`. To narzędzie porządkujące, nie automat decyzyjny — przy remisie decyduje kolumna „Wpływ na prostotę".

---

## 0. Ocena ogólna

FastMask jest produktem o nietypowo wysokiej dyscyplinie na to, ile ma powierzchni. Główne ścieżki są krótkie, destrukcyjne akcje mają potwierdzenia i Undo, tryb demo pozwala zobaczyć wartość przed podaniem tokenu, a 20 lokalizacji jest kompletnych bez ani jednego dryfu. To nie jest projekt, w którym trzeba szukać dużych ruchów produktowych.

Dwa obserwacje warte uwagi na poziomie produktu, a nie pojedynczych ekranów:

**Pierwsze uruchomienie ma jeden twardy próg i wszystko inne jest łatwe.** Użytkownik musi wejść na fastmail.com, znaleźć Settings → Privacy & Security → Integrations → API tokens, utworzyć token o właściwym zakresie i wkleić go. To pięć kroków w cudzym interfejsie, zanim aplikacja pokaże cokolwiek. Tryb demo słusznie łagodzi ten próg, ale nie skraca samej procedury. To jest miejsce, gdzie tracisz najwięcej ludzi, i jedyne miejsce, gdzie warto rozważyć realną pracę projektową (rekomendacja B1).

**Aplikacja jest bardzo dobra dla użytkownika czytnika ekranu i wyraźnie gorsza dla widzącego użytkownika z zaburzeniem widzenia barw.** To nietypowy rozkład i wynika wprost z tego, jak szły poprzednie audyty: dodawano `contentDescription`, bo to dało się zrobić w kodzie bez dotykania wyglądu. Stan maski na liście jest nadal zakodowany wyłącznie kolorem, i to kolorami o niemal identycznej luminancji (A1).

---

## A. Quick wins

Małe, tanie, niskiego ryzyka.

### A1 · Rozróżnij stan maski kształtem, nie tylko kolorem ⭐ *najwyższy priorytet*

**Problem użytkownika.** Na liście — najczęściej przewijanym ekranie aplikacji — jedynym wskaźnikiem stanu jest kolorowa kropka. Policzone kontrasty *między* wypełnieniami: archived vs pending w motywie jasnym **1,04:1**, enabled vs pending w ciemnym **1,02:1**. Odcień jest jedyną różnicą; luminancja praktycznie identyczna, więc dla użytkownika z deuteranopią albo protanopią te stany zlewają się całkowicie. Użytkownik czytnika ekranu ma tę informację (`stateContentDescription`), widzący z zaburzeniem widzenia barw — nie. To potwierdzona niezgodność z **WCAG 1.4.1 (poziom A)**.

**Rozwiązanie.** Zachowaj kolory, dodaj drugi nośnik w `StateDot`: wypełnione koło (enabled) · pierścień (disabled/off) · koło z ukośnikiem albo kwadrat (archived) · kropka z obwódką przerywaną (pending). Cztery kształty, ten sam rozmiar i ta sama pozycja, zero zmian w układzie wiersza.

**Dlaczego nie etykieta tekstowa.** Poprzedni audyt rozważał czwartą linię tekstu w wierszu i odrzucił — słusznie, bo to zagęszcza najgęstszy ekran. Kształt rozwiązuje 1.4.1 bez tego kosztu.

**Walidacja.** Zrzuty listy przepuszczone przez symulator dichromatyczny: cztery stany muszą być rozróżnialne w skali szarości. **Metryka:** brak — to zgodność, nie eksperyment.

**Dlaczego nie zrobiłem tego w audycie:** zmienia język wizualny centralnego ekranu produktu, którego „warm-ink design" jest deklarowaną wartością. To Twoja decyzja projektowa, nie naprawa defektu.

| Impact | Effort | Confidence | Risk | Priority |
|---|---|---|---|---|
| 4 | 2 | 5 | 1 | **10,0** |

### A2 · Ogłaszaj stany ładowania, pustki i błędu czytnikowi ekranu

**Problem.** Po naprawie `PillButton` w tym audycie w aplikacji zostają trzy miejsca z `liveRegion`, a ciche przejścia to: ładowanie listy (sześć nieopisanych `Box`-ów — ekran czyta się jako **pusty**, nie „ładuję"), wyszukiwanie bez wyników, błąd wczytania listy, nieudany zapis na szczegółach, nieudane tworzenie, ostrzeżenie o pustym wklejeniu przy logowaniu, eksport CSV w toku.

**Rozwiązanie.** `Modifier.semantics { liveRegion = LiveRegionMode.Polite }` na kontenerach tych stanów. Banery offline i błędu inline (`MaskedEmailListScreen.kt:851`, `:884`) są zrobione poprawnie — użyj ich jako wzorca.

**Walidacja.** Przejście TalkBackiem. **Metryka:** brak sensownej ilościowej; to zgodność.

| Impact | Effort | Confidence | Risk | Priority |
|---|---|---|---|---|
| 3 | 2 | 4 | 1 | **6,0** |

### A3 · `heading()` na czterech brakujących ekranach

Jest na liście, tworzeniu, ustawieniach i Pro. Brakuje na logowaniu, powitaniu, **szczegółach maski** (tytuł ekranu = nazwa maski) i ekranie zamka. Bez tego nawigacja po nagłówkach w TalkBacku pomija połowę aplikacji.

| Impact | Effort | Confidence | Risk | Priority |
|---|---|---|---|---|
| 2 | 1 | 5 | 1 | **10,0** |

### A4 · `ImeAction.Next` w formularzu tworzenia

Cztery pola na `KeyboardOptions.Default`, więc klawiatura pokazuje „gotowe" zamiast „dalej" i użytkownik musi tapać w każde pole osobno. Jedna linia na pole. Pozycja otwarta od audytu 2026-07-24.

| Impact | Effort | Confidence | Risk | Priority |
|---|---|---|---|---|
| 2 | 1 | 5 | 1 | **10,0** |

### A5 · `selectableGroup()` na trzech grupach wyboru

Aktywna/Wyłączona przy tworzeniu, akcenty i języki w ustawieniach. `Role.RadioButton` jest ustawione poprawnie na elementach, ale bez grupy TalkBack nie powie „1 z 5".

| Impact | Effort | Confidence | Risk | Priority |
|---|---|---|---|---|
| 2 | 1 | 4 | 1 | **8,0** |

### A6 · Nie sklejaj stringów w Kotlinie

Trzy miejsca: `append(" ")` między fragmentami hero na logowaniu (psuje japoński `静かな場所へ マスクメール。`, chiński, i w arabskim odrywa proklitykę `لـ` od `البريد المقنّع`), `createdMessageTemplate.replace("%s", email)` na liście (omija kontrolę placeholderów w loncie — jeden tłumacz piszący `%1$s` daje snackbar z dosłownym placeholderem) i `"FastMask · ${…}"` w ustawieniach (kolejność bidi w arabskim nie do wysterowania z zasobu). Każde to zamiana na `stringResource(R.string.x, arg)`.

| Impact | Effort | Confidence | Risk | Priority |
|---|---|---|---|---|
| 2 | 1 | 4 | 1 | **8,0** |

### A7 · Ręczne odświeżanie osiągalne bez gestu

`PullToRefreshBox` to jedyny sposób ręcznego odświeżenia, gdy lista ma treść; przycisk „Ponów" istnieje tylko w gałęzi „pusto + błąd", a baner nieudanego odświeżenia nie oferuje żadnej akcji. Dla użytkownika klawiatury albo kogoś z ograniczoną motoryką nie ma alternatywy. Dodaj akcję „Ponów" do banera błędu inline.

| Impact | Effort | Confidence | Risk | Priority |
|---|---|---|---|---|
| 2 | 1 | 4 | 1 | **8,0** |

---

## B. Średni zakres

### B1 · Skróć drogę do pierwszego tokenu

**Problem.** Najkosztowniejszy moment produktu: pięć kroków w cudzym interfejsie, zanim aplikacja pokaże cokolwiek. Instrukcja jest tekstem, a użytkownik przełącza się między aplikacją a przeglądarką, próbując zapamiętać ścieżkę menu.

**Rozwiązanie.** (a) Przycisk otwierający **bezpośredni** deep link do ekranu tokenów Fastmaila zamiast strony głównej ustawień, jeśli taki URL jest stabilny. (b) Automatyczne wykrycie tokenu w schowku po powrocie do aplikacji, z jawnym potwierdzeniem („Wykryto token w schowku — użyć?"), nigdy cichym wklejeniem. (c) Konkretny komunikat, gdy token nie ma zakresu Masked Email — dziś `MaskedEmailScopeMissingException` istnieje, warto sprawdzić, czy jego komunikat mówi użytkownikowi **co zrobić**, a nie tylko co poszło źle.

**Ryzyko.** Odczyt schowka to wrażliwa operacja — na Androidzie 12+ system pokazuje toast przy każdym odczycie. Robić to **wyłącznie** po jawnym geście użytkownika, nigdy w tle. Wpływ na prostotę: neutralny, jeśli (b) jest potwierdzeniem, a nie automatem.

**Walidacja.** Test użyteczności na 3-5 osobach, które nigdy nie generowały tokenu API. **Metryka:** odsetek sesji, które od powitania dochodzą do listy z prawdziwym tokenem, i mediana czasu do pierwszej maski.

| Impact | Effort | Confidence | Risk | Priority |
|---|---|---|---|---|
| 5 | 3 | 3 | 3 | **5,0** |

### B2 · Ścieżka wyjścia z „token odrzucony"

Komunikat `error_auth` brzmi „Fastmail odrzucił token. Zaloguj się ponownie", a jedyna droga do tego to Ustawienia → Wyloguj, bez podpowiedzi. Napis obiecuje akcję, której interfejs nie oferuje. Minimalnie: akcja „Zaloguj ponownie" bezpośrednio w banerze błędu. Docelowo: wykrycie 401 przenoszące na ekran logowania z zachowanym kontekstem.

| Impact | Effort | Confidence | Risk | Priority |
|---|---|---|---|---|
| 3 | 2 | 4 | 2 | **6,0** |

### B3 · Potwierdzenie przed eksportem CSV

Eksport wysyła **każdą maskę w plaintekście** do dowolnie wybranej aplikacji z arkusza udostępniania, bez ostrzeżenia. Archiwizacja i wylogowanie mają potwierdzenia; ta operacja, która wyprowadza komplet danych konta poza aplikację, nie ma. Jeden dialog, spójny z resztą — mówiący wprost, że plik zawiera adresy i opisy w postaci jawnej i że znika z pamięci podręcznej po godzinie.

| Impact | Effort | Confidence | Risk | Priority |
|---|---|---|---|---|
| 3 | 1 | 4 | 1 | **12,0** |

### B4 · Licznik wyników po wyszukaniu

Po wpisaniu frazy lista podmienia się bez żadnego komunikatu. Linia „n z m masek" pod polem filtra pomaga wszystkim, a użytkownikowi czytnika ekranu domyka najgorszy przypadek z A2 (przejście do „brak wyników" jest dziś całkowicie ciche).

| Impact | Effort | Confidence | Risk | Priority |
|---|---|---|---|---|
| 3 | 2 | 4 | 1 | **6,0** |

### B5 · Adres maski czytelny przy dużej czcionce

`MaskedEmailDetailScreen.kt:265-274` — sam adres ma `maxLines = 1` z wielokropkiem, podobnie wiersze metadanych. Kopiowanie działa, ale **przeczytanie** pełnego adresu przy skalowaniu czcionki 200% już nie; tekst nie jest zaznaczalny i nie ma rozwinięcia. Zawijanie w dwie linie albo zaznaczalny tekst.

| Impact | Effort | Confidence | Risk | Priority |
|---|---|---|---|---|
| 2 | 2 | 4 | 1 | **4,0** |

### B6 · Snackbar Undo przeżywający obrót ekranu

`MaskedEmailListScreen.kt:149` używa `remember`, nie `rememberSaveable`, a stan archiwizacji jest czyszczony z `SavedStateHandle` zanim snackbar zostanie obsłużony. Obrót przy widocznym „Zarchiwizowano — Cofnij" i afordancja znika bezpowrotnie. Odzyskanie istnieje (filtr Archiwum → szczegóły → przełącz stan), więc to nie jest utrata danych — ale to dokładnie odwrotność poprawki z poprzedniego audytu, która usuwała *zombie* snackbar.

| Impact | Effort | Confidence | Risk | Priority |
|---|---|---|---|---|
| 2 | 2 | 4 | 1 | **4,0** |

---

## C. Eksperymenty — wartość niepewna, najpierw dowód

### C1 · Czy pola edycyjne są poprawnie czytane przez TalkBack

`DesignInput.kt:106-114` i pole wyszukiwania nazywają pola przez `contentDescription`. Rozwiązywanie tekstu węzła w TalkBacku preferuje `contentDescription` nad treścią węzła edytowalnego, więc **istnieje ryzyko, że użytkownik edytujący prefiks, domenę, URL czy opis słyszy etykietę i nigdy nie słyszy tego, co w polu jest**. To zostało wprowadzone przez poprawkę dostępności `d1e3edd` i mogłoby być regresją netto. **Nie potwierdzone — wymaga urządzenia z TalkBackiem.** Jeśli się potwierdzi, właściwym wzorcem jest powiązanie etykiety, nie opis. To pierwsza rzecz do sprawdzenia, gdy będziesz miał TalkBacka pod ręką.

### C2 · Tutorial bez cofania i bez pozycji

Pięć kroków, tylko „Dalej" i „Pomiń"; mistap na „Dalej" traci krok bezpowrotnie, a nigdzie nie widać „2 z 5". Kroki zmieniają się też bez `liveRegion` i bez przeniesienia fokusu, więc dla czytnika ekranu tapnięcie „Dalej" nie produkuje żadnego komunikatu. Zanim to rozbudujesz — sprawdź, ilu ludzi w ogóle dochodzi do końca; jeśli większość pomija, właściwym ruchem jest skrócenie tutorialu, nie dodanie mu nawigacji.

### C3 · Czy ktokolwiek używa filtrów listy

Filtry Aktywne / Wyłączone / Archiwum zajmują stałe miejsce nad najczęściej przewijaną listą. Jeśli udział sesji, w których ktoś ich dotyka, jest niski, ten pasek jest kandydatem do zwinięcia pod ikonę — co da miejsce na licznik wyników z B4 bez zagęszczania ekranu. Wymaga danych, których świadomie nie zbierasz, więc realnie: obserwacja na sobie i na kilku użytkownikach, nie analityka.

---

## D. Odrzucone

| Propozycja | Dlaczego odrzucona |
|---|---|
| Etykieta tekstowa stanu przy każdym wierszu listy | Rozwiązuje ten sam problem co A1, ale kosztem czwartej linii tekstu na najgęstszym ekranie. Kształt jest tańszy wizualnie |
| Analityka produktowa, żeby zasilić C2/C3 | Wprost sprzeczne z tożsamością produktu i z polityką prywatności, która obiecuje zero telemetrii. Nie warto tego handlować za dane o użyciu filtrów |
| Cache'owanie treści maili / podgląd wiadomości w aplikacji | Poza zakresem produktu (menedżer masek, nie klient poczty) i wprowadziłby na urządzenie kategorię danych, której dziś tam nie ma |
| Certificate pinning dla `api.fastmail.com` | Rozważone i **słusznie** odrzucone w kodzie: pinning wymaga hashy SPKI zdobywanych poza pasmem i rotowanych na harmonogram; przeoczona rotacja to aplikacja, która przestaje działać dla wszystkich naraz. Ograniczenie zaufania do systemowego magazynu CA już blokuje MITM przez CA zainstalowane przez użytkownika, czyli realny model zagrożenia |
| Automatyczny odczyt schowka przy starcie (bez potwierdzenia) | Toast systemowy na Androidzie 12+ przy każdym odczycie, i cichy odczyt schowka w aplikacji trzymającej token to dokładnie ten rodzaj zachowania, którego ten produkt unika. Wersja z jawnym potwierdzeniem jest w B1 |
| Migracja na najnowsze Compose BOM / Hilt / AGP w ramach audytu | 27 ostrzeżeń `GradleDependency` mówi o nieaktualności, nie o podatności. Masowy bump bez powodu to ryzyko bez korzyści — i akurat teraz ma sens dopiero po tym, jak CI faktycznie buduje PR-y Dependabota (nowy `build.yml`), bo wtedy każdy bump przychodzi z dowodem |
| Rozbicie `MaskedEmailListScreen.kt` na mniejsze pliki | ~880 linii to dużo, ale plik jest spójny tematycznie i dobrze skomentowany. Podział bez konkretnego problemu do rozwiązania to ruch kosmetyczny o realnym koszcie w konfliktach merge'a |

---

## E. Roadmapa

**Najbliższy patch (1.10.2) — same rzeczy tanie i domykające ten audyt**
A3 (`heading()`), A4 (`ImeAction.Next`), A5 (`selectableGroup()`), A6 (sklejanie stringów), B3 (potwierdzenie eksportu). Wszystkie mają `Priority ≥ 8`, żadna nie dotyka architektury.

**Kolejny release (1.11) — dostępność domknięta**
A1 (kształty stanu — pozycja numer jeden), A2 (`liveRegion` na stanach), A7 (ponów bez gestu), B4 (licznik wyników). Przed tym: sprawdź C1 na urządzeniu, bo jeśli podejrzenie się potwierdzi, jest to pilniejsze niż wszystko powyżej.

**Większy release (1.12+)**
B1 (droga do pierwszego tokenu) — jedyna pozycja wymagająca realnej pracy projektowej i jedyna, która może ruszyć retencję. B2 (wyjście z odrzuconego tokenu) naturalnie idzie razem z nią.

**Do walidacji, zanim cokolwiek zbudujesz**
C1 (TalkBack na polach — to jest test, nie eksperyment, i ma najwyższy priorytet z tej trójki), C2 (ukończenia tutorialu), C3 (użycie filtrów).

---

## F. Proponowane metryki

Dobrane tak, żeby **żadna nie wymagała zbierania treści użytkownika ani danych wrażliwych** — co jest twardym ograniczeniem tego produktu, nie preferencją.

| Metryka | Skąd | Po co |
|---|---|---|
| Crash-free users, crash-free sessions | Firebase Crashlytics (już masz) | Jedyne, co dziś realnie mierzysz. Po tym audycie warto obserwować, czy B-3 miało ofiary w polu |
| ANR rate, wskaźnik czasu startu | Play Console → Android vitals | Zero kosztu prywatnościowego, dane agregowane przez Google |
| Odinstalowania / instalacje, retencja D1-D7-D30 | Play Console | Proxy dla progu z B1 — jeśli B1 zadziała, D1 rusza pierwsze |
| Oceny i treść recenzji | Play Console | Najtańsze źródło jakościowe, jakie masz przy zerowej analityce |
| Liczba kroków do pierwszej maski | Test użyteczności, nie telemetria | Jedyny uczciwy sposób zmierzenia B1 bez łamania obietnicy o braku śledzenia |

Świadomie **nie** proponuję: śledzenia zdarzeń w aplikacji, lejków konwersji, map ciepła ani czegokolwiek, co wymagałoby SDK analitycznego. `CrashReportingPrivacyTest` psuje build, gdy zależność analityczna pojawi się w grafie — to dobra bariera i nie warto jej ruszać dla metryk, które można przybliżyć z Play Console.
