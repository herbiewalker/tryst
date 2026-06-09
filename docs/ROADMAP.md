# Tryst — Roadmap / Milestones

Status: **Draft v0.1** — high-level sequencing. Each milestone should end runnable & tested.

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
- [ ] Deferred: history **filters/search**, change-PIN UI, configurable auto-lock timeout.

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
- [x] **Curated catalog** (`data/achievements/Achievements.kt`, ~35): milestones (1→500 trysts), week
      streaks (2/4/8/12), variety (acts/positions/partners/places/kinks/toys, all weekdays, all months),
      pleasure (own/partner orgasm totals, 5-star nights), and occasion/misc one-offs (morning sex, makeup
      sex, quickie, special occasion, first photo, 60-min marathon). Emoji badges (swappable for art later).
- [x] **Unlock UI:** a dedicated **Achievements screen** (grouped by category; progress bars + unlock
      dates; a "New" ribbon for unlocks within ~14 days), opened by a **trophy icon** in the Insights top
      bar; plus a compact **teaser card** in the Insights scroll (unlocked count, recent unlocks, nearest
      in-progress) with "See all". `AchievementEngineTest` (JVM) green.
- [ ] Deferred: a persistent "just unlocked!" celebration (needs acknowledged-ids in encrypted storage).

## M8 — Polish & release prep
- A11y pass + **i18n: extract all hardcoded UI strings to `strings.xml`** (deferred "chunk 6").
- Optional cleanup: refactor large editor VMs to a single immutable `UiState` (deferred "chunk 6").
- Onboarding copy (esp. PIN-loss / no-recovery warning).
- Finalize **license & distribution** ([DECISIONS.md](DECISIONS.md)); F-Droid metadata if chosen.
- Security self-review against [THREAT_MODEL.md](THREAT_MODEL.md).

> Sequencing rationale: security/storage foundations come **before** features so we never
> retrofit encryption onto plaintext data.
