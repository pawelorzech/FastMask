# FastMask — model monetyzacji (dokument decyzyjny)

Data: 2026-07-19 · Wersja aplikacji: 1.7.0 (versionCode 13) · Autor analizy: Jarvis (sesja E5)

## 1. Produkt — stan i obserwacje

- **Czym jest FastMask**: natywny, open-source'owy (MIT) klient Android do zarządzania maskami Fastmail przez JMAP. Bez backendu — apka rozmawia wyłącznie z `api.fastmail.com` tokenem użytkownika. Zero analityki i śledzenia (to dosłowna obietnica w short description Play: *"Open-source, private, no tracking"*).
- **Użytkownik**: klient Fastmail (już płaci $5–15/mies. za pocztę), świadomy prywatności, techniczny. Nisza — realny rynek to tysiące, nie miliony instalacji.
- **Wzorzec użycia**: epizodyczny utility — tworzenie maski przy zapisie do usługi, wyłączenie przeciekającej, sprawdzenie aktywności. Krótkie, rzadkie sesje.
- **Koszty operacyjne dewelopera**: ~0 zł (brak backendu, brak API keys po stronie apki). Koszt = czas rozwoju.
- **Alternatywy darmowe**: oficjalna apka Fastmail (masked email schowany głęboko), web. Wartość FastMask = szybkość, wygoda, prywatność, design.
- **Dystrybucja**: GitHub Releases (APK) + Google Play (closed testing od 1.5.1). Brak ugruntowanej bazy w Play — okno na wprowadzenie monetyzacji bez łamania oczekiwań jest teraz.

## 2. Porównanie modeli (13 ocenionych)

| Model | Werdykt | Kluczowy argument |
|---|---|---|
| 1. Całkowicie płatna apka | ❌ | Zabija lejek w niszy; kod MIT — każdy zbuduje za darmo |
| 2. Jednorazowy zakup premium | ✅ **WYBRANY** | Pasuje do epizodycznego użycia; zero zobowiązań; etos "wesprzyj open source" |
| 3. Freemium z trwałym odblokowaniem | ✅ (=2) | Wariant wybranego |
| 4. Subskrypcja mies./roczna | ❌ | Brak odnawialnej wartości i kosztów; user już płaci sub Fastmailowi — resentyment |
| 5. Subskrypcja z trialem | ❌ | J.w.; trial niepotrzebny, bo free tier JEST trialem |
| 6. Ograniczona darmowa wersja | ❌ częściowo | Ograniczanie rdzenia = odbieranie funkcji istniejącym userom; gate tylko NOWYCH funkcji |
| 7. Reklamy | ❌ | Łamie obietnicę "no tracking"; DAU za niskie na sensowny przychód; UMP/zgody/Data Safety; śmierć reputacji w niszy privacy |
| 8. Reklamy + usuwanie za opłatą | ❌ | J.w. |
| 9. Reklamy nagradzane | ❌ | Brak pętli, w której nagroda ma sens |
| 10. Zakupy zużywalne | ❌ | Nic zużywalnego w produkcie |
| 11. Napiwki | ⚠️ | Zgodne z etosem, ale konwersja <1% bez wartości w zamian; Pro pełni tę rolę lepiej |
| 12. Hybryda | ⚠️ | Możliwa później (Pro + tips); w V1 prostota > opcje |
| 13. Brak monetyzacji | ⚠️ | Uczciwa opcja, ale zlecenie zakłada system przychodu; jednorazowy unlock nie szkodzi produktowi |

## 3. Decyzja

**Freemium + jednorazowy lifetime unlock: „FastMask Pro"** (produkt `pro_lifetime`, typ: one-time / non-consumable).

### Podział funkcji

| Free (na zawsze, bez zmian) | Pro (jednorazowo) |
|---|---|
| Lista, wyszukiwanie, filtry, sort po aktywności | **Motywy akcentów** (Amber, Ink, Sage, Plum, Cobalt) |
| Tworzenie / edycja / enable–disable / archiwizacja / undo | **Blokada biometryczna aplikacji** |
| Quick-copy, demo mode, tutorial, 20 języków | **Eksport CSV wszystkich masek** |
| Wszystko, co istniało do wersji 1.6.0 | + wszystkie przyszłe funkcje Pro |

