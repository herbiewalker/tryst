# Tryst — Roadmap / Milestones

Status: **Live (2026-06-08)** — M0–M7 complete and verified on the emulator; **M8 (polish & release)**
is the only milestone remaining. Each milestone ends runnable & tested.

> **Update (2026-07-02):** M0–M8 are all **done** and the app has **shipped** (v0.1.0 → v0.2.0 →
> v0.3.0; the F-Droid submission MR is open, waiting on the v0.3.0 content-policy rework). This file is
> the milestone *history*; all forward work — post-1.0 features
> **and** the engineering/infra/housekeeping backlog (CI instrumented tests, F-Droid screenshots, SPDX
> headers, Argon2id export KDF, baseline profile, etc.) — now lives in
> [ROADMAP_FUTURE.md](ROADMAP_FUTURE.md). The legacy "deferred polish backlog" notes below (history
> filters, VACUUM secure-delete, Argon2id, monotonic attempt counter, …) are folded into / superseded
> by that document.

## M0 — Project scaffold  ✅ done (verified building & running)
- [x] Android project: Gradle KTS, version catalog, Compose, Hilt, base theme.
- [x] CI skeleton + the **anti-leak guard** (no network permission, no banned SDKs), enforced
      both in-build (`checkNoNetwork*` Gradle task) and in CI.
- [x] `allowBackup=false`, `FLAG_SECURE`, data-extraction exclusions baseline.
- [x] Placeholder launcher icon + sample unit/instrumented tests.
- [x] **Build verified** on AGP 9.2.1 / Kotlin 2.2.10 / Gradle 9.5 (Android Studio Quail):
      `assembleDebug` succeeds, anti-leak guard passes, unit test passes, app installs & runs
      on emulator. Gradle wrapper committed.

## M1 — Encrypted storage foundation  ✅ done (verified on emulator)
- [x] Room + SQLCipher wired via an injectable key behind `DatabaseKeyProvider`
      (M1 placeholder = `InsecureDevKeyProvider`; real key is M2). Native lib loaded once
      via `SqlCipherLibrary.ensureLoaded()`.
- [x] Media crypto module (Tink AES-256-GCM-HKDF streaming) + `EncryptedMediaStore`
      (encrypted blobs in app-internal storage, never MediaStore).
- [x] Core entities + cross-refs (Encounter, Partner, Location, Tag, Position, Media) with
      converters; schema v1 exported to `app/schemas/`. Repositories for Encounter & Partner.
- [x] Instrumented tests pass: relations round-trip; **DB file on disk is verified encrypted**
      (no "SQLite format 3" header, no plaintext values); media crypto round-trips & rejects
      wrong associated data. Room bumped 2.6.1 → 2.7.1 for KSP2/Kotlin 2.2 compatibility.

## M2 — Security & app lock
Key model decided: **Keystore-only + distinct 6-digit app PIN** (O-1 → D-12). See
[SECURITY_DESIGN.md](SECURITY_DESIGN.md) §1.

### M2a — Key vault core  ✅ done (verified on emulator)
- [x] Keystore KEK (StrongBox when available, TEE fallback) + PIN-derived layer (PBKDF2);
      double-wrapped DEK.
- [x] Vault: `setup(pin)`, `unlock(pin)→DEK`, `changePin`, `isInitialized`, failed-attempt
      lockout + self-wipe.
- [x] Instrumented tests pass (real Keystore on emulator): setup/unlock returns same DEK,
      wrong-PIN countdown, persistence across instances, changePin, lockout-wipe after 10 fails.

### M2b — Biometric, UI & session  ✅ done (PIN/session verified; biometric pending manual test)
- [x] First-run PIN **setup screen** + **lock screen** (PinPad), driven by `LockViewModel`;
      `MainActivity` (now a `FragmentActivity`) renders by `LockState`.
- [x] **Session lifecycle**: `SessionManager` builds the SQLCipher DB *after* unlock from the
      vault DEK and closes it on lock; `InsecureDevKeyProvider` and the eager DB module removed.
      Repositories + media store now read from the session.
- [x] **Auto-lock** on background (ProcessLifecycle ON_STOP → `lock()`), clearing the DEK.
- [x] **Biometric unlock** (`BiometricVault`: a separate auth-gated Keystore key wraps the DEK;
      `BiometricPrompt` CryptoObject flow), enable/disable from Home, PIN always a fallback.
      Builds & graceful when unavailable; **needs manual test with an enrolled fingerprint**.
- [x] Instrumented `SessionManagerTest`: setup→unlock→lock, persistence across restart, wrong-PIN.

> Deferred (minor): a user-configurable auto-lock *timeout* (settings UI, with M3) — the
> default (immediate on background) is already active. Tracked as a small enhancement.

## M3 — Core logging  ✅ done (verified building & running on emulator)
- [x] Compose Navigation shell (bottom nav: History / Partners / Settings) replacing the
      placeholder home; `MainActivity` Unlocked branch renders `TrystApp()`.
- [x] **Add/edit/delete encounters**: date/time pickers, duration, partner multi-select,
      protection (multi), mood / orgasm / initiator, 1–5 rating, note (`EncounterEditScreen`).
- [x] **Partner management**: list, add/edit dialog (named or anonymous, note), archive.
- [x] **History** list with empty state; tap to edit.
- [x] **Settings**: biometric enable/disable (relocated from home), Lock now, **Delete all data**
      (`SessionManager.deleteAllData()` wipes keys + DB + media → setup).
- [x] ViewModels use `stateIn` + `.catch` (survive the DB closing on auto-lock).
- [ ] Deferred: history **filters/search**. (change-PIN UI ✅ M8/D-31; configurable auto-lock timeout ✅ M8/D-31.)

