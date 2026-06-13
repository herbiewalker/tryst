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
- See insights: customizable stats, charts (bars/line/donut), streaks, and per-type breakdowns.
- Earn achievements for milestones, streaks, variety, and more (all derived on-device).
- Lock the app behind biometric/PIN; auto-lock when backgrounded (with a configurable delay) and a
  Change-PIN flow.
- Tune it in **Settings → General**: app info, change PIN, auto-lock timeout, haptics, calendar week start.

## Status

🚧 **In development.** Kotlin + Jetpack Compose, `minSdk 31` / `targetSdk 36`. Builds and runs.
See [docs/FLOWCHARTS.md](docs/FLOWCHARTS.md) for how the core logic fits together.

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
- ✅ **M4** — encrypted photo attachments UI: pick via the Android Photo Picker (no permission),
  encrypted to app-internal storage, decrypted in-memory only; on encounters (editor + viewer +
  history/calendar thumbnail) and partners (avatar).
- ✅ **M5** — password-encrypted backup export + restore (full fidelity incl. photos; see
  [EXPORT_FORMAT.md](docs/EXPORT_FORMAT.md)), plus **CSV import** from other apps/spreadsheets
  with column mapping.
- ✅ **M6** — **Insights**: a pure-Kotlin stats engine (totals, week streaks, averages, monthly &
  weekday trends, ratings, and per-attribute breakdowns incl. partners, acts, positions, moods, kinks,
  initiator, and an orgasm drill-down). Fully **customizable**: reorder/hide the stat tiles and section
  cards, pick a chart style **per card** (bars / line / donut), and consistent **per-type colors**
  across every chart. Hand-drawn charts (no chart dependency).
- ✅ **M7** — **Achievements**: ~67 derived achievements (milestones, week streaks, variety, pleasure,
  occasions) with progress bars and unlock dates, on a dedicated screen (trophy icon in Insights) plus an
  Insights teaser card. Computed from the log — no extra storage.
- 🚧 **M8** — release prep, in progress: ✅ i18n string extraction (~200 `strings.xml` entries); ✅ CI
  **quality gates** (build-failing Detekt + ktlint); ✅ security self-review (+ a LOW backup-importer
  SQL-injection fix); plus a **solo-aware editor** and a **Settings → General** section (app info, Change
  PIN, configurable auto-lock timeout, haptics, calendar week start). Remaining: optional `UiState`
  refactor, onboarding copy, and the license & **distribution** choice.

### Pre-release audit passes

Separate from the milestones, a **12-pass pre-release audit program** runs each pass in a fresh session
(full prompts in [docs/PRERELEASE_PROMPT_PACK.md](docs/PRERELEASE_PROMPT_PACK.md); status tracked in
[docs/ROADMAP.md](docs/ROADMAP.md)). **All 12 done — final verdict: GO (conditional):**

- ✅ 1 Material 3 / Modern UI · ✅ 2 Edge-to-edge & insets · ✅ 3 Motion & micro-interactions · ✅ 4 Accessibility
  · ✅ 5 Adaptive layouts *(nav rail on medium+, two-pane Trysts/editor on expanded, width-capped screens)*
  · ✅ 6 Manifest & exported components *(MASVS — clean: sole exported activity takes no intent input; no
  dangerous perms; debuggable/tooling stripped from release)*
  · ✅ 7 Secrets, storage & logging *(MASVS — no hardcoded secrets, zero sensitive logging, Keystore
  double-wrapped DEK; one LOW fix: sweep orphaned plaintext camera temps on unlock + full-wipe)*
- ✅ 8 Network security *(MASVS — zero network surface; no networking lib, no INTERNET perm)*
  · ✅ 9 WebView & input *(no WebViews; fixed MED Zip-Slip + LOW KDF-DoS in backup import)*
  · ✅ 10 Dependencies & license *(no CVEs, no version bumps needed; all deps GPLv3-compatible;
  project licensed **GPLv3** — LICENSE + THIRD_PARTY_NOTICES.md + in-app Settings → About screen)*
- ✅ 11 Release hardening (R8) *(release build verified end-to-end on emulator under R8: zero R8 warnings,
  no app keep rules needed, full PIN→SQLCipher→unlock path works obfuscated; unsigned artifact = no leaked
  signing creds; Play Integrity rejected as incompatible with no-network)*
  · ✅ 12 Final go/no-go *(**GO, conditional** — regression re-scan clean, license artifacts current, R8
  release build launches end-to-end on the actual minified binary, zero TODO/FIXME; remaining before a
  public build is human-only: real signing config, MobSF dynamic scan, real-device + TalkBack pass)*

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
| [FLOWCHARTS.md](docs/FLOWCHARTS.md) | Visual maps of the core logic flows |
| [ROADMAP.md](docs/ROADMAP.md) | Milestones |
| [DECISIONS.md](docs/DECISIONS.md) | Decision log & open questions |
| [SETUP_WINDOWS.md](docs/SETUP_WINDOWS.md) | Build & run on Windows |

## License

**GPLv3.** Tryst is free, open-source software licensed under the
[GNU General Public License v3.0](LICENSE) — so anyone can verify the privacy promises above,
and any redistributed version must also be GPLv3 with source available. Third-party components
and their licenses are listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and in-app under
**Settings → About** (all GPLv3-compatible).
