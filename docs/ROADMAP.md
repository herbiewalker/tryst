# Ember — Roadmap / Milestones

Status: **Draft v0.1** — high-level sequencing. Each milestone should end runnable & tested.

## M0 — Project scaffold
- Android project: Gradle KTS, version catalog, Compose, Hilt, base theme.
- CI skeleton + the **anti-leak guard** (no `INTERNET`, no banned SDKs) wired early.
- `allowBackup=false`, `FLAG_SECURE` baseline.

## M1 — Encrypted storage foundation
- Room + SQLCipher wired with an injectable key.
- Media crypto module (Tink AES-GCM) + encrypted file store.
- Core entities (Encounter, Partner, etc.) and migrations.

## M2 — Security & app lock  ← finalize the key model here
- Decide Option A vs B ([SECURITY_DESIGN.md](SECURITY_DESIGN.md) §1).
- First-run setup (passphrase + biometric), Argon2id KDF, Keystore wrapping.
- App lock, auto-lock, key lifecycle/zeroization.

## M3 — Core logging
- Add/edit/delete encounters with rich fields.
- Partner management (named/anonymous, archive).
- History list + filters.

## M4 — Media attachments
- Attach/view photos (encrypted end-to-end on device), in-memory decryption only.

## M5 — Backup & portability
- Encrypted export + import; write `docs/EXPORT_FORMAT.md`.
- Full wipe / secure delete.

## M6 — Insights
- Stats engine, charts, streaks/trends, per-partner & per-attribute breakdowns.

## M7 — Achievements
- Local achievement rules, progress tracking, unlock UI.

## M8 — Polish & release prep
- A11y pass, theming, onboarding copy (esp. passphrase-loss warning).
- Finalize **license & distribution** ([DECISIONS.md](DECISIONS.md)); F-Droid metadata if chosen.
- Security self-review against [THREAT_MODEL.md](THREAT_MODEL.md).

> Sequencing rationale: security/storage foundations come **before** features so we never
> retrofit encryption onto plaintext data.
