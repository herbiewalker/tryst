# Tryst — Data Model

Status: **Draft v0.1** — entities and fields; refine when defining the Room schema.

> All tables live in the encrypted SQLCipher DB. IDs are app-local (UUID or autoincrement).
> Nothing here ever leaves the device except via the user's encrypted export.

## Entities

### Encounter
The central record.
| Field | Type | Notes |
|-------|------|-------|
| id | UUID | |
| startAt | timestamp | date & time |
| durationMin | int? | optional |
| note | text? | free text |
| satisfactionRating | int? | e.g. 1–5 |
| orgasm | enum/bool? | none / self / partner / both — TBD |
| mood | enum? | small curated set |
| initiator | enum? | me / partner / mutual |
| protectionUsed | enum/set? | none / condom / other (multi) |
| locationId | FK? | → Location |
| createdAt / updatedAt | timestamp | audit |

Relations: many partners (M:N), many positions (M:N), many tags (M:N), many media.

### Partner
| Field | Type | Notes |
|-------|------|-------|
| id | UUID | |
| displayName | text? | null/blank ⇒ **anonymous** |
| isAnonymous | bool | |
| color/avatar | text? | local only |
| archivedAt | timestamp? | soft-archive |
| note | text? | |

### Position
Curated + user-addable list. `EncounterPosition` join table (M:N).

### Location
| id | UUID |
| label | text | e.g. "home", "hotel" — user-defined, generic by default (no GPS) |

> **No GPS / coarse location.** Locations are user-typed labels only — collecting real
> location would be both a privacy risk and would tempt a location permission. Keep it textual.

### Tag
Freeform user tags. `EncounterTag` join table (M:N).

### Media (attachment)
| Field | Type | Notes |
|-------|------|-------|
| id | UUID | |
| encounterId | FK | |
| encFilePath | text | path to AES-GCM-encrypted blob in app-internal storage |
| mimeType | text | |
| createdAt | timestamp | |

> Media bytes are **not** in the DB and **not** in MediaStore — encrypted files referenced by path.

### Achievement / AchievementProgress
| Achievement: id, key, title, description, rule metadata (all local/static) |
| AchievementProgress: achievementId, unlockedAt?, progress counters |

## Derived / computed (not stored, or cached)
- Stats: totals, frequency per period, streaks, per-partner and per-attribute breakdowns.
  Compute from the encounter tables; cache only if needed for performance.

## Schema / migration rules
- Versioned Room migrations; **never destructive**.
- Export format is decoupled from the live schema and versioned independently
  (see [SECURITY_DESIGN.md](SECURITY_DESIGN.md) §4).

## Open questions
- Exact enums (mood, protection, orgasm) — curate during UI design.
- Whether an encounter can have >1 partner in v1 (model supports M:N; UI may start with 1).
