# Tryst — Architecture

Status: **Live (v1, 2026-06-08)** — reflects the actual code. See [FLOWCHARTS.md](FLOWCHARTS.md) for
visual flows and [CLAUDE.md](../CLAUDE.md) for the stack rationale and hard constraints.

## Stack
Kotlin (JDK 17) · Jetpack Compose + Material 3 · Room + **SQLCipher** · Hilt · Coroutines/Flow ·
**Tink** (media + backup AEAD) · PBKDF2-HMAC-SHA256 (PIN & backup KDF; Argon2id reserved for a future
backup-format version) · AndroidX Biometric · Gradle KTS + version catalog.

`minSdk 31` · `compileSdk`/`targetSdk 36`. Toolchain: AGP 9.2.1 / Kotlin 2.2.10 / KSP 2.3.2 / Gradle 9.5.
Single `:app` module, **package-by-feature**.

## Pattern
- **MVVM + Repository**, unidirectional data flow: Compose screen → ViewModel → Repository → DAO /
  crypto / file source.
- **State exposure:** list/derived screens (History, Insights, Partners) expose `StateFlow` via
  `stateIn(...).catch { }` so they survive the DB closing on auto-lock. The encounter editor VM uses
  per-field `mutableStateOf` (idiomatic for a large form; a single immutable `UiState` refactor is the
  deferred "chunk 6", M8).
- **Domain logic is pure Kotlin and unit-tested** off the UI thread — notably the stats engine
  (`data/stats/InsightsEngine`) and the CSV parser, both JVM-tested without Robolectric.

## Package layout (`app.tryst`)
```
core/
  security/   Vault (DEK double-wrap), SecureKeyStore, BiometricVault, Pbkdf2, SessionKeys
  session/    SessionManager (lock lifecycle, opens/closes the DB), LockState
  crypto/     MediaCrypto (Tink streaming), BackupCrypto (password KDF + AEAD container)
  prefs/      ThemePreferences, InsightsPreferences  (SharedPreferences, non-sensitive)
data/
  db/         TrystDatabase, TrystDatabaseFactory, entities, DAOs, Converters, Migrations, SqlCipherLibrary
  repository/ Encounter / Partner / Position / Act repositories (read DAOs from the unlocked session)
  media/      EncryptedMediaStore (encrypted blobs in app-internal storage)
  backup/     BackupManager (export/restore), Csv (importer)
  stats/      InsightsEngine + Insights model (pure Kotlin)
di/           Hilt wiring (most types use @Inject constructors; module is minimal)
ui/
  common/     SelectionField/Chips, MediaImages, ImagePicker, Format, Position/Act options, PracticeVisuals
  lock/        SetupScreen, LockScreen, LockViewModel, BiometricPromptHelper
  history/     HistoryScreen (list + calendar), HistoryViewModel
  encounter/   EncounterEditScreen + ViewModel
  partner/     PartnersScreen + ViewModel
  insights/    InsightsScreen, charts, StatTiles/InsightSections catalogs, TypeColors, ViewModel
  settings/    SettingsScreen + Appearance/Backup/CsvImport/CustomActs/CustomPositions VMs
  theme/       Color, Theme, Type, Shape (brand purple/green; sleek-dark default)
MainActivity   FragmentActivity; FLAG_SECURE; renders by LockState (Setup / Lock / Unlocked → TrystApp)
```

## Security touchpoints (cross-cutting — see [SECURITY_DESIGN.md](SECURITY_DESIGN.md))
- **`SessionManager`** owns the unlock lifecycle: it builds the SQLCipher DB from the vault DEK on
  unlock, closes it and zeroes the DEK on lock, and gates auto-lock (with a one-shot grace for the
  photo-picker/camera handoff). Repositories and the media store read from it — there is no DB or key
  in memory while locked.
- The **`Vault`** double-wraps the DEK (Keystore key + PIN via PBKDF2) and self-wipes after 10 fails.
- A single audited crypto layer (`core/crypto`, Tink); feature code never rolls its own crypto.
- `MainActivity` is the only `Activity` and sets `FLAG_SECURE`.

## Navigation
Compose Navigation. Bottom-nav top destinations: **Trysts** (history/calendar), **Insights**,
**Partners**, **Settings**. Plus the encounter editor (`encounter/new`, `encounter/{id}`) and the
Insights customizer sub-screen (`insights/customize`, reached from Settings, with a back arrow). The
whole graph is gated by the lock screen in `MainActivity`.

## Testing strategy
- **JVM unit:** stats engine (`InsightsEngineTest`), Insights catalogs (`StatTilesTest`,
  `InsightSectionsTest`), CSV parser (`CsvParseTest`).
- **Instrumented (emulator, real Keystore/SQLCipher):** vault, DB-encrypted-on-disk, media crypto,
  session lifecycle, Room migrations (v1→v6), media attachment round-trip, backup round-trip.
- **CI anti-leak guard:** fails the build if the *merged* manifest declares any network permission;
  runs locally (`checkNoNetwork*`) and in CI.

## Quality gates (planned for M8)
Detekt/ktlint, Android Lint, FOSS-only dependency check. Current builds pass with only benign legacy
variant-API / deprecation warnings (see `gradle.properties`).
