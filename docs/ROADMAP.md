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

## M2 — Security & app lock
Key model decided: **Keystore-only + distinct 6-digit app PIN** (O-1 → D-12). See
[SECURITY_DESIGN.md](SECURITY_DESIGN.md) §1.

### M2a — Key vault core  ✅ done (verified on emulator)
- [x] Keystore KEK (StrongBox when available, TEE fallback) + PIN-derived layer (PBKDF2);
      double-wrapped DEK.
- [x] Vault: `setup(pin)`, `unlock(pin)→DEK`, `changePin`, `isInitialized`, failed-attempt
      lockout + self-wipe.
- [x] Instrumented tests pass (real Keystore on emulator): setup/unlock returns same DEK,
      wrong-PIN countdown, persistence across instances, changePin, lockout-wipe after 10 fails.

### M2b — Biometric, UI & session  ✅ done (PIN/session verified; biometric pending manual test)
- [x] First-run PIN **setup screen** + **lock screen** (PinPad), driven by `LockViewModel`;
      `MainActivity` (now a `FragmentActivity`) renders by `LockState`.
- [x] **Session lifecycle**: `SessionManager` builds the SQLCipher DB *after* unlock from the
      vault DEK and closes it on lock; `InsecureDevKeyProvider` and the eager DB module removed.
      Repositories + media store now read from the session.
- [x] **Auto-lock** on background (ProcessLifecycle ON_STOP → `lock()`), clearing the DEK.
- [x] **Biometric unlock** (`BiometricVault`: a separate auth-gated Keystore key wraps the DEK;
      `BiometricPrompt` CryptoObject flow), enable/disable from Home, PIN always a fallback.
      Builds & graceful when unavailable; **needs manual test with an enrolled fingerprint**.
- [x] Instrumented `SessionManagerTest`: setup→unlock→lock, persistence across restart, wrong-PIN.

> Deferred (minor): a user-configurable auto-lock *timeout* (settings UI, with M3) — the
> default (immediate on background) is already active. Tracked as a small enhancement.

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
