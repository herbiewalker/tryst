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

## [0.5.4] — 2026-08-31 (versionCode 11)

### Added

- **Edit photos in place.** From the photo viewer, a new pencil action opens an Edit sheet with
  **Rotate 90° left**, **Rotate 90° right**, and **Crop…**. Rotate applies immediately; Crop
  opens a full-screen editor with corner handles and aspect presets (Free / 1 : 1 / 4 : 3 /
  16 : 9). Edits replace the encrypted blob in place, so nothing else in the app needs
  re-threading. Embedded EXIF (camera, GPS coordinates) is dropped in the re-encode — a natural
  strip-on-edit safeguard that aligns with D-51's "no strip on import, strip on export/edit"
  hook. There is no undo.

### Internal
- New `PhotoTransforms` (`data/media/`): pure `Bitmap`/`Matrix` helpers (rotate, crop, encode
  JPEG q92) plus JVM-testable rect math (`FractionalRect`, `computeCropRectPx`, `snapToAspect`,
  `clampRect`) so the crop UI's snap-to-aspect logic doesn't need an emulator to verify.
- New `EncounterRepository.replacePhotoBytes(media, bytes, mimeType)` and
  `PersonPhotoRepository.replaceBlobBytes(blobId, bytes)` — both stage the new blob via
  `EncryptedMediaStore.saveStaged` and then `promoteStaged` atomically, so a failure mid-encrypt
  leaves the live blob untouched.
- `GalleryViewModel.photoRevision` + `photoBustKey` — a per-blob revision counter that pairs
  with the blob id in every loader/cache-key. On edit the counter bumps, decoded ImageBitmap
  caches re-load, and the aspect-ratio cache entry is invalidated.
- New `PhotoEditor` composables — `EditPhotoSheet` (bottom sheet) and `PhotoCropper` (full-screen
  Dialog with corner handles and aspect chips).

## [0.5.3] — 2026-08-31 (versionCode 10)

### Added

- **Photo captions.** Every attached photo can carry a short user-typed note, edited from the
  photo viewer. Where the editor appears is a Settings choice
  (**Settings → Gallery → Photo captions**): the info panel, a new top-toolbar button, both
  (the default), or off. Captions are folded into the same search index as tryst notes, partner
  names, and category labels, so typing a caption phrase surfaces the tryst it belongs to.
  Round-trips through the encrypted backup for free.

### Internal
- Schema **v16** (`MIGRATION_15_16`): adds `media.caption TEXT NULL`. Pure additive DDL; a fresh
  Room CREATE also makes it nullable, so no `defaultValue` mirror is needed (the D-53 rule
  applies only to NOT-NULL additions).
- New `SearchField.PHOTO_CAPTION` in `EncounterSearch`; captions across an encounter's photos are
  aggregated into that field and matched folded (accent- and case-insensitive) like every other
  text source.
- New `CaptionEntryPoint` enum + `GalleryPreferences.captionEntryPoint` (default `BOTH`) drive
  which viewer surfaces render the caption UI. `OFF` hides the UI entirely but data still rides
  in the backup and still fires search hits.

## [0.5.2] — 2026-07-30 (versionCode 9)

Quality-of-life + audit-cleanup patch. Every fix here came out of the v0.5.1 on-device
validation walkthrough (`docs/audits/2026-07-30-session-validation.md`) or the audit
nice-to-haves. Nothing that shipped in v0.5.1 has yet reached F-Droid, so this is still
the first "publicly landing" release for everything since v0.4.0.

### Added

- **Add a new category on the fly.** Every catalog list in the encounter editor — acts,
  positions, kinks, toys, occasions, finish locations — now has an "Add a new one…" field
  at the bottom of its More… sheet. Type a name, tap Add, and it's saved AND selected for
  the current tryst. Adds are visible in Settings → Categories immediately afterwards,
  just like anything added there.
- **"Replace my current data with the backup" checkbox** on Import (Bundle-E Q1, default on).
  When on, the backup restore first wipes every row in the app before inserting the backup
  rows — all inside a single DB transaction, so a mid-restore failure rolls both halves back
  and leaves your data exactly where it was. Then the restored blob-id set becomes the "keep"
  filter for a media dir sweep so old encrypted files that no row references any more get
  cleaned up too. Off keeps the previous merge behaviour (advanced — leaves cross-refs to
  local-only encounters dangling on any partner id shared with the backup).

### Fixed

