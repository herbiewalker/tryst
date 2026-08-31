# Tryst — Architecture

> **Status:** Live — v0.5.2 / schema v15. Reflects the actual code. See [FLOWCHARTS.md](FLOWCHARTS.md) for visual
> flows and [CLAUDE.md](../CLAUDE.md) for the stack rationale and hard constraints.

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
  `stateIn(...).catch { }` so they survive the DB closing on auto-lock. The encounter editor VM exposes
  a single immutable `EncounterEditUiState` (`var uiState by mutableStateOf(...)`, `private set`; all
  edits go through `copy()`-ing update methods).
- **Domain logic is pure Kotlin and unit-tested** off the UI thread — notably the stats engine
  (`data/stats/InsightsEngine`) and the CSV parser, both JVM-tested without Robolectric.

## Package layout (`app.tryst`)
```
core/
  security/   Vault (DEK double-wrap), SecureKeyStore, BiometricVault, Pbkdf2, SessionKeys
  session/    SessionManager (lock lifecycle, opens/closes the DB), LockState
  crypto/     MediaCrypto (Tink streaming), BackupCrypto (password KDF + AEAD container)
  prefs/      ThemePreferences, InsightsPreferences, GeneralPreferences (auto-lock/haptics/week-start/last-seen-version), GalleryPreferences (tryst_gallery: layout/columns/sort/blur/slideshow) — all SharedPreferences, non-sensitive, excluded from backup
data/
  db/         TrystDatabase, TrystDatabaseFactory, entities, DAOs, Converters, Migrations, CatalogAdoption (v10–v12 removed-id → custom adoption for acts/kinks/positions/toys/occasions/ejaculation-locations; also run post-restore), CatalogSeeds (v12 fresh-install + upgrade seeding of the neutral starter rows), SqlCipherLibrary
  repository/ Encounter / Partner / Profile / Position / Act / Kink / Toy / Occasion / EjaculationLocation / PersonPhoto repositories (read DAOs from the unlocked session)
  filter/     EncounterFilter + matches() + EncounterQuery (shared JVM-tested filter layer — Search, Insights scope, Gallery all consume it)
  search/     EncounterSearch (accent-folded, AND-across-tokens over note + partner names + resolved category labels)
  gallery/    GalleryPhotos.build (pure fn folding encounter photos + person_photo portraits into the gallery pipeline), GalleryModels
  media/      EncryptedMediaStore (encrypted blobs + staging support for atomic restore), PhotoMetadata (androidx.exifinterface EXIF read at view time)
  backup/     BackupManager (export/restore, atomic wipe+restore in one tx, PRAGMA table_info NOT-NULL backfill), Csv (importer)
  stats/      InsightsEngine + Insights model (pure Kotlin, scope-aware)
di/           Hilt wiring (most types use @Inject constructors; module is minimal)
ui/
  common/     SelectionField/Chips, MediaImages, ImagePicker, Format, Position/Act/Kink/Toy options, ActVisuals, Haptics (LocalHapticsEnabled), WindowSize, AppVersion, DateScopeChips (shared by Search + Insights scope), EncounterCard, DateHeader, PersonPhotoStrip
  lock/        SetupScreen, LockScreen, ChangePinScreen, LockViewModel, BiometricPromptHelper, PinPad
  history/     HistoryScreen (list + calendar), HistoryViewModel
  encounter/   EncounterEditScreen + ViewModel
  partner/     PartnersScreen (+ "You" profile card) + ViewModel; PartnerEditScreen + PartnerEditViewModel (full-screen editor, QOL-3)
  profile/     ProfileScreen + ViewModel (the user's own photo + demographics; single self row)
  search/      SearchScreen + SearchViewModel; MoreFiltersSheet (the advanced-dims sheet, shared with the gallery's filters)
  gallery/     GalleryScreen + GalleryViewModel; PhotoViewer (pinch-zoom + info panel + add-to-person + set-as-avatar + slideshow); GalleryFiltersSheet; GallerySettingsScreen; AvatarViewer
  insights/    InsightsScreen, charts, StatTiles/InsightSections catalogs, TypeColors, ViewModel
  settings/    SettingsScreen (General/Security/Appearance/Insights/Categories/Backup/Danger/About) + ResetDataScreen (type-to-confirm wipe) + Appearance/General/Backup/CsvImport/CustomActs/CustomKinks/CustomPositions/CustomToys/CustomOccasions/CustomEjaculationLocations VMs
  whatsnew/    ReleaseNotes (bundled notes), WhatsNewScreen + WhatsNewDialog (post-update popup)
  theme/       Color, Theme, Type, Shape (brand purple/green; sleek-dark default)
MainActivity   FragmentActivity; FLAG_SECURE; renders by LockState (Setup / Lock / Unlocked → TrystApp)
```

