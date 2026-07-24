# FastMask — Rekomendacje UX 2026-07-24

## Ocena obecnego UX

FastMask ma dojrzały, przemyślany interfejs. Design system (warm-ink palette, Instrument Serif + Inter Tight) jest spójny i wyróżniający się. Podstawowe ścieżki (login → lista → tworzenie → zarządzanie) są proste i działają. Aplikacja radzi sobie dobrze z obsługą błędów sieciowych i stanów pustych.

**Mocne strony:**
- Czysty, pozbawiony rozpraszaczy interfejs
- Spójny design system (DesignKit, PillButton, StatePill)
- Dobre puste stany i loading states
- Prawidłowe etykiety dostępnościowe
- Inteligentne sortowanie i filtrowanie masek
- Przemyślany mechanizm Undo dla archiwizacji

**Słabe strony:**
- Toggle app lock dla nie-Pro użytkowników daje mylący feedback
- Tutorial demo może się nie pokazać w niektórych konfiguracjach
- Brak podpowiedzi Pro funkcji w kontekście (np. przy próbie eksportu)

---

## Rekomendacje

### A. Quick wins

| # | Rekomendacja | Problem | Impact | Effort | Confidence | Risk | Score |
|---|-------------|---------|--------|--------|------------|------|-------|
| A1 | Snackbar "Pro required" przy próbie włączenia app lock bez Pro | Toggle wraca bez feedbacku | 3 | 1 | 5 | 1 | **15.0** |
| A2 | Dodaj tooltip/podpowiedź przy ikonie kłódki w ustawieniach | Użytkownik nie wie czemu funkcja jest zablokowana | 3 | 1 | 4 | 1 | **12.0** |
| A3 | Dodaj badge z liczbą nieprzeczytanych wiadomości na liście | Brak informacji o użyciu masek | 3 | 2 | 3 | 1 | **4.5** |
| A4 | Wyraźniejszy przycisk "Try demo" na welcome screen | Demo to kluczowy kanał pozyskiwania | 4 | 1 | 3 | 1 | **12.0** |

### B. Usprawnienia średniego zakresu

| # | Rekomendacja | Problem | Impact | Effort | Confidence | Risk | Score |
|---|-------------|---------|--------|--------|------------|------|-------|
| B1 | Podgląd akcentów dla free users (z badge "Pro") | Użytkownik nie wie co traci | 3 | 3 | 4 | 1 | **4.0** |
| B2 | Autofill API dla adresów maskowanych | Kopiowanie adresu to dodatkowy krok | 4 | 3 | 3 | 2 | **4.0** |
| B3 | Pull-to-refresh z wyraźną animacją sukcesu | Brak potwierdzenia że refresh zadziałał | 2 | 2 | 3 | 1 | **3.0** |
| B4 | "Recent activity" na karcie maski (kiedy ostatnio użyta) | Użytkownik nie wie które maski są aktywne | 3 | 3 | 3 | 1 | **3.0** |
| B5 | Widżet na ekran główny (szybkie kopiowanie maski) | Otwieranie aplikacji dla każdej maski | 4 | 4 | 3 | 2 | **3.0** |

### C. Eksperymenty produktowe

| # | Rekomendacja | Problem | Impact | Effort | Confidence | Risk | Score |
|---|-------------|---------|--------|--------|------------|------|-------|
| C1 | Automatyczne sugerowanie nazwy domeny/usługi przy tworzeniu | Wpisanie domeny to dodatkowy krok | 3 | 3 | 2 | 2 | **2.0** |
| C2 | "Quick mask" z poziomu notification/shade | Najszybsza ścieżka tworzenia maski | 5 | 4 | 2 | 2 | **2.5** |
| C3 | Grupowanie masek po domenie | Lista może być długa przy wielu maskach | 3 | 4 | 2 | 2 | **1.5** |

### D. Pomysły odrzucone

| # | Pomysł | Powód odrzucenia |
|---|--------|-----------------|
| D1 | Integracja z password managerami (Bitwarden, 1Password) | Zwiększa powierzchnię bezpieczeństwa; maski są już kopiowalne |
| D2 | Statystyki użycia masek (wykresy, trendy) | Feature creep — nie rozwiązuje realnego problemu |
| D3 | Powiadomienia push o nowych wiadomościach na masce | Wymaga serwera pośredniczącego; Fastmail nie udostępnia webhooków |
| D4 | Multi-account (wiele tokenów Fastmail) | Zwiększa złożoność o rząd wielkości; mała baza użytkowników |

---

## Proponowana roadmapa UX

### v1.8.1 (patch) — ten audyt
- ✅ Wszystkie poprawki techniczne (P0-P2)
- ✅ Nowe stringi błędów (rate limit, server error) w 19 językach

### v1.9 (minor)
- A1-A4: Quick wins (snackbar, tooltip, badge, demo button)
- B3: Pull-to-refresh feedback

### v2.0 (major)
- B1: Podgląd akcentów
- B2: Autofill API
- B5: Widżet
- Eksperymenty C1-C3 do walidacji

---

## Metryki sukcesu

| Metryka | Cel | Sposób pomiaru |
|---------|-----|----------------|
| Crash-free rate | >99.5% | Google Play Console |
| Czas do pierwszej maski | <30s dla nowego użytkownika | Manualne testy |
| Pro conversion z demo | >5% | Licznik demo→login→Pro (lokalny) |
| Otwarcia z widżetu | >10% aktywnych użytkowników | Google Play Console (po dodaniu widżetu) |
| Porzucenie formularza tworzenia | <20% | Analityka lokalna (debug) |
