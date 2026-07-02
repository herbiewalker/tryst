# Tryst — Data Model

Status: **Live — schema v10** (Room over SQLCipher). Matches the entities in
`app/src/main/java/app/tryst/data/db/`. Exported schemas live in `app/schemas/`; every change
ships a non-destructive `MIGRATION_x_y` validated by `MigrationTest`.

> All tables live in the encrypted SQLCipher DB. IDs are app-local UUID strings. Nothing here
> leaves the device except via the user's encrypted export (M5).

## Encoding conventions

Category values are stored as TEXT via Room `TypeConverters` (`data/db/Converters.kt`):

- **Enum-name sets** (e.g. `protectionUsed`, `contexts`, `occasions`, `toys`): comma-joined
  enum `name`s. Parsing **skips unknown names**, so values that are renamed or moved between
  categories never crash older rows — they're dropped on read.
- **String-id sets** (`positions`, `practicesPerformed`, `practicesReceived`, `kinks`): comma-joined
  IDs where each ID is a built-in enum `name` **or** `custom:<uuid>` for a user-defined entry.
  (`kinks` joined this group in **v9** — a built-in kink's id is its old `Kink` enum name, so existing
  values are unchanged; user-defined kinks live in the `kinks` table, mirroring `acts`/`positions`.)
- **Maps**: `partnerOrgasms` = `partnerId=count` pairs; `ejaculationLocations` =
  `orgasmIndex=LOC1|LOC2` pairs — **multi-select** locations per orgasm, joined on `|` (legacy
  single-value rows parse as a one-element set; backward compatible).

## Encounter (table `encounters`)
The central record.

| Field | Type | Notes |
|-------|------|-------|
| id | String (UUID) | PK |
| startAt | Long | epoch millis (date & time) |
| durationMin | Int? | optional |
| note | String? | free text |
| satisfactionRating | Int? | 1–5 |
| mood | Mood? | single |
| initiator | Initiator? | me / partner / mutual |
| protectionUsed | Set\<Protection\> | multi |
| orgasmCountSelf | Int? | times the user came |
| partnerOrgasms | Map\<String,Int\>? | **per-partner** orgasm counts (partnerId → count) |
| ejaculationLocations | Map\<Int,Set\<EjaculationLocation\>\>? | **per-orgasm** location(s) — multi-select per orgasm |
| practicesPerformed / practicesReceived | Set\<String\>? | **Acts** gave/received (built-in `Act` name, or `custom:<id>`) |
| positions | Set\<String\>? | built-in `Position` name or `custom:<uuid>` |
| kinks | Set\<String\>? | **Kink & BDSM** (built-in `Kink` name, or `custom:<id>`) |
| contexts | Set\<Place\>? | **Setting & Location** (places) |
| occasions | Set\<Occasion\>? | occasion / context |
| toys | Set\<ToyType\>? | |
| locationId | FK? | → Location (legacy; UI uses `contexts`) |
| createdAt / updatedAt | Long | audit |
| orgasm, orgasmCountPartner | legacy | superseded; kept for migration safety |

Relations: many partners (M:N via `EncounterPartnerCrossRef`), many media (1:N). The M1
position/tag cross-refs are legacy/unused (positions live in the `positions` column now).

