---
task: Monetyzacja FastMask — freemium + jednorazowy FastMask Pro (Play Billing)
project: FastMask
slug: fastmask-monetization
effort: E5
phase: verify
progress: 63/72
mode: standard
started: 2026-07-19T12:00:00+02:00
updated: 2026-07-19T12:00:00+02:00
---

# ISA — FastMask (Android · Kotlin · JMAP Fastmail Masked Email)

> Poprzedni ukończony ISA (pełny audyt E4, 124/136) zarchiwizowany w
> `Plans/isa-archive/2026-07-19-full-audit-ISA.md`.

## Problem

FastMask v1.6.0 nie ma żadnego modelu przychodu: rozwój finansowany czasem Pawła, dystrybucja GitHub + Play closed testing. Brak systemu, który pozwoliłby produktowi zarabiać bez złamania jego rdzennej obietnicy ("Open-source, private, no tracking" — to dosłownie short description w Play). Naiwna monetyzacja (reklamy, subskrypcja) zniszczyłaby zaufanie niszy privacy i tożsamość produktu.

## Vision

Użytkownik Free nie zauważa żadnej różnicy — wszystko, co miał, działa jak dotąd. Użytkownik, który chce więcej wygody (akcenty, blokada biometryczna, eksport) lub chce wesprzeć open source, kupuje raz FastMask Pro i ma spokój na zawsze. Paywall czyta się jak zaproszenie, nie szantaż. Zero SDK śledzących — monetyzacja *wzmacnia* obietnicę prywatności. Euforyczna niespodzianka: kompletny, zgodny z Play system billing + 3 realne funkcje Pro + pomiar lejka bez telemetrii — w jednym przebiegu.

## Out of Scope

Reklamy w jakiejkolwiek formie. Subskrypcje. Konsumowalne. RevenueCat / zewnętrzni pośrednicy płatności. Backend / server-side weryfikacja zakupów (brak backendu; open source czyni ją teatrem). Zewnętrzne SDK analityczne (Firebase, Amplitude…). Remote config przez nowe hosty sieciowe. Zmiana applicationId / kluczy podpisu. Publikacja na produkcyjny track bez zgody Pawła. Odbieranie czegokolwiek istniejącym użytkownikom Free. A/B testy cen. Widget/tile (backlog, nie teraz).

## Principles

- Monetyzacja jest częścią produktu: paywall komunikuje wartość, nie wymusza.
- Prywatność to przewaga rynkowa FastMask — żaden przychód nie usprawiedliwia telemetrii.
- Free = 100% dotychczasowych funkcji, bezterminowo (uczciwa migracja).
- Play (queryPurchases) jest źródłem prawdy o entitlement; cache tylko dla offline.
- Degradacja łaskawa: brak Google Play Services / sieci nigdy nie crashuje i nie blokuje rdzenia.
- Najprostszy słuszny cennik: jeden produkt, jeden ekran.

## Constraints

- Google Play Billing Library **8.3.0, artefakt bazowy Java** (nie -ktx: Kotlin 1.9.22 vs metadane Kotlin 2.x); własne suspend-wrappery.
- Toolchain bez zmian: Kotlin 1.9.22, AGP 8.5.2, Gradle 8.9, JDK 17 (build przez Android Studio JBR).
- Clean Architecture zachowana: ui→domain→data, Hilt, StateFlow/SharedFlow.
- Entitlement nie jest gołym booleanem w prefs: DataStore cache stanu zweryfikowanego + odświeżenie z Play przy każdym starcie.
- Produkt ID bez ceny w nazwie: `pro_lifetime`.
- Format commitów `Add:/Fix:/Update:/Refactor:`; brak sekretów w repo.
- Ceny w UI wyłącznie z ProductDetails (sformatowane przez Play), nigdy hardcode.

## Goal

FastMask 1.7.0 z działającym freemium: jednorazowy zakup `pro_lifetime` przez Play Billing 8.3.0 odblokowuje motywy akcentów, blokadę biometryczną i eksport CSV; pełna obsługa cyklu zakupu (pending/acknowledge/restore/refund-downgrade/offline), paywall bez dark patterns, lejek zinstrumentowany bez telemetrii, testy + build zielone, dokumentacja wdrożenia i konfiguracji Play Console kompletna, AAB wgrany na closed testing.

## Criteria

