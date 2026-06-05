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
    via a slow KDF (PBKDF2-HMAC-SHA256, high iteration count — abstracted, upgradeable to Argon2id).
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
- **Preferences:** app settings via DataStore; anything sensitive encrypted, non-sensitive
  prefs (theme, lock timeout) may be plaintext.

## 3. App lock & UI hardening

- App lock on cold start and on return from background (after configurable timeout).
- `FLAG_SECURE` on all windows (no screenshots, redacted recents preview).
- DEK released into memory only while unlocked; cleared on lock/background.
- Optional re-auth before sensitive actions (export, wipe).

## 4. Encrypted export / import

- User-initiated. Output = a single **password-encrypted** file (independent password; derive
  with Argon2id; authenticated encryption, e.g., AES-256-GCM, with versioned header).
- Includes DB contents + media. Import validates the header/version and round-trips losslessly.
- Document the format in `docs/EXPORT_FORMAT.md` (to be written at M5) so it's auditable.

## 5. Anti-leak invariants (enforced in code & CI where possible)

- No `INTERNET` permission in any manifest (including dependencies' merged manifests — verify
  the merged manifest in CI).
- No analytics/ads/crash third-party SDKs.
- `allowBackup=false`, `fullBackupContent`/data-extraction rules exclude everything.
- Release builds strip/disable verbose logging; no sensitive data in logs ever.
- Secure deletion: overwrite/remove media files and vacuum DB on "delete all data".

## 6. Open items

- KDF tuning (PBKDF2 iteration count per device class); optional upgrade to Argon2id.
- Keystore-backed monotonic attempt counter (harden lockout against file tampering).
- Decoy/duress mode — **deferred** (leave seams; do not build in v1).
