# CHANGELOG_AGENT — audyt 2026-07-24

Baza: `dfe4b2f` (v1.8.0, vc17, main). Jeden commit.

## Zmienione pliki

| Plik | Zmiana |
|------|--------|
| `ui/components/ErrorMessage.kt` | Modifier przed onRetry (lint ModifierParameter) |
| `di/NetworkModule.kt` | `@OptIn(ExperimentalSerializationApi::class)` na `provideJson()` |
| `ui/detail/MaskedEmailDetailScreen.kt` | Usunięta nieużywana zmienna `context`, usunięty niepotrzebny `!!`, usunięty dead code `email1 != null` |
| `ui/settings/SettingsScreen.kt` | Usunięta nieużywana zmienna `extras` z `LanguagePickerDialog` |
| `ui/list/MaskedEmailListScreen.kt` | Brak zmian (przywrócone po analizie — parametry wymagane przez SharedTransitionLayout API) |
| `app/src/test/.../MaskedEmailTest.kt` | **NOWY** — 7 testów regresyjnych dla modelu domenowego |

## Naprawione problemy

| ID | Prio | Problem | Poprawka |
|----|------|---------|----------|
| B1 | P2 | Modifier na końcu parametrów (lint) | Kolejność parametrów zgodna z konwencją Compose |
| B2 | P2 | Brak opt-in dla ExperimentalSerializationApi | Dodano `@OptIn` |
| B3 | P3 | Nieużywana zmienna `context` w DetailContent | Usunięto |
| B4 | P3 | Niepotrzebny `!!` na non-null receiver | Usunięto |
| B5 | P3 | Dead code: `email1 != null` zawsze true | Usunięto pośrednią zmienną |
| B6 | P3 | Nieużywana zmienna `extras` w LanguagePickerDialog | Usunięto |

## Dodane testy

- `MaskedEmailTest.kt` — 7 testów: displayName hierarchy, isActive per EmailState

## Wyniki

- 99 testów (92 → 99), 0 porażek
- lint: 0 ModifierParameter, 0 ExperimentalSerializationApi
- Build debug + release: SUCCESS

## Potencjalne regresje

- Zmiana kolejności parametrów w `ErrorMessage` może wpłynąć na wywołania z named arguments — wszystkie istniejące wywołania używają positional arguments, więc bezpiecznie.

## Elementy wymagające manualnego QA

- Sprawdzić, czy `ErrorMessage` renderuje się poprawnie na wszystkich ekranach (login error, detail error, list error)
- Sprawdzić, czy export CSV nadal działa (zmiany nie dotykają eksportu)
