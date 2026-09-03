# Tryst — Decision Log

> **Status:** Live — decisions through **schema v16** (latest: **D-58**, SEC-2 tier 2 is a
> presence-only re-auth via BiometricPrompt with device-credential fallback — no CryptoObject,
> no DEK touch, v0.5.7).
> D-42 records storing the search history in the encrypted DB rather than prefs.
> D-41 covers the F-Droid content-policy rework — acts/kinks in
> 0.3.0, positions/toys in 0.3.1, then empty predefined lists + custom occasions/finish-locations in
> 0.3.2. Lightweight ADR log;
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
  **Phase 4 (DONE 2026-07-03, schema v12 / `MIGRATION_11_12` — FDP-5):** on further re-review linsui still
  saw explicit entries (e.g. "69", "Anal") among the *kept* mainstream acts and asked to "just use an
  empty pre-defined list to be safe." Decision: **empty the predefined lists entirely — no category is
  compiled in.** All six category enums are now empty; the couple of neutral starters ship instead as
  ordinary **user-owned rows** seeded by `CatalogSeeds` (acts: Kissing/Cuddling; occasions:
  Date night/Anniversary; finish: Didn't finish/In condom; kinks/positions/toys: nothing) — so even the
  starters appear on the management pages and are renamable/removable like any entry. Seeding runs on
  fresh install (Room `onCreate`) and on upgrade (`MIGRATION_11_12`, **before** adoption so a used
  starter keeps its nice label). **Occasion** and **EjaculationLocation** were made id-based &
  custom-capable too (new `occasions`/`ejaculation_locations` tables + repos; `encounters.occasions`
  `Set<Occasion>`→`Set<String>`, `encounters.ejaculationLocations` `Map<Int,Set<EjaculationLocation>>`→
  `Map<Int,Set<String>>`) and trimmed to two neutral seeds each (occasions: Date night/Anniversary;
  finish: Didn't finish/In condom). `CatalogAdoption` now covers all six categories — with a dedicated
  adopter for ejaculation's **map-encoded** column and a **table-existence guard** so migrations that
  predate a table don't touch it. The five occasion-specific achievements were reworked to be
  occasion-agnostic (the two seed-anchored ones kept). The per-category **Manage** dialogs were replaced
  by dedicated full-screen **management pages** (one nav route per category). Shipped as **v0.3.2** (kept
  in the 0.3 range). Verified by `MigrationTest.migrate11To12…` + the full instrumented suite (26/26) and
  a clean release-APK explicit-string sweep.
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

## Search (SRCH-1, 2026-07-09)

- **D-42 (2026-07-09) Recent searches live in the encrypted DB, never in prefs and never in a backup.**
  Search history is a standard convenience, and in most apps it lands in `SharedPreferences`. In Tryst
  that would be a **new plaintext-at-rest surface for the most sensitive strings in the product**: the
  three prefs stores (`tryst_appearance`, `tryst_insights`, `tryst_general`) are the only user-facing
  state *not* inside the SQLCipher database. A list of what the user searched for — partner names, acts,
  kinks — is exactly the sort of thing the threat model exists to protect, so prefs was rejected outright.
  **Chosen:** a `recent_searches` table (`query` PK, `lastUsedAt`) in the encrypted DB — schema **v13 /
  `MIGRATION_12_13`** (pure additive DDL). Consequences and deliberate choices:
  - **Excluded from `BackupManager.TABLES`.** The backup is the one artefact that leaves the device;
    the user's queries have no business travelling in it. Because that list also drives restore, an
    import leaves the local history alone rather than overwriting it with someone else's.
  - **Only submitted queries are recorded** (the IME "Search" action), never each keystroke — otherwise
    every prefix of every word would be persisted.
  - Capped at `RecentSearchRepository.MAX_RECENTS` (8), pruned on write; re-searching a term bumps its
    timestamp rather than duplicating it (`query` is the primary key).
  - `SessionManager.deleteAllData` drops the DB, so a wipe clears the history with everything else.
  - **Rejected outright:** voice search (needs the microphone permission — no new permissions, ever) and
    saved/synced searches. Fuzzy matching was rejected as noise on short catalog labels.

- **D-43 (2026-07-09) Search matches labels, so it must say *why* a result matched.** Search covers the
  note, partner names, and the resolved labels of acts/positions/places/occasions/kinks/toys/mood — but
  the result card only shows a few of those. A query for a kink therefore returns cards showing no trace
  of it, which reads as a bug. Each result reports the fields the query hit ("Matched in Acts · Place"),
  expands **in place** to show every field (rather than forcing a round trip through the editor), and
  bolds the matched text. Matching is case- **and accent-insensitive** via a length-preserving fold, which
  is what lets the highlight offsets map straight back onto the original string.

## Insights time scope (INS-2, 2026-07-10)

- **D-44 (2026-07-10) The scope must reach the engine, not just filter its input.** The roadmap assumed
  INS-2 was "feed a date-bounded subset into `InsightsEngine.compute()`; everything downstream already
  reflects its input." That is false for every figure computed against *today* rather than against the
  input list: the month buckets were the trailing 12 ending **this** month (so a 2023 scope produced
  twelve buckets for the wrong year, all zero), `avgPerMonth` divided by months since the *first ever*
  encounter, and `thisMonthCount`/`thisYearCount`/`currentStreakWeeks`/`daysSinceLast` all read the real
  calendar. `compute()` therefore takes a `scope: DateRange?` (FILT-1's primitive, shared with Search) and:
  - buckets months **across the scope** — one bucket per month in the window, capped at the most recent
    24; unscoped keeps the historical trailing-12. Labels gain a `'yy` suffix only when the window spans
    more than one year, since "Jan" is otherwise ambiguous.
  - divides `avgPerMonth` by the **window's** months.
  - **withholds** rather than fakes the two "as of today" figures: `daysSinceLast` → null,
    `currentStreakWeeks` → 0. `longestStreakWeeks` is a property of the window, so it survives.

- **D-45 (2026-07-10) Tiles that can't be honest under a scope disappear.** *This month*, *This year*,
  *Current streak* and *Days since last* return null while `Insights.isScoped`, and the existing
  "null tiles are skipped" rule removes them from the Overview grid. Rejected: reinterpreting them to
  the window (they'd quietly mean something other than their label) and greying them out (dead grid
  space). **Achievements are exempt from the scope entirely** — they are lifetime progress, they read
  the full log through their own ViewModel, and the card says so plainly while a scope is active.

- **D-46 (2026-07-10) An empty window is an answer, not an empty app.** Scoped to a year with no data,
  Insights says *"No trysts in 2021"* and **keeps the scope chip reachable** (it sits above the empty
  state, or you'd be trapped). Within a window that *does* have data, a section with nothing to show
  keeps its card and reports *"Nothing logged in 2024"* rather than vanishing — cards popping in and out
  as the window changes loses the reader's place, and a zero is signal in a tracker. Unscoped behaviour
  is unchanged: an empty card there is just noise and is still dropped.

- **D-47 (2026-07-10) The scope is remembered.** Persisted in `InsightsPreferences` (plain prefs,
  alongside card order/style — a date range is not sensitive the way a search query is, cf. **D-42**),
  so Insights opens where you left it. `resetLayout()` clears it back to all-time. `DateScope.decode`
  falls back to `AllTime` on any unrecognized value, so a corrupt or future-format pref can never crash.

- **D-48 (2026-07-10) One date vocabulary for Insights and Search.** Both screens narrow time, so both
  narrow it the *same way*: **year → quarter → custom range**, left to right, widest to narrowest. The
  model therefore lives in `data/filter/DateScope` next to `DateRange` (it is not an Insights concept),
  and the controls live in one `ui/common/DateScopeChips` so the two screens cannot drift.
  - The three chips are **one selection**, not three. Changing the year **keeps the quarter**, so you can
    step 2025 Q2 → 2024 Q2 to compare a season across years. The quarter chip is **disabled until a year
    anchors it** — "Q2 of all time" is not a window. The custom chip opens the picker directly; picking a
    year or *All time* is how you leave it.
  - `Quarter(year, q)` is a first-class variant rather than sugar over a custom range, so "Q2 2025" is
    something the labels, the persisted pref, and the empty states can all name. Its `init` rejects
    `q ∉ 1..4`, and `decode` swallows that — a corrupt `quarter:2025:9` degrades to `AllTime`.
  - **Cost, accepted:** Search lost its relative presets (*Last 7 / 30 / 90 days*), which have no
    equivalent in a year/quarter model. Re-add them as extra entries in the year dropdown if they're missed.
  - Chips show the locale's *short* date form to stay narrow; sentences ("No trysts in …") use the
    *medium* form, which has room.

- **D-49 (2026-07-11) The rest of FILT-1 lives behind one "More filters" sheet.** Search's chip row keeps
  only the four dimensions worth a permanent chip (date, rating, partner, photo); everything else FILT-1
  can express — acts, positions, kinks, toys, occasions, place, protection, mood, initiator, weekday,
  time-of-day, duration, has-note, include-solo — goes in a `ModalBottomSheet` reached from a "Filters"
  chip badged with the count of active advanced filters.
  - **Live-apply, not a staged draft.** Every toggle updates results immediately, matching the base
    chips; the sheet's scrim hides the list, so the bottom bar shows a running "Show N results" (reads the
    same `hits.size` the screen already computes) and its button only dismisses. No draft/commit state,
    no second source of truth.
  - **One `_advanced` holder, not fourteen flows.** Kotlin's typed `combine` tops out at five flows and the
    base chips already use four, so the advanced dimensions live in a single `MutableStateFlow<EncounterFilter>`
    (advanced fields only) merged with the base filter via a 2-arg `combine(base, adv)` + `.copy(...)`. The
    two halves never write the same field.
  - **Catalog chips carry the `custom:` prefix.** Catalog rows have bare-uuid ids but encounters store
    `custom:<uuid>`, so the sheet builds each chip's value as `"custom:" + row.id` to match what
    `EncounterFilter.matches` compares against. A category with no rows hides its section — an empty
    catalog can only match nothing.
  - **Ephemeral, like the other Search chips.** Advanced filters reset when you leave Search; they are not
    persisted. (Contrast the Insights scope, D-47 — a date range is not sensitive the way a filter over
    kinks/partners is, cf. D-42 on why search queries stay out of prefs and backups.)

- **D-50 (2026-07-12) The photo gallery is a view over the log, and its layout is a preference.** GAL-1
  adds a Photos tab, but no new table: `GalleryPhotos.build` reads `EncounterRepository.observeAll()`,
  filters it through the same `EncounterFilter` + `EncounterSearch` the Search screen uses (the gallery is
  FILT-1's **third consumer**), then flattens matched encounters' `media` into photos that inherit the
  tryst's date/partners/rating. So a photo already knows how to group and how to jump back to its tryst,
  with **zero schema change**.
  - **Encounter photos only (v1).** Partner/profile avatars are `photoMediaId` blobs with no `media` row,
    no date, and nothing to filter on, so they'd need separate handling in every grouping — deferred as
    GAL-1a (they could later become partner-section headers). Video is GAL-2 (needs MED-1).
  - **Layout is the user's choice, not ours.** All four layouts ship as selectable options — date grid,
    flat grid, by-partner, feed — with column density (2–4) and sort, **default date grid**. The picker
    lives in the gallery's own app-bar (contextual, live) backed by `GalleryPreferences`, which mirrors
    `InsightsPreferences`: plain prefs, StateFlow-exposed, **excluded from backup** (arrangement isn't
    sensitive). No duplicate copy buried in the global Settings screen.
  - **By-partner duplicates, the viewer de-duplicates.** In the by-partner layout a threesome's photo
    appears under *each* partner (that's the point of "photos with Alex"), but the flat list the
    full-screen viewer pages counts each source photo once. Grid keys are `sectionKey:mediaId` so the same
    photo across two partner sections doesn't collide.
  - **The viewer respects insets.** Its top chrome (close / counter / open-tryst) takes `statusBarsPadding`
    — the app draws edge-to-edge under the status bar, so without it the controls sit under the status bar
    and can't be tapped (caught on-device).
  - **Ease-of-use refinements (2026-07-12).** (a) The layout/density/sort picker **moved out of the gallery
    app-bar into a dedicated `Settings → Gallery` screen** (`GallerySettingsScreen`) — a set-once look
    preference belongs with the other appearance settings, not competing for space with per-session
    filtering. (b) The gallery top bar is now **just search + one Filters button**; the always-visible
    chip row is gone. (c) That Filters button opens a single sheet with **everything** — date, rating,
    partners, and the advanced `MoreFiltersColumn` (extracted from Search's sheet so both share it). (d)
    By-partner headers show the partner's **avatar + name** (`PartnerEntity.photoMediaId` via
    `PartnerRepository.openPhoto`), the first slice of GAL-1a.

- **D-51 (2026-07-12) Photo metadata is read, not stripped; the Photos gate is blur, not auth.** Two GAL-1
  follow-ons, both shaped by a user decision.
  - **Don't strip EXIF on import; read it at view time (META-1).** The obvious privacy move is to scrub
    EXIF (GPS) on import, but we deliberately don't. EXIF is tiny (negligible storage), stripping forces a
    **lossy re-encode**, and — decisively — the app has **no network, no share/export-single-photo path,
    and encrypts every blob at rest**, so embedded location never actually leaves the device. So the blob
    keeps its original bytes and the viewer simply **reads** the metadata (capture date, dimensions,
    camera, GPS) on demand via `androidx.exifinterface` from the decrypted stream (`data/media/PhotoMetadata`).
    **No schema change, nothing persisted.** GPS shows as **raw coordinates** — no `Geocoder`, which can
    hit the network. **The one future trigger to revisit:** if a "share/export a single photo" feature is
    ever added, strip-on-export at that boundary. (`checkNoNetworkDebug` re-verified: `exifinterface` adds
    no INTERNET permission.)
  - **The Photos gate is blur-only (SEC-2), off by default (user choice).** Of the two tiers discussed
    (blur/reveal vs biometric re-auth), the user picked **blur only**, configurable in Settings → Gallery.
    A `GalleryPreferences.blurUntilRevealed` flag (default off); when on, the gallery body renders behind
    `Modifier.blur` + a "Show photos" cover. It re-arms on tab entry **but only after a grace window** so a
    quick switch away and back doesn't re-prompt: a process-scoped `GalleryRevealState` (@Singleton, **in
    memory, not persisted**) records the last reveal time (`SystemClock.elapsedRealtime`), refreshed both on
    "Show photos" and on leaving the tab while revealed (so grace is measured from *last active in Photos*,
    `GRACE_MS = 30s`); the screen seeds its `revealed` flag from `revealedRecently()`. Deliberately **not**
    reset on app-lock — the app lock is itself the gate for the locked case, and a fresh process starts
    gated. Tapping a photo keeps it revealed while browsing. The biometric re-auth tier stays on the
    roadmap, unbuilt.

- **D-52 (2026-07-30) Solo tryst photos attribute to self, and photos can be manually tagged onto a
  person from the viewer.** Two related shifts to make the Photos tab match how people actually think
  about "photos of X".
  - **Solo → self (auto).** A solo tryst has no partner rows, and the previous pipeline dropped its
    photos into a nameless `GalleryGroup.Solo` bucket — drilling into You from People showed *nothing*
    because the drill filter set `partnerIds = {"self"}` and no encounter has that as a real partner.
    Now `GalleryPhotos.encounterPhoto` attributes solo photos to `SELF_PARTNER_ID` so By-partner puts
    them under You (alongside the self-profile portrait album), and the drill-into-self path translates
    the filter to `includeSolo = true, partnerIds = {}` so `EncounterFilter.matches` still lets solo
    encounters through. No schema change; no encounter data mutated.
  - **Add to person's photos (manual).** A new action in `PhotoViewer` (`AddPhotoAlternate` icon) opens
    a menu of every active partner + You; picking one copies the current photo into that person's
    portrait album via `PersonPhotoRepository.add`. The source stays put. Cheap way to fix a
    mis-attribution ("Alex was in the shot but the tryst was solo") without editing the encounter's
    partner list — and a photo can be tagged onto multiple people that way.
  - **Non-goals kept explicit.** No new photo↔person tag table — we already have `person_photo` and
    encounter-partner rows, and layering a third association model would fragment the story. The
    copy-into-album approach reuses the existing tables and stays consistent with "portrait = photo
    of a person, encounter photo = photo attached to a tryst".

- **D-53 (2026-07-30) Restore backfills NOT-NULL columns from the live schema's DEFAULT; entities
  must declare `defaultValue` for every column added post-v1.** A shipped v0.4.0 phone was on schema
  v13; upgrading to v15 (main) runs `MIGRATION_13_14` which sets `media.favorite INTEGER NOT NULL
  DEFAULT 0`. But **fresh** v15 installs got the column with no SQL DEFAULT because the entity
  lacked `@ColumnInfo(defaultValue = "0")` — so restoring a v13 backup on a brand-new phone died
  with `SQLiteConstraintException: NOT NULL constraint failed: media.favorite`. Two-layer fix:
  - **Entity source-of-truth.** `MediaEntity.favorite` gains `@ColumnInfo(defaultValue = "0")` so
    fresh Room CREATE matches the migration. Rule going forward: any new NOT-NULL column added via
    an `ADD COLUMN … NOT NULL DEFAULT X` migration also carries `defaultValue = "X"` on the entity.
  - **BackupManager backfill (defense-in-depth).** `restoreDatabase` now runs `PRAGMA table_info`
    per target table and, for any NOT-NULL column with a live SQL DEFAULT that the backup row
    doesn't carry, fills that DEFAULT into the ContentValues before insert. That means the next
    migration to forget the entity annotation still restores older backups cleanly. Columns with
    no SQL DEFAULT still fail loudly — we deliberately don't fabricate a `0` / `""` because the
    "right" value is domain-specific and silent data corruption is worse than a loud error.
  - **Also (observability).** `BackupViewModel.import` no longer swallows the exception silently —
    it now `Log.e("TRYSTIMPORT", …)` before showing the generic toast. This bug was invisible for
    a full session because of the swallow; the log line kills that class of debugging pain.

- **D-54 (2026-07-30) OPEN — Ejaculation / finish-location model needs a design pass.** A
  field-testing question surfaced during v0.5.1 validation: the current per-self-orgasm
  "ejaculation location" prompt was designed with a penis-owning user in mind, and doesn't
  cover (a) a partner ejaculating, or (b) a vagina-owning user squirting. The prompt
  itself is not anatomy-gated (nothing in code enforces penis-only), but the framing +
  catalog labels do. Options weighed:
  - **A.** Add ejaculation location per PARTNER orgasm too, and add "squirted" as an
    option. Schema change: `partnerEjaculations: Map<partnerId, Map<orgasmIndex, Set<String>>>`
    or a per-orgasm ejaculation regardless of who orgasmed. Non-trivial: entity + DAO
    change, migration, backup format additions, editor UI work.
  - **B.** Just add "Squirted" as a self-orgasm option (built-in or in the customizable
    starter set). No schema change. Doesn't help partner-ejaculation.
  - **C.** Rename the field to "Finish location" throughout, leave the catalog fully
    customizable (users add "Squirted", "Chest", etc. as they like), AND add a matching
    per-partner prompt with the same catalog. Cleanest long-term — aligns names and covers
    both cases. Schema change still needed for the partner side (roughly the same shape
    as A).
  - **D.** Defer (this decision — write it down, sit with it, revisit in v0.6).
  - **Decision (deferred):** D. The user picked "write a DECISIONS entry and defer".
    Right call because A/C both touch schema (a bigger commitment than a v0.5.x patch)
    and B is a band-aid that leaves the partner side unaddressed. Revisit as part of a
    v0.6 design pass alongside any other data-model expansions (see D-XX/roadmap open
    items). No code changes this cycle.

- **D-55 (2026-08-31) Per-photo captions ship with a user-picked entry point (CAP-1, v0.5.3).**
  The photo viewer already has a densely-packed top action row (Close · counter · slideshow ·
  add-to-person · avatar · info · open-tryst); a caption editor could reasonably live in the
  (i) info panel (quieter, discoverable) or as its own top-row icon (fastest access). Rather
  than pick one and be wrong for half the users, ship both entry points under a Settings
  toggle: **Info panel** / **Top toolbar** / **Both** (default) / **Off**. The `OFF` value
  hides the UI entirely but the underlying data still rides in the encrypted backup and
  still fires search hits — it's a pure UI toggle, not a data toggle. That preserves the
  freedom to switch on/off without ever losing a caption a user typed under a different
  setting. The pref lives in `GalleryPreferences` (`CaptionEntryPoint` enum), joining the
  other Photos-tab look/behaviour prefs.

- **D-58 (2026-08-31) SEC-2 tier 2 is a presence-only re-auth; no CryptoObject, no DEK
  touch (v0.5.7).** The re-auth gate the user opts into for the Photos tab is not the same
  crypto path the main app lock uses. The main lock's BiometricPrompt runs with a
  CryptoObject-backed cipher so a successful auth *unwraps the DEK*; that's why it's
  BIOMETRIC_STRONG only (no device credential — the OS won't hand you a cipher for a
  screen-lock unlock). SEC-2 tier 2 is different: the DEK is already in memory (the app is
  unlocked), we just want to confirm the person holding the phone is still the owner. So we
  reuse the same `BiometricPromptHelper` file but add a **presence-only** overload
  (`confirmPresence`) that skips the CryptoObject and allows
  `BIOMETRIC_STRONG or DEVICE_CREDENTIAL`. That means:
  - Devices with an enrolled biometric use it (fastest path).
  - Devices with only a screen-lock PIN/pattern/password still work — the OS chains to the
    device credential. Otherwise this tier would be biometric-only, which excludes a
    material slice of users.
  - We don't verify against Tryst's *own* app PIN — that would need a custom PIN pad and
    doesn't add security over the device credential the user has already trusted for their
    phone. Simple, native, less code.
  Toggle is hidden on devices with no biometric AND no screen lock (the OS reports no
  authenticator available); showing it would be a lie.

- **D-57 (2026-08-31) QOL-7 dialog audit: promote CsvMappingDialog + ReassignDialog to
  routes; leave every other dialog as-is (QOL-7, v0.5.5).** A grep-driven audit over every
  Compose `AlertDialog` / `ModalBottomSheet` / `DatePickerDialog` / `TimePickerDialog` in
  `ui/` found 15 files with dialog usage. The rule from ROADMAP_FUTURE (`substantial
  multi-field form → route; genuine picker / confirm / menu → dialog`) selected only two:
  - **CsvMappingDialog** (`SettingsScreen.kt`) — the mapping form has a row per `CsvField`
    entry with a header dropdown each, capped inside a 460dp `AlertDialog` with an inner
    `verticalScroll`. Promoted to `CsvImportScreen` (`ui/settings/`, `Routes.CSV_IMPORT`);
    file picker moves into the route so re-picking a different CSV doesn't need a return
    to Settings.
  - **ReassignDialog** (`GalleryScreen.kt`) — a scrollable tryst list capped at 360dp with
    no way to search. Promoted to `ReassignPicker`, a full-screen `Dialog` overlay (same
    `usePlatformDefaultWidth = false` pattern `PhotoCropper` uses — no nav route because the
    picker's state is transient) with a `TopAppBar` and a search field that folds through
    `EncounterSearch.fold`.
  Everything else was left as-is: WhatsNewDialog is a one-shot post-update popup; the
  Date/Time/DateRange dialogs are proper pickers; MoreFiltersSheet + GalleryFiltersSheet are
  intentional filter sheets (a bottom sheet is the right pattern for "keep the results
  visible below the controls"); PersonPhotoStrip + SelectionField menus are short pickers;
  discard-changes / delete-confirm / import-password dialogs are confirms. BackupPasswordDialog
  is a single-field prompt with an optional checkbox — borderline, but not "substantial." The
  discard-changes guard (D-33) was intentionally NOT added to the two new routes because
  neither has a destructive save step: CSV import only writes rows on the explicit Import
  action, and reassign is a single tap → apply.

- **D-56 (2026-08-31) In-app photo edit replaces the blob in place; EXIF is dropped in the
  re-encode (EDIT-1, v0.5.4).** Two branches on the design:
  - **Edit in place vs new blob.** Chosen: **in place**. Same blob id keeps every reference
    (`media.encFilePath`, `person_photo.mediaBlobId`, `partners.photoMediaId`) valid with no
    schema touch and no orphan-sweep after commit. Downside is no cross-session undo — the
    original bytes are gone. Given the app's small photo set and the "small edit" scope
    (rotate + crop, no destructive filters), the ergonomic win outweighs a history feature
    nobody explicitly asked for. Safety comes from `EncryptedMediaStore.saveStaged` +
    `promoteStaged`: the new bytes land in `<id>.enc.staging` first, then atomically replace
    the live blob — a mid-encrypt crash leaves the original blob intact.
  - **EXIF stripping on edit.** Any real edit re-decodes the pixels and re-encodes as JPEG,
    so embedded EXIF (camera model, GPS coordinates, original date) is naturally lost — no
    extra plumbing. This is the strip-on-edit hook D-51 reserved when it decided against
    stripping on import; that decision still holds for imports (EXIF is small, stripping =
    lossy re-encode, and the vault never leaks it anyway), but the moment a user chooses to
    edit, dropping the metadata is the right default. Original creation-date in the tryst
    is unaffected (it's the tryst's own `startAt`, not the photo's EXIF).

- **D-55 (2026-08-31) Per-photo captions ship with a user-picked entry point (CAP-1, v0.5.3).**
> and **history filters/search** (deferred features, ROADMAP M3); **VACUUM on delete-all** for
> secure-delete hardening (ROADMAP M5, SECURITY_DESIGN §6); **Keystore-backed monotonic attempt
> counter** (SECURITY_DESIGN §6); **Argon2id** upgrade for the PIN/backup KDF (SECURITY_DESIGN §6).
