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

## Open

- **O-2 License & distribution:** GPLv3 vs MIT/Apache; F-Droid and/or Play. **Decide at M8.**
  Repo structured to keep options open.
- **O-3 Charts library:** Vico vs alternatives — decide at M6.
- **O-4 Multi-partner per encounter in UI:** data model supports M:N; confirm v1 UI scope.
