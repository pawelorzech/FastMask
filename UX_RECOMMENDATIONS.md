# FastMask — Rekomendacje UX (audyt 2026-07-24)

Kontekst: rekomendacje z audytu 2026-07-23 pozostają w mocy — tu NOWE obserwacje z bieżącego pełnego sweepu. Scoring 1–5: Impact / Effort (niższy = tańszy) / Confidence / Risk; Priority = Impact × Confidence / Effort.

## Ocena obecnego UX

**Mocne strony:**
- Ciepła, spójna paleta "warm-ink" — wyróżnia się na tle Material You
- Instrument Serif + JetBrains Mono dają charakterystyczny, czytelny styl
- Pierwszy ekran (welcome) jasno komunikuje dwie ścieżki: zaloguj lub wypróbuj demo
- Undo archiwizacji z długim snackbarze (~10s) — dobry affordance
- Quick-copy na listach — najszybsza akcja bez otwierania detailu
- In-flight states na Buy/Restore (spinner) — naprawione z A18
- Export z progress indicator — naprawione z A23

**Słabe strony:**
- Paywall ukryty głęboko w Settings (3 tapnięcia: Settings → Pro → Buy)
- Brak wizualnego wyróżnienia Pro po zakupie (OwnedCard jest mało celebracyjny)
- Error states na detailu pokazują tekst bez retry (gdy email != null)
- Demo tutorial jest prosty, ale pojawia się tylko raz — brak możliwości powrotu

---

## A. Quick wins

| # | Rekomendacja | Problem użytkownika | Impact | Effort | Conf. | Risk | Prio |
|---|---|---|---|---|---|---|---|
| 1 | **Stan in-flight na Buy/Restore** (już naprawiony w A18) | Po tapnięciu „Buy" nic się nie dzieje 1–3 s — wygląda jak zwis | 4 | 1 | 5 | 1 | **20.0** |
| 2 | **Spinner przy „Export masks"** (już naprawiony w A23) | Eksport robi fetch sieciowy — na wolnym łączu sekundy bez feedbacku | 3 | 1 | 5 | 1 | **15.0** |
| 3 | **„Cancel"→„OK" w dialogach akcent/język** | „Cancel" sugeruje revert, a wybór już się zastosował | 2 | 2 | 5 | 1 | 5.0 |
| 4 | **Post-purchase: link „Wypróbuj akcent →" na OwnedCard** | Moment po zakupie jest płaski; konwersja zakupu w natychmiastowe użycie | 3 | 2 | 3 | 1 | 4.5 |
| 5 | **Zdanie o czasie przy PENDING** („zwykle minuty, do 24 h") | Pending purchase to klasyczny trigger „czy mnie oszukali?" | 3 | 1 | 4 | 1 | 12.0 |

## B. Usprawnienia średniego zakresu

| # | Rekomendacja | Problem | Impact | Effort | Conf. | Risk | Prio |
|---|---|---|---|---|---|---|---|
| 1 | **Podgląd akcentów dla free** — picker otwarty, live preview, gate na zapisie | Locked row jest abstrakcyjny; „chcę TEN kolor" to motywacja zakupowa | 4 | 3 | 3 | 2 | 4.0 |
| 2 | **Subtelna wzmianka o Pro po 5. masce** (dismissable) | 3 funkcje Pro niewidoczne dopóki user nie wejdzie w Settings | 4 | 3 | 3 | 2 | 4.0 |
| 3 | **Undo przywraca stan sprzed** — `restoreTo` z `previousState` (A22) | „Undo" obiecuje stan sprzed; DISABLED wraca jako ENABLED | 2 | 2 | 4 | 2 | 4.0 |
| 4 | **Eksport z timestampowaną nazwą** (już naprawiony — A21 fix w codesie) | Wolny odbiorca share może dostać ucięty plik | 2 | 1 | 3 | 1 | 6.0 |

## C. Eksperymenty

- **Anchoring „no subscription"** — `pro_one_time_note` nad CTA zamiast pod; test na 3–5 użytkownikach
- **Placeholder ceny w LOADING** (shimmer zamiast spinnera w CTA) — mniejszy skok layoutu
- **Konwersja per źródło wejścia na paywall** — `source` już jest w evencie; porównać close-rate per źródło

## D. Odrzucone

- **Server-side weryfikacja zakupów** — nieproporcjonalne dla indie-aplikacji bez backendu
- **BIOMETRIC_STRONG + CryptoObject** — złamałoby fallback na PIN (celowy)
- **Rozbudowa anti-tamper** — teatr bezpieczeństwa; root i tak wygrywa
- **Launch-popup Pro** — sprzeczne z „buduj tak, żeby chcieli"

## Metryki (privacy-first)

Lokalnie/dev: crash-free sessions (logcat), skuteczność zakupu (Play Console), czas tap→billing-sheet, close-rate paywalla. Z Play Console: konwersja sklepu, ANR/crash rate, refund rate.

## Roadmapa

1. **Najbliższy patch:** A-quick-wins #1–#3 (in-flight states + pending copy + dialog buttons)
2. **Kolejny release:** B1+B2 (widoczność Pro), B3 (undo prev-state), decyzja Pawła: B4 (amber dark)
3. **Większy release:** Podbicie stacka (AGP/Kotlin/Compose), instrumented tests, Autofill
