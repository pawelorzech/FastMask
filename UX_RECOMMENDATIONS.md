# FastMask — Rekomendacje UX & niewdrożone znaleziska (2026-07-24 pass B)

> Scoring 1–5: **Impact / Effort / Confidence / Risk**. `Priority = Impact × Confidence / Effort` (narzędzie pomocnicze, nie automat).
> Znaleziska naprawione w tym przebiegu są w `AUDIT_REPORT.md §3` i §6.

> **✅ Batch 2 — WDROŻONE po decyzji Pawła:** SEC1 (weryfikacja podpisu Play — klucz pobrany z Play Console), R1 (rozjaśniony amber w dark mode), B5 (dialog niezapisanych zmian), R4 (potwierdzenie wylogowania). Szczegóły w `AUDIT_REPORT.md §6` i `CHANGELOG_AGENT.md`. Pozostałe poniżej to nadal-otwarte rekomendacje.

---

## A. Quick wins (małe, tanie, niskie ryzyko)

### R4 — Potwierdzenie wylogowania
- **Problem użytkownika:** tapnięcie „Sign out" natychmiast czyści token i wyrzuca na welcome. Mniej destrukcyjna akcja (archiwizacja maska) MA dialog potwierdzenia — niespójność, a przypadkowy tap = konieczność ponownego logowania.
- **Rozwiązanie:** `AlertDialog` potwierdzenia (reużyj wzorca `ArchiveDialog`).
- **Impact 3 · Effort 1 · Confidence 5 · Risk 1 · Priority 15.**
- **Walidacja:** spadek przypadkowych wylogowań (proxy: liczba re-loginów w krótkim oknie).
- **Dlaczego nie zrobione teraz:** dodaje tarcie do akcji — wolę decyzję Pawła (tak/nie) niż jednostronne dokładanie modala.

### R8 — Przyciski „loading" tracą etykietę na „…"
- **Problem:** login/create/detail podmieniają tekst przycisku na `"…"` + własny spinner; `PillButton` ma wbudowany `loading` który zachowuje etykietę (używa go ProScreen). TalkBack czyta przycisk jako „ellipsis".
- **Rozwiązanie:** przełącz te przyciski na `PillButton(loading = true)`.
- **Impact 2 · Effort 2 · Confidence 5 · Risk 1 · Priority 5.**

### Zrobione w tym przebiegu (nie wymaga akcji)
- **B1** StateDot semantyka · **R5** label search · **R6** demo-banner 48dp+Button · **R7** segmented `selectable` — patrz AUDIT_REPORT §3.

---

## B. Średni zakres (nowe stany/komponenty, kilka miejsc)

### B5 — Ostrzeżenie o niezapisanych zmianach (create + detail)
- **Problem użytkownika:** wypełniony formularz create (kilka pól) albo edycja detalu ginie bez ostrzeżenia przy back/swipe.
- **Rozwiązanie:** `BackHandler` gdy formularz „dirty" → dialog „Odrzucić zmiany?".
- **Impact 4 · Effort 3 · Confidence 4 · Risk 2 · Priority 5.3.**
- **Ryzyko:** zmienia zachowanie nawigacji (dodatkowy tap), możliwe regresje back-stack. Nowe zachowanie UX → wymaga zgody.
- **Walidacja:** spadek „create abandonment po wpisaniu ≥1 pola".

### R2 — Chaining klawiatury w formularzach
- **Problem:** pola create/detail nie mają `imeAction` (Next między polami, Done na końcu). Login robi to dobrze.
- **Rozwiązanie:** `ImeAction.Next`/`Done` + `keyboardActions`.
- **Impact 2 · Effort 2 · Confidence 5 · Risk 1 · Priority 5.**

### R3 — Blokujący snackbar na ekranie create
- **Problem:** po utworzeniu maska `showSnackbar(Long)` suspenduje, a `onNavigateBack()` czeka na jego zniknięcie — user siedzi na wypełnionym formularzu do ~10s, nie widząc maska na liście.
- **Rozwiązanie:** nawiguj natychmiast, hostuj „utworzono / kopiuj" na liście (jak flow archive/undo).
- **Impact 3 · Effort 3 · Confidence 4 · Risk 2 · Priority 4.**

### R1 — Kontrast domyślnego amber jako mały tekst (dark mode)
- **Problem (zmierzony):** `AccentAmber #A8530F` na `DarkBg #171513` = **≈3.39:1** — poniżej AA (4.5:1) dla małego tekstu. Dotyczy: prefix w preview create, numery instrukcji login, „Sign in" w demo-bannerze, tinty ikon akcentowych. Duży tekst (hero) OK (≥3:1). Pozostałe akcenty Pro są rozjaśniane w dark mode; domyślny amber nie.
- **Rozwiązanie:** rozjaśnić `accent` w `DarkExtras` do ~`#C9761F`+ (cel ≥4.5:1) LUB rezerwować amber-jako-foreground tylko dla dużego tekstu.
- **Impact 3 · Effort 2 · Confidence 4 · Risk 3 · Priority 6.**
- **Dlaczego nie zrobione:** zmiana koloru brandu = decyzja wizualna. Nie ruszam jednostronnie; do obejrzenia na urządzeniu.

