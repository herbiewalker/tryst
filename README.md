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

🚧 **Early planning.** No code yet — see [`docs/`](docs/) for requirements, threat model,
and architecture. Start with [docs/REQUIREMENTS.md](docs/REQUIREMENTS.md).

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

## License

To be decided (see [DECISIONS.md](docs/DECISIONS.md) O-2).
