# Tryst — Decision Log

Lightweight ADR log. Newest at top. "Open" items still need a call.

## Decided (from scoping conversation, 2026-06-04)

- **D-1 User model:** Solo user with multiple partners (named or anonymous), per-partner
  stats. No accounts, no sync, no second device.
- **D-2 Threat model:** Protect against (a) someone holding the phone, (b) device
  seizure/forensics, (c) any network leakage. Disguise/decoy mode deferred.
- **D-3 Privacy posture:** **No `INTERNET` permission**; no analytics/ads/crash SDKs;
  `allowBackup=false`; `FLAG_SECURE`.
- **D-4 Encryption at rest:** SQLCipher DB + AES-GCM-encrypted media in app-internal storage.
- **D-5 Entry data:** Rich details + photo attachments.
- **D-6 Insights:** Stats + charts + achievements/badges (all local).
- **D-7 Backup:** Manual, user-initiated **encrypted** export/import only.
- **D-8 Platform/stack:** Kotlin, Compose/Material 3, Room+SQLCipher, Hilt,
  **`minSdk 31` (Android 12)** / `targetSdk 36` (Android 16).
- **D-9 App name & package:** **Tryst** / `app.tryst`. (Prefix with a personal domain/handle
  later if publishing.)
- **D-10 (M1) Key behind an interface:** all data-at-rest keys come from `DatabaseKeyProvider`.
  M1 binds a clearly-labeled `InsecureDevKeyProvider` placeholder so the storage layer can be
  built/tested; the real implementation (O-1) swaps only that one Hilt binding at M2.
- **D-11 (M1) Media encryption:** Tink `AesGcmHkdfStreaming` (AES-256-GCM-HKDF, streaming),
  built directly from key material — no Tink keyset/Keystore management until M2.
- **D-12 (O-1 resolved) Key model = Keystore-only.** Random DEK wrapped by a hardware-backed
  Android Keystore key (StrongBox when available) layered with a key derived from a **distinct
  6-digit app PIN** (separate from the device lock). Biometric unlock via the Keystore (M2b).
  Chosen for UX over the recommended passphrase model; see [SECURITY_DESIGN.md](SECURITY_DESIGN.md)
  §1 for the design + residual risk (short-PIN brute force).
- **D-13 Quick unlock:** biometric (M2b) with a **6-digit app PIN** fallback; the PIN is distinct
  from the device PIN so someone who knows the phone's lock still can't open Tryst.
- **D-14 Auto-lock:** lock on background by default (immediate); timeout is user-configurable.
- **D-15 PIN KDF:** PBKDF2-HMAC-SHA256 (high iteration count) for M2a to avoid a native-lib
  dependency on the new AGP 9 toolchain; abstracted so it can be upgraded to Argon2id later.
- **D-16 (M3+) Expanded encounter fields (schema v2):** per-person **orgasm counts**
  (self/partner, replacing the single who-finished enum — the legacy `orgasm` column is kept,
  unused, for migration safety), **ejaculation locations** (multi), and **practices
  performed/received** (two multi-selects over a `Practice` enum). Expanded `Mood` and
  `Protection` option sets. Delivered via the project's **first Room migration** (v1→v2,
  additive nullable columns), validated by an instrumented `MigrationTest` against the exported
  schemas. New set columns are nullable to keep the migration default-free (avoids Room's
  NOT-NULL-default schema-validation mismatch).
- **D-17 (M3+) Positions + pop-out selectors (schema v3):** `positions` column (migration v2→v3,
  `MIGRATION_2_3`) stores **string IDs** — a built-in `Position` enum name or `custom:<uuid>` — so
  custom positions can be mixed in. Custom positions are user-managed `PositionEntity` rows
  (`isBuiltIn=false`) via `PositionRepository`, added/removed in **Settings → Manage custom
  positions**, and merged with built-ins in the editor's Positions picker. Editor category
  selectors use `MultiSelectField`/`SingleSelectField` (ui/common): **inline shows the curated
  common set until something is selected, then only the selections** (+ "More…" dialog with the
  full set **alphabetical** by label). `MigrationTest` validates v1→v3. (The M1 position cross-ref
  relation is unused; kept for migration safety.)

- **D-18 (M3+) Category restructure + display labels (schema v4):** "Practices" split into
  **Acts** (gave/received, `Practice` enum), **Kink & BDSM** (`Kink`), **Setting & context**
  (`Setting`), and **Toys** (`ToyType`) — each its own nullable column (kinks/contexts/toys;
  `MIGRATION_3_4`). Every category enum implements **`DisplayLabel`** with explicit human-written
  labels (fixes IUD/PrEP/PEP/DoxyPEP/69 casing, "Birth control"→"Pill", "Gave/Received" wording),
  shown in the UI via `it.label`. Added moods (tipsy, confident, desired, loved, safe…), acts
  (titjob, anal fingering, spit play, face-fucking), the full setting list, and toy types. Enum-set
  converters now **skip unknown names**, so values that moved categories don't crash older rows.
  `MigrationTest` validates v1→v4.

