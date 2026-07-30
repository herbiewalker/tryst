# Tryst — Pre-audit plan (bugs + code review)

Drafted 2026-07-30, base commit `ab0c3c0`. Scope: everything that has landed on `main`
since the v0.4.0 tag (`245d56e`) and made it into the v0.5.0 release plus the UX pass
that followed. A separate lens per pass — each best run in its own fresh session so the
lens sees the code cold and the reviewer's context doesn't spill between them.

The playbook adapter for this is [DevPlaybook `pb-audit-pass`](file:///E:/ClaudeFolder/Git/DevPlaybook)
— each lens below maps to one pass and can be launched with that skill.

## What "counts as a finding"

Not code-smell hunts. Each lens has a finding bar defined below. If the pass produces zero
findings that meet its bar, the pass reports "clean" and we move on. Nothing gets logged as
"consider maybe refactoring X" — that's for the deferred REFAC queue in
[ROADMAP_FUTURE.md](ROADMAP_FUTURE.md), not a review finding.

---

## Lens 1 — Correctness of the Photos gallery core (`data/gallery/`)

**Files:** `data/gallery/GalleryModels.kt`, `data/gallery/GalleryPhotos.kt`, `ui/gallery/GalleryViewModel.kt`.

**What to look at**
- `GalleryPhotos.build()` — filter/flatten/sort/group pipeline. Sole reachable path for what
  the tab renders. Covered by `GalleryPhotosTest` (7 cases) but the new v15 person-photo
  merge got only 2 additional cases.
- Person-photo album folding (v15): partner-filter path INCLUDES portraits; structured
  filters (date/rating/duration/place/…) EXCLUDE them because portraits have no
  tryst-side data. This asymmetry is the single most likely correctness bug source.
- `assignablePeople` StateFlow + `addPhotoToPerson` in `GalleryViewModel` — new this
  session, no test.

**What would count as a finding**
- A filter combination that includes a photo when the app's UI says it shouldn't (or vice
  versa) with concrete inputs → wrong output.
- Sort or grouping ambiguity that makes the tab non-deterministic across restarts.
- A NPE, out-of-bounds, or off-by-one under a legal state (empty gallery, single photo,
  duplicate blob ids, missing partner).

**Rough effort:** 1 session, medium.

## Lens 2 — Backup restore edge cases (`data/backup/`)

**Files:** `data/backup/BackupManager.kt`, `Migrations.kt`, `MediaEntity.kt`.

**Why this lens:** this session fixed a real NOT-NULL-on-restore bug (`13b37f2`) and added
PRAGMA-defaults backfill (`378e0d5`). Bugs of this shape are silent until a specific
old-backup-on-new-install path hits them. Also `person_photo` blob-id gathering (v15) is
new to backup and got no direct integration test.

**What to look at**
- Every `MIGRATION_*_*` in `Migrations.kt` for `ADD COLUMN … NOT NULL` without a matching
  `@ColumnInfo(defaultValue=…)` on the entity (session claim: `media.favorite` was the only
  one — verify).
- `BackupManager.restoreDatabase` PRAGMA backfill: does it correctly handle SQL DEFAULTs
  quoted as strings, integers, and NULL? What happens if the live table has a column the
  backup row DOES have but with a legal `NULL` — does the backfill leave it alone?
- `person_photo` blob-id gathering path — if a portrait blob is referenced by a
  `person_photo` row but ALSO by a `media` row, is it exported once or twice? Restoring: is
  the second write idempotent or does it clobber?
- Import failure logging (`TRYSTIMPORT`): does it leak PII in exception messages? It
  logs the underlying `SQLiteException` which can include column values on some drivers.

**What would count as a finding**
- A restore path that fails silently (still shows generic "Import failed" toast, no
  `Log.e`), or fails loudly with a state where a fix is possible.
- A backup that round-trips lossy (data on export → different data on import).
- PII in a log line.

**Rough effort:** 1 session, medium-heavy. Run in isolation because the fix rate is
usually zero and the false-positive rate is high.

## Lens 3 — Migration safety (JVM Room-validated vs real SQLCipher)

**Files:** `data/db/Migrations.kt`, `androidTest`'s `MigrationTest.kt`, exported schema JSONs
under `app/schemas/`.

**Why this lens:** memory flags that `MIGRATION_13_14` and `MIGRATION_14_15` were validated
via JVM Room's `runMigrationsAndValidate` but NOT via the real encrypted SQLCipher flow on
device. First real-world v13→v15 upgrade will hit that path live.

**What to look at**
- Exported `13.json`/`14.json`/`15.json` diff vs the DDL in `MIGRATION_13_14`/`MIGRATION_14_15`.
- Whether `person_photo` indexes are actually created (Room autogenerates from `@Entity` —
  did the migration DDL match?).
- Whether SQLCipher plays nicely with the `ALTER TABLE … ADD COLUMN … DEFAULT …` syntax
  used.

**What would count as a finding**
- A DDL that Room's JVM validator accepts but SQLCipher rejects at open time.
- An index Room expects that the migration didn't create.
- An ambiguity that would leave a real v13 install stuck at v13 with a
  `IllegalStateException: Migration didn't properly handle`.

**Rough effort:** 1 session, light — mostly reading the JSON diffs and running the
instrumented `MigrationTest` on a real emulator.

## Lens 4 — SearchVM ↔ GalleryVM duplication (REFAC candidate, deferred)

**Files:** `ui/search/SearchViewModel.kt`, `ui/gallery/GalleryViewModel.kt`.

**Why this lens:** memory calls out that GalleryVM was built by reusing SearchVM's machinery
verbatim — the two now duplicate the base-chip filter plumbing. Not a bug per se, but a
future filter change has to be made twice.

**What to look at**
- List every `MutableStateFlow` field that appears in both files with the same shape.
- Sketch what a shared `BaseFilterViewModel` (or a shared filter-state holder) would look
  like — but NOT implement it here. The point is to spec the refactor cleanly so it can
  be picked up in a dedicated session.

**What would count as a finding**
- A field that already drifted between the two (e.g. a fix landed in Search but not
  Gallery, or vice versa). That's a real bug hiding as duplication.
- If nothing drifted: the lens produces a REFAC ticket, not a review finding.

**Rough effort:** 1 session, light — mostly diff-reading.

## Lens 5 — Security invariants (still hold?)

**Files:** `AndroidManifest.xml`, `app/build.gradle.kts` (anti-leak guard), all
`Log.` call sites, `FLAG_SECURE` setup.

**Why this lens:** the "hard constraints" in CLAUDE.md are the app's headline promise. They
are enforced by build gates (`checkNoNetworkRelease`, `allowBackup=false`, `FLAG_SECURE`)
but every new dependency and every new log line is a chance to break them.

**What to look at**
- Merged manifest of `assembleRelease` — the anti-leak guard already checks. Verify it did
  not get accidentally disabled.
- Every `Log.d/i/w/e` call added since v0.4.0 — does any carry PII (partner name, note
  contents, EXIF coords)? The `TRYSTIMPORT` line added this session is the specific
  concern.
- `androidx.exifinterface` was added for META-1. Does it declare any permissions in its
  merged manifest? (It shouldn't — but verify.)
- `allowBackup=false` still set. `FLAG_SECURE` still applied unconditionally.

**What would count as a finding**
- A log line that could carry a partner name or note text into logcat on a release build.
- A permission introduced by a new dep, even non-network.
- A build-flag or manifest attribute that silently changed the security posture.

**Rough effort:** 1 session, light-medium. Runs the `pb-audit-pass` `secrets`/`network`
lenses back-to-back.

## Lens 6 — adb-untestable UI paths (Compose popups under FLAG_SECURE)

**Why this lens:** memory documents that Compose `DropdownMenu` and gallery-picker popups
dismiss without firing under adb tap when FLAG_SECURE is on. That's an *emulator testing*
constraint — but it also means any state that depends on those callbacks may never have
been exercised in an automated way.

**What to look at**
- Every DropdownMenu callback (`onClick` handlers on menu items) that has landed since
  v0.4.0 — was it validated by real touch, or only by claim?
- Photo-picker + camera flows in the encounter editor and the new person-photo strip.

**What would count as a finding**
- A callback that never gets tested end-to-end on-device, that the reviewer isn't
  confident it's actually wired.
- An UI state (e.g. selection cleared, focus moved) that the callback should set but
  provably doesn't.

**Rough effort:** 1 session, medium — the review is code-only but the fix (if any) needs
user-driven emulator confirmation.

## Lens 7 — This session's UX ordering pass

**Files:** `SettingsScreen.kt`, `GallerySettingsScreen.kt`, `EncounterEditScreen.kt`,
`strings.xml`.

**Why this lens:** the four commits `d87dc11..ab0c3c0` moved UI blocks around. Compose is
forgiving about vertical order — but not perfectly. Local `val`s inside a Column can go
out of scope if a block that owns them is moved; conditional `if (!solo) { … }` blocks may
now sit in an odd place. A JVM-test does not exercise this.

**What to look at**
- The three files diffed against `b0c2a83` (pre-pass).
- Every `val` declared inside the reordered spans — is it still declared before its
  first use?
- Every `if (…) { … }` conditional — does it still render in a sensible relative
  position when the condition flips?
- The dropped "Blur gate" title (`gallery_blur_setting` reused for the row label) —
  ktlint doesn't catch orphan strings. Are there any references to strings that were
  effectively removed from the UI?

**What would count as a finding**
- A compile-only regression (missed by `assembleDebug` because it's dead code).
- A visible ordering that's demonstrably worse than the pre-pass version.
- An orphan string resource that should be deleted.

**Rough effort:** 1 session, light. Run this LAST so it can also spot findings the
previous lenses surfaced.

---

## Suggested run order

1. Lens 3 (migration safety) — smallest scope, sets confidence for Lens 2.
2. Lens 2 (backup restore) — highest bug-yield expected.
3. Lens 1 (gallery core) — second highest.
4. Lens 5 (security invariants) — cheap sanity check.
5. Lens 7 (this session's UX pass) — cheap sanity check, catches the last mile.
6. Lens 6 (adb-untestable paths) — plans on-device follow-up work.
7. Lens 4 (SearchVM/GalleryVM dup) — REFAC ticket output, low urgency.

## Out of scope

- Insights engine correctness — hasn't changed since 0.4.0; the v0.4.0 pre-release audit
  covered it and no new bugs have been reported.
- Achievements — hasn't changed since 0.3.x.
- Custom-catalog persistence — stable since 0.3.2; no migrations since.
- Encounter editor field-level validation — this session only reordered blocks, it did
  not change any field's validation, VM binding, or persistence.

## Bringing findings back

Each pass outputs a Markdown file `docs/audits/<date>-lens-<n>.md` with the finding list,
severity per finding (blocker / needs-fix / nice-to-have), and a proposed remediation.
Nothing gets committed to `main` from these passes without a follow-up session that either
fixes the finding or explicitly defers it into the roadmap.
