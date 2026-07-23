# FastMask — Rekomendacje UX (audyt 2026-07-23)

Kontekst: rekomendacje z audytu 2026-07-19 (sekcje 7–8 tamtego raportu) pozostają w mocy — tu tylko NOWE obserwacje z obszaru monetyzacji/Pro + rzeczy, które wyszły przy okazji. Scoring 1–5: Impact / Effort (niższy = tańszy) / Confidence / Risk; Priority = Impact × Confidence / Effort.

## Ocena flow Pro (stan po naprawach)

Mocne strony: paywall ma komplet stanów (loading/unavailable+retry/ready/pending/owned), cenę bierze z Play w walucie użytkownika, restore jest widocznym przyciskiem (lepiej niż w większości aplikacji), gating „locked rows" w Settings daje odkrywalność bez nachalności, anti-lockout locka przemyślany. Największe braki to feedback in-flight na akcjach płatnych i niewidoczność Pro poza Settings.

## A. Quick wins

| # | Rekomendacja | Problem użytkownika | Impact | Effort | Conf. | Risk | Prio |
|---|---|---|---|---|---|---|---|
| 1 | **Stan in-flight na Buy/Restore** (disabled alpha + spinner w PillButton) | Po tapnięciu „Buy" nic się nie dzieje 1–3 s (wstawanie billing sheet) — wygląda jak zwis, zachęca do tap-mashingu na ekranie PŁATNOŚCI | 4 | 2 | 5 | 1 | **10.0** |
| 2 | **Spinner przy „Export masks" w trakcie eksportu** (`exportInFlight` do UI state, chevron→spinner) | Eksport robi fetch sieciowy — na wolnym łączu sekundy bez żadnego feedbacku | 3 | 1 | 5 | 1 | **15.0** |
| 3 | **Zdanie o czasie przy PENDING** („zwykle minuty, do 24 h przy niektórych metodach") | Pending purchase to klasyczny trigger „czy mnie oszukali?" | 3 | 1 | 4 | 1 | **12.0** |
| 4 | **„Cancel"→„OK/Done" w dialogach akcent/język/info** + dedykowany klucz dla „Pro feature" na locked rows | „Cancel" sugeruje revert, a wybór już się zastosował | 2 | 2 | 5 | 1 | 5.0 |
| 5 | **Post-purchase: link „Wypróbuj pierwszy akcent →" na OwnedCard** (deep-link do dialogu akcentów) | Moment po zakupie jest płaski; konwersja zakupu w natychmiastowe użycie | 3 | 2 | 3 | 1 | 4.5 |
| 6 | **Rozjaśniony amber jako tekst/kursor w dark mode** (np. #D97F38 tylko dla foreground-użyć; fill FAB bez zmian) | Klasyczny amber ma 3.1:1 na ciemnych tłach jako tekst (poniżej AA) | 3 | 2 | 4 | 3 (gust — decyzja Pawła) | 6.0 |

## B. Średni zakres

| # | Rekomendacja | Problem | Impact | Effort | Conf. | Risk | Prio |
|---|---|---|---|---|---|---|---|
| 1 | **Podgląd akcentów dla free** — picker otwarty dla wszystkich, live preview, gate dopiero na zapisie | Locked row jest abstrakcyjny; „chcę TEN kolor" to konkretna motywacja zakupowa | 4 | 3 | 3 | 2 | 4.0 |
| 2 | **Subtelna wzmianka o Pro po momencie sukcesu** (np. po 5. utworzonej masce, dismissable) | 3 funkcje Pro niewidoczne, dopóki user sam nie wejdzie w Settings | 4 | 3 | 3 | 2 (nachalność — jedna wzmianka, nie popup) | 4.0 |
| 3 | **Wyniesienie zapisu CSV z composable do use case'a** + odporność na rotację (eksport ginie przy obrocie w trakcie [A23]) | Utrata eksportu przy rotacji; nietestowalna logika w UI | 3 | 3 | 4 | 1 | 4.0 |
| 4 | **Eksport z nazwą timestampowaną** + kasowanie starych po wieku, nie wszystkich (A21) | Wolny odbiorca share (Drive) może dostać ucięty plik | 2 | 2 | 3 | 1 | 3.0 |
| 5 | **Undo przywraca stan sprzed archiwizacji** (ENABLED/DISABLED przez `onArchived(id, prevState)`) (A22) | „Undo" obiecuje stan sprzed; maska DISABLED wraca jako aktywna i znów przyjmuje maile | 2 | 2 | 4 | 2 | 4.0 |

## C. Eksperymenty

- **Konwersja per źródło wejścia na paywall** — `source` (settings/accent/app_lock/export) już jest w evencie; po przeniesieniu PAYWALL_CLOSED do `onCleared()` (A24) porównać close-rate per źródło i wyostrzyć copy najsłabszego. Wymaga decyzji o jakiejkolwiek telemetrii (dziś analytics = debug-log — zgodnie z privacy policy; ewentualny pomiar tylko lokalny/dev).
- **Anchoring „no subscription"** — `pro_one_time_note` nad CTA zamiast pod; test na 3–5 użytkownikach.
- **Placeholder ceny w LOADING** (shimmer zamiast spinnera w miejscu CTA) — mniejszy skok layoutu; sprawdzić czy Play query bywa realnie wolne na starszych urządzeniach.

## D. Odrzucone

- **Server-side weryfikacja zakupów / własny backend licencji** — nieproporcjonalne dla indie-aplikacji bez backendu; Play Billing 8.x waliduje on-device, rekoncyliacja przy starcie wystarcza. Koszt utrzymania i prywatność (nowy serwer z danymi zakupów) > zysk.
- **Wzmocnienie locka do BIOMETRIC_STRONG + CryptoObject** — złamałoby fallback na PIN (celowy), a bez szyfrowania danych at-rest kluczem z biometrii i tak nie podnosi realnie bezpieczeństwa. Obecny model (gate prywatności) jest uczciwy.
- **Rozbudowa lokalnego anti-tamperu entitlementu** (np. podpisywanie store'a) — teatr bezpieczeństwa: root i tak wygrywa, a uczciwy użytkownik nie zauważy różnicy. Zamiast tego złagodzić docstring.
- **Launch-popup/onboarding Pro** — sprzeczne z „buduj tak, żeby chcieli"; zamiast tego B2.

## Metryki (privacy-first, bez telemetrii produkcyjnej)

Lokalnie/dev + testy z użytkownikami: crash-free sessions (logcat przy QA), skuteczność zakupu w internal testing (ile prób buy kończy się PRO — mierzalne w Play Console), czas tap-Buy→billing-sheet, odsetek eksportów zakończonych share'em, liczba wejść na paywall per źródło (debug-log), close-rate paywalla po fixie A24. Z Play Console (bez SDK): konwersja strony sklepu, ANR/crash rate, refund rate pro_lifetime — refund rate to też sygnał problemów z acknowledgment (po fixie A3 powinien być ~0 z tego tytułu).

## Roadmapa (propozycja)

1. **Najbliższy patch (1.7.4, razem z merge'em audit-fixes):** A-quick-wins #1–#3 (in-flight states + pending copy) — komplet napraw audytu + feedback na akcjach płatnych.
2. **Kolejny release:** B3+B4 (refactor eksportu), B5 (undo z prev-state), A4 (stringi dialogów), decyzja Pawła: A6 (amber dark) i B1/B2 (widoczność Pro).
3. **Większy release:** podbicie stacka (AGP/Kotlin/Compose — osobny PR, backlog z 19.07), instrumented tests dla lock state-machine i undo (luki z sekcji testowej), potem duże C z poprzedniego audytu (Autofill nadal najlepszą dużą dźwignią produktu).
4. **Do walidacji:** eksperymenty C powyżej.
