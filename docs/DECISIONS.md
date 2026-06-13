# Tryst — Decision Log

Lightweight ADR log. Newest at top. "Open" items still need a call.

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
  optional follow-up. Distribution (F-Droid / Play) still open.
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

- **O-2 (resolved) License:** **GPLv3** — see D-29 (pre-release Pass 10). LICENSE + THIRD_PARTY_NOTICES.md
  + in-app Settings → About screen in place; all deps GPLv3-compatible. **Distribution** (F-Droid and/or
  Play) is still open, to settle at M8 release prep.
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
