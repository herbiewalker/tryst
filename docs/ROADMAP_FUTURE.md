# Tryst — Future Roadmap (post-v0.1.0)

> **Status: ACTIVE roadmap (last reviewed 2026-06-28).** Originally a raw idea dump (2026-06-14); since
> then ENC-1 and the v0.2.0 QOL/calendar items have shipped, and a holistic review added an
> **Engineering, infra & housekeeping backlog** section (CI-1, STORE-1, etc.). Version groupings and
> ordering remain a *proposal* — reshuffle freely. See "Recommended near-term ordering" under that
> section for the current suggested sequence.

Every item preserves the **hard constraints** (no network ever, encrypted at rest, `FLAG_SECURE`,
local-only, additive/nullable migrations only). Anything that would violate them is out of scope by
definition.

---

## ⚡ ACTIVE: F-Droid policy compliance — configurable acts & kinks (decision [D-41](DECISIONS.md))

F-Droid review (linsui, 2026-06-29) flagged the bundled explicit **acts/kinks** as non-compliant and
asked to make them user-configurable. **Chosen path:** one clean app (no flavors) where explicit content
is **user data**, not compiled-in; the maintainer runs the F-Droid build, with zero data loss and full
search/insights/achievement functionality on custom entries (achievements already key off raw ids, so
this is cheap). Distinct from the rest of this roadmap — it's distribution-driven and blocks the MR merge.

| ID | Phase | Status |
|----|-------|--------|
| **FDP-1** | **Kinks → id-based & custom-capable** (parity with acts/positions): `KinkEntity`/`kinkDao`/`KinkRepository`, `kinks` column `Set<Kink>`→`Set<String>` (ids == old enum names → no data rewrite), Settings → Manage custom kinks, `resolveKink` in Insights, ENC-1 + achievements adapted. **schema v9 / `MIGRATION_8_9`.** | ✅ **DONE (2026-06-29).** Behaviour unchanged (built-ins still full); all gates + instrumented migration/backup tests green on emulator. |
| **FDP-2** | **Ship clean:** trim built-in act/kink catalogs to a small non-explicit starter set; migrate existing users' explicit built-in ids → custom entries (labels via **generic prettify** of the enum name, so the APK ships **zero** explicit strings). New installs start clean. | ✅ **DONE (2026-07-02).** **schema v10 / `MIGRATION_9_10`** — generic `CatalogAdoption` (no removed-id list in the APK; used-only adoption; restore self-heals via the same routine in `BackupManager.import`). Catalogs: Act 16/40, Kink 17/53 kept. Bonus: custom entries **renamable in place**; explicit-named icon drawables deleted; `Practice`→`Act` / `Setting`→`Place` uniformity renames. Full record in D-41. |
| **FDP-3** | **Release & F-Droid:** version bump, CHANGELOG/ReleaseNotes/fastlane, reply to linsui, update MR after release. | ⏳ Planned (next). |

*Scope = acts/kinks only (what was flagged). Positions/toys/ejaculation-locations left as-is unless a
follow-up review flags them.*

---

## The one architectural decision that shapes everything: a shared filter/query layer

Most of the big asks — **Search**, the **page-wide Insights scope (INS-2)**, the **Insights Explorer**,
the **Photo Gallery**, and **Granular erase** — are all the same primitive underneath: *"select a
subset of encounters by date range(s), partner, act/position/place, time-of-day, rating, etc."* The
**date range** in particular is the single most-reused filter (INS-2 makes it the default lens on the
whole Insights tab).

Rather than hand-roll filtering four times, build **one reusable filter/query layer** first (a
`EncounterFilter` data model + a pure-Kotlin query function over the log, mirroring how
`InsightsEngine` is a stateless derive). Then Search, the Explorer, the Gallery, and selective-delete
are each a thin UI on top of it. This is the "foundations before features" sequencing — it's why
v0.3.0 below is deliberately the filter layer, before the features that consume it.

**Industry-standard filter options** to support in that layer:
- **Date:** presets (this week / month / year / last 30/90/365 days / all time) **and** custom range,
  **and** multiple ranges / compare-two-periods.
