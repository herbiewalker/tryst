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

## M3 — Core logging  ✅ done (verified building & running on emulator)
- [x] Compose Navigation shell (bottom nav: History / Partners / Settings) replacing the
      placeholder home; `MainActivity` Unlocked branch renders `TrystApp()`.
- [x] **Add/edit/delete encounters**: date/time pickers, duration, partner multi-select,
      protection (multi), mood / orgasm / initiator, 1–5 rating, note (`EncounterEditScreen`).
- [x] **Partner management**: list, add/edit dialog (named or anonymous, note), archive.
- [x] **History** list with empty state; tap to edit.
- [x] **Settings**: biometric enable/disable (relocated from home), Lock now, **Delete all data**
      (`SessionManager.deleteAllData()` wipes keys + DB + media → setup).
- [x] ViewModels use `stateIn` + `.catch` (survive the DB closing on auto-lock).
- [ ] Deferred: history **filters/search**, change-PIN UI, configurable auto-lock timeout.

## M3.x — Category, partner & presentation expansion  ✅ done (verified on emulator)
- [x] **M3.1 (schema v5):** partner sex/gender/relationship (+ `photoMediaId` M4 hook); custom
      **acts** (new `acts` table; practices → string IDs gave/received); big enum expansion;
      **Setting & Context** → **Setting & Location** (places) + new **Occasion** category;
      threesome/group/swinging moved to Kink. **Theming:** purple/green palette + Material You
      toggle + Light/Dark/System (persisted in `ThemePreferences`).
- [x] **M3.2:** Trysts card badge → **custom per-act vector icons** (intensity-ranked); **calendar
      view** toggle (month grid; each day shows its headline act icon). Icons swappable for realistic
      art later with no code change.
- [x] **M3.3 (schema v6):** per-partner orgasm counts + per-orgasm ejaculation; blowjob & oral-for-her
      acts.
- [x] **Cleanup:** PBKDF2 → 600k (OWASP); dependency refresh (Room pinned 2.7.1); modern Kotlin DSL.

## M4 — Media attachments  ✅ done (verified on emulator)
- [x] Pick photos via the Android Photo Picker (`PickVisualMedia`, image-only — no storage permission,
      keeps the zero-permission guarantee).
- [x] Encrypt on attach into app-internal storage; thumbnails + full view **decrypted in-memory only**
      (manual `BitmapFactory` downsampling via `ui/common/MediaImages` — no third-party loader, no temp files).
- [x] Attach/remove photos on the encounter editor (staged, committed on Save), full-screen viewer,
      thumbnail on the history/calendar card.
- [x] Partner photo (reuses `photoMediaId`) on the partner add/edit dialog + card avatar.
- [x] **In-app camera capture** (FileProvider → private cache → encrypt → delete plaintext temp);
      sensitive shots never touch MediaStore/gallery/cloud. No CAMERA permission needed.
- [x] Resilient picker: Photo Picker with an ACTION_GET_CONTENT fallback (some emulators advertise
      the picker without providing it).
- [x] `MediaAttachmentTest`: attach round-trip, on-disk blob verified encrypted, delete cleans up. 14/14 green.

## M5 — Backup & portability
- Encrypted export + import; write `docs/EXPORT_FORMAT.md`.
- Full wipe / secure delete.

## M6 — Insights
- Stats engine, charts, streaks/trends, per-partner & per-attribute breakdowns.

## M7 — Achievements
- Local achievement rules, progress tracking, unlock UI.

## M8 — Polish & release prep
- A11y pass + **i18n: extract all hardcoded UI strings to `strings.xml`** (deferred "chunk 6").
- Optional cleanup: refactor large editor VMs to a single immutable `UiState` (deferred "chunk 6").
- Onboarding copy (esp. PIN-loss / no-recovery warning).
- Finalize **license & distribution** ([DECISIONS.md](DECISIONS.md)); F-Droid metadata if chosen.
- Security self-review against [THREAT_MODEL.md](THREAT_MODEL.md).

> Sequencing rationale: security/storage foundations come **before** features so we never
> retrofit encryption onto plaintext data.