### R10 — Czasy względne bez `<plurals>`
- **Problem:** `RelativeTime` używa `%d`-stringów, nie `<plurals>`. Dla ru/pl/ar wychodzi gramatycznie źle („1 минуты назад").
- **Impact 2 · Effort 3 · Confidence 4 · Risk 1 · Priority 2.7.**

---

## C. Eksperymenty / do walidacji

- **Instrumented (androidTest) E2E** — 0 plików. Największa luka pokrycia: lock/unlock, buy-flow, nawigacja shared-element. Zacznij od happy-path listy + create.
- **Autofill API** dla maskowanych adresów (integracja z menedżerem haseł).

---

## D. Odrzucone / poza zakresem (świadomie)

### SEC1 — Weryfikacja podpisu zakupów Play
- **Znalezisko:** pipeline entitlement ufa `Purchase` z `BillingClient` wyłącznie po `purchaseState`/`isAcknowledged`. `Purchase.signature`/`originalJson` nigdy nie są weryfikowane kluczem publicznym RSA aplikacji (a `toBillingPurchase()` w ogóle je odrzuca). Na zrootowanym urządzeniu z hookowanym Play → sfałszowany `PURCHASED` odblokowuje Pro; downgrade tylko na autorytatywnie pustą odpowiedź, więc offline utrzymuje fałsz.
- **Dlaczego nie naprawione teraz (a nie „odrzucone" na stałe):** wymaga **Base64 klucza licencyjnego RSA z Play Console** (Monetization setup → Licensing). Wpisanie pustego/błędnego klucza = fail-closed = blokada WSZYSTKICH realnych zakupów. To ryzyko większe niż samo znalezisko (bypass wymaga roota + narzędzia).
- **Proponowana implementacja (gdy Paweł dostarczy klucz):**
  1. `BillingPurchase` niesie `originalJson` + `signature` (przestań je gubić w `toBillingPurchase`).
  2. Helper `Security.verifyPurchase(base64PublicKey, originalJson, signature)` — `SHA1withRSA` (wzorzec z Play Billing sample).
  3. W `handlePurchases`: traktuj `PURCHASED` jako Pro **tylko** gdy podpis się zgadza; odrzuć w przeciwnym razie.
  4. Klucz w `BuildConfig` z gradle-property/env (nie w repo).
- **Scoring:** Impact 3 (revenue) · Effort 3 · Confidence 5 · Risk 4 (fail-closed jeśli źle) · **wymaga sekretu**.
- **Uwaga o pozornym zabezpieczeniu:** `ProEntitlementStore` trzyma `purchaseToken.sha256()` i odrzuca PRO bez „proof" — ale `read()` nie waliduje proofa z niczym, więc na root nie chroni. Realną bramą jest odpowiedź Play (której podpis nie jest sprawdzany). Nie dokładać kolejnych pozornych warstw bez modelu zagrożeń.

### R9 — Martwe/niespójne komponenty
- `ui/components/ErrorMessage.kt` i `LoadingIndicator.kt` są nieużywane (detail ma własny prywatny `ErrorMessage`). Współdzielony `ErrorMessage` używa stockowego Material `Button` — jedyny w apce, która wszędzie używa `PillButton`. **Rekomendacja:** usunąć albo dostosować do `PillButton`. Czysto porządkowe, zero wpływu na użytkownika, do decyzji.

---

## Proponowane metryki (dopasowane do produktu)

| Obszar | Metryka |
|---|---|
| Stabilność | crash-free users / sessions, ANR rate |
| Główna operacja | success rate create/toggle/archive/restore; retry success rate |
| Onboarding | % ukończenia tutoriala (demo), czas do 1. maska |
| Formularze | abandonment create po wpisaniu ≥1 pola (walidacja B5), błędy walidacji prefixu |
| Monetyzacja | paywall view→buy, restore success, entitlement mismatch rate |
| Dostępność | (jakościowo) przejście TalkBack głównych ścieżek bez blokad |

---

## Roadmapa

- **Patch v1.8.1:** wszystkie fixy z tego przebiegu (CC1–CC5, SEC2/SEC3, B1–B7, R5–R7). Tłumaczenia 4 nowych stringów.
- **v1.9:** SEC1 (po dostarczeniu klucza Play), R1 (kontrast), B5 (unsaved-changes), R2/R3/R8, pierwsze testy instrumented.
- **v2.0:** `<plurals>` (R10), sprzątanie martwych komponentów (R9), Autofill, Material 3 dynamic color.