- **Partner(s):** multi-select (incl. "solo").
- **Acts / positions / places / occasions / kinks / toys / protection:** multi-select.
- **Time-of-day** bucket, **day-of-week**, **rating** range, **duration** range, **has-photo**,
  **has-note / note contains**.
- Combinable (AND across categories), with a visible "active filters" chip row and a clear-all.

---

## Proposed releases

> **Tracking IDs** are stable handles for each item (they survive reordering); the **version** is just
> what ships to users. A whole theme ships as one minor release. Reserve `v0.2.1`/`.2` etc. for actual
> post-release **fixes**, not planned features.

### v0.2.0 — label cleanup, category fixes & haptics ✅ DONE (2026-06-21, bundled with the 4 post-tag features)

**SHIPPED in 0.2.0 / versionCode 2** (schema **v8**, `MIGRATION_7_8` + `MigrationTest.migrate7To8…`
verified on the emulator; assembleDebug/testDebugUnitTest/ktlint/detekt all green). FIX-1..8 below
landed as written; the 4 already-on-`main` features (calendar redesign, today ring, multi-select
ejaculation + shower, lingerie) were documented into CHANGELOG/ReleaseNotes/fastlane `2.txt`. *(Originally
captured 2026-06-20 as a "v0.1.1" batch — promoted to 0.2.0 because `main` already carried features past
the 0.1.0 tag.)* All items are
**history-preserving**: the DB stores enum **names** (not labels — see `Converters.kt`) and custom items
by `custom:<uuid>`, so wording edits are free, and the moves/promotions are handled by a single
**`MIGRATION_7_8`** (schema **v7→v8**) plus a `7→8` migration test. There is **no built-in seeding** into
the `acts`/`positions` tables (built-ins live only in the enums), so renames/promotions can't collide on
the unique-label index. **Restore inserts raw rows and does *not* replay migrations** (`BackupManager`),
so after upgrading on-device, **re-export to refresh the canonical TRYSTBK1 backup** or the old strings
return on a future restore.

