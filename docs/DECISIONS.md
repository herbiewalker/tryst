# Tryst — Decision Log

> **Status:** Live — decisions through **v0.3.1 / schema v11** (latest: **D-41**, the F-Droid
> content-policy rework — acts/kinks in 0.3.0, then positions/toys in 0.3.1). Lightweight ADR log;
> entries are numbered D-1… ascending, so the **newest are at the bottom**. "Open" items still need a call.

## Decided (from scoping conversation, 2026-06-04)

- **D-1 User model:** Solo user with multiple partners (named or anonymous), per-partner
  stats. No accounts, no sync, no second device.
- **D-2 Threat model:** Protect against (a) someone holding the phone, (b) device
  seizure/forensics, (c) any network leakage. Disguise/decoy mode deferred.
- **D-3 Privacy posture:** **No `INTERNET` permission**; no analytics/ads/crash SDKs;
  `allowBackup=false`; `FLAG_SECURE`.
- **D-4 Encryption at rest:** SQLCipher DB + AES-GCM-encrypted media in app-internal storage.
- **D-5 Entry data:** Rich details + photo attachments.
- **D-6 Insights:** Stats + charts + achievements/badges (all local).
- **D-7 Backup:** Manual, user-initiated **encrypted** export/import only.
- **D-8 Platform/stack:** Kotlin, Compose/Material 3, Room+SQLCipher, Hilt,
  **`minSdk 31` (Android 12)** / `targetSdk 36` (Android 16).
- **D-9 App name & package:** **Tryst** / `app.tryst`. (Prefix with a personal domain/handle
  later if publishing.)
- **D-10 (M1) Key behind an interface:** all data-at-rest keys come from `DatabaseKeyProvider`.
  M1 binds a clearly-labeled `InsecureDevKeyProvider` placeholder so the storage layer can be
  built/tested; the real implementation (O-1) swaps only that one Hilt binding at M2.
- **D-11 (M1) Media encryption:** Tink `AesGcmHkdfStreaming` (AES-256-GCM-HKDF, streaming),
  built directly from key material — no Tink keyset/Keystore management until M2.
- **D-12 (O-1 resolved) Key model = Keystore-only.** Random DEK wrapped by a hardware-backed
  Android Keystore key (StrongBox when available) layered with a key derived from a **distinct
  6-digit app PIN** (separate from the device lock). Biometric unlock via the Keystore (M2b).
  Chosen for UX over the recommended passphrase model; see [SECURITY_DESIGN.md](SECURITY_DESIGN.md)
  §1 for the design + residual risk (short-PIN brute force).
- **D-13 Quick unlock:** biometric (M2b) with a **6-digit app PIN** fallback; the PIN is distinct
  from the device PIN so someone who knows the phone's lock still can't open Tryst.
- **D-14 Auto-lock:** lock on background by default (immediate); timeout is user-configurable.
- **D-15 PIN KDF:** PBKDF2-HMAC-SHA256 (high iteration count) for M2a to avoid a native-lib
  dependency on the new AGP 9 toolchain; abstracted so it can be upgraded to Argon2id later.
- **D-16 (M3+) Expanded encounter fields (schema v2):** per-person **orgasm counts**
  (self/partner, replacing the single who-finished enum — the legacy `orgasm` column is kept,
  unused, for migration safety), **ejaculation locations** (multi), and **practices
  performed/received** (two multi-selects over a `Practice` enum). Expanded `Mood` and
  `Protection` option sets. Delivered via the project's **first Room migration** (v1→v2,
  additive nullable columns), validated by an instrumented `MigrationTest` against the exported
  schemas. New set columns are nullable to keep the migration default-free (avoids Room's
  NOT-NULL-default schema-validation mismatch).
- **D-17 (M3+) Positions + pop-out selectors (schema v3):** `positions` column (migration v2→v3,
  `MIGRATION_2_3`) stores **string IDs** — a built-in `Position` enum name or `custom:<uuid>` — so
  custom positions can be mixed in. Custom positions are user-managed `PositionEntity` rows
  (`isBuiltIn=false`) via `PositionRepository`, added/removed in **Settings → Manage custom
  positions**, and merged with built-ins in the editor's Positions picker. Editor category
  selectors use `MultiSelectField`/`SingleSelectField` (ui/common): **inline shows the curated
  common set until something is selected, then only the selections** (+ "More…" dialog with the
  full set **alphabetical** by label). `MigrationTest` validates v1→v3. (The M1 position cross-ref
  relation is unused; kept for migration safety.)

