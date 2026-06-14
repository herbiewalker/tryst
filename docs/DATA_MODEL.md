# Tryst — Data Model

Status: **Live — schema v7** (Room over SQLCipher). Matches the entities in
`app/src/main/java/app/tryst/data/db/`. Exported schemas live in `app/schemas/`; every change
ships a non-destructive `MIGRATION_x_y` validated by `MigrationTest`.

> All tables live in the encrypted SQLCipher DB. IDs are app-local UUID strings. Nothing here
> leaves the device except via the user's encrypted export (M5).

## Encoding conventions

Category values are stored as TEXT via Room `TypeConverters` (`data/db/Converters.kt`):

- **Enum-name sets** (e.g. `protectionUsed`, `kinks`, `contexts`, `occasions`, `toys`): comma-joined
  enum `name`s. Parsing **skips unknown names**, so values that are renamed or moved between
  categories never crash older rows — they're dropped on read.
- **String-id sets** (`positions`, `practicesPerformed`, `practicesReceived`): comma-joined IDs where
  each ID is a built-in enum `name` **or** `custom:<uuid>` for a user-defined entry.
- **Maps**: `partnerOrgasms` = `partnerId=count` pairs; `ejaculationLocations` = `orgasmIndex=LOCATION`
  pairs (one ejaculation location per self-orgasm).

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
| ejaculationLocations | Map\<Int,EjaculationLocation\>? | **per-orgasm** location (orgasm index → where) |
| practicesPerformed / practicesReceived | Set\<String\>? | **Acts** gave/received (built-in `Practice` name or `custom:<uuid>`) |
| positions | Set\<String\>? | built-in `Position` name or `custom:<uuid>` |
| kinks | Set\<Kink\>? | Kink & BDSM |
| contexts | Set\<Setting\>? | **Setting & Location** (places) |
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
- **Act** (`acts`): same shape; built-ins from the `Practice` enum, custom rows managed in
  Settings → Manage custom acts.

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
`Protection`, `EjaculationLocation`, `Practice` (acts), `Kink`, `Setting` (places),
`Occasion`, `ToyType`, `Position`, plus partner/profile enums `Sex`, `Gender`, `RelationshipType`,
and the **v7 demographic** enums `Ethnicity`, `BodyType`. `Orgasm` is a legacy enum kept for migration.

## Achievements (M7 — derived, **no tables**)
Achievements are **not persisted**. The catalog is static code (`data/achievements/Achievements.kt`)
and `AchievementEngine` derives progress + a derived `unlockedAt` date by replaying the encounter log
on demand — the same stateless pattern as the stats engine. No schema/migration was needed. (A future
persistent "acknowledged/seen" state, for a one-time unlock celebration, would add the only achievement
storage — likely a small row in the encrypted DB.)

## Schema / migration rules
- Versioned Room migrations; **never destructive**. Bump `TrystDatabase.version`, add a
  `MIGRATION_x_y` (additive/nullable columns), extend `MigrationTest`.
- Export format (M5) is decoupled from the live schema and versioned independently
  (see [SECURITY_DESIGN.md](SECURITY_DESIGN.md) §4).
