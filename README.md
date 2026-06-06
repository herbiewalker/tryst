# Tryst

A **private, local-only, open-source** Android app for tracking intimate encounters.
Inspired by the iOS app *Nice*. Built so your most personal data never has to leave your
device or be trusted to anyone.

## Privacy promises

- 🚫 **No network access at all** — the app declares no internet permission, so it
  *cannot* send your data anywhere.
- 🔒 **Encrypted on device** — database and photos are encrypted at rest.
- 📵 **No analytics, no ads, no tracking** — zero third-party SDKs.
- 📤 **You own your data** — the only way data leaves is a manual, password-encrypted
  export that you control.
- 🔍 **Open source** — so anyone can verify the above.

## What it does

- Log encounters with rich details and optional (encrypted) photos.
- Track named or anonymous partners, with per-partner stats.
- See insights: stats, charts, streaks, and achievements.
- Lock the app behind biometric/PIN; auto-lock when backgrounded.

## Status

🚧 **In development.** Kotlin + Jetpack Compose, `minSdk 31` / `targetSdk 36`. Builds and runs.

Progress against the [roadmap](docs/ROADMAP.md):

- ✅ **M0** — Project scaffold; CI with an anti-leak guard (build fails if any network
  permission appears); `allowBackup=false`, `FLAG_SECURE`.
- ✅ **M1** — Encrypted storage: Room over SQLCipher, Tink-encrypted media store
  (DB verified encrypted on disk).
- ✅ **M2a** — Key vault: random data key double-wrapped by an Android Keystore key + a
  distinct 6-digit app PIN, with failed-attempt self-wipe.
- ✅ **M2b** — PIN setup/lock screens, post-unlock database session, auto-lock on background,
  and biometric unlock (with PIN fallback).
- ✅ **M3** — Navigation shell, encounter logging (add/edit/delete with rich fields), partner
  management, history list, and a settings screen (biometric, lock, delete-all-data).
- ✅ **M3.x** — Partner sex/gender/relationship; custom **acts** (alongside custom positions);
  big category expansion; **Setting & Location** split out from **Occasion**; per-partner orgasm
  counts + per-orgasm ejaculation; **theming** (purple/green palette, Light/Dark/System, optional
  Material You); a **calendar** view on the Trysts screen with custom per-act icons. Schema at **v6**.
- ⬜ **M4** media attachments UI (encrypted photos on encounters & partners) · M5 encrypted backup ·
  M6 insights · M7 achievements · M8 release (incl. string/i18n + a11y pass).

New here? See [docs/REQUIREMENTS.md](docs/REQUIREMENTS.md) for scope and
[docs/SETUP_WINDOWS.md](docs/SETUP_WINDOWS.md) to build it.

## Documentation

| Doc | What |
|-----|------|
| [REQUIREMENTS.md](docs/REQUIREMENTS.md) | Functional & non-functional requirements |
| [THREAT_MODEL.md](docs/THREAT_MODEL.md) | Adversaries, mitigations, residual risk |
| [SECURITY_DESIGN.md](docs/SECURITY_DESIGN.md) | Encryption & key management |
| [DATA_MODEL.md](docs/DATA_MODEL.md) | Entities & fields |
| [ARCHITECTURE.md](docs/ARCHITECTURE.md) | Stack & module layout |
| [ROADMAP.md](docs/ROADMAP.md) | Milestones |
| [DECISIONS.md](docs/DECISIONS.md) | Decision log & open questions |
| [SETUP_WINDOWS.md](docs/SETUP_WINDOWS.md) | Build & run on Windows |

## License

To be decided (see [DECISIONS.md](docs/DECISIONS.md) O-2).