- **D-18 (M3+) Category restructure + display labels (schema v4):** "Practices" split into
  **Acts** (gave/received, `Practice` enum), **Kink & BDSM** (`Kink`), **Setting & context**
  (`Setting`), and **Toys** (`ToyType`) — each its own nullable column (kinks/contexts/toys;
  `MIGRATION_3_4`). Every category enum implements **`DisplayLabel`** with explicit human-written
  labels (fixes IUD/PrEP/PEP/DoxyPEP/69 casing, "Birth control"→"Pill", "Gave/Received" wording),
  shown in the UI via `it.label`. Added moods (tipsy, confident, desired, loved, safe…), acts
  (titjob, anal fingering, spit play, face-fucking), the full setting list, and toy types. Enum-set
  converters now **skip unknown names**, so values that moved categories don't crash older rows.
  `MigrationTest` validates v1→v4.

- **D-19 (cleanup, 2026-06-06):** PBKDF2 iterations **200k → 600k** (OWASP; per-vault `iter` so it's
  back-compatible). Dependency refresh (Hilt 2.57.1, Compose BOM 2026.04.01, lifecycle 2.10.0,
  activity 1.12.4, coroutines 1.11.0, navigation 2.9.0, coreKtx 1.16.0). **Room pinned at 2.7.1** —
  2.8+ `room-testing` needs a newer kotlinx-serialization than the Kotlin 2.2.10 toolchain ships
  (breaks `MigrationTest`); bump with the next Kotlin upgrade. Deprecated `kotlinOptions` →
  `kotlin { compilerOptions }`.
- **D-20 (M3.1, schema v5):** **Partner** gains sex / gender / relationshipType (enums) + a
  `photoMediaId` hook for M4 photos. **Custom Acts** added (new `acts` table mirroring `positions`;
  practices now stored as **string IDs** — built-in `Practice` name or `custom:<uuid>` — gave/received,
  managed in Settings). **Setting & Context** split into **Setting & Location** (places only) + a new
  **Occasion** category; threesome/group/swinging moved to **Kink**. **Theming:** brand purple/green
  Material 3 palette as default with a **Material You** toggle and **Light/Dark/System** mode,
  persisted in `core/prefs/ThemePreferences` (plain SharedPreferences — non-sensitive). `MigrationTest`
  v1→v5.
- **D-21 (M3.2):** Trysts card badge switched from ambiguous emoji to **custom per-act vector
  drawables** (`res/drawable/ic_act_*.xml`), chosen by a full intensity ranking
  (`ui/common/PracticeVisuals`). Added a **calendar view** toggle (month grid; each day shows its
  headline act icon). Icons are single tintable colour, so they can be **swapped for more realistic
  artwork later** with no code change. `rankedActs()` already supports a future top-2 badge.
- **D-22 (M3.3, schema v6):** **Per-partner orgasm counts** (`partnerOrgasms` column = partnerId→count,
  one counter per selected partner labelled by name; legacy `orgasmCountPartner` kept) and
  **per-orgasm ejaculation** (the `ejaculationLocations` column repurposed to orgasmIndex→location —
  your self-orgasm count drives N single-select ejaculation rows). Added Blowjob, Ball-sucking,
  Cunnilingus, Clit-sucking acts. `MigrationTest` v1→v6.
- **D-25 (M5):** **Encrypted backup** = a password-derived key (PBKDF2-HMAC-SHA256, 600k; header
  carries salt+iters so Argon2id can come later) over a **Tink-streamed ZIP** of `data.json` (every
  table, generic column dump) + decrypted media. Re-encrypt model (not raw-DB copy) because the live
  DEK is device-bound. Restore re-encrypts media under the new device's key, repoints `encFilePath`,
  `INSERT OR REPLACE` with deferred FKs. **Export is encrypted-only** (no plaintext export) to keep the
  no-unprotected-data promise. Files via SAF; auto-lock suppressed across the handoff. Format in
  `docs/EXPORT_FORMAT.md`. **Importing other apps' data (Intimacy/LoveLust/etc.) = M5b**, a separate
  generic **CSV importer with column mapping** (no shared standard between trackers).