## Partner (table `partners`)
| Field | Type | Notes |
|-------|------|-------|
| id | String (UUID) | PK |
| displayName | String? | null/blank ⇒ **anonymous** |
| isAnonymous | Bool | |
| sex | Sex? | Male / Female / Intersex |
| gender | Gender? | Man / Woman / Non-binary / Other |
| relationshipType | RelationshipType? | spouse / partner / FWB / … |
| photoMediaId | String? | **M4 hook** — encrypted partner photo (wired in M4) |
| birthDate | Long? | **v7** epoch millis (date only, stored local-noon); age is derived |
| ethnicity | Ethnicity? | **v7** demographic |
| height | String? | **v7** free-text (units the user's choice) |
| bodyType | BodyType? | **v7** demographic |
| location | String? | **v7** free-text city |
| color | String? | optional UI accent, local only |
| note | String? | |
| archivedAt | Long? | soft-archive |
| createdAt / updatedAt | Long | |

## Profile (table `profile`) — the user's own self (v7)
A **single row** (`id = "self"`) holding the user's photo + demographics, mirroring the partner fields
so "you" and a partner read the same way. Surfaced in Settings → Your profile and the "You" card on
Partners.

| Field | Type | Notes |
|-------|------|-------|
| id | String | PK, always `"self"` |
| displayName | String? | |
| photoMediaId | String? | encrypted profile photo (reuses the media store, like a partner avatar) |
| sex | Sex? | |
| gender | Gender? | |
| birthDate | Long? | epoch millis (date only, local-noon); age derived |
| ethnicity | Ethnicity? | |
| height | String? | free-text |
| bodyType | BodyType? | |
| location | String? | free-text city |
| note | String? | "about you" |
| updatedAt | Long | |

## Custom-option tables
- **Position** (`positions`): `id`, `label`, `isBuiltIn`. Built-ins come from the `Position` enum;
  user-defined rows (`isBuiltIn=false`) are managed in Settings → Manage custom positions.
- **Act** (`acts`): same shape; built-ins from the `Act` enum, custom rows managed in
  Settings → Manage custom acts.
- **Kink** (`kinks`, **v9**): same shape; built-ins from the `Kink` enum, custom rows managed in
  Settings → Manage custom kinks.
- Custom rows can be **renamed in place** (v10): the row id — and so every encounter ref — is
  untouched; a label that collides with an existing entry is rejected (unique-label index).
- **v10 adopted rows:** ids of built-ins removed in the v10 catalog trim live here as custom rows
  whose `id` is the old enum `name` (not a uuid) — refs are `custom:<NAME>`. See D-41 / v10 below.

## Location / Tag / Media
- **Location** (`locations`): `id`, `label` — user-typed generic label. **No GPS / coarse location**
  (privacy; avoids tempting a location permission).
- **Tag** (`tags`): freeform user tags (legacy/unused in the current UI).
- **Media** (`media`): `id`, `encounterId` (FK, CASCADE), `encFilePath` (AES-GCM blob in app-internal
  storage), `mimeType`, `createdAt`. Bytes are **not** in the DB and **not** in MediaStore. (M4 wires
  the attach/view UI; partner photos reuse this via `Partner.photoMediaId`, and the profile photo via
  `Profile.photoMediaId` — both are blobs with no `media` row, so the backup gathers their ids
  explicitly, like partner avatars.)

## Category enums (`data/db/entity/Enums.kt`)
All implement `DisplayLabel` (human-written `label` shown in the UI): `Initiator`, `Mood`,
`Protection`, `EjaculationLocation`, `Act` (acts; named `Practice` before v10), `Kink`,
`Place` (places; named `Setting` before v10), `Occasion`, `ToyType`, `Position`, plus partner/profile
enums `Sex`, `Gender`, `RelationshipType`, and the **v7 demographic** enums `Ethnicity`, `BodyType`.
`Orgasm` is a legacy enum kept for migration. (The class renames are code-only — the DB stores enum
*constant* names, never class names.) Since **v10** the `Act`/`Kink` catalogs are a small non-explicit
starter set (F-Droid policy, D-41); everything beyond it is user data in the custom tables.

## Achievements (M7 — derived, **no tables**)
Achievements are **not persisted**. The catalog is static code (`data/achievements/Achievements.kt`)
and `AchievementEngine` derives progress + a derived `unlockedAt` date by replaying the encounter log
on demand — the same stateless pattern as the stats engine. No schema/migration was needed. (A future
persistent "acknowledged/seen" state, for a one-time unlock celebration, would add the only achievement
storage — likely a small row in the encrypted DB.)

## Schema / migration rules
- Versioned Room migrations; **never destructive**. Bump `TrystDatabase.version`, add a
  `MIGRATION_x_y`, extend `MigrationTest`. Migrations are usually additive/nullable columns, but may
  also be **data-only** value rewrites when category values move (see `MIGRATION_7_8`).
- **v8 (`MIGRATION_7_8`, 0.2.0)** is data-only — no DDL. Because the DB stores enum `name`s (not
  labels), pure label renames need no migration; this one rewrites stored *values* where category
  members moved: deletes `Position.ORAL_69_SIDE` → remaps refs to `LYING_ORAL`; moves `WATCHING_PORN`
  from `Practice` (acts) to `Kink`; and promotes 5 custom positions + 2 custom acts to built-ins
  (matches the custom row by label, rewrites `custom:<uuid>` refs to the new enum `name`, deletes the
  row; an unmatched label safely stays custom). Also additive enum values: `Setting.FRIENDS_FAMILY`,
  new `Position`/`Practice` entries. ⚠️ Backup **restore inserts rows raw and does NOT replay
  migrations** — re-export after upgrading so a future restore keeps the new values.
- **v9 (`MIGRATION_8_9`)** adds the custom **`kinks`** table (DDL only, mirrors `acts`/`positions`),
  making kinks user-configurable string ids instead of a fixed enum. The `encounters.kinks` column is
  unchanged — a built-in kink's id **is** its old `Kink` enum `name`, so existing comma-joined values
  stay valid with no data rewrite; the table starts empty (built-ins live in the enum). Foundation for
  the F-Droid policy work (ship without a predefined explicit catalog — see DECISIONS D-41).
- **v10 (`MIGRATION_9_10`, FDP-2)** is data-only — no DDL. The shipped `Act`/`Kink` catalogs were
  trimmed to a non-explicit starter set, and `CatalogAdoption.adoptUnknownIds` adopts every **used**
  bare id the current binary doesn't recognize into the custom `acts`/`kinks` tables (row id = the old
  enum `name`, label = generic prettify of it; label collisions merge into the existing custom row) and
  rewrites refs to `custom:<NAME>` — row-by-row in code, not SQL `REPLACE` (substring-id hazard). The
  routine is generic (no removed-id list ships in the APK) and idempotent, and **`BackupManager.import`
  runs it after every restore**, so pre-v10 backups self-heal instead of resurrecting removed ids —
  the v8 "re-export after upgrading" caveat no longer applies to catalog trims.
- Export format (M5) is decoupled from the live schema and versioned independently
  (see [SECURITY_DESIGN.md](SECURITY_DESIGN.md) §4).
