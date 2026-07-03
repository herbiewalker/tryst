# Tryst — Threat Model

> **Status:** Live — **v0.3.0 / schema v10**, aligned with the *implemented* security model
> (Keystore-only + distinct app PIN; see [SECURITY_DESIGN.md](SECURITY_DESIGN.md) §1, Option B). The
> v9–v10 changes (kinks made id-based, then the acts/kinks catalog trim — FDP-2/D-41) are category-data
> only and add **no new attack surface** over the v7 demographics + single-row self `profile` baseline.

## Assets to protect

- **A1** Encounter records (the highly sensitive content).
- **A2** Partner identities — including the v7 **demographics** (date of birth, ethnicity, height,
  body type, location) attached to each partner.
- **A3** Photo attachments — encounter photos, partner avatars, and the self-profile photo.
- **A4** The mere fact / metadata of usage (timestamps, app presence).
- **A5** The user's **own** identifying data — the single-row `profile` (name, photo, sex/gender,
  date of birth, ethnicity, height, body type, location). Self-identifying, so its disclosure also
  links the device owner to all of the above.

## Adversaries in scope

| ID | Adversary | Capability | Priority |
|----|-----------|------------|----------|
| T1 | **Curious person with the unlocked phone** | Picks up the device, opens apps, glances at the screen, checks the app switcher | **High** |
| T2 | **Device seizure / forensic examiner** | Takes the (locked or off) device; images storage; may have root / forensic tooling | **High** |
| T3 | **Network / cloud snooping** | ISP, ad networks, app vendors, anyone in transit | Addressed by design |

## Adversaries explicitly out of scope (v1)

- **Targeted malware / fully compromised OS** with the user actively unlocking — if the
  attacker controls the OS while data is decrypted, no app-level control fully wins.
- **Someone who knows the passphrase** (e.g., coerced disclosure) — partly addressed later
  by optional decoy mode (deferred).
- **Physical observation / shoulder-surfing** while actively using the app.

## Threats → mitigations

| Threat | Mitigation |
|--------|-----------|
| T1 opens the app | App lock (biometric/PIN) required on launch; auto-lock on background and after timeout |
| T1 sees content in app switcher / screenshots | `FLAG_SECURE` on all windows → redacted preview, screenshots blocked |
| T1 finds photos in the gallery | Media never written to MediaStore/shared storage; encrypted in app-internal storage only |
| T2 images the disk | SQLCipher-encrypted DB + AES-GCM-encrypted media; no plaintext at rest |
| T2 extracts the key | DEK is **double-wrapped** and never persisted in plaintext: an outer layer from a non-exportable, hardware-backed **Android Keystore** key (StrongBox when available) and an inner layer from the **app PIN** (PBKDF2, 600k). A *disk image alone* can't open it — the Keystore key can't be extracted. Residual: a rooted, live device could attempt an on-device PIN brute force; the slow KDF + 10-attempt self-wipe raise the cost (see R-PIN). See [SECURITY_DESIGN.md](SECURITY_DESIGN.md) |
| T2 recovers data from OS cloud backup | `allowBackup=false`; excluded from auto-backup |
| T3 intercepts traffic | **No `INTERNET` permission** — the app cannot open a socket; nothing to intercept |
| T3 via bundled SDK | No analytics/ads/crash/third-party SDKs |
| Data exfil via export file | Export is password-encrypted; user controls where it goes |
| Crafted backup file abuses the importer | Import treats the file as untrusted: magic + version check, the file-supplied PBKDF2 iteration count is bounded (100k–5M) so a hostile header can't hang the app, media-blob ids are rejected if they contain path separators / `..` (no Zip-Slip write outside the media dir), and DB column names from the backup JSON are vetted against a plain-identifier pattern before reaching the framework's unquoted `INSERT` column list (no SQL injection — `BackupManager.restoreDatabase`). Wrong password still fails AEAD auth. See [SECURITY_DESIGN.md](SECURITY_DESIGN.md) §4 |

## Residual risks (document, don't pretend away)

- **R1** **No recovery.** If the user forgets the app **PIN** and has no biometric enrolled, the data
  is unrecoverable by design; likewise a forgotten **backup password**. UX must make this explicit at
  setup. (An optional passphrase-root mode could be added later behind the same key seam.)
- **R2** While the app is unlocked and in the foreground, decrypted content is in memory and on screen
  — vulnerable to live observation or a compromised OS.
- **R-LOCK** Auto-lock defaults to **immediate** on background. If the user raises the auto-lock timeout
  (Settings → General), the DEK and open DB stay in memory for that window while backgrounded, so a
  curious person (T1) who picks the phone up within the window finds it unlocked. Opt-in; the default
  preserves the strongest guarantee and the setting states the trade-off inline. See D-31.
- **R3** Biometric convenience unlock is only as strong as the device's biometric + Keystore; the app
  **PIN** is the fallback and root of trust.
- **R-PIN** A 6-digit PIN is brute-forceable in principle. The hardware-bound outer wrap means a disk
  image alone can't crack it, but a rooted live device could try on-device; mitigated (not eliminated)
  by the slow KDF and the 10-attempt self-wipe. A user passphrase would be strictly stronger.
- **R4** Forensic tools may detect the app's *presence* even if content is encrypted (no deniability
  until/unless decoy mode is built).