| ID | Item | Mechanism / notes |
|----|------|-------------------|
| **FIX-1** | **Oral position wording** — Kneeling oral → **"Oral - Kneeling"**; Lying-back oral → **"Oral - Laying down"**; Standing oral → **"Oral - Standing"** | Label-string edit in `Enums.kt` `Position` only. Zero data change. |
| **FIX-2** | **"Ball sucking / teabagging" → "Ball sucking / ball play"** | Label-string edit in `Enums.kt` `Practice` only. Zero data change. |
| **FIX-3** | **Delete `ORAL_69_SIDE`** ("Oral, side-by-side"); remap existing encounters → `LYING_ORAL` | `MIGRATION_7_8`: `REPLACE(positions,'ORAL_69_SIDE','LYING_ORAL')` on `encounters` (set semantics dedupe on read). Remove the enum constant. Only other ref is the Python dataset generator. |
| **FIX-4** | **Move `WATCHING_PORN` from `Practice` (acts) → `Kink`** | `MIGRATION_7_8`: add `WATCHING_PORN` to `encounters.kinks` where present in `practicesPerformed`/`practicesReceived`, then strip it from both practice columns (comma-list removal). Add `Kink.WATCHING_PORN`, remove `Practice.WATCHING_PORN`, remove its `PracticeVisuals` ordering/icon entry. |
| **FIX-5** | **Add 5 built-in `Position` options** (additive enum values) | New `Position` enum constants in `Enums.kt` — additive, no migration. Any pre-existing custom entry with a similar name simply stays custom. *(A one-off promotion of the maintainer's own custom entries to these built-ins ran once on-device and was kept out of the shipped migration.)* |
| **FIX-6** | **Add 2 built-in `Practice` (act) options** (additive enum values) | Same as FIX-5, on the `Practice` enum. |
| **FIX-7** | **New location** `Setting` **"Friend / family's place"** (for sex at family/friends' homes) | Additive enum constant near HOME/HOTEL/AIRBNB. Decided 2026-06-20 (chosen over "Guest house"). No migration. |
| **FIX-8** | **Haptics not firing when enabled** | `Haptics.kt`: call `view.performHapticFeedback(c, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)`. Wiring is correct (pref → `LocalHapticsEnabled` → call); the View's haptic flag silently suppresses the bare call on most devices. **Caveat:** if the device's system "vibrate on touch" is off, the OS overrides regardless — not app-fixable. Verify on device. |

**Also part of this patch:** bump DB version 7→8 + add to `ALL_MIGRATIONS`; add the `7→8` `MigrationTest`;
update `tools/dataset/generate_dataset.py` (drop `ORAL_69_SIDE`, move `WATCHING_PORN`); and update
`CHANGELOG.md` + in-app "What's new" (`ReleaseNotes.kt`) + F-Droid changelog when the release is cut.

### v0.2.0 — QOL & polish *(small, low-risk, high-felt-value; no schema change)*
| ID | Item | Current state | Proposed |
|----|------|---------------|----------|
| **QOL-1** | **Calendar: swipe to change month** | ✅ **DONE (2026-06-14).** | Horizontal swipe on the grid pages month/week (`Modifier.horizontalSwipe`, ~48dp threshold); arrows kept for a11y. Shipped as part of the calendar redesign (D-38). |
| **QOL-2** | **Calendar: today indicator** | ✅ **DONE (2026-06-14).** | Verified it rendered (bold + primary text + "today" a11y label) but found it too subtle vs logged days (which also use primary), so added an **outline ring** around today's cell when it isn't the selected day (`DayCell` `border`). |
| **QOL-3** | **Partner editor → its own page** | Partner add/edit is an `AlertDialog` popout; the **self-profile is already a full-screen page** (`ProfileScreen`). | Convert the partner editor to a full-screen route to match. Reuse `DemographicFields`/`OptionalChips`; carry over the existing discard-changes guard (D-33). Net effect: "you" and "a partner" edit identically. |
| **QOL-4** | **Updated icon set** | Launcher icon + per-act vector icons (`ic_act_*.xml`). | New launcher icon (adaptive + **themed/monochrome** for Material You), refreshed act icons. Largely a design/drawable swap — act icons are already a drawable-swap-only seam (`PracticeVisuals`). Design brief: [design/ICON_PROJECT_PROMPT.md](../design/ICON_PROJECT_PROMPT.md). Can land in any release. |
| **QOL-5** | ⭐ **RECOMMENDED NEXT FEATURE (2026-06-28 review).** **App settings in the backup** (survive reinstall / new phone) | The encrypted backup restores DB + media but **deliberately excludes** the three settings stores — `tryst_appearance` (theme), `tryst_insights` (Insights customization), `tryst_general` (haptics/auto-lock/week-start) — the prefs classes' own comments say "excluded from backup/transfer". Note: a same-device **"Delete all data" does *not* clear these prefs** (`SessionManager.deleteAllData` wipes DB/keys/media only), so settings survive an in-app reset; they're lost on **reinstall / new phone / OS clear** (`allowBackup=false` → no cloud backup). | Add a non-sensitive `settings.json` (the three prefs as key→value) to the encrypted backup container so **theme + Insights layout + general prefs restore on a new device / reinstall**. The Insights customization (reorder/hide cards, per-card chart styles) is the highest-value thing to preserve — it's real user effort. Restore reapplies **known keys only** (skip-unknown, forward-compatible). Bump the export-format version (additive/optional section; older apps ignore it). **Never** include the PIN / vault / biometric config. Optional: an "include settings" toggle on export, and a separate *"Reset preferences to default"* action on the reset page (since data-reset currently leaves them). |
| **BKP-1** | **Automatic local backups** *(added 2026-06-20)* | Today the only backup path is a **manual** encrypted export (`BackupManager.export`). | Scheduled on-device encrypted backups, **no network** (local files only — preserves the no-INTERNET invariant). Open design Qs: **unattended keying** — auto-backup can't prompt for the backup password each run, so either derive/store a dedicated key or reuse the vault DEK (security trade-off to settle); **retention/rotation** (keep last N, prune old); **storage location** — app-internal (lost on uninstall) vs a user-picked SAF folder (survives uninstall, but a folder handle is a small external surface); **trigger/cadence** (on app close / daily / after N new encounters). Pairs naturally with **QOL-5** (settings-in-backup). Must keep every hard constraint. |
| **ENC-1** | ✅ **DONE (2026-06-28, unreleased on `main`).** **Most-used options auto-surface in the editor** *(added 2026-06-20)* | The inline "common" set per category is a **hardcoded curated list** (`ActOptions`/`PositionOptions` `COMMON_IDS`); everything else is hidden behind **"More"**, so frequent picks need an extra tap every time. | Surface the user's **most-frequently-picked** options inline so frequent choices (e.g. *Vasectomy* in Protection) appear without opening "More". **Read-side only** — frequency derived from the existing log; **no schema change**. **Shipped design:** new pure `OptionUsage.from(log)` tallies per-category pick counts (JVM-tested, like `InsightsEngine`); `mostUsedCommon(curated, all, usageOf)` builds each field's inline set = **most-used first (≥`INLINE_MIN_COUNT`=2 uses), capped at `INLINE_TARGET`=8, with the curated set backfilling** when there aren't enough frequent picks (so new logs still show today's curated defaults; nothing is ever removed from reach — full set stays in "More…"). Applied to **all** common/More categories: acts, positions, protection, mood, ejaculation, kinks, places, toys, occasions. **Open Qs resolved:** *replace-with-backfill* (not pure augment, keeps the row tight); count cap = 8; pure **frequency** (no recency); **ordering stability is free** because `SelectionField` already re-sorts the inline set alphabetically, so frequency only shifts slow-moving membership, never visibly reshuffles. Editor VM observes the log → `StateFlow<OptionUsage>` (off-main, empty fallback on lock). |
| **ENC-1a** | **Tune most-used surfacing after dogfooding** *(follow-up to ENC-1, added 2026-06-28)* | Shipped with `INLINE_MIN_COUNT=2`, `INLINE_TARGET=8`, pure-frequency, replace-with-backfill (constants in `OptionUsage.kt`). | Revisit on a real log: does a category feel slow to "learn" (lower `MIN_COUNT` to 1) or too crowded (lower `TARGET`)? Consider light **recency** weighting if stale picks dominate. No code structure change — just constant/weighting tweaks. Low priority; decide from on-device feel. |

### v0.3.0 — Search & the filter foundation *(the enabling layer)*
| ID | Item | Proposed |
|----|------|----------|
| **FILT-1** | **Shared `EncounterFilter` query layer** | The reusable filter model + stateless query described above. Pure-Kotlin, JVM-testable, no schema change (it queries the existing log). |
| **SRCH-1** | **Global search** | A search entry (on Trysts, and/or app-wide) that filters encounters by free text (notes, partner names, act/position/place labels) on top of the filter layer. First consumer of the layer. |

### v0.4.0 — Insights Explorer & scope *(the big one)*
| ID | Item | Current state | Proposed |
|----|------|---------------|----------|
| **INS-2** | **Page-wide time scope** *(do this first — simpler, high-value)* | All Insights stats are computed over the **entire** log; no time selector. | A scope selector at the top of the Insights tab that **re-computes every stat/chart/tile** for the chosen window. **Default = current calendar year**, with options: pick a **specific year**, **all time**, or a **custom date range** (+ the standard presets). Implementation is light: feed a date-bounded subset of encounters into the existing `InsightsEngine.compute()`; everything downstream already reflects its input. |
| **INS-1** | **Clickable category cards → drill-down page** | Insights cards are display-only; the engine already computes a rich **orgasm drill-down** (you-vs-partner, per-partner, over-time, finish). | Tapping a category card (e.g. Orgasms, Acts, Partners) opens a **dedicated detail page**: all stats for that category, with the **full filter set** (multi/range dates, people, time-of-day, …), adjustable chart style (reuse the per-card Bars/Line/Donut), and searchable. Generalize the existing orgasm drill-down into a per-category pattern, fed by the v0.3.0 layer. The page inherits the **INS-2 scope** as its starting filter, then refines. |

**Product rules / design notes for the Insights scope (INS-2):**
- **Achievements are exempt — always all-time / cumulative.** They represent lifetime progress, so the
  scope selector must **not** apply to the Achievements section (keep feeding the full log to
  `AchievementEngine`). Make this visually clear (the scope chip sits on the stats sections, not the
  Achievements teaser/page).
- **Streaks need a decision:** "longest streak" naturally respects the window, but "current streak" is
  inherently all-time. Proposal: show *longest-in-range* under a scope, and only show *current* streak
  in the all-time view (or label it explicitly).
- **Persistence:** decide whether the chosen scope is remembered (persist last choice in
  `InsightsPreferences`) or resets to "current year" each visit. Default-to-current-year on launch is
  the safest, most predictable behaviour.
- **Empty windows — DECIDED: empty-state, do NOT auto-hide cards.** A scoped window with no data
  keeps its card and shows a compact empty state (e.g. *"No encounters in 2021"*), rather than the card
  vanishing. Rationale: (1) **layout stability** — cards popping in/out as the scope changes feels
  broken and loses the user's place; (2) **a zero is signal** in a tracker (a real example: the
  dataset's 2021 gap — auto-hide would blank the page into confusion); (3) it respects the **existing
  manual show/hide** (`InsightsPreferences`) instead of overriding it on data state; (4) **empty is
  teachable** — *"No toys logged in 2024"* shows what's trackable and invites action.
  - *List items within a breakdown* already omit zeros (a per-act breakdown lists only acts used) —
    keep that; the rule is about whole **cards/sections**, which persist.
  - Optional **opt-in** toggle *"Hide empty cards for this range"* (default **off**) for users who
    prefer the tidy view — keeps the predictable default while offering the choice.
- INS-2 shares the date primitive from **FILT-1** (v0.3.0) — same reason the filter layer is built
  first. It could even land in v0.3.0 if you want the scope before the full Explorer.

### v0.5.0 — Photo & video gallery *(reuses decrypt-in-memory + the filter layer)*
| ID | Item | Proposed |
|----|------|----------|
| **GAL-1** | **Gallery by person / date** | A browsing view aggregating all encrypted photos (encounter photos, partner avatars, profile), grouped/filtered **by partner or by date** via the filter layer. Reuses `MediaImages` (decrypt in-memory only); `FLAG_SECURE` already protects it. Watch list-scroll performance with many photos (thumbnail sampling/caching). No schema change — photos already link to encounters/partners. |
| **MED-1** | **Video attachments** *(added 2026-06-20)* | Let an encounter attach **video** alongside photos. The storage model already fits: `MediaEntity` carries a `mimeType` (the table is media-generic, not photo-specific), and `MediaCrypto` is Tink `AesGcmHkdfStreaming` — which **supports a *seekable* decrypting channel**, so video can be **decrypted-and-seeked on the fly** rather than decrypted fully into memory (photos can decrypt in-memory; videos can't). **Pieces:** (1) **capture/import** — extend `ImagePicker` to accept video MIME types, and add a video-record mode to the in-app camera (same FileProvider-temp → encrypt → delete pattern; temps already land in `cacheDir/captures`, already swept on unlock + `deleteAllData`); (2) **playback** — **Media3/ExoPlayer** with a **custom `DataSource`** backed by Tink's seekable channel over `EncryptedMediaStore` (no plaintext ever hits disk; `FLAG_SECURE` already blanks the surface + app-switcher); (3) **thumbnails** — extract a frame via `MediaMetadataRetriever` over the decrypting stream for the gallery grid + a play-badge overlay. **No schema change.** **Watch-outs:** Media3 ExoPlayer is a **new dependency** — must be **FOSS (Apache-2.0) and used local-only** (no DRM/network modules; the `checkNoNetwork*` guard + CI still apply); backup container **size balloons** (the ZIP holds decrypted bytes re-encrypted — consider a per-file size cap and an export size warning); larger temp files raise the orphaned-plaintext stakes (the sweep already covers it, re-verify). |
| **GAL-2** | **Gallery includes video** *(added 2026-06-20)* | Once **MED-1** lands, the gallery (GAL-1) surfaces videos beside photos: frame thumbnails with a play badge, tap → inline encrypted playback. Pure UI on top of MED-1 + the filter layer; no schema change. |

### v0.6.0 — Granular data management *(privacy feature; reuses the filter layer)*
| ID | Item | Current state | Proposed |
|----|------|---------------|----------|
| **DEL-1** | **Selective erase** | Settings → only **"Delete all data"** (`ResetDataScreen`, type-DELETE gated). Single-entry delete exists in the editor. | Add scoped deletes: **by date range**, **by partner**, and **bulk-select** entries — choosing the set via the filter layer, each behind a confirm gate. Tie into the deferred **VACUUM-on-delete / secure-delete** hardening so removed rows don't linger in the encrypted DB pages. |

---

## Engineering, infra & housekeeping backlog *(from the 2026-06-28 holistic review)*

> These are **not** user-facing feature releases — they're code/infra/distribution quality items that
> don't map onto a version theme. They can land in any release (or between releases). Ordered by value.
> The codebase is in good shape; these are polish on a solid base.

| ID | Item | Current state | Proposed | Effort / priority |
|----|------|---------------|----------|-------------------|
| **CI-1** | ✅ **DONE (2026-06-28).** **Run instrumented tests in CI (the data-safety net)** | Was: CI ran `assembleDebug testDebugUnitTest lint checkNoNetworkDebug` + a banned-SDK scan; the `MigrationTest`/backup/crypto/vault tests ran only locally. | Added an **`instrumented`** job to `.github/workflows/ci.yml`: KVM-accelerated `reactivecircus/android-emulator-runner@v2` (API 34, `google_apis` x86_64) running the **full `connectedDebugAndroidTest`** suite on push/PR — migrations, backup round-trip + regression, on-disk DB encryption, media crypto, vault. StrongBox is absent on emulators → vault uses the TEE-Keystore fallback (handled in code). ✅ **Verified green** — CI run #77 on `4bf5edf` passed the full suite first try, no flakiness (no need to narrow to a subset). | Done. |
| **STORE-1** | **F-Droid listing screenshots** | `fastlane/metadata/android/en-US/` has title + descriptions + changelogs but **no `images/phoneScreenshots/`** — the F-Droid page gallery is empty. | Capture 4–6 phone screenshots (lock, calendar, an entry, Insights) via the documented `FLAG_SECURE`-off procedure (RELEASE.md / Pass-5 note), drop them in `images/phoneScreenshots/`. Optional: a `featureGraphic`. Pure listing/discoverability win, no code risk. | **HIGH.** Low effort. |
| **DOC-1** | **README status drift** | ✅ **DONE (2026-06-28).** README said *"the v0.1.0 submission is in review"* (stale). | Updated to: v0.2.0 shipped + in the F-Droid test queue; ENC-1 + calendar-default-view unreleased on `main`. | Done. |
| **CLEAN-1** | ✅ **DONE (2026-06-28).** **Delete scaffold tests** | Was: `ExampleUnitTest.kt` (asserted `2+2==4`) + `ExampleInstrumentedTest.kt` (Android Studio template leftovers). | Both removed; unit + androidTest sources still compile. | Done. |
| **LIC-1** | **SPDX per-file license headers** | 0 of 99 main files carry an SPDX identifier. | Add `// SPDX-License-Identifier: GPL-3.0-or-later` to each source file (scriptable one-time pass). FSF-recommended for GPLv3 and what license/reuse scanners expect — reinforces the "verifiably open" pitch that *is* this app. Was consciously deferred as optional in Pass 10. | **LOW–MED.** Scriptable. |
| **TEST-1** | **A few Compose UI smoke tests** | `androidx.ui.test.junit4` is on the classpath but unused beyond the example. | Add a handful of happy-path Compose tests (lock → unlock → add-entry → it appears). `FLAG_SECURE` doesn't block Compose tests (they read the semantics tree), so these are feasible and catch navigation/state regressions. | **MED.** |
| **SEC-1** | **Argon2id for the export passphrase** | The encrypted backup container derives its key with **PBKDF2-HMAC-SHA256 (600k)**; `SECURITY_DESIGN.md` *reserves* Argon2id for the export passphrase but it was never adopted. (Distinct from the deferred "Argon2id **PIN** KDF" idea in ROADMAP.md.) | Upgrade the export container KDF to **Argon2id** (OWASP's first-choice memory-hard KDF), versioned so older `.tryst` files still restore. Adds one FOSS dependency; changes the backup format additively. | **MED.** Vet the dep (FOSS, local-only) like Pass 10. |
| **PERF-1** | **Baseline Profile** | None. Cold start is unoptimised (small app, so likely fine). | Add a `:macrobenchmark` module + generated baseline profile for faster cold start / smoother first frames. Standard for polished apps; lower priority for a small local F-Droid app. | **LOW.** |
| **REFAC-1** | **Opportunistic large-file extraction** | `HistoryScreen` (769 LOC), `EncounterEditScreen` (689), `SettingsScreen` (680) are the biggest files. Not problematic, just large. | Extract sub-composables **only when next editing these files** — not as dedicated work. | **LOW / opportunistic.** |

**Recommended near-term ordering (2026-06-28 review):** ~~CI-1~~ ✅ + **STORE-1** first (protect data integrity + adoption; neither touches app logic) → ~~CLEAN-1~~ ✅ / **LIC-1** tidy-up (DOC-1 ✅) → then **QOL-5** as the next user-facing feature. **TEST-1 / SEC-1 / PERF-1** as capacity allows; **REFAC-1** opportunistically. *Remaining quick wins: STORE-1 (screenshots), LIC-1 (SPDX headers).*

## Relationship to the existing deferred backlog (ROADMAP.md / DECISIONS.md)
- **Search** and **history filters** were already noted as deferred → now formalized as v0.3.0.
- **Selective erase** pairs with the deferred **VACUUM-on-delete secure-delete** hardening.
- **Today indicator** appears to already be implemented — verify before scheduling.
- New user-facing strings from all of the above fold into the deferred **Localization** milestone
  (English identity keys already in `strings.xml`).

## Cross-cutting considerations
- **Schema impact:** most items are **read-side** and need **no migration** (search, explorer, gallery
  are derived; selective-erase is deletes). Icons = none. **QOL-5** touches the **backup/export format**
  (not the DB schema) — version that format additively (`EXPORT_FORMAT.md`), and restore must skip
  unknown keys for forward-compat. If any item later needs a column, follow the standard rule (version
  bump + additive migration + migration test).
- **Privacy invariants:** all items stay local, no-network, encrypted, `FLAG_SECURE` — no new
  permissions, no new external surface.
- **New dependency (MED-1 only):** video playback needs **Media3/ExoPlayer** — the first runtime
  media/playback dep. Vet it like Pass 10: FOSS (Apache-2.0), pull **only** the core + UI playback
  modules (no DRM, no cast, no network/HLS-DASH-Smooth-Streaming extensions), confirm the
  `checkNoNetwork*` guard + CI still pass with it on the classpath, and add it to
  `THIRD_PARTY_NOTICES.md` + the in-app About list. Everything else on this roadmap stays
  dependency-free.
- **Testability:** keep the filter/query logic pure-Kotlin (like `InsightsEngine`) so it's JVM-tested
  without Robolectric.

## Open questions for you to decide
1. **Ordering** — is the QOL batch (v0.2.0) first, or do you want the Insights Explorer sooner? (It's
   the most work but the highest-value; it just needs the filter layer first.)
2. **Icon set** — do you have the new art, or is producing it part of the work?
3. **Selective erase scope** — by date + partner is clear; do you also want "delete everything *with*
   X act/place" (full filter-driven delete), or keep it simpler?
4. **Version cadence** — happy with one feature theme per minor version, or bundle more per release?
