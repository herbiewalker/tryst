# Tryst — Roadmap / Milestones

Status: **Live (2026-06-08)** — M0–M7 complete and verified on the emulator; **M8 (polish & release)**
is the only milestone remaining. Each milestone ends runnable & tested.

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
- **i18n: extract all hardcoded UI strings to `strings.xml`** (deferred "chunk 6"; the a11y half of this
  item is done — see Pass 4 below).
- Optional cleanup: refactor large editor VMs to a single immutable `UiState` (deferred "chunk 6").
- Onboarding copy (esp. PIN-loss / no-recovery warning).
- Finalize **license & distribution** ([DECISIONS.md](DECISIONS.md) O-2); F-Droid metadata if chosen.
- Security self-review against [THREAT_MODEL.md](THREAT_MODEL.md) (complements the security passes 6–9 below).

> Sequencing rationale: security/storage foundations come **before** features so we never
> retrofit encryption onto plaintext data.

## Pre-release audit passes

A separate **12-pass pre-release program** (not tied to any milestone), each pass run in a fresh session
to keep the audit mindset critical. Full self-contained prompts live in
[PRERELEASE_PROMPT_PACK.md](PRERELEASE_PROMPT_PACK.md). Order: **UI → security → license/release**, so
code/dependency changes from earlier passes get re-checked. Status: **5 / 12 done.**

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
- [x] **Pass 5 — Adaptive layouts** (tablet/foldable via `WindowSizeClass`; two-pane list/detail on
      expanded width). Was marked optional; built out the full two-pane refactor on request.
      **Not built/run in this session** — the Claude-on-the-web container has no Android SDK, so compile
      verification rides on CI (`assembleDebug testDebugUnitTest lint checkNoNetworkDebug`); on-device
      tablet/fold behaviour still needs a human pass. Pure layout change — no new permissions
      (`material3-window-size-class` is a BOM-managed layout helper, no network).
  - **Size class plumbing:** `MainActivity` computes `calculateWindowSizeClass(this).widthSizeClass`
    (recomputed across fold/rotate/resize) and passes it into `TrystApp(widthSizeClass)`. New
    BOM-managed dep `androidx.compose.material3:material3-window-size-class`.
  - **Adaptive navigation shell** (`TrystApp`): **compact** → bottom `NavigationBar`; **medium /
    expanded** → side `NavigationRail` (each owns its own system-bar insets). Single `navigateTopLevel`
    helper preserves each tab's saved back stack for both.
  - **Two-pane list/detail on expanded width:**
    - **Trysts ↔ editor** (`HistoryPane`): list (`LIST_WEIGHT`) + `VerticalDivider` + editor
      (`DETAIL_WEIGHT`). Selection is a `rememberSaveable` string (`edit:<id>` / `new:<nonce>`); the
      detail `EncounterEditViewModel` is **keyed** on it (`hiltViewModel(key = …)` + `key(sel)`) so every
      selection — including a fresh "+" — gets a clean form (`load()` early-returns on a null id, so a
      reused VM couldn't reset). Empty state shows a `DetailPlaceholder`.
    - **Insights ↔ Achievements** (`InsightsPane`): dashboard + the full Achievements list side by side.
      `InsightsScreen(twoPane = true)` hides the now-redundant top-bar Achievements action and the
      in-list teaser; `AchievementsScreen(showBack = false)` drops the back arrow as a permanent pane.
  - **No stretched phone layouts:** single-pane screens (Partners, Settings, the full-screen editor /
    Achievements / Customize routes, and History/Insights on medium width) are centred in a
    `CenteredPane` max-width lane (840dp) on medium/expanded; compact is a transparent pass-through so
    the container-transform morph is untouched.
  - **Morph made optional:** `HistoryScreen` / `EncounterEditScreen` `sharedScope`/`animatedScope` are
    now nullable — the card/FAB→editor container transform runs in single-pane only; the two-pane detail
    is already on screen, so no morph.
  - **Less state loss on config change:** `calendarMode` (History) and `editMode` (Insights) toggles
    upgraded `remember` → `rememberSaveable`, alongside the saveable pane selection.
  - **Residuals (reported, not fixed):** folding expanded→compact while the editor detail pane is open
    drops back to the list (compact can't show two panes — selection survives but isn't re-navigated);
    `LazyColumn` scroll positions still use default (non-saveable) state, so they reset on rotation as
    before; the editor reuses *Material* full-form layout in the detail pane rather than a denser tablet
    form.

### Security (6–9)
- [ ] **Pass 6 — Manifest & exported components** (OWASP MASVS): exported flags, intent-filter input
      validation, `debuggable`/`allowBackup`/cleartext, unnecessary dangerous permissions.
- [ ] **Pass 7 — Secrets, storage & logging** (MASVS): no hardcoded secrets; no sensitive data in logs
      or plain prefs; Keystore-backed encryption. *Strong fit with the existing privacy/crypto design.*
- [ ] **Pass 8 — Network security** (MASVS). *Expected near-trivial — the app declares **no INTERNET
      permission** and does no networking; confirm there's no trust-all / cleartext config and move on.*
- [ ] **Pass 9 — WebView & input validation.** *WebView half is N/A (no WebViews);* still re-check the
      intent / deep-link / file-picker input paths.

### License / release (10–12)
- [ ] **Pass 10 — Dependency vulnerabilities & license compliance:** CVE/outdated scan + safe bumps;
      confirm every dependency's license is compatible with the chosen license; LICENSE file + notices
      screen. Gated on **O-2** (license still undecided — the prompt pack assumes GPLv3; confirm first).
- [ ] **Pass 11 — Release build hardening:** enable R8 minify + resource shrinking, write/repair
      `proguard-rules.pro`, confirm `debuggable=false` + debug logging stripped, bump version, protect signing.
- [ ] **Pass 12 — Final pre-release checklist:** go/no-go — re-scan for regressions (exported components,
      secrets, cleartext, sensitive logging), confirm license artifacts, R8 release build launches, triage
      remaining TODO/FIXME, then summarize resolved / deferred (with risk) + a final recommendation.
