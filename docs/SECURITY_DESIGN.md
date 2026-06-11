# Tryst — Security & Encryption Design

Status: **Draft v0.2** — key model decided at M2: **Keystore-only** with a distinct app PIN
(see §1 Decision). Everything else is the working design.

---

## 1. The core decision: where does the master key come from?

All user data is encrypted with a **Data Encryption Key (DEK)**. The question is how the DEK
is protected and unlocked. Two viable models:

### Option A — Passphrase as root of trust (+ biometric convenience)  ★ Recommended
- At first run the user sets a **passphrase**.
- Derive a **Key Encryption Key (KEK)** from it with **Argon2id** (tuned params + random salt).
- Generate a random **DEK**; store it only as `Enc_KEK(DEK)` (wrapped).
- For daily use, also store `Enc_KeystoreKey(DEK)` where the Keystore key is **biometric-gated**
  and hardware-backed → user unlocks with fingerprint/face without typing the passphrase.
- The **passphrase is still required** periodically / after reboot / if biometrics fail, and is
  the only thing that can recover data.

**Pros:** Strongest against device seizure (T2) — without the passphrase the DEK is
unrecoverable even on a rooted device, because the Keystore wrapper is gated and the passphrase
wrapper needs the secret. Survives device loss (passphrase + export = recoverable).
**Cons:** Forgotten passphrase = permanent data loss (must be made explicit at setup). Slightly
heavier first-run UX.

### Option B — Keystore-only (PIN/biometric, no passphrase)
- Generate a random DEK, wrap it with a hardware-backed Keystore key gated by device
  biometric/PIN. No separate passphrase.

**Pros:** Smoothest UX, nothing to memorize.
**Cons:** Weaker vs. a sophisticated T2 with the unlocked/rooted device; recovery is tied to the
device + its lock; export needs its own independent password anyway.

### Decision (M2): Option B — Keystore-only, with a distinct app PIN

Chosen for UX. The original recommendation was Option A; the user accepted Option B's weaker
guarantee against forensic seizure in exchange for not having to remember a passphrase. The
`DatabaseKeyProvider` seam means an optional passphrase mode can be added later with no rework.

**Implemented design:**
- Random 256-bit **DEK** (generated once at first setup) encrypts the SQLCipher DB and is the
  media key material.
- The DEK is persisted on disk **double-wrapped**: `Enc(KEK_keystore, Enc(pinKey, DEK))` where
  - `KEK_keystore` = a non-exportable **Android Keystore** AES-256-GCM key (StrongBox when the
    device supports it), and
  - `pinKey` = a key derived from a **distinct 6-digit app PIN** (separate from the device lock)
    via a slow KDF (PBKDF2-HMAC-SHA256, **600,000 iterations** per OWASP 2023 — the count is stored
    per-vault in the `iter` field, so it can be raised without breaking existing vaults; abstracted,
    upgradeable to Argon2id).
- **Unlock:** the Keystore strips the outer layer; the entered PIN derives `pinKey` to strip the
  inner layer → DEK in memory. A wrong PIN causes an AEAD auth failure → failed-attempt counter.
- **Lockout:** after N failed attempts the vault self-wipes (wrapped blobs + Keystore keys deleted).
- **Biometric (M2b):** a second Keystore key with `setUserAuthenticationRequired(true)` wraps a
  copy of the DEK for fingerprint/face unlock; the PIN remains the fallback.

**Residual risks (honest):**
- A short PIN is brute-forceable in principle. The hardware-bound outer layer means a *disk image
  alone* can't crack it (the Keystore key can't be extracted), but a **rooted, live device** could
  attempt an on-device brute force. The slow KDF + self-wipe raise the cost; a passphrase would be
  strictly stronger.
- The attempt counter lives in the (encrypted-at-rest) vault file, not tamper-proof hardware, so it
  deters casual on-device guessing more than a determined forensic attacker. (Upgrade path:
  Keystore-backed monotonic counter.)

> `minSdk 31` gives modern Keystore behavior and StrongBox on supporting devices.

---

## 2. Data-at-rest encryption

- **Database:** Room over **SQLCipher** (AES-256). DB opened with the DEK; key bytes zeroed
  from memory ASAP after use.
- **Media (photos):** never via MediaStore. Stored in app-internal storage as
  **AES-256-GCM** streams (Google Tink) under a media key derived/wrapped from the DEK.
  Decrypted only in-memory for display; no decrypted temp files.
- **Preferences:** non-sensitive prefs (theme mode, Material You toggle) in plain
  `SharedPreferences` (`tryst_appearance`), excluded from backup/transfer like everything else.
  Anything sensitive would be encrypted; none is stored yet.

## 3. App lock & UI hardening

- App lock on cold start and **immediately on background** (`ProcessLifecycle ON_STOP` →
  `SessionManager.onAppBackgrounded()`). A user-configurable *timeout* is a deferred enhancement; the
  current default is immediate. The one exception is a one-shot ~2-minute grace while handing off to the
  OS photo picker / camera (`suppressNextAutoLock`), which otherwise would background us and drop the
  result — see [FLOWCHARTS.md](FLOWCHARTS.md) §8.
- `FLAG_SECURE` on all windows (no screenshots, redacted recents preview).
- DEK released into memory only while unlocked; cleared (zeroed) on lock/background.
- Re-auth before sensitive actions is **not** currently required beyond the app lock (a possible
  hardening: re-prompt before export/wipe).

## 4. Encrypted export / import (implemented, M5)

- User-initiated. Output = a single **password-encrypted** file (independent backup password) with a
  versioned cleartext header (magic, version, salt, iter) followed by a Tink **AES-256-GCM-HKDF**
  stream. The container key is **PBKDF2-HMAC-SHA256** over the password (same `Pbkdf2` as the PIN, 600k;
  iteration count is in the header so it can rise — a future format version may switch to Argon2id, the
  strongest case for a memory-hard KDF being the offline-attackable backup).
- Includes all tables (`data.json`) + decrypted media, re-encrypted under the device key on restore.
  Wrong password fails AEAD auth on first read. Round-trips losslessly.
- **Import-side input validation (Pass 9):** a backup file is untrusted input, so import validates it
  defensively — magic + version check; the header's PBKDF2 iteration count is bounded (100k–5M) so a
  crafted value can't hang the app on key derivation (DoS); media-blob ids (taken from the backup's ZIP
  entry names / `data.json`) are rejected if they contain path separators or `..`, and the resolved file
  is verified to stay inside the media dir, so a malicious backup can't write outside it (Zip-Slip). The
  guard lives in `EncryptedMediaStore.fileFor`, covering every read/write path. Regression-tested in
  `BackupRoundTripTest`.
- The format is documented and auditable in [EXPORT_FORMAT.md](EXPORT_FORMAT.md). Importing *other
  apps'* data is a separate generic **CSV importer** (M5b).

## 5. Anti-leak invariants (enforced in code & CI where possible)

- No `INTERNET` permission in any manifest (including dependencies' merged manifests — verify
  the merged manifest in CI).
- No analytics/ads/crash third-party SDKs.
- `allowBackup=false`, `fullBackupContent`/data-extraction rules exclude everything.
- Release builds strip/disable verbose logging; no sensitive data in logs ever.
- Secure deletion: overwrite/remove media files and vacuum DB on "delete all data".

## 6. Open items

- KDF: PBKDF2 set to 600k iterations (OWASP); optional upgrade to Argon2id (memory-hard) later.
- Keystore-backed monotonic attempt counter (harden lockout against file tampering).
- Decoy/duress mode — **deferred** (leave seams; do not build in v1).