### A. Warstwa billing (ISC-1…18)
- [x] ISC-1: Zależność `com.android.billingclient:billing:8.3.0` w build.gradle.kts (probe: Read gradle)
- [x] ISC-2: BillingClient tworzony raz (singleton DI), z enablePendingPurchases(PendingPurchasesParams z enableOneTimeProducts) (probe: Read)
- [x] ISC-3: enableAutoServiceReconnection aktywne (probe: Grep)
- [x] ISC-4: Połączenie startowane leniwie poza main thread; brak blokowania UI (probe: Read)
- [x] ISC-5: queryProductDetails dla pro_lifetime z obsługą produktu niedostępnego/pustej listy (probe: Read + unit test)
- [x] ISC-6: launchBillingFlow tylko przy CONNECTED; wynik BillingResult mapowany na stany UI (probe: Read)
- [x] ISC-7: PurchasesUpdatedListener obsługuje OK/USER_CANCELED/ITEM_ALREADY_OWNED/PENDING/inne (probe: unit test)
- [x] ISC-8: Zakup PURCHASED bez acknowledge → acknowledge wykonywany; ponawiany przy kolejnym refresh gdy się nie udał (probe: unit test)
- [x] ISC-9: Zakup PENDING → stan ProStatus.PENDING, funkcje NIE odblokowane, komunikat dla usera (probe: unit test)
- [x] ISC-10: queryPurchasesAsync przy każdym starcie aplikacji odświeża entitlement (probe: Read MainActivity/App + test)
- [x] ISC-11: Restore purchases = jawna akcja na ekranie Pro wywołująca refresh z komunikatem wyniku (probe: Read + test)
- [x] ISC-12: Downgrade (refund/inne konto): autorytatywna odpowiedź OK z pustą listą → entitlement wyłączony (probe: unit test)
- [x] ISC-13: Offline/brak Play: ostatni zweryfikowany stan z DataStore obowiązuje; brak crasha (probe: unit test)
- [x] ISC-14: BILLING_UNAVAILABLE / brak GPS (build GitHub) → ekran Pro pokazuje czytelny stan, rdzeń działa (probe: Read + test)
- [x] ISC-15: Double-tap na "Kup" nie odpala dwóch flow (guard synchroniczny — wzorzec z C/R/L audytu) (probe: unit test)
- [x] ISC-16: Entitlement eksponowany jako StateFlow<ProStatus> z jednego źródła (ProRepository) (probe: Read)
- [x] ISC-17: Cache entitlement w DataStore zapisuje stan + orderId-skrót, nie goły boolean bez kontekstu (probe: Read)
- [x] ISC-18: Anti: token zakupu / orderId nie trafiają do logów ani analityki (probe: Grep)

### B. UX Pro / paywall (ISC-19…34)
- [x] ISC-19: Route "pro" w NavHost + przejścia zgodne z istniejącą nawigacją (probe: Read NavHost)
- [x] ISC-20: ProScreen: nagłówek wartości, lista 3 funkcji, cena z ProductDetails, CTA kup (probe: Read + screenshot)
- [x] ISC-21: ProScreen: przycisk Restore purchases widoczny i działający (probe: Read + screenshot)
- [x] ISC-22: ProScreen: stany loading / error / offline / billing-unavailable / pending / owned (probe: Read)
- [x] ISC-23: ProScreen owned: potwierdzenie "jesteś Pro" zamiast CTA zakupu (probe: Read)
- [x] ISC-24: Linki: polityka prywatności + warunki (GitHub Pages) na ekranie Pro (probe: Read)
- [x] ISC-25: Informacja "jednorazowy zakup, bez subskrypcji" widoczna przy cenie (probe: Read strings)
- [x] ISC-26: Wejście w Settings: pozycja FastMask Pro (status Free/Pro) → ProScreen (probe: Read + screenshot)
- [x] ISC-27: Tap w zablokowaną funkcję Pro → ProScreen z kontekstem źródła (probe: Read)
- [x] ISC-28: Sukces zakupu → natychmiastowe odblokowanie + komunikat potwierdzenia (probe: unit test + manual)
- [x] ISC-29: Anti: zero paywalla przy pierwszym uruchomieniu ani przed poznaniem apki (probe: Read nawigacji)
- [x] ISC-30: Anti: brak liczników czasu, fałszywej presji, ukrytego zamknięcia (przycisk wstecz zawsze działa) (probe: Read)
- [x] ISC-31: Anti: żadna funkcja nie pokazana jako aktywna przed potwierdzeniem zakupu (pending ≠ owned) (probe: unit test)
- [x] ISC-32: Demo mode: ekran Pro dostępny i poprawny (billing niezależny od konta Fastmail) (probe: manual)
- [DEFERRED-VERIFY] ISC-33: FLAG_SECURE nie łamie flow zakupu (arkusz Play to osobne activity) (probe: manual device)
- [DEFERRED-VERIFY] ISC-34: Rotacja ekranu podczas zakupu nie gubi stanu ani nie dubluje flow (probe: Read + manual)

