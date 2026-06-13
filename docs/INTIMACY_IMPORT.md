# Importing legacy data from the Intimacy app (one-off migration)

> Handoff note for a future session. The maintainer is migrating ~7 years of personal history
> (~710 entries) out of a third-party Android app — **"Intimacy" by centertable**
> (`centertable.advancedscalendar`) — into tryst, both as real data and as a realistic import test.

## Where the data lives (NOT in this repo)
All extracted data, the full value-by-value mapping, and the generated backup live **outside the
repo** at:

```
E:\ClaudeFolder\IntimacyData\
```

That folder contains **sensitive personal data** and is deliberately kept out of version control.
**Never copy its contents (entries, partner names, notes, dates, the backup file, MAPPING.md) into
this repo or commit them.** Read it there if you need the specifics.

## How the import works
A hand-built **`TRYSTBK1` encrypted backup** is generated from the dataset and restored via
**Settings → Backup → Restore**. The minimal CSV importer (`CsvImportViewModel`) only covers
date/partner/duration/rating/note; the backup carries the full richness.

## tryst-side changes required: **NONE**
Verified against **schema v6**. Every Intimacy field maps onto existing structures:

| Intimacy | tryst (`encounters`) |
|---|---|
| entry date (date only) | `startAt` (set to 12:00 local — source has no time) |
| sex types | `practicesPerformed` (built-in `Practice` names) |
| places | `contexts` (`Setting`) |
| positions | `positions` column (built-in `Position` name **or** `custom:<uuid>`) |
| initiator | `initiator` (`Initiator`); "spontaneously" → null + `occasions += SPONTANEOUS` |
| orgasm counts | `orgasmCountSelf` + `partnerOrgasms` |
| notes / rating | `note` / `satisfactionRating` |

Values without a built-in equivalent become **custom acts/positions** (`ActEntity`/`PositionEntity`
rows with `isBuiltIn = false`, referenced as `custom:<uuid>`) — already a supported feature, so **no
schema or code change is needed** for the import to map correctly.

## Optional enhancements (NOT required for the import)
- **Promote a few heavily-used custom acts/positions to built-in `Practice`/`Position` enum values**
  if you want them first-class in the picker and stats. The specific candidates are listed in the
  external `MAPPING.md`.
- A dedicated in-app **"import with categories"** flow if this kind of migration recurs (the current
  CSV import is intentionally minimal — 6 fields).

## Caveats baked into the migrated data
- No time-of-day in the source → encounters set to **12:00 local**.
- **Ratings are sparse** (only a handful were ever rated in the source).
- The source's "calories burned" metric is **dropped** (no tryst field).
- Single partner in the dataset.

> Full value-by-value mapping, the list of custom acts/positions, and all decisions:
> `E:\ClaudeFolder\IntimacyData\MAPPING.md` (external — derives from personal data).