- **D-19 (cleanup, 2026-06-06):** PBKDF2 iterations **200k → 600k** (OWASP; per-vault `iter` so it's
  back-compatible). Dependency refresh (Hilt 2.57.1, Compose BOM 2026.04.01, lifecycle 2.10.0,
  activity 1.12.4, coroutines 1.11.0, navigation 2.9.0, coreKtx 1.16.0). **Room pinned at 2.7.1** —
  2.8+ `room-testing` needs a newer kotlinx-serialization than the Kotlin 2.2.10 toolchain ships
  (breaks `MigrationTest`); bump with the next Kotlin upgrade. Deprecated `kotlinOptions` →
  `kotlin { compilerOptions }`.
- **D-20 (M3.1, schema v5):** **Partner** gains sex / gender / relationshipType (enums) + a
  `photoMediaId` hook for M4 photos. **Custom Acts** added (new `acts` table mirroring `positions`;
  practices now stored as **string IDs** — built-in `Practice` name or `custom:<uuid>` — gave/received,
  managed in Settings). **Setting & Context** split into **Setting & Location** (places only) + a new
  **Occasion** category; threesome/group/swinging moved to **Kink**. **Theming:** brand purple/green
  Material 3 palette as default with a **Material You** toggle and **Light/Dark/System** mode,
  persisted in `core/prefs/ThemePreferences` (plain SharedPreferences — non-sensitive). `MigrationTest`
  v1→v5.
- **D-21 (M3.2):** Trysts card badge switched from ambiguous emoji to **custom per-act vector
  drawables** (`res/drawable/ic_act_*.xml`), chosen by a full intensity ranking
  (`ui/common/PracticeVisuals`). Added a **calendar view** toggle (month grid; each day shows its
  headline act icon). Icons are single tintable colour, so they can be **swapped for more realistic
  artwork later** with no code change. `rankedActs()` already supports a future top-2 badge.
- **D-22 (M3.3, schema v6):** **Per-partner orgasm counts** (`partnerOrgasms` column = partnerId→count,
  one counter per selected partner labelled by name; legacy `orgasmCountPartner` kept) and
  **per-orgasm ejaculation** (the `ejaculationLocations` column repurposed to orgasmIndex→location —
  your self-orgasm count drives N single-select ejaculation rows). Added Blowjob, Ball-sucking,
  Cunnilingus, Clit-sucking acts. `MigrationTest` v1→v6.
- **D-25 (M5):** **Encrypted backup** = a password-derived key (PBKDF2-HMAC-SHA256, 600k; header
  carries salt+iters so Argon2id can come later) over a **Tink-streamed ZIP** of `data.json` (every
  table, generic column dump) + decrypted media. Re-encrypt model (not raw-DB copy) because the live
  DEK is device-bound. Restore re-encrypts media under the new device's key, repoints `encFilePath`,
  `INSERT OR REPLACE` with deferred FKs. **Export is encrypted-only** (no plaintext export) to keep the
  no-unprotected-data promise. Files via SAF; auto-lock suppressed across the handoff. Format in
  `docs/EXPORT_FORMAT.md`. **Importing other apps' data (Intimacy/LoveLust/etc.) = M5b**, a separate
  generic **CSV importer with column mapping** (no shared standard between trackers).
- **D-24 (M4):** Photo input = Android **Photo Picker** with an **ACTION_GET_CONTENT fallback**
  (some devices/emulators advertise the picker without providing the activity → `ActivityNotFoundException`).
  Plus **in-app camera capture** via a `FileProvider` into a private cache file, encrypted into the media
  store and the plaintext temp then deleted — sensitive shots never touch MediaStore / gallery / cloud
  backup. No CAMERA permission (ACTION_IMAGE_CAPTURE delegates to the camera app), so the zero-permission
  / no-network guarantee holds (anti-leak guard still green). Encounter photos stage on pick and commit
  on Save; partner photo is a single avatar; both clean up camera temps on save/cancel.
  **Auto-lock vs. handoff:** launching the picker/camera backgrounds the app, which would trip the
  immediate auto-lock and drop the result + in-progress screen. Fixed via
  `SessionManager.suppressNextAutoLock()` (a ~2-min one-shot window, consumed on the next background)
  called right before launch; `ON_STOP` now routes through `onAppBackgrounded()` which honours it.
- **D-23 (deferred):** Chunk 6 — extracting hardcoded UI strings to `strings.xml` and refactoring
  `EncounterEditViewModel` to a single `UiState` — is **deferred to M8**. String extraction belongs
  with the a11y/i18n pass; the per-field `mutableStateOf` VM pattern is already idiomatic, so the
  UiState wrap is low-value churn pre-release.

## Open

- **O-2 License & distribution:** GPLv3 vs MIT/Apache; F-Droid and/or Play. **Decide at M8.**
  Repo structured to keep options open.
- **O-3 Charts library:** Vico vs alternatives — decide at M6.
- **O-4 (resolved) Multi-partner per encounter:** **yes** — the editor supports selecting multiple
  partners (M:N) with per-partner orgasm counts (D-22).