## M3.x — Category, partner & presentation expansion  ✅ done (verified on emulator)
- [x] **M3.1 (schema v5):** partner sex/gender/relationship (+ `photoMediaId` M4 hook); custom
      **acts** (new `acts` table; practices → string IDs gave/received); big enum expansion;
      **Setting & Context** → **Setting & Location** (places) + new **Occasion** category;
      threesome/group/swinging moved to Kink. **Theming:** purple/green palette + Material You
      toggle + Light/Dark/System (persisted in `ThemePreferences`).
- [x] **M3.2:** Trysts card badge → **custom per-act vector icons** (intensity-ranked); **calendar
      view** toggle (month grid; each day shows its headline act icon). Icons swappable for realistic
      art later with no code change.
- [x] **M3.3 (schema v6):** per-partner orgasm counts + per-orgasm ejaculation; blowjob & oral-for-her
      acts.
- [x] **Cleanup:** PBKDF2 → 600k (OWASP); dependency refresh (Room pinned 2.7.1); modern Kotlin DSL.

## M4 — Media attachments  ✅ done (verified on emulator)
- [x] Pick photos via the Android Photo Picker (`PickVisualMedia`, image-only — no storage permission,
      keeps the zero-permission guarantee).
- [x] Encrypt on attach into app-internal storage; thumbnails + full view **decrypted in-memory only**
      (manual `BitmapFactory` downsampling via `ui/common/MediaImages` — no third-party loader, no temp files).
- [x] Attach/remove photos on the encounter editor (staged, committed on Save), full-screen viewer,
      thumbnail on the history/calendar card.
- [x] Partner photo (reuses `photoMediaId`) on the partner add/edit dialog + card avatar.
- [x] **In-app camera capture** (FileProvider → private cache → encrypt → delete plaintext temp);
      sensitive shots never touch MediaStore/gallery/cloud. No CAMERA permission needed.
- [x] Resilient picker: Photo Picker with an ACTION_GET_CONTENT fallback (some emulators advertise
      the picker without providing it).
- [x] `MediaAttachmentTest`: attach round-trip, on-disk blob verified encrypted, delete cleans up. 14/14 green.

## M5 — Backup & portability  ✅ core done (verified on emulator)
- [x] **Encrypted backup export + restore** (Settings → Backup & restore): password-derived key
      (PBKDF2) over a Tink-streamed ZIP of `data.json` (all tables) + decrypted media; re-encrypted
      under the device key on restore. SAF file picker; auto-lock suppressed across the handoff.
      `docs/EXPORT_FORMAT.md` written. `BackupRoundTripTest` green (15/15).
- [x] Full wipe = `SessionManager.deleteAllData` (keys + DB + media), from M3.
- [x] **M5b — import other apps' data:** generic CSV importer with column mapping
      (Settings → Import from CSV). Auto-detects common columns; flexible date parsing; find-or-create
      partners by name. Covers Intimacy / LoveLust / spreadsheets once you have their CSV export.
      `CsvParseTest` (JVM) green.
- [ ] Optional: VACUUM on delete-all for secure-delete hardening.

## M6 — Insights  ✅ done (build + unit tests green; visual check pending user unlock)
- [x] **Stats engine** (`data/stats/InsightsEngine.kt`): pure-Kotlin `compute(encounters, …) → Insights`,
      JVM-unit-testable (`InsightsEngineTest`, 10 cases). Totals (all-time / month / year), days-since-last,
      avg-per-month, **week streaks** (current + longest, ISO weeks w/ mid-week grace), rating histogram +
      average, duration totals/avg, self & partner orgasm totals/avg, trailing-12-month + day-of-week buckets,
      and ranked breakdowns (partners, acts, positions, moods, kinks, places, occasions, toys, protection,
      finish location). Acts/positions resolve built-in enum names **and** `custom:<uuid>` via passed label maps.
- [x] **Charts** drawn with plain Compose layout — **no chart dependency** (Vico candidate dropped; keeps the
      project dependency-light/FOSS, consistent with the hand-rolled icons & manual bitmap decode). `VerticalBarChart`
      (months / weekdays / ratings) + `RankedBars` (horizontal top-N) in `ui/insights/InsightsCharts.kt`.
- [x] **Insights tab** added to the bottom nav (📊, between Trysts and Partners): summary stat-card grid +
      chart/breakdown sections, empty state until there's data. `InsightsViewModel` combines the encounter log
      with custom act/position rows and computes off the main thread; survives the DB closing on auto-lock.

## M6.1 — Insights UX lift + global "dark & moody" polish  ✅ done (build + unit tests green; visual check pending user unlock)
- [x] **Switchable chart styles** (`ChartStyle` = Bars / Line / Donut), chosen from a chip selector on
      Insights and persisted. One picker drives every chart via `TrendChart`/`BreakdownChart` dispatchers
      with graceful fallback (donut→bars for ordered time series; line→bars for categories). New
      `LineAreaChart` + `DonutChart` (with legend + "Other" tail) join the refined bar/ranked charts —
      still hand-drawn Compose, no chart dependency.
- [x] **Customizable Overview stat boxes:** in-place **edit mode** (Tune icon → Done) where tiles are a
      drag-to-reorder list (`ReorderableColumn`, long-press) each with a show/hide eye toggle + "Reset to
      default layout"; view mode shows visible tiles in saved order as a 2-col grid. Stable tile catalog
      `ui/insights/StatTiles.kt`; order + hidden set + chart style persisted in
      `core/prefs/InsightsPreferences.kt` (SharedPreferences, mirrors `ThemePreferences`).
      Settings → **Customize Insights** deep-links into edit mode (`insights?edit=true` nav arg).
- [x] **Clearer labels & grouping:** sections — Activity, Satisfaction, People, What you did, Vibe &
      context, Details (with sub-labels); "Finish / ejaculation" relabel; "Trysts / month".