- **D-26 (M6.1):** **Insights UX lift + global "dark & moody" polish.** (1) One global `ChartStyle`
  (Bars/Line/Donut) drives every chart with per-chart graceful fallback (donut is meaningless for an
  ordered time series, line for categories) — simplest "pick a look" mental model; charts stay
  hand-drawn (extends D-25). (2) Overview stat boxes are customizable via an **in-place edit mode**
  (not a separate screen) with long-press drag-reorder + per-tile show/hide; a Settings row deep-links
  in (`insights?edit=true`). Layout (order + hidden) and chart style persist in `InsightsPreferences`
  (SharedPreferences, mirrors `ThemePreferences` — non-sensitive, excluded from backup). Tiles have
  **stable ids** (`StatTiles` catalog) so saved layouts survive adding/reordering tiles in future
  versions. (3) Global polish is done by **refining the design tokens** (`Color`/`Type`/`Shape` +
  Material nav icons) rather than editing each screen — every screen inherits the new look through
  `MaterialTheme`. Theme default stays SYSTEM; the dark scheme is the headline look. Reorder is a
  reliable single-column drag list (full free-form grid drag deferred).
- **D-27 (M6.2):** **Per-type chart colors = deterministic `colorFor(label)`** (`ui/insights/TypeColors.kt`),
  not rank-based. Color is a pure function of the value's label (`String.hashCode` → curated vivid
  dark-bg palette, seeded with the reference app's pink/blue/violet, bit-mixed, cycling). Rationale:
  the requirement is that a type reads as the **same color across every card** (rank-based would change
  a label's color per chart) and **expands automatically** as new acts/partners/etc. appear (no manual
  registry). Trade-off: with an unbounded category set the palette cycles, so two labels can collide on
  a hue — acceptable. Applied to ranked bars + donut (breakdowns); trend charts (time axis) stay
  single-accent. A stacked monthly-by-category chart (per the reference) is deferred — it needs an
  act→high-level-category taxonomy Tryst doesn't have.
- **D-28 (M7):** **Achievements are fully derived, not persisted.** `AchievementEngine` replays the
  encounter log to compute each achievement's progress and a derived `unlockedAt` (the date the metric
  first crossed the target), exactly like `InsightsEngine`. Rationale: no Room migration, privacy-clean
  (no plaintext "unlocked X" markers), and it can never drift from the data. Trade-off: no persistent
  "acknowledged" state, so there's **no one-time "just unlocked!" celebration** in v1 — instead a derived
  "New" ribbon flags unlocks within ~14 days. A real celebration would store acknowledged ids in the
  encrypted DB (future). **Placement:** a dedicated Achievements screen (trophy icon in the Insights top
  bar) + a teaser card in the Insights scroll — chosen over a 5th nav tab to keep the bottom nav at four.
- **D-29 (pre-release Pass 10, 2026-06-11):** **License = GPLv3** (resolves O-2). Chosen over
  MIT/Apache because copyleft best serves the project's "open source so anyone can verify the privacy
  claims" thesis — a redistributed binary must ship its (matching) source, so the no-network /
  encrypted-at-rest guarantees stay auditable downstream. Artifacts: full `LICENSE` (GPLv3 text) at the
  repo root, `THIRD_PARTY_NOTICES.md`, and an in-app **Settings → About** open-source licenses screen
  (`ui/about/`). Pass 10's dependency audit found **no CVEs and no version bumps needed**, and every
  dependency is GPLv3-compatible (all Apache-2.0 except JSR-305 BSD-3 and SQLCipher's Zetetic BSD-style;
  SQLCipher bundles OpenSSL 3.x = Apache-2.0, so no GPL/OpenSSL conflict). Per-file source license headers
  were **not** added (82 files) — README + LICENSE + notices satisfy the obligation; headers remain an
  optional follow-up. Distribution (F-Droid / Play) settled in **D-32**.
- **D-32 (M8 release prep, 2026-06-12):** **Distribution = F-Droid only** (resolves the distribution
  half of O-2). Chosen over Google Play (and over "both") because F-Droid is the principled home for a
  FOSS, privacy-first, zero-network app: it builds from source on F-Droid's own infra (so the published
  binary is verifiably the audited source — reinforcing the D-29 "verify the privacy claims" thesis),
  reaches the privacy-conscious audience that browses F-Droid, has no Google-Play-Services / Play
  Integrity dependency (which the no-`INTERNET` constraint rules out anyway), and avoids the GPLv3-vs-Play
  Terms §6 "further restrictions" friction plus Play's content-policy review exposure for an
  intimacy/sexual-wellness app. Cost accepted: slower update cadence and maintaining the submission
  metadata ourselves. Artifacts: **`fastlane/metadata/android/en-US/`** (title / short + full
  description / changelog) in-repo for F-Droid to ingest, and **`docs/RELEASE.md`** documenting the
  F-Droid submission + signing/tagging process. The release build stays **unsigned in-repo** (F-Droid
  signs its own builds); the gitignored `keystore.properties` path is for any future self-distributed
  APK, not F-Droid. Google Play remains a possible later addition if reach demands it — not pursued now.
