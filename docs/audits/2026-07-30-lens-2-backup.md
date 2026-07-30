# Lens 2 — Backup restore edge cases (2026-07-30)

**Base commit:** `main` @ `6706500`
**Scope:** `app/src/main/java/app/tryst/data/backup/BackupManager.kt`,
`app/src/main/java/app/tryst/data/db/Migrations.kt`, all entities under
`app/src/main/java/app/tryst/data/db/entity/`, tests under `app/src/test/` and
`app/src/androidTest/data/backup/`, plus the import-failure log-site in
`app/src/main/java/app/tryst/ui/settings/BackupViewModel.kt`.

**Status:** 3 findings (1 needs-fix, 2 nice-to-have). No blockers.

## What I verified (and how)

- **NOT-NULL migration audit.** Read every `MIGRATION_*_*` in `Migrations.kt` end-to-end
  looking for `ADD COLUMN … NOT NULL`. The **only** hit is
  `MIGRATION_13_14` line 256 (`ALTER TABLE media ADD COLUMN favorite INTEGER NOT NULL
  DEFAULT 0`). Cross-checked every entity file in
  `data/db/entity/` for the corresponding `@ColumnInfo(defaultValue=…)`:
  `MediaEntity.favorite` at `SupportingEntities.kt:107` has
  `@ColumnInfo(defaultValue = "0")`. Session's claim confirmed — this is the only
  NOT-NULL column whose backfill defence-in-depth matters, and it is now covered
  both by the entity annotation and the PRAGMA backfill.
- **PRAGMA backfill correctness.** `notNullColumnDefaults` handles INTEGER (parses
  to Long or skips on parse failure), REAL (parses to Double or skips), and TEXT/
  BLOB/no-affinity (passes literal through). Skips PK columns
  (`c.getInt(pkIdx) != 0`) so an implicit NOT NULL on the primary key doesn't get
  a fabricated default. Skips columns with no DEFAULT so restore fails loudly
  rather than inventing a `0`/`""` for a domain-sensitive field. The
  `if (values.containsKey(col)) continue` guard correctly leaves an existing
  NULL alone — including the `row.isNull(k)` → `values.putNull(k)` branch. This
  is the correct behaviour for our current migration set (no column has ever
  transitioned nullable → NOT NULL in-place).
- **`person_photo` blob-id gathering.** Blobs are gathered into a
  `LinkedHashSet<String>` (`BackupManager.kt:61`) that unions ids from four
  sources — `media.id`, `partners.photoMediaId`, `profile.photoMediaId`,
  `person_photo.mediaBlobId`. If the same blob appears in more than one source
  (e.g. a partner avatar that was originally picked from that partner's
  portrait album) it is exported exactly once. Verified in the app that this
  overlap cannot actually happen today: `PersonPhotoRepository.add` (line 42)
  and `PartnerRepository.savePhoto` (line 39) each mint a fresh `UUID` before
  encrypting, and `setAsPartnerAvatar` re-encrypts a new blob rather than
  reusing the portrait's id — so blob ids don't cross-reference in normal use,
  but the dedupe is still the right belt-and-braces.
- **Restore idempotency.** All inserts use
  `SQLiteDatabase.CONFLICT_REPLACE`; a repeat import of the same backup replays
  every row identically and media files are re-encrypted from the same
  plaintext. Restoring a v14 backup on a v15+ install skips the missing
  `person_photo` table via `tables.optJSONArray(table) ?: continue`.
  `recent_searches` is deliberately absent from `TABLES` (D-42) so an import
  never carries a search history across devices.
- **Injection.** `COLUMN_NAME` regex rejects any JSON key that isn't a plain
  SQL identifier before it reaches Android's unquoted `insert()` column list.
- **KDF DoS + wrong-password.** Bounds check on the file-header iteration
  count (line 91) is in place and covered by
  `BackupRoundTripTest.import_rejectsAbsurdIterationCount`.

## Findings

### F1 — Partial restore leaves the DB fully committed while blob writes are still streaming

**Severity:** needs-fix.
**File:** `app/src/main/java/app/tryst/data/backup/BackupManager.kt:95-106`
(`import`, the ZipInputStream loop) with `restoreDatabase` internal transaction
at 141-193.

**Claim.** `restoreDatabase` opens its own `beginTransaction()` /
`setTransactionSuccessful()` and commits the DB **before** the ZIP loop moves
on to the media entries. If any subsequent `media/<id>` entry throws (Tink
segment tag mismatch mid-stream, `IOException`, a truncated container, or
`EncryptedMediaStore.save` failing on a full disk), the DB rows are already
persisted but only the media files written so far are on disk. The remaining
DB rows point at blobs that don't exist. `BackupViewModel.import`'s catch
shows the generic `backup_status_import_failed` toast — the user has no idea
their DB was half-swapped.

**Failure scenario.** User A's backup contains 300 encounter photos and 20
portrait blobs. Backup is copied to user A's new device. Restore starts; the
storage volume fills after 220 media entries are written, so entry 221's
`file.outputStream()` throws `IOException`. Result on the new device: every
row from `data.json` is committed (encounters, partners, media rows referring
to 300 blob ids, person_photo rows referring to 20 blob ids), but only 220 of
the blobs are on disk. The Gallery opens, shows thumbnails, and every photo
past the first 220 renders as "decrypt failed" / blank. From the user's
perspective this looks like silent data loss on backup restore.