- [x] **Global polish (reskins every screen via shared tokens):** enriched dark scheme (deep
      near-black-purple background/surface + layered `surfaceContainer*` tones), larger rounded `Shapes`,
      refined `Typography`, and Material vector nav icons replacing the emoji tabs.
- [x] Tests: `StatTilesTest` (JVM) green; `InsightsEngineTest` still green; assembleDebug + anti-leak OK.

## M6.2 — Insights customization deepening  ✅ done (build + unit tests green; visual check + color-coding pending)
- [x] **Per-card chart style** (top global Bars/Line/Donut buttons removed): each section card's style is
      chosen in the Customize screen and persisted per section id (`InsightsPreferences.sectionStyles`).
- [x] **Reorder / hide whole section cards** (Activity, Satisfaction, People, What you did, Vibe & context,
      Initiator, Orgasms, Details) in the Customize screen, mirroring the stat-tile editor. Stable section
      catalog `ui/insights/InsightSections.kt`; order + hidden set persisted.
- [x] **New Initiator section** (who started it) and **Orgasms section** — your-vs-partner totals, **orgasms
      per partner** (named), total orgasms **over time**, and finish/ejaculation (moved here from Details).
      New engine fields: `topInitiators`, `orgasmsPerPartner`, `orgasmsMonthly`.
- [x] Tests: `InsightSectionsTest` + new `InsightsEngineTest` cases (initiator, per-partner orgasms, orgasm
      trend) green; assembleDebug + anti-leak OK.
- [x] **Color-code charts by type** — `ui/insights/TypeColors.kt` maps a value's label to a stable vivid
      color (pure fn of `String.hashCode` into a curated dark-bg palette seeded with the reference
      pink/blue/violet, cycling for new values). Applied to ranked bars (color dot + bar) and donut
      slices/legend, so the same type reads the same color across every card. Trend charts stay
      single-accent (their axis is time, not type). Future option: stacked monthly-by-category bars
      (needs an act→category taxonomy).

## M7 — Achievements  ✅ done (build + unit tests green; visual check pending user unlock)
- [x] **Derived achievement engine** (`data/achievements/AchievementEngine.kt`, pure Kotlin, JVM-tested):
      replays the encounter log to compute, per achievement, `current`, `unlocked`, and a derived
      `unlockedAt` date. Rule kinds: `Count` / `Sum` / `Distinct` / `Streak`. **No schema change, nothing
      persisted** — progress is recomputed reactively like the stats engine.
- [x] **Curated catalog** (`data/achievements/Achievements.kt`, ~67): milestones (1→1,000 trysts), week
      streaks (2→52), variety (acts/positions/partners/places/kinks/toys, moods, protection & finish types,
      all weekdays, all months), pleasure (own/partner orgasm totals, multi-orgasm sessions, 5-star nights),
      occasions & places (morning, makeup, quickie, date night, special, vacation, public, outdoors, car),
      and odds & ends (photos, marathon, solo sessions, notes/journaling, safe sex, initiator). Emoji badges
      (swappable for art later).
