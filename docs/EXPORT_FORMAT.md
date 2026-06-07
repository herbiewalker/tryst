# Tryst — Backup / Export Format

Status: **v1** (M5). Produced/consumed by `data/backup/BackupManager.kt` + `core/crypto/BackupCrypto.kt`.

A Tryst backup is a single **password-encrypted** file (suggested name `tryst-backup-<date>.tryst`).
It contains everything — all encounters, partners, custom positions/acts, and photos — so it can
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
  media/<id>       the decrypted bytes of each photo (re-encrypted by the container)
```

- **KDF:** PBKDF2-HMAC-SHA256 (same `Pbkdf2` as the app PIN, 600k iters). The iteration count is in
  the header so it can rise over time; a future format version may switch to Argon2id (the offline-
  attackable backup is the strongest case for a memory-hard KDF).
- **Wrong password** ⇒ the AEAD stream fails authentication on first read (import aborts cleanly).
- **Tables** (insert order respects FKs; `PRAGMA defer_foreign_keys` also guards it): partners,
  locations, tags, positions, acts, encounters, media, encounter_partner, encounter_position,
  encounter_tag. Rows are inserted with `INSERT OR REPLACE` (idempotent re-import).
- **Media rows:** `encFilePath` is device-specific, so on import it's repointed at this device's media
  dir and the blob is written via `EncryptedMediaStore` (re-encrypted under the current media key).
- **Schema version** is recorded; restores assume forward-only, additive-nullable migrations (a newer
  backup into an older app isn't supported).

## Importing data from *other* apps (planned, M5b)
Separate path from this backup format. Because intimacy/period trackers share no standard, the plan is
a **generic CSV importer with column mapping** (date, partner, duration, rating, note…), which covers
most apps and spreadsheets — including exporting history out of apps like Intimacy / LoveLust.