Zasada migracji: **nikt nic nie traci** — Pro gate'uje wyłącznie funkcje, które nigdy wcześniej nie istniały. Zero okresów przejściowych, zero legacy-statusów — nie są potrzebne.

### Cena

- **Bazowa: 4,99 USD / 19,99 zł** (szablon cen Play, ceny lokalne z uwzględnieniem siły nabywczej — Play generuje; zaokrąglić do lokalnych progów psychologicznych).
- Rationale: jedna miesięczna rata Fastmaila; typowa półka "supporter unlock" dla niszowych utility (porównywalne aplikacje open-source: 3–8 USD); niżej = sygnał niskiej wartości, wyżej = tarcie w niszy, która może użyć webu za darmo.
- Walidacja: brak danych → start 4,99 USD, po 60–90 dniach ocena konwersji w Play Console (cel: 1–3% aktywnych userów Play). Zmiany ceny nie wymagają zmian w kodzie (cena zawsze z `ProductDetails`).
- Trial: nie dotyczy (one-time). Plan mies./roczny: nie dotyczy (świadomie odrzucone).

### Analityka — decyzja świadomie niestandardowa

Obietnica "Zero analytics, telemetry" to **przewaga produktu w niszy privacy** — dodanie Firebase/Amplitude do apki, której copy w Play brzmi "no tracking", byłoby samobójstwem reputacyjnym i wymagałoby zmian w polityce prywatności oraz Data Safety. Zamiast tego:

- Interfejs `MonetizationAnalytics` zainstrumentowany we wszystkich punktach lejka (paywall_viewed, premium_feature_tapped ze źródłem, purchase_started/completed/cancelled/pending/failed/restored, entitlement_activated/expired, paywall_closed). Implementacja: log lokalny w debug, no-op w release, zero sieci.
- Metryki biznesowe z **Play Console** (bez SDK): liczba zakupów, przychód, refund rate, konwersja sklepowa, kraje.
- Gdyby kiedyś zaszła potrzeba realnej telemetrii: opt-in, self-hosted, osobna decyzja + aktualizacja privacy policy i Data Safety. Kod funkcyjny nie wymaga wtedy zmian (podmiana implementacji w DI).

### Metryki sukcesu (pierwsza ocena: 30 dni po pełnym rollout, potem kwartalnie)

| Metryka | Źródło | Baseline | Cel 90 dni |
|---|---|---|---|
| Konwersja instalacja→Pro | Play Console (orders / aktywne instalacje) | 0% | 1–3% |
| Przychód / 1000 MAU | Play Console | 0 | ≥ 15 USD |
| Refund rate | Play Console | — | < 5% |
| Purchase failure rate | Play Console orders | — | < 2% |
| Średnia ocena Play | Play Console | brak (closed) | ≥ 4,5 (monetyzacja nie może jej obniżyć) |
| Uninstall rate po 1.7.0 | Play Console | baseline 1.6.0 | bez wzrostu |

## 4. Architektura wdrożenia (co jest w kodzie)