- **D-31 (M8, 2026-06-12):** **Configurable auto-lock timeout** (Settings → General), default
  **immediate**. A process-scoped delayed `lock()` is scheduled on background and cancelled on
  foreground (`SessionManager.onAppBackgrounded`/`onAppForegrounded` + `GeneralPreferences`), so the DEK
  lives in memory only up to the chosen delay (Immediately / 30s / 1m / 5m). A non-zero value weakens the
  immediate-background-lock guarantee — see THREAT_MODEL R-LOCK; surfaced inline in the setting's
  description. Default keeps current behaviour. Same commit added the **General** settings section
  (app/how-it-works blurb, haptics toggle, calendar week-start) and the **Change PIN** flow.
- **D-30 (M8 quality gates, 2026-06-12):** **CI quality gates = Detekt + ktlint** (resolves O-5).
  Build-failing `detekt` (1.23.8, **AST-only** — no type resolution, so its Kotlin-2.0.21 frontend
  analyses the 2.2.10 source without a compiler-classpath mismatch) + `ktlint` (ktlint-gradle 14.2.0),
  run in a dedicated CI `quality` job. **Curated/pragmatic** config (`config/detekt/detekt.yml`,
  `.editorconfig`): rules that fight idiomatic Compose / deliberate patterns are tuned off — MagicNumber
  (dp/sp literals), TooManyFunctions (DAOs/Room Converters/VMs), `@Composable` naming + complexity, and
  `TooGenericExceptionCaught`/`SwallowedException` (Pass 7's intentional catch-broad → user-message,
  don't-leak-details handling). Line length is delegated/disabled (the achievement data-table rows).
  **Fixed all violations, no baseline** (cohesive complex functions carry a documented site `@Suppress`).
  **No license-aggregation plugin** — Pass 10's hand-maintained `OssLicenses` + the CI banned-SDK grep
  cover the FOSS guard, consistent with the no-extra-deps ethos. **Android Lint** already ran in CI
  (`lint`), left as-is. No stock rule enforces "no hardcoded Compose strings" — noted as a possible
  future custom Detekt rule.
- **D-33 (2026-06-13) Unsaved-changes guard on the editor forms.** The partner add/edit `AlertDialog`
  dismissed on an outside-scrim tap **and** on back-press, and `onDismissRequest` threw away all
  in-memory state — so a stray tap past the soft keyboard (reaching for Save) wiped typed fields and a
  **just-taken camera photo**; the full-screen encounter editor had the analogous loss on the
  predictive-back swipe. Decision: **disable outside-tap dismissal** on the partner dialog
  (`DialogProperties(dismissOnClickOutside = false)`) and route back/Cancel through a **"Discard
  changes?"** confirmation **only when the form is dirty** (an untouched form still closes silently).
  The encounter editor gets a `BackHandler(enabled = dirty)` + the same prompt on the Cancel chevron;
  dirtiness is `EncounterEditViewModel.hasUnsavedChanges()` = current `uiState` ≠ a baseline snapshot
  captured at load. Chosen over "never prompt, never dismiss accidentally" because the explicit prompt
  is the platform-standard, least-surprising behaviour and keeps predictive-back animation on a clean
  form (the `BackHandler` stays disabled until something is touched).
- **D-34 (2026-06-13) Reset-all moved to its own page, type-to-confirm gated.** "Delete all data" was a
  red button one tap deep in the main Settings scroll behind a single yes/no dialog — too easy to fire
  an irreversible wipe. Decision: move it to a dedicated `settings/reset` destination (`ResetDataScreen`)
  whose erase button stays **disabled until the user types the confirmation word `DELETE`**
  (case-insensitive), with a reminder to export a backup first. Chosen over hold-to-confirm / a second
  dialog because typing is the strongest, most deliberate guard (the GitHub/Google "danger zone"
  convention) for an unrecoverable action. The wipe still calls `LockViewModel.deleteAllData()` →
  `SessionManager` → `NeedsSetup`, which tears the nav graph back to first-run, so no post-wipe
  navigation is needed.
- **D-35 (2026-06-13) In-app "What's new" = bundled notes + post-update popup.** Ship release notes
  **bundled in the binary** (`ui/whatsnew/ReleaseNotes.kt`) — no fetch, consistent with the no-network
  constraint — surfaced both as a browsable **What's new** screen (Settings → About, route `whats-new`)
  and a **one-time popup** on the first launch after a `versionCode` increase. The trigger compares the
  installed `versionCode` (read via `PackageManager`, so no `BuildConfig` build-feature) against
  `GeneralPreferences.lastSeenVersionCode` (default `0`); a **fresh install shows nothing** (the `0`
  sentinel) and just records the current code, so only genuine updates announce. Notes are kept in sync
  across three places — `ReleaseNotes.all`, the F-Droid `fastlane/.../changelogs/<versionCode>.txt`, and
  the repo `CHANGELOG.md` — documented in RELEASE.md and the `ReleaseNotes` KDoc. **No version bump** in
  this change: it folds into the still-unreleased v0.1.0, so the popup first fires on the eventual v2.
- **D-36 (2026-06-13, schema v7) Partner demographics + a self profile.** Added a "standard" demographic
  set — **date of birth → derived age, ethnicity, height, body type, location** — to partners, and a new
  single-row **`profile`** table giving the user their **own** photo + the same demographics (the app had
  no concept of "you" before). Field-style choices mirror the existing pattern: `Ethnicity` / `BodyType`
  are `DisplayLabel` enum chips (like `Sex`/`Gender`, translation- and stats-ready), while height/location
  are free-text and DOB is a date picker. **Height is free-text** (e.g. `5'10"` or `178 cm`) rather than a
  canonical unit — avoids a unit-conversion UX for a field with no stats use yet. **DOB is stored as
  local-noon millis** for the picked calendar day (the Material picker returns UTC-midnight, which renders
  as the day before in behind-UTC zones), normalised on the way in/out. Storage = the **encrypted DB**
  (demographics are sensitive — not SharedPreferences); the profile photo reuses the encrypted media store
  like a partner avatar, and the backup gathers its blob id explicitly (same fix shape as the Pass-12
  partner-avatar gap). Migration **`MIGRATION_6_7`**: additive nullable columns on `partners` + a
  `CREATE TABLE profile`; `MigrationTest` extended to v1→v7. Profile reached from **both** Settings → Your
  profile and a pinned **"You" card** atop Partners (D-33's discard guard applies to both editors). The
  shared `DemographicFields` + `OptionalChips` composables keep the partner and profile editors identical.
- **D-37 (2026-06-14, post-v0.1.0) Ejaculation location is multi-select per orgasm.** A single orgasm can
  land in more than one place (e.g. *on chest **and** stomach*), so the per-orgasm value changed from one
  `EjaculationLocation` to a **`Set<EjaculationLocation>`** (`ejaculationLocations: Map<Int, Set<…>>`), and
  the editor field became a `MultiSelectField`. **No DB migration:** the column is plain `TEXT` and its
  app-side encoding had already moved Set→Map at v5→v6 without a SQL change, so this Map→Map-of-Sets move is
  likewise encoding-only. The converter stays **backward-compatible** — entries join on `,` (SEP) and the
  per-orgasm locations on `|`, so legacy single-value rows (`0=ON_CHEST`, no `|`) parse into a one-element
  set. Insights/Achievements `flatten()` the sets before tallying. Also added `IN_SHOWER` ("In the shower")
  for solo-in-the-shower, and renamed the kink label `Costume / dress-up` → `Lingerie / dress-up` (display
  label only; the persisted enum name `COSTUME_PLAY` is unchanged, so existing entries keep their data).
- **D-38 (2026-06-14, post-v0.1.0) Calendar redesign — tonal chips + activity heatmap, fixed size, month/week.**
  The month grid was bland (transparent cells, just a number). Reworked `DayCell` into a filled rounded
  **tonal chip** (`surfaceContainerHigh`) that, on days with encounters, fills toward `primary` by the
  day's **encounter count** — a heatmap (1/2/3+ → 42%/65%/85% blend; text/icon flip to `onPrimary` on the
  2+ and selected fills). Intensities are deliberately strong because the typical day has exactly one
  encounter, so even one must read clearly vs an empty day. Day number is bold `titleMedium`, the act icon
  grew 18→30dp. The grid is now a **fixed height** (72dp rows) rather than filling the screen, so it stays
  the same size whether or not a day is selected; the selected day's trysts occupy the flexible, scrolling
  area below (the calendar screen itself doesn't scroll). Added a **Month/Week segmented toggle**
  (`CalView`): week view is one tall row (116dp) over the day's trysts, with a "MMM d – d" range title and
  ±1-week nav. **Swipe** left/right on the grid pages the period (D-/QOL-1). Today keeps its outline ring
  (QOL-2), selected keeps the solid pill. No data/schema impact — purely presentational, driven by a new
  per-day count derived from the existing log.
- **D-39 (2026-06-15, F-Droid review) Reproducible builds DECLINED — F-Droid signs (permanent).** In the
  first fdroiddata review the maintainer asked to add `Binaries` + `AllowedAPKSigningKeys` for reproducible
  builds; we declined ("No, I don't want this." in the App-inclusion template). Tryst deliberately holds **no
  signing key** (no committed keystore, no signing config — D-32, RELEASE.md "Why no signing config"); F-Droid
  building **and signing** from source is the trust model, and F-Droid is the only channel, so there is no
  separately self-signed APK to reconcile. For a solo maintainer, long-term custody of a release private key
  is itself a liability against the threat model. **This is irreversible:** F-Droid now signs with its own key
  and the app cannot be switched to developer-signed / reproducible later. Enabling it would have meant owning
  a release keystore forever and publishing our own signed APK each release.
- **D-40 (2026-06-21, 0.2.0 / schema v8) Category cleanup + first data-only migration; release-gap closed.**
  Bundled a fix batch with the already-on-`main` post-tag features (D-37/D-38) into **0.2.0 / versionCode 2**
  — the first release after 0.1.0, closing the gap where features sat on `main` undelivered (F-Droid pins the
  tag). The fixes change **category membership**, which the enum-name storage model (`Converters` store enum
  `name`s, not labels) makes safe: **pure label renames need no migration** ("Oral - Kneeling/Standing/Laying
  down"; "Ball sucking / ball play"; `ANAL_TOY` displays "Anal - Toy"). Value moves need **`MIGRATION_7_8`** —
  the first **data-only** migration (no DDL): delete `Position.ORAL_69_SIDE` → remap refs to `LYING_ORAL`;
  move `WATCHING_PORN` from `Practice` (acts) → `Kink` (add to `kinks`, strip from both practice columns).
  Plus additive enum values: new built-in `Position`/`Practice` options and `Setting.FRIENDS_FAMILY` ("Friend
  / family's place") — additive, so any pre-existing custom entry with a similar name just stays custom.
  **Haptics fix:** every `performHapticFeedback` now passes `FLAG_IGNORE_VIEW_SETTING` — the bare call was
  silently swallowed by the host View's haptic flag, so the in-app toggle did nothing (device-level "vibrate
  on touch" still wins). ⚠️ Backup **restore inserts rows raw and does NOT replay migrations**
  (`BackupManager`) — re-export after upgrading or old values return on a future restore.
  **Migration-safety verification:** because this rewrites stored values, the maintainer verified it against a
  real **decrypted backup** before installing — confirming the live labels matched and the rewrite was a
  faithful transform (deep-diff: only intended fields change). The same **decrypt → edit `data.json` (Gson,
  `serializeNulls` for a faithful superset; deep-diff) → repack → restore** flow is the pattern for any
  out-of-band bulk data fix on the user's own device — tooling lives outside the repo, not in app code.
  *(An earlier draft of `MIGRATION_7_8` hard-coded the maintainer's own custom-entry labels to auto-promote
  them to built-ins; that one-off ran once on the maintainer's device and was then removed from the code so
  no personal labels ship in the public migration — the built-in options remain as generic app choices.)*
  **Bulk data edits (out-of-band):** for retroactive note-based tagging (e.g. add a kink to every encounter
  whose note mentions some keyword), the pattern is **decrypt backup → edit `data.json` (Gson,
  `serializeNulls` for a faithful superset; deep-diff to prove only intended fields change) → repack →
  restore in-app** — tooling in `IntimacyData/tools` (`Unpack/Repack/EditTrystBackup`). Not app code; a data
  operation on the user's device.
- **D-41 (2026-06-29, F-Droid policy) Make acts & kinks user-configurable; ship F-Droid without a predefined
  explicit catalog.** F-Droid reviewer (linsui) flagged the bundled explicit **acts/kinks** as non-compliant
  and asked to "make them configurable instead." **Chosen path (Strategy 2):** one clean app — no build
  flavors — where explicit content is **user data**, not compiled-in. The maintainer is fine running the
  F-Droid build; the requirement is zero data loss + **full functionality** (search/insights/achievements) on
  custom/explicit entries. Verified that's cheap: achievements already key off **raw stored ids** (distinctness,
  not labels — so custom already counts; *no* "did [explicit thing]" achievement exists), and stats already
  resolve acts via id→label. **Phase 1 (DONE, schema v9 / `MIGRATION_8_9`):** kinks brought up to the same
  id-based, custom-capable model as acts/positions — new `KinkEntity`/`kinkDao`/`KinkRepository`, `kinks` column
  `Set<Kink>`→`Set<String>` (ids == old enum names, so **no data rewrite**), Settings → Manage custom kinks,
  Insights `resolveKink`, ENC-1 + achievements adapted. Behaviour is unchanged this phase (built-in catalogs
  still full). **Phase 2 (DONE 2026-07-02, schema v10 / `MIGRATION_9_10` — FDP-2):** the shipped built-in
  catalogs were trimmed to maintainer-approved non-explicit starter sets — `Act` 16 of 40 (KISSING, MAKING_OUT,
  ORAL, SIXTY_NINE, MANUAL, FINGERING, VAGINAL, ANAL, PROSTATE_MASSAGE, NIPPLE_PLAY, BREAST_PLAY, MASSAGE,
  MUTUAL_MASTURBATION, MASTURBATION, CUDDLING, OTHER), `Kink` 17 of 53 (DOMINATION, SUBMISSION, BONDAGE,
  RESTRAINTS, SPANKING, HAIR_PULLING, BITING, BLINDFOLD, SENSORY_PLAY, TEMPERATURE_PLAY, EDGING, PRAISE,
  ROLEPLAY, COSTUME_PLAY, DIRTY_TALK, AFTERCARE, OTHER). The migration is **generic, not list-driven**:
  `CatalogAdoption.adoptUnknownIds` scans the log for bare ids the current binary doesn't recognize and adopts
  each **used** one into the custom `acts`/`kinks` tables (row id = the old enum name → refs rewritten to
  `custom:<NAME>`; label = `prettify(name)`, e.g. "Anal creampie"; merge into an existing custom row on a
  label collision). So **no removed-id list ships in the APK** (the ids are themselves explicit), unused
  removed built-ins simply drop out of the picker, and the routine is idempotent. Ref rewriting is done
  row-by-row in Kotlin (split→map→join), not SQL `REPLACE` — substring ids (`CREAMPIE` ⊂ `ANAL_CREAMPIE`)
  make string surgery unsafe. **Restore self-heals:** `BackupManager.import` runs the same adoption after
  inserting raw rows, so pre-v10 backups no longer resurrect removed ids (this class of migration no longer
  needs the "re-export after upgrading" caveat). Unused explicit-named act icon drawables were deleted
  (resource names ship in the APK's resource table), and the 0.2.0 in-app release note was reworded — an APK
  string sweep then came back clean. *Known residual:* the `WATCHING_PORN` **id** remains inside
  `MIGRATION_7_8`'s SQL (an internal all-caps token, not a UI label) — removing it would break the shipped
  v7→v8 migration; accepted. Companion changes: custom acts/kinks/positions gained
  **rename-in-place** (id — and so every encounter ref — untouched; unique-label collisions rejected), since
  prettified labels may want polish ("Sixty nine"→"69") and delete+re-add would orphan refs; the three
  manage-custom dialogs were unified into one `CustomCatalogDialog`; and the `Practice`→`Act` /
  `Setting`→`Place` enum-class renames landed first as a pure refactor (DB stores constant names, not class
  names — zero data impact). Phase-2 scope = acts/kinks only (what was first flagged); positions/toys
  left as-is initially. Verified by `MigrationTest.migrate9To10…` (incl. substring pair,
  label-collision merge, idempotence) + `BackupRestoreRegressionTest.restoreOfPreTrimBackup…`.
  **Phase 3 (DONE 2026-07-03, schema v11 / `MIGRATION_10_11` — FDP-4):** on re-review linsui reported the
  labels were "still hardcoded" — the app still shipped explicit **positions** and **toys** (out of
  Phase-2 scope). The same rework was extended to both: `Position` trimmed (11 explicit/slang/group
  entries removed; it was already custom-capable) and `ToyType` made **id-based & custom-capable** (new
  `toys` table + `ToyRepository` + Manage-custom-toys, `encounters.toys` `Set<ToyType>`→`Set<String>`)
  then trimmed (9 removed). `CatalogAdoption` now also adopts removed position/toy ids (migrate +
  restore), and the `"Deep throat"` doc-comment example was scrubbed. Shipped as **v0.3.1** (kept in the
  0.3 range). Verified by `MigrationTest.migrate10To11…` + the full instrumented suite (25/25).
- **D-25 (M6):** **No chart library** (resolves O-3). Insights charts are drawn with plain Compose
  layout (`VerticalBarChart`, `RankedBars`) instead of Vico/MPAndroidChart. Rationale: the app already
  hand-rolls its visuals (per-act vector icons, manual `BitmapFactory` downsampling, no third-party
  image loader); bar/ranked charts are simple enough that a dependency isn't worth the FOSS-audit /
  size / lock-in cost, and fewer deps = smaller attack surface. The **stats engine**
  (`data/stats/InsightsEngine.kt`) is a pure-Kotlin, Android-free `compute()` so it's JVM-unit-tested
  directly (`InsightsEngineTest`) with no Robolectric. Streaks are **ISO weeks** (Mon-anchored) with a
  mid-week grace (current week stays "alive" if last week had activity). Acts/positions tally by their
  stored id and resolve labels via the custom `uuid→label` maps the VM passes in.
- **D-24 (M4):** Photo input = Android **Photo Picker** with an **ACTION_GET_CONTENT fallback**
  (some devices/emulators advertise the picker without providing the activity → `ActivityNotFoundException`).
  Plus **in-app camera capture** via a `FileProvider` into a private cache file, encrypted into the media
  store and the plaintext temp then deleted — sensitive shots never touch MediaStore / gallery / cloud
  backup. No CAMERA permission (ACTION_IMAGE_CAPTURE delegates to the camera app), so the zero-permission
  / no-network guarantee holds (anti-leak guard still green). Encounter photos stage on pick and commit
  on Save; partner photo is a single avatar; both clean up camera temps on save/cancel.
  **Auto-lock vs. handoff:** launching the picker/camera backgrounds the app, which would trip the
  immediate auto-lock and drop the result + in-progress screen. Fixed via
  `SessionManager.suppressNextAutoLock()` (a ~2-min one-shot window, consumed on the next background)
  called right before launch; `ON_STOP` now routes through `onAppBackgrounded()` which honours it.
- **D-23 (deferred):** Chunk 6 — extracting hardcoded UI strings to `strings.xml` and refactoring
  `EncounterEditViewModel` to a single `UiState` — is **deferred to M8**. String extraction belongs
  with the a11y/i18n pass; the per-field `mutableStateOf` VM pattern is already idiomatic, so the
  UiState wrap is low-value churn pre-release.

## Open

- **O-2 (resolved) License & distribution:** **License = GPLv3** (D-29) and **distribution = F-Droid only**
  (D-32). LICENSE + THIRD_PARTY_NOTICES.md + in-app Settings → About screen in place; all deps
  GPLv3-compatible. F-Droid fastlane metadata + `docs/RELEASE.md` submission guide added.
- **O-3 (resolved) Charts library:** **none** — Insights charts are hand-drawn in Compose (D-25).
- **O-4 (resolved) Multi-partner per encounter:** **yes** — the editor supports selecting multiple
  partners (M:N) with per-partner orgasm counts (D-22).
- **O-5 (resolved) CI quality gates:** **Detekt + ktlint** added as build-failing CI gates (M8, D-30),
  in a separate `quality` job; **Android Lint** already ran in CI (`lint`); the **FOSS guard** stays the
  hand-maintained `OssLicenses` + banned-SDK grep (no license plugin). Fixed-all-violations, no baseline.
- **O-6 Insights/chart accessibility — mostly closed (pre-release Pass 4, 2026-06-10):** the bar / ranked-bar /
  donut charts already render their label+count as real `Text`, so TalkBack reads them; the
  **line/area** chart painted its point values on the Canvas, so Pass 4 gave it a summarizing
  `contentDescription` ("Trend chart. Jan: 3, Feb: 5, …"). Remaining nicety (deferred): a single
  rolled-up per-chart summary node for the bar/donut charts too, and wiring it through D-23 string
  extraction. NFR-6.
- **O-7 Stacked activity-by-category chart** (the layered monthly bars in the reference app) needs an
  **act → high-level-category taxonomy** (e.g. Intercourse / Oral / Manual / Solo) that Tryst doesn't
  have. Deferred pending the user's grouping. Until then, monthly/weekday trends stay single-series.

> Still tracked elsewhere (not re-listed): user-configurable **auto-lock timeout** & **change-PIN UI**
> and **history filters/search** (deferred features, ROADMAP M3); **VACUUM on delete-all** for
> secure-delete hardening (ROADMAP M5, SECURITY_DESIGN §6); **Keystore-backed monotonic attempt
> counter** (SECURITY_DESIGN §6); **Argon2id** upgrade for the PIN/backup KDF (SECURITY_DESIGN §6).
