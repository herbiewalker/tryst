# Tryst — Roadmap / Milestones

Status: **Draft v0.1** — high-level sequencing. Each milestone should end runnable & tested.

## M0 — Project scaffold  ✅ done (verified building & running)
- [x] Android project: Gradle KTS, version catalog, Compose, Hilt, base theme.
- [x] CI skeleton + the **anti-leak guard** (no network permission, no banned SDKs), enforced
      both in-build (`checkNoNetwork*` Gradle task) and in CI.
- [x] `allowBackup=false`, `FLAG_SECURE`, data-extraction exclusions baseline.
- [x] Placeholder launcher icon + sample unit/instrumented tests.
- [x] **Build verified** on AGP 9.2.1 / Kotlin 2.2.10 / Gradle 9.5 (Android Studio Quail):
      `assembleDebug` succeeds, anti-leak guard passes, unit test passes, app installs & runs
      on emulator. Gradle wrapper committed.

## M1 — Encrypted storage foundation  ✅ done (verified on emulator)
- [x] Room + SQLCipher wired via an injectable key behind `DatabaseKeyProvider`
      (M1 placeholder = `InsecureDevKeyProvider`; real key is M2). Native lib loaded once
      via `SqlCipherLibrary.ensureLoaded()`.
- [x] Media crypto module (Tink AES-256-GCM-HKDF streaming) + `EncryptedMediaStore`
      (encrypted blobs in app-internal storage, never MediaStore).
- [x] Core entities + cross-refs (Encounter, Partner, Location, Tag, Position, Media) with
      converters; schema v1 exported to `app/schemas/`. Repositories for Encounter & Partner.
- [x] Instrumented tests pass: relations round-trip; **DB file on disk is verified encrypted**
      (no "SQLite format 3" header, no plaintext values); media crypto round-trips & rejects
      wrong associated data. Room bumped 2.6.1 → 2.7.1 for KSP2/Kotlin 2.2 compatibility.

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