**Suggested remediation.** Either (a) wrap the entire ZIP-loop body in an
outer DB transaction and roll it back on any exception (harder — the current
`beginTransaction` lives inside `restoreDatabase`), or (b) stream media
entries into a *staging* subdirectory and atomically rename into `media/`
only after every entry has been processed successfully. (b) also gives
"restore over existing" a clean rollback path that (a) doesn't. As a
minimum: if the outer catch fires after `data.json` has been consumed,
surface a distinct status string
(`backup_status_import_partial_media_missing`) instead of the generic
failure toast so the user knows their DB is compromised and needs a re-run.

### F2 — `Log.e("TRYSTIMPORT", …)` survives release builds and ships the raw throwable

**Severity:** nice-to-have.
**File:** `app/src/main/java/app/tryst/ui/settings/BackupViewModel.kt:60`
plus `app/proguard-rules.pro:5-9`.

**Claim.** The proguard rule only strips `Log.v/d/i` — the release build
keeps `Log.e` and `Log.w`. The import catch is `Log.e("TRYSTIMPORT", "import
failed: ${e.javaClass.name}: ${e.message}", e)` — `e.message` is emitted
once as a string and the whole throwable (including cause chain and stack
trace) is passed to `Log.e`'s third arg, which logcat formats fully.

Today the exception messages Room / Android-SQLite emit for our restore
paths are constraint / column-name shaped ("NOT NULL constraint failed:
partners.displayName", "no such column: X", "malformed JSON"). None of them
contains user-typed values — verified by walking the code paths — so this
isn't a live PII leak. But: (i) it does leak schema shape to logcat on a
build that promises "logging is stripped/neutered" (CLAUDE.md constraint 4),
(ii) a future exception type in the chain — a JSON parser upgrade, a
SQLCipher wrapping exception, a driver change — could very well embed the
offending value, and the current wording gives that future value a
back-door into logcat.

**Failure scenario.** A user with a partner named `Sam` restores a backup
that hits a JSON-parse edge case (e.g. an ancient backup with a stray
non-UTF-8 byte in the note field). Future org.json versions have historically
included the offending characters near the error position ("Value ... of
type ... at column X"). That value ends up in logcat under the
`TRYSTIMPORT` tag on a **release** APK, readable by anyone with adb access
or a logcat sink app.

**Suggested remediation.** In release, log only `e.javaClass.name` — drop
`e.message` and drop the throwable arg. Or gate the whole
`Log.e(…)` call behind `BuildConfig.DEBUG`. Either satisfies constraint 4;
the second keeps the debug affordance. Bonus: extend the proguard rule to
also strip `Log.w` and `Log.e` (Timber-style — or move the DEBUG-only log
behind a helper).

### F3 — Restoring a `partners` row cascade-clears cross-refs for local-only encounters

**Severity:** nice-to-have.
**File:** `app/src/main/java/app/tryst/data/backup/BackupManager.kt:187`
(`db.insert(table, CONFLICT_REPLACE, values)`), interacting with the
`ON DELETE CASCADE` foreign keys on `encounter_partner`, `encounter_position`,
`encounter_tag` (see `entity/CrossRefs.kt`).

**Claim.** `defer_foreign_keys = TRUE` defers FK **checks**, not cascade
**actions**. `INSERT OR REPLACE partners` on a row whose PK exists locally
first does a DELETE → cascades DELETE on `encounter_partner` for that
partner id → then INSERTs the new partner row. If the local DB has an
encounter that is NOT in the backup but SHARES a partner with the backup,
the local cross-ref for that local encounter is silently removed.

**Failure scenario.** User restores a v0.4.0 backup on top of an already-in-
use v0.5.0 install (not the "delete-all-data then restore" path — the
"import over existing" path exercised by
`BackupRestoreRegressionTest.restoreOverExistingData_keepsPhoto`, which
happens to insert identical data before and after and so masks this).
Concretely: local device has partner `Sam` (id=p1) with local encounters
`localA`, `localB`. Backup contains partner `Sam` with just `backupC`.
Restore: `INSERT OR REPLACE partners('p1', 'Sam', …)` → cascade DELETE
FROM encounter_partner WHERE partnerId='p1' → local rows for `localA`,
`localB` are gone. The `encounters` rows for `localA`/`localB` survive but
are now partnerless in the UI. Same shape hits `encounter_position` if the
backup contains a positions row that the local DB also has.

**Suggested remediation.** Either (a) document restore-over-existing as
"replaces all data" and steer the UI toward the "delete-all-data → restore"
flow explicitly (a `SessionManager.deleteAllData()` call inside `import` if
a preference is set — a "wipe local data first" checkbox in the restore
dialog), or (b) change the parent-table inserts to `INSERT OR IGNORE` /
`UPDATE` so the DELETE cascade never fires when the row's PK already
exists locally. Either is a behavior change and shouldn't be quietly
squeezed in — worth a DECISIONS entry.

## Not-a-finding, but worth noting

- `restoreOfPreTrimBackup_adoptsRemovedIds` in `BackupRestoreRegressionTest`
  builds its "old backup" by inserting raw pre-v10 SQL and then calling
  `backup.export` — which packages it under the *current* schema shape. The
  test still validates `CatalogAdoption` after restore, but doesn't
  round-trip a genuine pre-v10 backup file. If we ever ship a fixture of a
  real old backup (`docs/EXPORT_FORMAT.md` promises the format is stable),
  restoring THAT is the stronger regression.
- `dumpDatabase` writes `schemaVersion` in the JSON root but `restoreDatabase`
  never reads it. Restoring a backup taken on a schema newer than the app
  fails loudly ("no such column X" from `db.insert`) rather than silently,
  which is fine — but the "Import failed" toast gives the user no clue that
  the backup came from a newer app version. Not worth a fix on its own;
  becomes relevant if F2's status string is revisited.