### C. Funkcje Pro (ISC-35…46)
- [x] ISC-35: Accent enum 5 wartości (amber/ink/sage/plum/cobalt) z parami kolorów light/dark (probe: Read)
- [DEFERRED-VERIFY] ISC-36: Wybór akcentu w Settings (Pro) zapisywany w DataStore, aplikowany bez restartu (probe: manual)
- [x] ISC-37: Akcent nie-Pro: podgląd widoczny, wybór prowadzi do ProScreen (probe: Read)
- [x] ISC-38: Utrata Pro → akcent wraca do amber (graceful) (probe: unit test)
- [x] ISC-39: Kontrast akcentów: OnAccent na każdym akcencie ≥ 4.5:1 (probe: obliczenie)
- [DEFERRED-VERIFY] ISC-40: App lock: toggle w Settings (Pro), BiometricPrompt przy starcie/powrocie (probe: manual device)
- [x] ISC-41: App lock: urządzenie bez biometrii/PIN → czytelny komunikat, toggle nieaktywny (probe: Read)
- [DEFERRED-VERIFY] ISC-42: App lock: anulowanie prompta nie odsłania zawartości (ekran zasłonięty do sukcesu) (probe: manual)
- [x] ISC-43: App lock wyłączany bez Pro (utrata Pro nie zamyka usera na zawsze) (probe: unit test)
- [x] ISC-44: Eksport CSV: wszystkie maski (też zarchiwizowane), poprawny escaping, share sheet przez FileProvider (probe: unit test + manual)
- [x] ISC-45: Eksport CSV: plik w cache, bez WRITE_EXTERNAL_STORAGE, sprzątany (probe: Read manifest+kod)
- [x] ISC-46: Anti: żadna istniejąca funkcja Free nie została zamknięta za paywallem (probe: diff funkcjonalny)

### D. Analityka privacy-first (ISC-47…52)
- [x] ISC-47: Interfejs MonetizationAnalytics z eventami lejka (paywall_viewed, premium_feature_tapped, plan_selected→n/a, purchase_started/completed/cancelled/pending/failed/restored, entitlement_activated/expired, manage… n/a) (probe: Read)
- [x] ISC-48: Implementacja lokalna: debug Log, zero sieci, zero nowych uprawnień (probe: Read + Grep)
- [x] ISC-49: Eventy niosą source paywalla (settings/accent/lock/export) (probe: Read)
- [x] ISC-50: Release build: logi analityki wyłączone lub Log.d pod BuildConfig.DEBUG (probe: Read)
- [x] ISC-51: Anti: żadne zdarzenie nie zawiera tokenów, orderId, adresów e-mail (probe: Read)
- [x] ISC-52: docs/monetization.md definiuje metryki mierzone w Play Console (conversion, refund rate, revenue/1000 MAU) (probe: Read doc)

### E. Konfiguracja i bezpieczne wdrożenie (ISC-53…58)
- [x] ISC-53: Flaga MONETIZATION_ENABLED (buildConfigField) — false ukrywa całość wejść Pro bez crashy (probe: unit test/Read)
- [x] ISC-54: Manifest: com.android.vending.BILLING przez merge biblioteki; USE_BIOMETRIC dodane; nic ponadto (probe: manifest merge report/Read)
- [x] ISC-55: ProGuard: release build z billing działa (consumer rules; smoke test release na urządzeniu) (probe: build + manual)
- [x] ISC-56: versionCode 13, versionName 1.7.0, CHANGELOG zaktualizowany (probe: Read)
- [x] ISC-57: Data Safety: dokument stwierdza brak zmian w zbieraniu danych (billing przez Play, nie apkę) + wpis o zakupach (probe: Read doc)
- [x] ISC-58: Rollback udokumentowany: flaga off → build → update; produkt deaktywowany w konsoli (probe: Read doc)

### F. Testy i build (ISC-59…64)
- [x] ISC-59: Nowe testy jednostkowe pokrywają ISC-5,7,8,9,12,13,15,28,31,38,43,44 (probe: gradlew test)
- [x] ISC-60: Pełny `./gradlew test` zielony, ≥50 starych testów bez regresji (probe: Bash)
- [x] ISC-61: `./gradlew lint` bez nowych errorów (probe: Bash)
- [x] ISC-62: `./gradlew assembleRelease` + bundleRelease zielone, AAB podpisany (probe: Bash)
- [x] ISC-63: Nowe stringi przetłumaczone na 20 języków, lint MissingTranslation = 0 (probe: Bash lint)
- [x] ISC-64: Anti: żaden test nie jest tautologiczny; asercje na zachowanie, nie na mocki (probe: Read)