## Security touchpoints (cross-cutting — see [SECURITY_DESIGN.md](SECURITY_DESIGN.md))
- **`SessionManager`** owns the unlock lifecycle: it builds the SQLCipher DB from the vault DEK on
  unlock, closes it and zeroes the DEK on lock, and gates auto-lock (with a one-shot grace for the
  photo-picker/camera handoff). Repositories and the media store read from it — there is no DB or key
  in memory while locked.
- The **`Vault`** double-wraps the DEK (Keystore key + PIN via PBKDF2) and self-wipes after 10 fails.
  **Change PIN** verifies the current PIN with a non-counting `verifyPin` and re-wraps the in-memory DEK
  via `reprotect` — never through the wipe path, so it can't lose data.
- **Auto-lock delay** is user-configurable (`GeneralPreferences`, default immediate): `SessionManager`
  schedules/cancels a process-scoped delayed `lock()` across background/foreground.
- A single audited crypto layer (`core/crypto`, Tink); feature code never rolls its own crypto.
- `MainActivity` is the only `Activity` and sets `FLAG_SECURE`.

## Navigation
Compose Navigation (adaptive: bottom bar on compact, nav rail on medium/expanded). Top destinations:
**Trysts** (history/calendar), **Insights**, **Partners**, **Settings**. Plus the encounter editor
(`encounter/new`, `encounter/{id}`), the Insights customizer (`insights/customize`), the Achievements
screen, the About screen, the **Change-PIN** flow (`change-pin`), the **reset-all** page
(`settings/reset`), the **What's-new** screen (`whats-new`), and the **self-profile** editor
(`profile`, reached from both Settings → Your profile and the "You" card on Partners) — the Settings
sub-pages all reached from Settings. The whole graph is gated by the lock screen in `MainActivity` (which also provides
`LocalHapticsEnabled` around setup/lock/unlocked). On entering the unlocked shell, `TrystApp` fires the
one-time **post-update What's-new popup** (installed `versionCode` vs `GeneralPreferences.lastSeenVersionCode`).

## Testing strategy
- **JVM unit:** stats engine (`InsightsEngineTest`), Insights catalogs (`StatTilesTest`,
  `InsightSectionsTest`), CSV parser (`CsvParseTest`).
- **Instrumented (emulator, real Keystore/SQLCipher):** vault, DB-encrypted-on-disk, media crypto,
  session lifecycle, Room migrations (v1→v15, incl. the v7→v8/v9→v10/v10→v11/v11→v12 data migrations plus additive v13/v14/v15 DDL), media attachment round-trip, backup round-trip, and
  backup/restore regression edge cases (`BackupRestoreRegressionTest`: restore-over-existing,
  restore-after-delete-all-data, partner-avatar-survives — the paths that produced the Pass-12 data-loss
  bugs).
- **CI anti-leak guard:** fails the build if the *merged* manifest declares any network permission;
  runs locally (`checkNoNetwork*`) and in CI.

## Quality gates (M8, live — see DECISIONS D-30)
Build-failing **Detekt** (1.23.8, AST-only) + **ktlint** (ktlint-gradle 14.2.0) run in a dedicated CI
`quality` job (`config/detekt/detekt.yml` + `.editorconfig`, curated to fit idiomatic Compose).
**Android Lint** already runs in CI (`lint`); the FOSS guard is the hand-maintained `OssLicenses` + the
CI banned-SDK grep (no license plugin). Builds pass with only benign legacy variant-API / Kotlin-2.x
annotation-use-site / deprecation warnings (see `gradle.properties`).
