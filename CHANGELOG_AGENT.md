# CHANGELOG_AGENT — audyt 2026-07-23 (branch `feature/audit-fixes`)

Baza: `1febc93` (v1.7.3, vc16). Cztery commity. Pełny kontekst każdego problemu: `AUDIT_REPORT.md` (ID A1–A25).

## `80c9403` — Fix: undo-archive snackbar cancelled by its own consume signal (P1)

- `ui/list/MaskedEmailListScreen.kt` — id archiwizowanej maski latchowany do lokalnego stanu PRZED `onArchivedConsumed()`; snackbar w osobnym efekcie kluczowanym na latchu; duration Short→Long. (A1)
- `ui/list/MaskedEmailListViewModel.kt` — `restoreMask` z `onFailure` → banner błędu listy. (A8)
- `local.properties` usunięte z indeksu gita (`git rm --cached`; plik roboczy zostaje). (A12 — trafiło do tego commitu przez wspólny staging, powinno być osobno)

## `d94211f` — Fix: billing hardening — listener authority, ack retry cadence, emission order

- `data/repository/ProRepositoryImpl.kt` — buy-flow update bez `pro_lifetime` nie downgrade'uje stanu/cache (autorytatywny o nieobecności jest tylko `queryPurchases`). (A2)
- `data/billing/PlayBillingDataSource.kt` — `tryEmit` synchronicznie z wątku listenera zamiast `scope.launch{emit}` (kolejność emisji). (A16)
- `MainActivity.kt`:
  - `proRepository.refresh()` na każde ON_RESUME, bez gate'a `MONETIZATION_ENABLED` (poprzednio: raz w onCreate, za gate'em). (A3)
  - restore stanu locka tylko z bundle'a TEGO procesu (losowy process-token w `onSaveInstanceState`); obcy bundle → wymuszony unlock. (A4)
  - `rememberNavController()` wyniesiony nad gate locka — back stack i ViewModele ekranów przeżywają cykl lock/unlock; NavHost nadal niekomponowany za lockiem. (A5)
  - obserwator re-locka ON_STOP kluczowany na `appLockEnabled` (flaga DataStore) zamiast `lockActive` — istnieje od pierwszej klatki. **Zmiana zachowania:** re-lock działa też, gdy Pro wygaśnie przy włączonej fladze (privacy-safer; toggle off nadal nie wymaga Pro). (A14)
  - snapshot cache (accent + entitlement) czytany w istniejącym bloku IO i użyty jako initial — bez flasha amberu na zimnym starcie. **Znany wyjątek:** refund w trakcie sesji zostawia akcent do restartu (kosmetyczne). (A15)

## `3e0e123` — Fix: dark-theme accent legibility + selection/toggle semantics (a11y)

- `ui/theme/AccentColors.kt` — `val Accent.color` → `fun Accent.color(darkTheme)` + `onColor(darkTheme)`; dark-warianty: INK #B5AC9A, SAGE #9DBB79, PLUM #D294B4, COBALT #8FB3DC (≥6.3:1 na ciemnych tłach; tekst na akcencie = LightInk ≥5.9:1). AMBER bez zmian. (A6)
- `ui/theme/Theme.kt` — scheme/extras kopiują też `onPrimary`/`onAccent`.
- `ui/settings/SettingsScreen.kt` — pickery akcent/język: `selectable(Role.RadioButton)`; toggle rows: `toggleable(Role.Switch)` + `Switch(onCheckedChange=null)`; swatch akcentu z wariantem motywu; chevron mirrorowany w RTL przez `graphicsLayer` (AutoMirrored.ChevronRight nie istnieje w icons z BOM 2024.09). (A7, A10)
- `ui/pro/ProScreen.kt` — LinkText (privacy/terms): 48 dp min-height + `Role.Button`, wizual bez zmian. (A17)

## `1104112` — Fix: CSV control-char neutralization, sensitive clipboard, security-crypto stable

- `domain/usecase/ExportMasksUseCase.kt` — `\r`,`\n` dodane do `FORMULA_LEAD_CHARS`. (A9)
- `ui/common/Clipboard.kt` — `EXTRA_IS_SENSITIVE` na API 33+, label z `R.string.app_name`. (A11)
- `app/build.gradle.kts` — `security-crypto` 1.1.0-alpha06 → **1.1.0 stable**. (A13)
- Testy (87→90): `MaskCsvTest` (wiodące znaki kontrolne), `ProRepositoryImplTest` („buy-flow OK+empty nie downgrade'uje PRO"), `MaskedEmailListViewModelTest` (błąd restoreMask trafia do UI).

## Zmiany zachowania do świadomego przyjęcia

1. `refresh()` biegnie przy każdym powrocie do foregroundu i również przy `MONETIZATION_ENABLED=false` — kill-switch przestaje blokować rekoncyliację/acknowledgment (zaktualizować `Plans/monetization.md` § Rollback przy okazji).
2. Re-lock przy `appLockEnabled=true` nie wymaga już aktywnego Pro w danej chwili.
3. Undo snackbar trwa ~10 s zamiast ~4 s.
4. Nieudany restore (Undo) pokazuje banner błędu zamiast ciszy.

## Potencjalne regresje (gdzie patrzeć)

- Hoisting NavControllera: rememberSaveable wewnątrz ekranów (nie-VM) nadal ginie przy lock/unlock — stan formularzy jest w VM, więc praktycznie bez skutku; sprawdzić scroll pozycje.
- Re-lock bez warunku Pro: user z wygasłym Pro i włączoną flagą zobaczy LockScreen (unlock działa; toggle off dostępny bez Pro).
- security-crypto stable: ta sama linia API; boot zweryfikowany na emulatorze — na fizycznym urządzeniu update NIE może czyścić danych (keyset zostaje) — zweryfikować po instalacji z internal testing, NIE odinstalowywać apki z telefonu (token!).

## Manual QA przed publikacją (nie do zrobienia w tym środowisku)

1. **Internal testing na OnePlus 13:** update z Play (Play App Signing!), boot, token przeżył update security-crypto, lista działa.
2. **App lock (urządzenie z biometrią):** włącz lock → zminimalizuj → wróć (prompt), unlock → obróć ekran (bez ponownego promptu), unlock → wejdź w create, wpisz coś, zminimalizuj+wróć+unlock → **formularz ma zachowaną treść i back stack** (fix A5).
3. **Billing (license tester):** zakup pro_lifetime E2E, restore na czystej instalacji, symulacja PENDING (metoda płatności odroczona) → dokończenie w tle → powrót do apki → Pro aktywne bez restartu (fix A3).
4. **Akcenty w dark mode:** każdy z 5 akcentów — kursor w search, FAB, ikony w Settings czytelne (fix A6).
5. API 26/27 (jeśli powstanie AVD): process death w tle przy locku → restart → apka ŻĄDA odblokowania (fix A4).
