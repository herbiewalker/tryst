# Changelog

All notable changes to Tryst are recorded here. The format is loosely based on
[Keep a Changelog](https://keepachangelog.com/). Each released version must stay in sync across
three places:

- this file,
- `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` (F-Droid release notes), and
- `ReleaseNotes.all` in `app/src/main/java/app/tryst/ui/whatsnew/ReleaseNotes.kt` (in-app "What's new").

On every release: bump `versionCode`/`versionName` in `app/build.gradle.kts`, add the new fastlane
`<versionCode>.txt`, prepend a `ReleaseNote` (newest first), and add a section below.

## [Unreleased]

## [0.4.0] — 2026-07-11 (versionCode 6)

### Added
- **Search.** A search icon on Trysts opens a dedicated search screen. Type to match against a tryst's
  note, its partners, and its acts, positions, places, occasions, kinks, toys, and mood. Multi-word
  queries match across fields in any order, and matching ignores case and accents (`cafe` finds *Café*).
- **Search filters.** Narrow results by date window (last 7 / 30 / 90 days, this year, or a **custom
  date range**), partner, rating, and whether the tryst has a photo. Sort by newest, oldest, highest
  rated, or longest. When nothing matches, Tryst offers to widen whichever filters are active.
- **More filters.** A "Filters" chip opens a sheet with the rest of what you can narrow by: acts,
  positions, kinks, toys, occasions, place, protection, mood, who started it, weekday, time of day,
  a duration range, whether the tryst has a note, and whether to include solo trysts. Each toggle
  applies immediately and the sheet shows a running "Show N results"; the chip carries a count of how
  many extra filters are on, and "Reset" clears just these without touching your date/rating/partner
  chips.
- **Results expand in place.** Tap a result to see the whole tryst — full note, every category, and its
  photos — without opening the editor. Because search looks at fields the card doesn't show, each result
  says which field your query hit, and the matched words are highlighted.
- **Recent searches**, stored in the encrypted database like the rest of your data — never in plain
  settings files, and deliberately **never included in an exported backup**. Only searches you actually
  submit are remembered, the last 8 are kept, and you can remove them individually or clear them all.

- **An Insights time range.** Controls at the top of Insights recompute every stat and chart for the
  window you pick — **year, then quarter, then a custom date range** if you need one. Your choice is
  remembered. Charts now show the window's own months rather than always the last twelve.
  - **Search narrows dates exactly the same way**, with the same three controls.
  - **Achievements are never scoped** — they always count your whole history, and say so.
  - Tiles that can only mean "as of today" (*This month*, *This year*, *Current streak*, *Days since
    last*) step aside while a range is selected, instead of quietly reporting something else.
  - A range with no trysts says so ("No trysts in 2021") rather than looking like an empty app, and a
    section with nothing in it keeps its card ("Nothing logged in 2024") instead of vanishing.

### Internal
- New shared `EncounterFilter` query layer (`data/filter`) — one reusable, JVM-tested way to select a
  subset of the log by date/partner/category/rating/duration/photo/note. Search and the Insights scope
  are its first consumers; the photo gallery and selective erase are next.
- `InsightsEngine.compute()` takes a `scope: DateRange?`. Several figures were computed against *today*
  rather than against their input (trailing-12 month buckets, per-month average, streak, days-since), so
  a scope had to reach the engine rather than merely filter what was handed to it.
- Schema **v13** (`MIGRATION_12_13`): adds the `recent_searches` table. Additive DDL only; no existing
  row is touched.

## [0.3.2] — 2026-07-03 (versionCode 5)

### Changed
- **Every category is now fully yours — nothing ships as a compiled-in catalog** (F-Droid content
  policy — the maintainer asked for empty predefined lists). Tryst no longer bakes any category into
  the app; instead it **pre-populates a few neutral starter entries as ordinary, editable rows** you
  can rename or remove like anything else: acts start with *Kissing* and *Cuddling*, occasions with
  *Date night* and *Anniversary*, finish locations with *Didn't finish* and *In condom*; kinks,
  positions, and toys start empty. Occasions and finish (ejaculation) locations became
  **customizable too** this release. Nothing you logged is lost: on first launch after the update,
  every entry you used from the old built-in lists is converted into your own entry with the same
  meaning (schema v12, automatic migration — including the per-orgasm finish-location data), still
  fully counted in insights and achievements and still pickable when logging.
- Restoring an **older backup** performs the same conversion automatically.

### Added
- **A dedicated management page for every category** — Settings → Categories now opens a full,
  polished page per category (acts, kinks, positions, toys, occasions, finish locations) where you can
  add, rename in place, or remove your entries, replacing the old pop-up dialogs.
- **Custom occasions and finish locations** — add your own under Settings → Categories, stored the same
  id-based way as acts/kinks/positions/toys (schema v12).

## [0.3.1] — 2026-07-03 (versionCode 4)

### Changed
- **The built-in positions and toys are now a small, non-explicit starter set** (F-Droid content
  policy — the same rework already done for acts and kinks in 0.3.0). Nothing you logged is lost: on
  first launch after the update, every position or toy you used from the old built-in lists is
  converted into a **custom** entry with the same meaning (schema v11, automatic migration), still
  fully counted in insights and achievements and still pickable when logging. Built-ins you never used
  simply leave the picker — re-add anything you miss under **Settings → Manage custom positions / toys**.
- Restoring an **older backup** performs the same conversion automatically, so pre-update backups keep
  working without resurrecting the old built-in ids.

### Added
- **Custom toys** — add your own toys under **Settings → Manage custom toys**, just like custom acts,
  kinks, and positions. They appear alongside the built-ins when logging and count fully toward
  insights and achievements. (Toys are now stored the same id-based way as acts/kinks/positions —
  schema v11.)

## [0.3.0] — 2026-07-02 (versionCode 3)

### Changed
- **The built-in acts and kinks catalogs are now a small, non-explicit starter set** (F-Droid
  content policy — the app ships without predefined explicit labels). Nothing you logged is lost:
  on first launch after the update, every entry you ever used from the old built-in lists is
  converted into a **custom** act/kink with the same meaning (label derived from the internal id,
  e.g. "Foot play"), still fully counted in insights, search, and achievements, and still pickable
  when logging (schema v10, automatic migration). Built-ins you never used simply leave the picker —
  re-add anything you miss under **Settings → Manage custom acts / kinks**.
- Restoring an **older backup** now performs the same conversion automatically, so pre-update
  backups keep working without resurrecting the old built-in ids.

### Added
- **Rename custom entries** — custom acts, kinks, and positions can now be renamed in place
  (Settings → Manage custom …). Renaming keeps the entry's identity, so every logged encounter
  follows the new label. Handy for polishing the auto-derived labels from the catalog conversion
  (e.g. turning "Sixty nine" back into "69").
- **Custom kinks** — add your own kinks under **Settings → Manage custom kinks**, just like custom
  acts and positions. They appear alongside the built-ins when logging, and count fully toward
  insights and achievements. (Kinks are now stored the same id-based way as acts/positions —
  schema v9, an additive migration; your existing kinks are untouched.)
- **Setting: open Trysts in calendar view by default** (Settings → General). The per-session
  list/calendar toggle still works as before; this just sets which one you land on.
- **Your most-used options surface inline** in the encounter editor. Each category (acts, positions,
  protection, places, kinks, toys, occasions, mood, ejaculation) now shows the choices you pick most
  often without opening "More…" — learned from your own history, no setup. Everything else stays one
  tap away under "More…".

## [0.2.0] — 2026-06-21 (versionCode 2)

### Added
- Redesigned **Trysts calendar**: tonal day chips with an activity heatmap, a month/week toggle, and
  swipe to change month/week.
- A subtle outline ring marks **today** on the calendar.
- **Ejaculation location** is now multi-select per orgasm, with an "in the shower" option.
- New **location**: "Friend / family's place" (for time at someone else's home).
- Several new built-in **positions** and **acts** to choose from.

### Changed
- "Watching porn" moved from **Acts** to **Kinks & BDSM** (existing entries are migrated automatically).
- Clearer oral-position names: "Oral - Kneeling", "Oral - Standing", "Oral - Laying down".
- "Ball sucking / teabagging" is now "Ball sucking / ball play".
- A "lingerie" label tidy-up in the editor.

### Fixed
- **Haptics** now actually buzz when enabled in Settings (the feedback was being silently suppressed on
  many devices). If your phone's system "vibrate on touch" is off, that still takes precedence.

### Notes
- Your history is preserved across all of the above. After updating, **re-export your backup** so a
  future restore keeps the new naming (restores don't re-run migrations).

## [0.1.0] — 2026-06-13 (versionCode 1)

First public release.

- Everything stays on this device — no account, no sync, and no internet access at all.
- Encrypted SQLCipher database and encrypted photo storage, locked behind your PIN with optional
  biometric unlock and auto-lock on background.
- Rich encounter and partner logging, with on-device Insights and Achievements.
- Manual, password-encrypted backup/restore for moving to a new phone.
