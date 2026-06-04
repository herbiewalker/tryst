# Tryst — Threat Model

Status: **Draft v0.1**

## Assets to protect

- **A1** Encounter records (the highly sensitive content).
- **A2** Partner identities.
- **A3** Photo attachments.
- **A4** The mere fact / metadata of usage (timestamps, app presence).

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
| T2 extracts the key | Key derived from user passphrase (Argon2id) and never persisted in plaintext; Keystore only holds a biometric-gated wrapper — see [SECURITY_DESIGN.md](SECURITY_DESIGN.md) |
| T2 recovers data from OS cloud backup | `allowBackup=false`; excluded from auto-backup |
| T3 intercepts traffic | **No `INTERNET` permission** — the app cannot open a socket; nothing to intercept |
| T3 via bundled SDK | No analytics/ads/crash/third-party SDKs |
| Data exfil via export file | Export is password-encrypted; user controls where it goes |

## Residual risks (document, don't pretend away)

- **R1** If the user forgets the passphrase (in passphrase-root model), data is unrecoverable
  by design. UX must make this consequence explicit at setup.
- **R2** While the app is unlocked and in the foreground, decrypted content is in memory and
  on screen — vulnerable to live observation or a compromised OS.
- **R3** Biometric convenience unlock is only as strong as the device's biometric + Keystore;
  the passphrase remains the true root of trust.
- **R4** Forensic tools may detect the app's *presence* even if content is encrypted (no
  deniability until/unless decoy mode is built).