- **Google Play Billing Library 8.3.0** — artefakt bazowy (Java), NIE `-ktx` (Kotlin 1.9.22 vs metadane Kotlin 2.x nowszych ktx); własne suspend-wrappery. Spełnia wymóg Play "8+" (obowiązuje od 31.08.2026). PBL 9.x odrzucone jako zbyt świeże (05.2026).
- **Warstwy**: `data/billing/BillingDataSource` (abstrakcja, testowalna fake'iem) → `PlayBillingDataSource` (BillingClient, auto-reconnect, pending purchases enabled) → `data/repository/ProRepositoryImpl` (cała logika entitlement) → `domain/repository/ProRepository` (StateFlow<ProStatus> — jedno źródło prawdy) → UI.
- **Entitlement**: Play `queryPurchasesAsync` przy każdym starcie = źródło prawdy; cache w DataStore (`pro_entitlement`: status + SHA-256 tokenu zakupu, nigdy goły boolean ani sam token) tylko dla offline. Downgrade wyłącznie po autorytatywnej pustej odpowiedzi OK (refund / zmiana konta); błędy połączenia zachowują ostatni zweryfikowany stan.
- **Acknowledge**: wykonywany zanim funkcje się odblokują; nieudany — ponawiany przy kolejnym refresh (Play refunduje nie-acknowledged po ~3 dniach).
- **Pending**: `ProStatus.PENDING` — funkcje zablokowane, komunikat "płatność w toku".
- **Restore**: jawny przycisk na ekranie Pro (refresh + komunikat wyniku).
- **Brak Google Play / offline (build z GitHuba!)**: ekran Pro pokazuje czytelny stan "billing niedostępny"; rdzeń aplikacji działa w 100%; zero crashy.
- **Kill-switch**: `buildConfigField MONETIZATION_ENABLED` — `false` ukrywa wszystkie wejścia Pro (sekcja w Settings, route zostaje ale bez wejść); właściciele Pro zachowują odblokowane funkcje. Świadomie NIE ma remote config: wymagałby nowego hosta sieciowego (osłabienie network security posture apki, która łączy się wyłącznie z Fastmail) — rollback przez update z flagą + staged rollout Play wystarcza przy tej skali.
- **Weryfikacja server-side**: brak — brak backendu, a przy licencji MIT każdy może zbudować wersję z odblokowanym Pro. Zakup Pro to w połowie akt wsparcia; obfuskacja ponad R8 byłaby teatrem. Ryzyko zaakceptowane i udokumentowane.

## 5. Konfiguracja Google Play Console (do wykonania ręcznie/przez przeglądarkę)

1. **Produkt**: Monetyzacja → Produkty → Produkty w aplikacji → Utwórz:
   - ID: `pro_lifetime` (stały, bez ceny w nazwie)
   - Nazwa: „FastMask Pro" (EN) / „FastMask Pro" (PL)
   - Opis EN: *"One-time unlock: accent themes, biometric app lock and CSV export. Supports open-source development. No subscription."*
   - Opis PL: *"Jednorazowe odblokowanie: motywy akcentów, blokada biometryczna i eksport CSV. Wspiera rozwój open source. Bez subskrypcji."*
   - Cena: szablon od 4,99 USD (PL: 19,99 zł); przejrzeć progi w głównych krajach (US/EU/UK/IN/BR)
   - Status: **Aktywny**
2. **AAB 1.7.0 (vc 13)** wgrać na **closed testing** (istniejący track), release notes EN/PL z `marketing/play/release-notes/`.
3. **License testing**: Ustawienia → License testing → dodać konta testerów (zakupy testowe bez obciążenia karty).
4. **Data Safety**: dodać sekcję "Purchases" → dane transakcyjne przetwarzane przez Google Play (apka nie zbiera nic nowego; deklaracja "No data collected" pozostaje dla danych aplikacyjnych — zob. §7).
5. **NIE publikować na produkcję** — decyzja Pawła po testach na closed tracku.

Uwaga: produkt w aplikacji da się przetestować dopiero, gdy build z uprawnieniem BILLING jest na dowolnym tracku, a konto testera jest license testerem.

## 6. Checklisty

### Przed publikacją (closed → produkcja)
- [ ] Produkt `pro_lifetime` aktywny, ceny przejrzane
- [ ] Zakup testowy license testera: kupno → odblokowanie akcentów/blokady/eksportu
- [ ] Restore na drugim urządzeniu z tym samym kontem Google
- [ ] Refund testowy w konsoli → apka wraca do Free po refresh (do 24h cache Play)
- [ ] Test bez Google Play Services (emulator AOSP) → brak crasha, komunikat "niedostępne"
- [ ] Test offline → funkcje Pro działają z cache; paywall pokazuje błąd sieci
- [ ] Rotacja + process death podczas zakupu
- [ ] Release build (R8) — pełny smoke test ścieżek billing
- [ ] Data Safety zaktualizowane; privacy policy opublikowana (sekcja zakupów + biometrii)
- [ ] terms.html dostępny (GitHub Pages)

### Testy na wewnętrznym torze
- [ ] paywall z każdego źródła (settings / accent / app_lock / export)
- [ ] pending purchase (metoda płatności "test card, always declines/slow") → PENDING, brak odblokowania
- [ ] anulowanie arkusza Play → snackbar "anulowano", brak zmian
- [ ] double-tap "Odblokuj Pro" → jeden arkusz
- [ ] blokada biometryczna: włączenie, zamknięcie/otwarcie apki, anulowanie prompta (treść zakryta), wyłączenie
- [ ] eksport CSV → share sheet, poprawny plik
- [ ] zmiana konta Google → Pro znika po refresh (zgodnie z projektem)

### Rollback (procedura wycofania)
1. Szybki: `MONETIZATION_ENABLED=false` → build 1.7.1 → staged rollout 100%. Wejścia Pro znikają; właściciele zachowują funkcje.
2. W konsoli: dezaktywować produkt `pro_lifetime` (nowe zakupy niemożliwe; istniejące entitlementy zostają).
3. Nuklearny: halt staged rollout 1.7.0 w Play Console (użytkownicy zostają na 1.6.0).
4. Refundy pojedynczych użytkowników: Play Console → Order management.

## 7. Prywatność i Data Safety — zmiany

- **Privacy policy** (docs/privacy.md): dodać sekcję *In-app purchases* — płatność przetwarza Google Play (Google LLC jako niezależny administrator); FastMask otrzymuje wyłącznie potwierdzenie zakupu (token, bez danych płatniczych), przechowuje lokalnie skrót; *App lock* — biometria przetwarzana w całości przez system Android (BiometricPrompt), aplikacja nie ma dostępu do danych biometrycznych i niczego nie przechowuje; *Eksport CSV* — plik tworzony lokalnie na żądanie użytkownika i udostępniany wybranej przez niego aplikacji.
- **Data Safety (Play)**: bez zmian w "data collected" (nadal nic); zakupy obsługuje Play. Brak nowych SDK zbierających dane. Brak reklam — sekcja Ads: "No".

## 8. Teksty (gotowce)

- **Play listing — dopisek do opisu** (EN): *"FastMask is free and open source. An optional one-time FastMask Pro purchase unlocks accent themes, a biometric app lock and CSV export — and supports development. No subscriptions, no ads, no tracking. Ever."*
- **Play listing — dopisek** (PL): *"FastMask jest darmowy i open source. Opcjonalny, jednorazowy zakup FastMask Pro odblokowuje motywy akcentów, blokadę biometryczną i eksport CSV — i wspiera rozwój aplikacji. Bez subskrypcji, bez reklam, bez śledzenia. Nigdy."*
- Paywall, komunikaty sukcesu/błędów/restore, opisy funkcji: zaimplementowane w `strings.xml` (klucze `pro_*`, `settings_pro_*`, 20 języków).

## 9. Ryzyka

| Ryzyko | Prawdopodobieństwo | Mitygacja |
|---|---|---|
| Niska konwersja (nisza + web za darmo) | średnie | Pro = wsparcie + wygoda; koszt utrzymania ~0, każdy zakup to czysty plus |
| "Czemu płatne, skoro open source" w ocenach | niskie | Rdzeń w 100% darmowy; komunikacja "support open source"; APK z GitHuba dalej darmowy |
| Fork z odblokowanym Pro | pewne (MIT) | Zaakceptowane — kto kompiluje forka, nie był klientem |
| Regres billing w release (R8) | niskie | Consumer rules biblioteki + smoke test release na urządzeniu |
| Wymóg PBL: kolejne wersje | pewne (cykl Play) | Bump biblioteki raz w roku; abstrakcja BillingDataSource izoluje zmiany |