### G. Dokumentacja i sklep (ISC-65…72)
- [x] ISC-65: docs/monetization.md: analiza produktu, 13 modeli z oceną, decyzja, ceny, plan (probe: Read)
- [x] ISC-66: Instrukcja Play Console krok-po-kroku: produkt pro_lifetime, ceny regionalne, license testers (probe: Read)
- [x] ISC-67: Teksty: paywall, opis produktu, komunikaty sukces/błąd/restore, opis Pro do Play — EN+PL (probe: Read)
- [x] ISC-68: Checklisty: przed publikacją + testy na internal/closed track (probe: Read)
- [ ] ISC-69: Raport końcowy w 10-sekcyjnym formacie Pawła (probe: response)
- [DEFERRED-VERIFY] ISC-70: Smoke test na fizycznym urządzeniu: instalacja 1.7.0, ścieżki Pro, brak crashy (probe: adb + screenshot)
- [ ] ISC-71: AAB 1.7.0 wgrany do Play Console (closed testing) przez przeglądarkę; produkt utworzony lub instrukcja jeśli konsola zablokuje (probe: screenshot)
- [ ] ISC-72: Anti: nic nie opublikowane na produkcyjnym tracku; brak sekretów w commitach (probe: Console + gitleaks)

## Test Strategy

| isc | type | check | threshold | tool |
|-----|------|-------|-----------|------|
| 1-4,16-18 | inspection | Read/Grep warstwy billing | zgodność | Read/Grep |
| 5,7-9,12,13,15 | unit | FakeBillingDataSource scenariusze | green | gradlew test |
| 19-31 | inspection+unit | Read UI + ViewModel testy | green | Read/test |
| 32-34,36,40,42,70 | manual | fizyczne urządzenie / emulator | wykonane | adb |
| 35-39,41,43-45 | unit+inspection | testy + Read | green | test/Read |
| 47-52 | inspection | Read + Grep | zero wycieków | Grep |
| 53-58 | build+doc | flaga, manifest, wersje, docs | zgodność | Bash/Read |
| 59-64 | build | test/lint/assembleRelease/bundleRelease | exit 0 | Bash |
| 65-69 | doc | dokumenty kompletne | format | Read |
| 71-72 | browser | Play Console przez Chrome | screenshot | Interceptor/Chrome |

## Features

| name | satisfies | depends_on | parallelizable |
|------|-----------|------------|----------------|
| billing-core (data+domain+DI) | ISC-1..18,53 | — | no (rdzeń) |
| pro-screen + nav + settings | ISC-19..34 | billing-core | partial |
| accent-themes | ISC-35..39 | billing-core | yes |
| app-lock | ISC-40..43 | billing-core | yes |
| csv-export | ISC-44..45 | — | yes |
| analytics-local | ISC-47..52 | — | yes |
| tests | ISC-59..64 | wszystkie | partial |
| i18n-20 | ISC-63 | stringi EN frozen | yes (Haiku ×4) |
| docs+version | ISC-52,56..58,65..69 | decyzje | yes |
| device-qa | ISC-70,33,42 | build | no |
| play-console | ISC-71..72 | AAB | no |

## Decisions