- **Back button on the People-drill for self.** Drilling into "You" from People used to
  render no top-bar back button AND swallow the system-back gesture (`_drilledPartnerId`
  was `"self"`, which isn't a real partner id, so `drilled` collapsed to false). The
  self-drill now shows the same DrillBar as any partner drill.
- **Partner/profile avatars from older backups render again.** `PersonPhotoStrip` only
  iterates the v15 `person_photo` table; blobs stored the pre-v15 way (`partners.photoMediaId`
  from a v13/v14 backup) had no matching portrait row, so the strip looked empty. Both
  editors now auto-adopt the legacy blob as a portrait row on first open — reuses the
  existing encrypted file, no re-encryption. Idempotent, no schema bump.

### Internal
- `PersonPhotoRepository.adoptExistingBlob(kind, ownerId, blobId)` — insert a portrait row
  for an already-encrypted blob. Used by the two editor VMs to heal legacy avatars.
- `BackupManager.restoreDatabase` refactored to `restoreDatabaseIntoTx` — no longer opens its
  own transaction; the caller owns the tx so wipe+restore commit atomically.
- `EncryptedMediaStore.deleteOrphans(keep)` — sweep every live `<id>.enc` whose id isn't in the
  keep set. The wipe-first import path uses this after the DB commit succeeds.
- Deleted 5 truly-orphan string resources: `settings_general` and `settings_insights` (Q2/Q3,
  freed by the this-cycle Settings resection), plus `partner_change_photo`,
  `partner_remove_photo`, `profile_you_initial` (dead before this session, no callers).
  Lens 7 also flagged 6 more but those are `<plurals>` used via `R.plurals.*` (SearchScreen,
  GalleryScreen, GalleryFiltersSheet, MoreFiltersSheet, EncounterCard) — kept.

## [0.5.1] — 2026-07-30 (versionCode 8)

Bug-fix release. Cluster came out of the v0.5.0 post-release audit
(`docs/audits/2026-07-30-*`) — every finding here was in code that shipped
in v0.5.0 but had never been F-Droid-published, so no user ever installs
the buggy v0.5.0 as long as the F-Droid MR merges after this tag.

### Fixed

- **Photos → drilling into "You" no longer leaks partners' portraits.** The
  filter that let a self-drill include solo tryst photos was also (wrongly)
  letting every partner's portrait album through. Now only the self portrait
  passes; every partner's portraits stay out. Same fix covers toggling the
  advanced "Include solo" chip on its own.
- **Photos search bar now narrows portraits.** Typing a name used to return
  that partner's tryst photos plus every other partner's portrait album,
  because the text query only filtered encounter-derived photos. Portraits
  are now matched by the (case+accent-folded) owner display name.
- **"Set as partner avatar" from the photo viewer** now stores the new
  avatar as part of that partner's portrait album — so the strip in the
  partner editor renders a checkmark on the current avatar and lets you
  delete it from the strip. Previously it created a fresh blob no
  `PersonPhotoEntity` referenced.
- **Deleting the portrait that is the current avatar** now nulls out
  `photoMediaId` in the same coroutine, so partner (or profile) rows no
  longer end up pointing at a deleted blob. Applies to both
  `PartnerEditViewModel` and `ProfileViewModel`.
- **Backup restore is now atomic.** Previously `restoreDatabase` committed
  its DB transaction on the first ZIP entry (`data.json`); if a subsequent
  `media/<id>` entry failed mid-loop (disk full, truncated container), the
  DB was fully swapped with rows pointing at blobs that had never been
  written — surfaced only as a generic "Import failed" toast, so from the
  user's perspective it looked like silent data loss. New flow drains the
  entire ZIP into memory (`data.json`) + a staging directory (`<id>.enc.staging`),
  then promotes every staged blob into place, THEN commits the DB. Any
  failure before the DB commit leaves the on-disk media dir and DB untouched;
  a `finally` block sweeps leftover `.staging` files.

### Internal

- `EncryptedMediaStore` gains staging support (`saveStaged` / `promoteStaged` /
  `clearStaged` / `clearAllStaged`) — the primitive the atomic-restore
  pipeline is built on. Same Zip-Slip guard as `fileFor`; `promoteStaged`
  atomically renames when possible and falls back to copy-delete for
  cross-fs edge cases.
- `PersonPhotoRepository.deleteByBlobId` now returns `Boolean` so callers
  can distinguish "the blob was a portrait and got cleaned up" from "the
  blob wasn't tracked as a portrait — you may need to delete it directly".
  `GalleryViewModel.setAsPartnerAvatar` uses the new return to route
  legacy pre-v15 avatar-blob cleanup to `PartnerRepository.deletePhoto`
  while portrait-tracked avatars go through the portrait path.
- **ProGuard: `Log.w` and `Log.e` now stripped in release too.** The prior
  rule only stripped `v/d/i`, so the `Log.e("TRYSTIMPORT", …, e)` line
  added last session survived R8 in release. Today's exception messages
  are benign but shipping `e.message` + a full throwable to logcat on a
  release APK is exactly the release-log leak vector CLAUDE.md
  hard-constraint #4 forbids. R8's `-assumenosideeffects` eliminates the
  whole call site including argument evaluation. Verified: the literal
  string `TRYSTIMPORT` is absent from `classes.dex` in the release APK.

## [0.5.0] — 2026-07-30 (versionCode 7)

### Added
- **Photos gallery.** A new **Photos** tab gathers every photo you've attached to a tryst into one
  browsable place. Choose how it looks in **Settings → Gallery** — grouped by date, a flat grid, grouped
  by partner, or a large feed — plus how many columns and newest- or oldest-first; your choice is
  remembered. The gallery stays clean: just a search and a filters button at the top. Search and the full
  filter set narrow which photos show. In the by-partner layout, each section is headed by that partner's
  photo and name. Tap any photo for a full-screen viewer: swipe between photos, pinch to zoom, and jump
  straight to the tryst it belongs to. Nothing leaves your device, and the viewer is screenshot-blocked
  like the rest of the app.
- **Multi-photo albums per person.** Add several photos to any partner or your own profile from the new
  **full-screen partner editor**, and pick any of them as their avatar. Portraits appear in the Photos
  gallery alongside encounter photos (partner filter includes them; structured filters like date/rating
  drop them since they have no tryst-side data). Round-trip through your encrypted backup.
- **Solo tryst photos count as photos of You.** In By-partner and when drilling into your own
  profile from the People layout, photos from solo trysts now appear under You alongside your
  self-profile portrait album — instead of hiding in a nameless "Solo" bucket. Nothing changes
  in the encounter log; only how the gallery groups those photos.
- **Add a photo to someone's photos, from the viewer.** A new action in the photo viewer copies
  the current photo into any partner's or your own portrait album — the source stays exactly where
  it is. Handy when someone was in a photo but wasn't listed on the tryst, or when a solo shot
  should also live under a partner. The photo shows in People → that-person right away, and can
  be set as their profile picture from the strip.
- **Photo details.** An info button in the photo viewer shows a photo's embedded details when present —
  when it was taken, its dimensions, the camera, and coordinates if the photo carries them. Read straight
  from the photo on your device; nothing is sent anywhere.
- **Blur photos until tapped** *(optional, off by default).* Turn it on in Settings → Gallery and the
  Photos tab opens blurred behind a "Show photos" tap, so your photos never appear the instant you open
  the tab — handy when you might hand your unlocked phone to someone. Popping to another tab and back
  within about half a minute keeps them showing; after that it re-blurs.
- **Favourite photos.** Tap the heart in the photo viewer to favourite a photo, and use the heart in the
  Photos tab to show only your favourites. Favourites are included in your encrypted backup.
- **Select and act on many photos at once.** Long-press a photo to start selecting, then favourite,
  remove the favourite, delete, or **move photos to a different tryst** — all in one go.
- **Two more gallery layouts.** **Mosaic** lays photos out in tidy justified rows at their true shapes
  (like Google Photos), and **People** shows your partners' and your own profile photos as a browsable
  grid of avatars — tap one to view it full-screen.
- **Gallery niceties.** Pinch the grid to change how many columns it shows; open a partner's photos in one
  tap from the Partners list ("View photos"); start a **slideshow** from the viewer; jump around a set with
  the new **filmstrip** strip; and set any photo as a partner's picture straight from the viewer.

### Fixed
- **Photo viewer counter no longer hides behind the action row.** The "N / M" counter used to
  sit in the middle of the top bar; as more actions were added to the right it started to overlap.
  It now lives next to the Close button on the left, so adding more actions later can't hide it.
- **Restoring an older backup on a fresh install now works.** A fresh install created the `media.favorite`
  column without the DEFAULT clause the v13→v14 migration sets, so importing a pre-v14 backup on a
  brand-new phone hit a NOT-NULL constraint on that column. Room's fresh CREATE now carries the same
  `DEFAULT 0` as the migration; the "move to a new phone" flow is unblocked.
- **Partner card subtitles** no longer show a stray "Â·" — the middle dot between sex/gender/age
  fields in the Partners tab renders correctly again ("Man · Age 39", not "Man Â· Age 39").

### Internal
- Import failures now log the underlying exception (`Log.e("TRYSTIMPORT", …)`) instead of silently
  showing a generic "Import failed" toast — the previous swallow made debugging the media-favorite
  restore bug above much harder than it needed to be.
- `BackupManager.restoreDatabase` now backfills any NOT-NULL column the backup row lacks with the
  live table's SQL DEFAULT (from `PRAGMA table_info`) — belt-and-braces for the media-favorite class
  of bug, so a future migration that adds a NOT-NULL column and forgets `@ColumnInfo(defaultValue=…)`
  on the entity still restores older backups cleanly. Columns without a SQL DEFAULT still fail loudly
  rather than getting a fabricated value.
- Schema **v14** (`MIGRATION_13_14`): adds `media.favorite INTEGER NOT NULL DEFAULT 0`.
- Schema **v15** (`MIGRATION_14_15`): adds the `person_photo` table (id / ownerKind / ownerId /
  mediaBlobId / addedAt, indexed by owner) for portrait albums. Additive DDL only.

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
