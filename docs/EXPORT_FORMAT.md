# Tryst — Backup / Export Format

> **Status:** Live — container format **v1** (`TRYSTBK1`). Produced/consumed by
> `data/backup/BackupManager.kt` + `core/crypto/BackupCrypto.kt`.

A Tryst backup is a single **password-encrypted** file (suggested name `tryst-backup-<date>.tryst`).
It contains everything — all encounters, partners, your self profile, your category entries
(acts/kinks/positions/toys/occasions/finish locations), and photos — so it can
restore the app on a new phone. The only secret needed to open it is the **backup password the user
chose at export** (independent of the app PIN). There is no recovery if it's lost.

## Why re-encrypt (not just copy the DB)?

Live data is encrypted with a Data Encryption Key that's wrapped by a **device-bound, non-exportable
Android Keystore key** — so the raw DB/media can't be opened on another device. Export therefore
**decrypts** the data while the app is unlocked and **re-encrypts** the whole container under a key
derived from the backup password. Restore reverses it and re-encrypts media under the new device's key.

## File layout

```
┌────────────────────────── cleartext header (29 bytes) ──────────────────────────┐
│ MAGIC      8 bytes   ASCII "TRYSTBK1"                                            │
│ version    1 byte    format version (currently 1)                               │
│ salt       16 bytes  random, for the password KDF                               │
│ iterations 4 bytes   big-endian int (PBKDF2 iteration count)                    │
└─────────────────────────────────────────────────────────────────────────────────┘
        ↓ everything after the header is one AEAD stream
AES-256-GCM-HKDF streaming (Tink `AesGcmHkdfStreaming`, 1 MiB segments,
associated data = "tryst-backup-v1"), key = PBKDF2-HMAC-SHA256(password, salt, iterations) → 32 bytes
        ↓ plaintext of that stream is a ZIP:
  data.json        every table dumped generically: { "schemaVersion": N, "tables": { <table>: [ {col: value, …}, … ] } }
  media/<id>       the decrypted bytes of each photo blob (re-encrypted by the container) —
                   both encounter photos (media-table rows) AND partner avatars (referenced
                   only by Partner.photoMediaId, no media-table row)
```

- **KDF:** PBKDF2-HMAC-SHA256 (same `Pbkdf2` as the app PIN, 600k iters). The iteration count is in
  the header so it can rise over time; a future format version may switch to Argon2id (the offline-
  attackable backup is the strongest case for a memory-hard KDF). On import the header count is
  **bounded to 100k–5M** — the value is untrusted, and an absurd one (e.g. `Int.MAX_VALUE`) would
  otherwise freeze the app deriving the key (DoS).
- **Wrong password** ⇒ the AEAD stream fails authentication on first read (import aborts cleanly).
- **Tables** (insert order respects FKs; `PRAGMA defer_foreign_keys` also guards it): partners,
  profile, locations, tags, positions, acts, kinks, toys, occasions, ejaculation_locations, encounters,
  media, encounter_partner, encounter_position, encounter_tag. Rows are inserted with `INSERT OR REPLACE`
  (idempotent re-import).
- **Media blobs:** export gathers ids from **both** `media` rows (encounter photos) **and**
  `partners.photoMediaId` (avatars) — avatars have no media-table row, so dumping the table alone would
  silently drop them from the backup. On import, `encFilePath` is device-specific, so it's repointed at
  this device's media dir and the blob is written via `EncryptedMediaStore` (re-encrypted under the
  current media key). `EncryptedMediaStore.save` recreates the media dir if it's missing — the standard
  "delete all data, then restore" migration removes it, and a stale singleton would otherwise fail the
  first write. The media id (from the ZIP entry name / `data.json`) is **validated as a safe filename** —
  no path separators or `..`, and the resolved file must stay inside the media dir — so a crafted backup
  can't use it to write outside app storage (Zip-Slip).
- **Schema version** is recorded; restores assume forward-only, additive-nullable migrations (a newer
  backup into an older app isn't supported).

## Importing data from *other* apps (implemented, M5b)
Separate path from this backup format. Because intimacy/period trackers share no standard, this is a
**generic CSV importer with column mapping** (Settings → Import from CSV): date, partner, duration,
rating, note… It auto-detects common columns, parses flexible date formats, and finds-or-creates
partners by name — covering most apps and spreadsheets (e.g. exporting history out of Intimacy /
LoveLust). Parser: `data/backup/Csv.kt`; UI: `ui/settings/CsvImportViewModel.kt`.