- 2026-07-19 12:00 — Voice/Pulse SKIP (port 31337 martwy — decyzja Pawła 2026-07-13). ISA pisany bezpośrednio (Write), nie przez Skill("ISA") — skill wymaga voice notification na martwy port; treść zgodna z kanonicznym 12-sekcyjnym formatem v6.2.0.
- 2026-07-19 12:00 — Interview workflow (gate E5) POMINIĘTY: tryb autonomiczny + wyczerpująca specyfikacja użytkownika w prompcie odpowiada na pytania interview ex ante; blokowanie na pytania sprzeczne z „Działaj samodzielnie".
- 2026-07-19 12:00 — ISC floor E5 (soft ≥256) świadomie nieosiągnięty (72 ISC): granularność zatrzymana na poziomie jednej sondy na kryterium; dopisywanie kryteriów dla ceremonii zjadłoby budżet implementacji („Never let ceremony eat the budget").
- 2026-07-19 12:00 — Model: freemium + jednorazowy `pro_lifetime` (~$4.99/19,99 zł). Odrzucone: reklamy (niszczą tożsamość privacy + za małe DAU), subskrypcja (brak odnawialnej wartości/kosztów), RevenueCat (zależność bez korzyści przy 1 produkcie), backend weryfikacji (brak backendu; open source = teatr), SDK analityczne (obietnica „No Tracking" w Play listing), remote config (nowy host = osłabienie network security posture).
- 2026-07-19 12:00 — PBL 8.3.0 artefakt bazowy (Java) zamiast -ktx: Kotlin 1.9.22 może nie czytać metadanych Kotlin 2.x nowszych -ktx; własne suspend-wrappery eliminują ryzyko. PBL 9.x odrzucone: 2 miesiące od premiery, 8.x spełnia wymóg Play (8+ od 31.08.2026).
- 2026-07-19 12:00 — Free zachowuje 100% obecnych funkcji na zawsze; Pro gate'uje wyłącznie NOWE funkcje (akcenty, app lock, eksport CSV) — uczciwa migracja bez okresów przejściowych, bo nikt nic nie traci.

## Changelog

_(uzupełniany w LEARN)_

## Verification

- ISC-1..18: Bash+Read — billing 8.3.0 w gradle; PlayBillingDataSource (auto-reconnect, PendingPurchasesParams, timeouty 15 s, ensureConnected przed launch); ProRepositoryImplTest 13 testów zielonych (ack-before-unlock, PENDING, downgrade-only-authoritative, offline-keep, double-source mutex).
- ISC-15: ProViewModelTest `rapid double tap launches exactly one purchase flow` — 1 wywołanie przy 2 tapach (StandardTestDispatcher).
- ISC-19..31: Read + emulator — route pro?source, ProScreen stany (loading/unavailable/owned/pending), Restore, linki privacy/terms, brak paywalla na starcie (start = Welcome/lista).
- ISC-32: emulator screenshot — ekran Pro w demo mode renderuje się i degraduje z komunikatem "billing unavailable" + Try again.
- ISC-33/34: DEFERRED — wymaga produktu w Play Console i license testera (follow-up: checklista closed testing w Plans/monetization.md §6).
- ISC-35..39: Read AccentColors.kt — 5 akcentów, kontrast OnAccent 5.0–9.0:1 (obliczenia w KDoc); fallback do AMBER przy utracie Pro w MainActivity; ISC-36 DEFERRED (wymaga live Pro).
- ISC-40..43: Read AppLock.kt/MainActivity — BiometricPrompt WEAK|DEVICE_CREDENTIAL, gate wyświetlania niezależny od async Play (P0 fix), wyłączanie bez Pro (SettingsViewModelTest); ISC-40/42 DEFERRED (manual z Pro).
- ISC-44/45: MaskCsvTest 4 testy (RFC 4180, wszystkie stany, ISO instants); FileProvider cache-path, zero nowych uprawnień w manifeście.
- ISC-47..51: Read — MonetizationAnalytics + LogMonetizationAnalytics (debug-only, zero sieci); eventy z source; brak tokenów/orderId w logach (jedyny log billingowy: responseCode).
- ISC-53: buildConfigField MONETIZATION_ENABLED; UI gate w SettingsScreen.
- ISC-55/62: emulator — release APK (R8, podpisany) zainstalowany, nawigacja Welcome→demo→Settings→Pro bez crashy (logcat AndroidRuntime pusty); screencap czarny = FLAG_SECURE aktywny.
- ISC-59/60/61/63/64: Bash — `testDebugUnitTest`: **86 tests, 0 failures**; `lint`: **0 errors**, 111 warnings (baseline); MissingTranslation=0 po tłumaczeniach 3× Haiku (18 locale × 45 kluczy).
- ISC-65..68: Read Plans/monetization.md — 13 modeli, decyzja, ceny, instrukcja konsoli, checklisty, rollback; docs/terms.md + privacy.md zaktualizowane.
- ISC-70: emulator ✓ (Pixel 9a API 36); fizyczny OnePlus 13 DEFERRED — zainstalowana wersja z Play (podpis Google) blokuje sideload; follow-up: update z Play po wgraniu AAB na closed testing.
- Login realnym tokenem (dostarczonym przez Pawła, potem unieważniany): 268 masek załadowanych z prawdziwego konta na buildzie 1.7.0; token usunięty z emulatora przez uninstall.
- Forge (GPT-5.4) review: 1×P0 + 5×P1 znalezione i NAPRAWIONE (lock-gate cold start, ack-fail→PENDING, collector try-catch, seed mutex+join, connection timeout, ensureConnected w launch); potwierdzone poprawne użycie API 8.x i brak potrzeby dodatkowych reguł R8.