- [x] **Unlock UI:** a dedicated **Achievements screen** (grouped by category; progress bars + unlock
      dates; a "New" ribbon for unlocks within ~14 days), opened by a **trophy icon** in the Insights top
      bar; plus a compact **teaser card** in the Insights scroll (unlocked count, recent unlocks, nearest
      in-progress) with "See all" — itself a **reorderable/hideable Insights section** like the charts (no
      per-card chart style, since it's a summary). `AchievementEngineTest` (JVM) green.
- [ ] Deferred: a persistent "just unlocked!" celebration (needs acknowledged-ids in encrypted storage).

## M8 — Polish & release prep

> Note: the numbered **pre-release audit passes** (Material 3, edge-to-edge, motion, a11y, security,
> license, release hardening, …) are a cross-cutting pre-release program tracked in their **own section
> below** — they are **not** M8 deliverables.

### Remaining
- ~~**i18n: extract all hardcoded UI strings to `strings.xml`**~~ **DONE (chrome).** All of the app's own
  Compose chrome — screen/app-bar titles, buttons, dialogs, field labels, helper/empty-state copy, and
  the Pass-4 `contentDescription` a11y labels — is now in `app/src/main/res/values/strings.xml`
  (`app_name` → ~200 entries + `<plurals>`), referenced via `stringResource`/`pluralStringResource`.
  Behaviour-preserving (English-only v1; the goal was translation-readiness + cleanliness, **not** a
  second locale). Included the runtime status/error strings in `LockViewModel`/`BackupViewModel`/
  `CsvImportViewModel` (resolved via injected `@ApplicationContext`) and `BiometricPromptHelper`
  (`getString` via its `activity`). **Deferred to a future "Localization" milestone** (all are
  English **identity keys** or non-`Context` plain-data objects, so localizing them naively would shift
  `TypeColors` chart colours / stats grouping, or needs a parallel `@StringRes`): the taxonomy enum
  `label`s (`data/db/entity/Enums.kt`), `ui/common/ActOptions`/`PositionOptions` + custom act/position
  labels, the `StatTiles`/`InsightSections` catalog display names + `editorNote`s, the achievement
  titles/descriptions/emoji + `category.label` (`data/achievements/Achievements.kt`), the inline chart
  `Tally`/`Bucket` labels (incl. `"You"`/`"Partners"`/`"Other"`), `CsvField.label`, and `Format.kt`'s
  `"Anonymous"`/`"Today"`/`"Yesterday"` (pure object, no `Context`). Note: Compose has no stock
  hardcoded-string lint, so extraction isn't auto-enforced — a custom Detekt rule remains a possible
  future add (the CI quality gates themselves landed in D-30).
- ~~**CI quality gates (O-5)**~~ **DONE (2026-06-12, D-30).** Added build-failing **Detekt** (1.23.8,
  AST-only) + **ktlint** (ktlint-gradle 14.2.0) gates in a separate CI `quality` job, with a
  curated/pragmatic `config/detekt/detekt.yml` + `.editorconfig`; fixed all violations (no baseline).
  Android Lint already ran in CI; the FOSS guard stays the hand-maintained `OssLicenses` + banned-SDK
  grep (no license plugin — Pass 10 ethos). A "no-hardcoded-Compose-strings" rule has no stock
  equivalent; left as a possible future custom Detekt rule.
- ~~Optional cleanup: refactor large editor VMs to a single immutable `UiState` (deferred "chunk 6")~~
  **DONE (2026-06-12).** `EncounterEditViewModel`'s ~21 per-field `mutableStateOf` properties collapsed
  into one immutable `EncounterEditUiState`, exposed as a single `var uiState by mutableStateOf(...)`
  (`private set`); all edits go through `copy()`-ing update methods (added explicit
  `setStartAt`/`setDuration`/`setRating`/`setMood`/`setInitiator`/`setNote` to replace the screen's former
  direct field writes). Repo-backed option `StateFlow`s + internal bookkeeping (`loadedId`/`createdAt`/
  `removedExisting`) stay on the VM; `solo` is now a derived prop on the state. Behaviour-preserving; the
  screen reads a single `val ui = viewModel.uiState` snapshot. Verified compile/ktlint/detekt/assembleDebug.
- ~~Onboarding copy (esp. PIN-loss / no-recovery warning)~~ **DONE (2026-06-12).** Added a one-time
  first-run **`WelcomeScreen`** (`ui/lock/WelcomeScreen.kt`), shown before PIN creation: app intro +
  three privacy points (local-only / encrypted+locked / yours-alone) + an **error-container callout**
  for the no-recovery warning, then a single "Get started" CTA. Gated in `MainActivity`'s
  `NeedsSetup` branch via a `rememberSaveable` `welcomed` flag (survives config changes; advances to
  the existing `SetupScreen`). Copy in `strings.xml` under a new `welcome_*` block. The inline
  no-recovery line on `setup_create_subtitle` stays as reinforcement. Verified assembleDebug + lint +
  ktlint + detekt. *(On-device visual check deferred — needs a fresh `pm clear` to reach NeedsSetup +
  the Pass-5 FLAG_SECURE-off procedure.)*
- ~~Finalize **license & distribution** ([DECISIONS.md](DECISIONS.md) O-2)~~ **DONE (2026-06-12).**
  License = GPLv3 (D-29); **distribution = F-Droid only** (D-32). Added F-Droid fastlane metadata
  (`fastlane/metadata/android/en-US/`: title, short/full description, `changelogs/1.txt`) and a
  **[docs/RELEASE.md](RELEASE.md)** cut-a-release + F-Droid submission guide (incl. the `app.tryst.yml`
  recipe template). **Open blocker noted in RELEASE.md:** the source repo is currently private — F-Droid
  needs it public before submission.
- ~~Security self-review against [THREAT_MODEL.md](THREAT_MODEL.md)~~ **DONE (2026-06-12).** Re-audited
  every THREAT_MODEL mitigation against live code after this session's broad i18n + ktlint churn — all
  intact: `FLAG_SECURE`, auto-lock (`TrystApplication` ProcessLifecycleOwner `onStop` + grace window),
  10-attempt self-wipe (`Vault.MAX_ATTEMPTS`), no `INTERNET`/`allowBackup=false`, **zero** `Log.*` in
  main, media never to MediaStore, importer MAGIC/version/iteration-bounds + `EncryptedMediaStore.fileFor`
  Zip-Slip guard. **One LOW finding fixed:** `BackupManager.restoreDatabase` bound row *values* via
  `ContentValues` but the *column names* came from the untrusted backup JSON straight into the framework's
  unquoted `INSERT` column list — possible SQL injection on a maliciously-crafted import (no exfil; bounded
  by AEAD-password + the importer's existing full DB access). Now vetted against a plain-identifier regex
  (`COLUMN_NAME`). (The heavier 12-pass program's final Pass 12 go/no-go has since passed — GO, conditional.)

### Late additions (2026-06-12)
- **Solo-aware editor** — with no partner selected, the editor hides "Who initiated" + "Acts — received"
  (per-partner orgasm counters already auto-hid) so it reads cleanly as solo; a solo save drops those.
- **Settings → General section** — app/how-it-works blurb, **Change PIN** (D-31), **auto-lock timeout**
  (D-31), **haptics** on/off (app-wide via `LocalHapticsEnabled`), **calendar week start** (Sun/Mon).

### Late additions (2026-06-13) — pre-release UX hardening
- **Unsaved-changes guard (data-loss fix, D-33)** — the partner add/edit dialog no longer dismisses on an
  outside-scrim tap (`DialogProperties(dismissOnClickOutside = false)`), and both it and the full-screen
  encounter editor now route back/Cancel through a **"Discard changes?"** prompt **only when the form is
  dirty**. Fixes the reported loss of a half-entered partner/encounter (and its just-taken photo) when a
  stray tap or back-swipe landed while the keyboard covered Save. Dirtiness =
  `EncounterEditViewModel.hasUnsavedChanges()` (uiState vs a load-time baseline) / a field comparison in
  the partner dialog; a clean form still closes silently (keeps predictive-back animation).
- **Reset-all on its own page with type-to-confirm (D-34)** — "Delete all data" moved off the main
  Settings scroll to a dedicated `settings/reset` page (`ResetDataScreen`); the erase button stays
  disabled until the user types **`DELETE`**. Far harder to fire by accident than the old inline
  red-button + single dialog.
- **"What's new" release notes + post-update popup (D-35)** — bundled `ui/whatsnew/ReleaseNotes.kt`,
  shown as a browsable screen (Settings → About) and a one-time popup on the first launch after a
  `versionCode` bump (fresh installs see nothing). Notes stay in sync with `CHANGELOG.md` + the F-Droid
  changelog files (RELEASE.md per-release steps). No version bump — folds into v0.1.0.
- **Partner demographics + a self profile (D-36, schema v7)** — partners gain DOB→age, ethnicity, height,
  body type, and location; a new single-row `profile` table gives the user their own photo + the same
  demographics, edited from Settings → Your profile and a pinned "You" card on Partners. Additive
  `MIGRATION_6_7` (+ profile table); `MigrationTest` extended to v1→v7; backup covers the profile row and
  its photo. Shared `DemographicFields`/`OptionalChips` keep the two editors identical.
- Verified: `assembleDebug`, `ktlintCheck`, `detekt`, `testDebugUnitTest` all green (the v1→v7
  `MigrationTest` is instrumented — its `CREATE TABLE` was checked byte-for-byte against the exported
  `7.json`, runs on a device).

> Sequencing rationale: security/storage foundations come **before** features so we never
> retrofit encryption onto plaintext data.

## Pre-release audit passes

A separate **12-pass pre-release program** (not tied to any milestone), each pass run in a fresh session
to keep the audit mindset critical. Full self-contained prompts live in
[PRERELEASE_PROMPT_PACK.md](PRERELEASE_PROMPT_PACK.md). Order: **UI → security → license/release**, so
code/dependency changes from earlier passes get re-checked. Status: **12 / 12 done — final verdict GO
(conditional), 2026-06-12.**

### UI (1–5)
- [x] **Pass 1 — Material 3 / Modern UI:** shared color/typography/shape tokens applied consistently
      (the theme was already strongly M3-compliant; only minor fixes).
- [x] **Pass 2 — Edge-to-edge & insets:** transparent system bars, per-screen inset handling, IME
      padding on the editor.
- [x] **Pass 3 — Motion & micro-interactions** (build + anti-leak green; driven on the emulator, no
      crashes across the full session). The app had no animations/haptics before this pass.
  - **Shared-element container transforms:** a history card — and the "+" FAB for a new entry — morph
    into the encounter editor and back, via one `SharedTransitionLayout` spanning the `NavHost`
    (`encounterSharedKey()` in `ui/common/SharedKeys.kt`).
  - **Predictive back:** `android:enableOnBackInvokedCallback="true"` + explicit `NavHost`
    enter/exit/pop fades that the system back-gesture seeks through.
  - **State-change animation:** `animateContentSize` on the editor form & partner dialog; `Crossfade`
    on History empty/calendar/list + the view-toggle icon; `animateItem()` on every list (History,
    Partners, Achievements, Insights sections); `AnimatedContent` stepper roll; animated calendar-day
    selection; `AnimatedVisibility` on Settings status/error text; animated achievement progress bars;
    a play-once **grow-in reveal** for bar/ranked/donut charts (guarded by `rememberSaveable` so it
    doesn't replay on scroll).
  - **Haptics** (`ui/common/Haptics.kt`, `HapticFeedbackConstants`): `confirm` on save / `reject` on
    destructive deletes & wrong PIN, `tick` on PIN digits / stepper / reorder slot-crossing, `pickUp`
    on drag start. Plus a wrong-PIN shake and reorder lift-scale spring.
  - **Ripple/pressed states:** cards switched to the `Card(onClick=…)` overload and PIN keys to
    `Surface(onClick=…)` for proper Material state layers; press-scale on keys.
- [x] **Pass 4 — Accessibility sweep** (`compileDebugKotlin` + `lintDebug` green; on-device TalkBack
      pass not possible — `FLAG_SECURE` blanks the screen-reader capture path, so verified via the
      semantics tree). Contrast was already AA-clean in both themes (no changes).
  - **Labels + roles + 48dp targets** for the bare-`clickable` glyph controls: photo-remove `×`
    (now `minimumInteractiveComponentSize()`, 48dp hit area / 22dp badge) & add-photo `＋`
    (`EncounterEditScreen`), PIN backspace `⌫` (`PinPad`); stepper `−`/`+` got "Decrease"/"Increase";
    `PinDots` exposes entry progress (count, not digits).
  - **Merged TalkBack stops** (`semantics(mergeDescendants)`): `EncounterCard` (+ practice badge now
    announces the headline act; `★`/`✨` pills carry spoken labels), `AchievementRow`, `StatCard`, and
    calendar `DayCell` (full date + today/has-trysts/selected state).
  - **Switch rows** (Settings "Material You", Partner "Anonymous") → `Modifier.toggleable(role=Switch)`
    so the label is associated and toggles.
  - **Chart a11y (O-6, partly closed):** `LineAreaChart` gained a `contentDescription` restating its
    canvas-drawn point values (bars/ranked/donut already expose label+count as real `Text`).
  - **Reorder is now TalkBack-operable:** `ReorderableColumn` rows expose Move-up / Move-down
    `CustomAccessibilityAction`s (long-press drag alone was invisible to screen readers).
  - **Font scaling:** `StatCard` fixed `height(92.dp)` → `heightIn(min = 92.dp)` (+ value ellipsis) so
    it grows instead of clipping at the largest font scale.
  - **Also fixed** 3 pre-existing `NonObservableLocale` lint **errors** (`HistoryScreen`
    MonthHeader/WeekdayLabels/DayCell) → read `LocalConfiguration.current.locales[0]` in composables.
  - **Residuals (reported, not fixed):** calendar day cells are ~43dp wide — a 7-column grid with 16dp
    side padding can't reach 48dp width on a 360dp screen (matches Material DatePicker's 40dp); edit-mode
    `ReorderableColumn` cards keep fixed heights (drag math) so can clip at the very largest font scale.
- [x] **Pass 5 — Adaptive layouts** (`assembleDebug` + `checkNoNetworkDebug` + `lintDebug` green;
      **visually verified on the emulator** at compact/medium/expanded widths — see the FLAG_SECURE note
      below). Tablet/foldable support without a `WindowSizeClass` dependency: a self-contained width
      bucket (`ui/common/WindowSize.kt`, `widthClass()` over `LocalConfiguration.screenWidthDp` at the
      standard 600/840dp Material breakpoints — recomposes on config change / fold-unfold, no extra lib,
      keeps the FOSS/no-network surface minimal).
  - **Adaptive navigation** (`TrystApp`): bottom `NavigationBar` on **compact**; a side
    `NavigationRail` on **medium + expanded**. Same four destinations; shared `navigateTop()` helper.
  - **Two-pane Trysts list/detail** on **expanded** width: `HistoryTwoPane` puts the tryst list and the
    encounter editor side by side (left list + `VerticalDivider` + right detail) instead of navigating —
    selecting a card (or +) fills the detail pane; an empty-state placeholder otherwise. Selection is
    `rememberSaveable` (survives config change). `EncounterEditScreen`'s shared-transition scopes were
    made **nullable** so it renders in the pane with no container-transform (the morph still runs in the
    compact full-screen path). Compact/medium keep the original navigate-to-editor + shared-element flow.
  - **No stretched screens:** single-column screens (Insights, Settings, Partners, Achievements, the
    full-screen editor) cap + centre content at 640dp via `Modifier.wrapContentWidth()
    .adaptiveContentWidth()` (a no-op on phones) so they don't run edge-to-edge on tablets.
  - **State retention:** the activity has no `configChanges` override, so resizes recreate it; verified
    the app stays **unlocked** across every resize (Hilt VMs + `SessionManager` survive; `lifecycle-process`
    doesn't treat config-change recreation as backgrounding, so no auto-lock fires) and the pane selection
    persists. No crashes/exceptions in logcat across all transitions.
  - **Verification note:** `FLAG_SECURE` normally blanks screenshots, so for this pass it was temporarily
    disabled in a debug build to capture the three width classes (rail, two-pane, width-capped Insights),
    then **restored** (confirmed the screenshot blanks again) — it ships on as the hard privacy constraint.

### Security (6–9)
- [x] **Pass 6 — Manifest & exported components** (OWASP MASVS) — **PASS, no fixes required**
      (release merged manifest generated + `checkNoNetworkRelease` green to verify, not assume).
  - **Exported components:** only `MainActivity` (`exported="true"`, MAIN/LAUNCHER) is app-exported, and
    it reads **no** intent extras/data — `onCreate` renders purely by `LockState`, so there's no
    intent-filter input to validate. `FileProvider` (`exported="false"`, per-use `content://` grant,
    cache-path only), `InitializationProvider`, and Room's `MultiInstanceInvalidationService` are all
    `exported="false"`. The one exported library receiver, `ProfileInstallReceiver`, is gated by
    `android:permission="android.permission.DUMP"` (signature|privileged → ADB/system only).
  - **Debug-only noise confirmed stripped from release:** `PreviewActivity` + androidx
    `ComponentActivity` (`exported="true"`) and `android:debuggable="true"` appear only in the **debug**
    merged manifest (from `debugImplementation` tooling); the **release** merged manifest has none of them.
  - **Permissions:** only `USE_BIOMETRIC` / `USE_FINGERPRINT` (normal-level, biometric unlock) plus the
    auto signature-level `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. **No dangerous permissions, no
    CAMERA** (capture uses FileProvider + camera-app intent), **no INTERNET** (anti-leak guard enforced).
  - **Backup / cleartext:** `allowBackup="false"` + `data_extraction_rules` exclude every domain from
    cloud-backup *and* device-transfer; no `usesCleartextTraffic` (targetSdk 36 defaults it false, and
    there's no networking regardless).
- [x] **Pass 7 — Secrets, storage & logging** (OWASP MASVS) — **PASS, one LOW fix applied.**
  - **No hardcoded secrets:** repo-wide grep for keys/tokens/passwords/credentials/PEM markers across
    `*.kt/xml/toml/gradle/kts/properties` found only crypto class names (`SecretKey(Spec)`), test markers,
    enum-token parsing, and the user-supplied **backup password** (never stored — derived via PBKDF2 in
    `BackupManager.export/import`). No `BuildConfig` fields, no committed signing/keystore creds.
  - **No sensitive logging:** **zero** `Log.*` / `println` / `printStackTrace` / Timber calls anywhere in
    `main`. Exceptions surface only as `e.message` written to **ephemeral UI state** shown to the already-
    unlocked user (lock/backup/CSV/settings VMs) — never logcat, never persisted. (Release R8 log-stripping
    is moot here, but stays Pass 11's job for any third-party lib chatter.)
  - **Storage:** all encounter data is in the SQLCipher DB; media blobs are AES-GCM (Tink) in
    `filesDir/media`. The only two `SharedPreferences` stores (`tryst_appearance`, `tryst_insights`) hold
    **non-sensitive** theme + Insights-layout prefs — no encounter content — so plaintext prefs are correct,
    and both are backup/transfer-excluded anyway. No DataStore.
  - **Keys in Keystore:** the random 256-bit DEK is **double-wrapped** — inner PIN-derived key (PBKDF2 600k)
    + outer non-exportable AndroidKeystore AES-GCM key (StrongBox→TEE fallback), persisted as ciphertext-only
    `vault.json`; biometric copy under an auth-required Keystore key in `bio.json` (ct+iv only). No key
    material ever hits disk in plaintext. Auth-token bullet is **N/A** (no network/accounts).
  - **LOW fix — orphaned plaintext camera captures:** the in-app camera writes a plaintext JPEG to
    `cacheDir/captures/` then encrypts→deletes it (`EncounterEditViewModel`/`PartnersViewModel`/`ImagePicker`).
    A process kill mid-capture could leave the temp behind, and **nothing swept it** — notably
    `SessionManager.deleteAllData()` (wipes `filesDir/media` only). Fixed: `deleteAllData()` now also wipes
    `cacheDir/captures`, and `openSession()` sweeps it on every unlock (safe — a live capture keeps the
    session open across the OS handoff, so any temp present at unlock is necessarily an orphan). Verified
    `compileDebugKotlin` green.
- [x] **Pass 8 — Network security** (MASVS) — **PASS, nothing to fix.** Confirmed zero network surface:
      no networking lib in `libs.versions.toml` (no Retrofit/OkHttp/Ktor/Volley); no `java.net`/`Socket`/
      `URLConnection`/`TrustManager`/`HostnameVerifier`/`SSLContext`/`WebView` anywhere in `app/src`. No
      `INTERNET`/`ACCESS_NETWORK_STATE`/`ACCESS_WIFI_STATE` in the merged **release** manifest (only
      `USE_BIOMETRIC`/`USE_FINGERPRINT` + the signature-level dynamic-receiver perm). No
      `network_security_config.xml` needed — with no INTERNET permission cleartext is moot, and targetSdk 36
      defaults `usesCleartextTraffic=false` regardless. Cert pinning N/A (no endpoints). Verified
      `checkNoNetworkRelease` exits 0 (build-time anti-leak guard green).
- [x] **Pass 9 — WebView & input validation** — WebView half **N/A** (no WebViews). Intent/deep-link
      surface **clean**: `MainActivity` reads no intent data; manifest is MAIN/LAUNCHER only (no custom
      scheme/host). DB **clean**: all DAO `@Query`s use bound `:params`; the `SELECT * FROM $table` dump in
      `BackupManager` interpolates only the hardcoded `TABLES` list. CSV import **clean**: values flow into
      parameterized Room inserts (duration digit-filtered, rating coerced 1–5, dates parsed in `runCatching`).
      **Two findings fixed in the backup-import path:**
  - **MED — Zip-Slip path traversal:** media blob ids came straight from the untrusted backup's ZIP entry
    names / `data.json` into `EncryptedMediaStore.fileFor("$id.enc")`; an id like `../../databases/tryst`
    could write outside the media dir. Fixed: `fileFor` now rejects empty/`.`/`..`/path-separator ids and
    verifies the resolved file's parent is exactly the media dir (canonical-path containment). Central guard
    so `save`/`open`/`delete` are all covered; legit ids are UUIDs so no behavior change.
  - **LOW — KDF DoS via crafted header:** the PBKDF2 iteration count was read from the (untrusted) file
    header and passed to `Pbkdf2.derive` unbounded; `Int.MAX_VALUE` would freeze the app for minutes. Fixed:
    `import` now requires `iterations in 100_000..5_000_000` (default export is 600k → round-trip unaffected).
  - Added regression tests in `BackupRoundTripTest` (`fileFor_rejectsPathTraversalIds`,
    `import_rejectsAbsurdIterationCount`); all 3 tests in the class pass on emulator via
    `connectedDebugAndroidTest`.

### License / release (10–12)
- [x] **Pass 10 — Dependency vulnerabilities & license compliance (2026-06-11):** CVE/outdated scan
      came back clean (no CVEs, no bumps needed — no networking/serialization libs in the tree); every
      dependency is GPLv3-compatible. **License decided: GPLv3** (resolves O-2, D-29). Added repo-root
      `LICENSE` (GPLv3) + `THIRD_PARTY_NOTICES.md` + in-app Settings → About licenses screen (`ui/about/`).
      Open follow-ups: per-file source headers (optional) and distribution choice (F-Droid/Play, M8).
- [x] **Pass 11 — Release build hardening (2026-06-11):** **PASS — release build verified end-to-end on
      the emulator under R8.** Config was already correct (`isMinifyEnabled` + `isShrinkResources` on
      `release`, `proguard-android-optimize.txt` + app rules); this pass *proved* it works at runtime, the
      part a green build can't show.
  - **R8 build clean:** `:app:assembleRelease` + `minifyReleaseWithR8` produce **zero** R8/ProGuard
    warnings — no "Missing class" notes, no `-dontwarn` needed. All third-party keep rules (Room 2.7.1,
    Hilt 2.57.1, Tink 1.15.0, SQLCipher 4.6.1) come from their bundled consumer rules; **no app keep rules
    required.** Serialization is `org.json` (manual key access, not reflection) so field renaming is a
    non-issue; persisted enum *names* round-trip via `Enum.valueOf`, which R8 preserves automatically.
  - **Runtime verification under R8 + obfuscation (the real test):** temporarily disabled `FLAG_SECURE`
    (Pass 5 procedure), rebuilt release, **debug-signed** the unsigned APK (zipalign + apksigner, debug
    keystore — *no* committed signing config) and installed on the Pixel 9 Pro XL emulator. Drove the full
    cold-start path: **PIN setup → Keystore double-wrap (PBKDF2 600k) + SQLCipher DB create/open → unlock →
    Trysts/Insights/encounter-editor**. Everything renders and the obfuscated DI/crypto graph resolves
    (logcat shows R8 names like `gd1.s()` running; SQLCipher `mlock errno=12` is benign emulator noise;
    **no** `FATAL`/`Exception`/`ClassNotFound`/`NoSuchMethod`). Enum-driven chips (Protection/Mood/Positions)
    prove the `valueOf`/`.label` round-trip survives obfuscation. Then **restored `FLAG_SECURE`**, rebuilt
    the shippable release, and confirmed `screencap` blanks to black again (PID alive). Working tree clean;
    temp APKs/screenshots removed.
  - **debuggable / logging:** no `android:debuggable` in the manifest → release defaults `false` (Pass 6
    confirmed it's absent from the release merged manifest); `proguard-rules.pro` strips `Log.v/d/i` via
    `-assumenosideeffects` (and Pass 7 already found zero `Log.*`/`println` in `main`).
  - **Signing — no leak (by absence):** there is **no signing config in the repo**, so the Gradle release
    artifact is `app-release-unsigned.apk` — zero keystore credentials committed (the debug-signing above was
    a throwaway for the emulator smoke test only). **Remaining release step (M8/Pass 12):** add a real
    signing config sourced from a **gitignored `keystore.properties`** (never inline creds); archive the R8
    `mapping.txt` per release for crash deobfuscation (it's already generated under
    `app/build/outputs/mapping/release/`).
  - **versionCode/versionName:** left at `1` / `0.1.0` — correct for the *first* release; bump on the next.
  - **Play Integrity API — assessed, deliberately NOT pursued:** it is **fundamentally incompatible** with
    Tryst's hard no-`INTERNET` constraint (attestation requires a network round-trip to Google's servers
    and ships Play Services). It also buys nothing here — there's no backend/account/entitlement to protect
    (fully local, single-user). Recommendation: **do not integrate** (would break the headline privacy
    guarantee and the anti-leak guard). Noted so Pass 12 doesn't revisit it.
- [x] **Pass 12 — Final pre-release checklist = GO (conditional), 2026-06-12.** Final go/no-go run in a
      fresh session. **Regression re-scan = clean:** no `INTERNET`/any permission in the manifest (only
      `MainActivity` exported — MAIN/LAUNCHER, reads no intent input; `FileProvider` `exported=false`);
      zero `Log.*`/`println`/`printStackTrace` in `src/main`; no hardcoded secrets (every `password` hit is
      user-supplied, PBKDF2-derived, never stored); `allowBackup=false` + `data_extraction_rules` exclude
      all domains (cloud-backup + device-transfer); no cleartext config needed (no networking). **License
      artifacts present + current:** root `LICENSE` (full GPLv3), `THIRD_PARTY_NOTICES.md`, in-app
      Settings → About (`ui/about/AboutScreen.kt` + `OssLicenses.kt`); per-file source headers deliberately
      omitted (D-10 ethos — README+LICENSE+notices satisfy GPLv3; not a blocker). **Release build verified
      end-to-end:** `checkNoNetworkRelease assembleRelease` = BUILD SUCCESSFUL, `minifyReleaseWithR8` zero
      warnings (only the known-benign Kotlin annotation-target KT-73255 warnings); produced
      `app-release-unsigned.apk` (~25.8 MB, no signing config = zero committed creds) + `mapping.txt`.
      **Launch smoke-tested on the actual R8 release binary** (not just debug): debug-signed the unsigned
      release APK (zipalign + apksigner, debug keystore — throwaway), installed on emulator, launched →
      `topResumedActivity=app.tryst/.MainActivity`, process alive, **zero FATAL/crash** in logcat; restored
      the normal debug build afterward. (Covers the runtime code that changed since Pass 11: WelcomeScreen,
      `EncounterEditViewModel` UiState refactor.) **TODO/FIXME/HACK = ZERO** across the whole `app/src`
      (main + tests). **Deferred (none release-blocking):** real signing config from gitignored
      `keystore.properties` (THE release step — by design not in repo); fdroiddata MR (manual GitLab step,
      post-Pass-12, runbook in RELEASE.md); localization (English-only v1); polish backlog (history
      filters, VACUUM secure-delete, Argon2id PIN KDF, Keystore monotonic attempt counter, persistent
      achievement celebration). **GO — conditional on the human-only checks an automated audit cannot
      cover:** real-device testing across OEMs/Android versions; visual + TalkBack/a11y runtime (FLAG_SECURE
      blanks the capture path — verified via code/lint/semantics only); dynamic analysis (MobSF on the
      release APK, recommended before public launch); biometric hardware flow on real fingerprint hardware.
      **All 12 passes now ✅.**
  - **Real-device pass on a Pixel 9 Pro XL (2026-06-12) — found + fixed TWO backup data-loss bugs.**
    Drove the R8 release build on real hardware: StrongBox **hard-proven** to back the vault key
    (instrumented `KeyInfo.getSecurityLevel()==STRONGBOX`, vs the emulator's TEE fallback); strong
    (Class 3) fingerprint enroll/unlock; encounter logging; real-camera photo with **zero** leakage to
    MediaStore/DCIM/shared storage; encrypted backup = maximal-entropy ciphertext. **Then backup
    RESTORE lost photos.** Root causes: (1) `EncryptedMediaStore.save` never recreated the `media/` dir,
    which `SessionManager.deleteAllData` removes — so the standard "delete all data, then restore"
    migration threw `FileNotFoundException` on the first media write (after `data.json` had already
    committed) and silently dropped every photo; (2) **partner avatars** are blobs referenced only by
    `Partner.photoMediaId` with no `media`-table row, so `export`'s `SELECT id FROM media` never included
    them — partner photos were absent from every backup. **Fixes:** `save` now `mkdirs` the dir; `export`
    gathers blob ids from both `media` rows and `partners.photoMediaId`. The shipped `BackupRoundTripTest`
    missed both (it only restored into a freshly-constructed store). Added `BackupRestoreRegressionTest`
    (restore-over-existing, restore-after-media-dir-wipe, partner-avatar-survives) — all green on the
    Pixel; **user-confirmed both photos return through the full wipe→restore flow.** These bugs exist in
    the `v0.1.0` tag → the tag must be re-cut on the fixed commit before any F-Droid submission.
