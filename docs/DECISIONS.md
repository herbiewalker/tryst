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

## Open

- **O-1 Encryption key model:** Option A (passphrase root + biometric) vs B (Keystore-only).
  Recommendation = A. **Decide at M2.** See [SECURITY_DESIGN.md](SECURITY_DESIGN.md) §1.
- **O-2 License & distribution:** GPLv3 vs MIT/Apache; F-Droid and/or Play. **Decide at M8.**
  Repo structured to keep options open.
- **O-3 Charts library:** Vico vs alternatives — decide at M6.
- **O-4 Multi-partner per encounter in UI:** data model supports M:N; confirm v1 UI scope.
