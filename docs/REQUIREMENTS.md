# Tryst — Requirements

Status: **v1 (2026-06-08)** — scope largely implemented through M7; M8 (polish & release) remaining.

## 1. Vision

A private, local-only Android app to log intimate encounters and surface fun, useful
insights — built so the user never has to trust anyone (no company, no cloud, no network)
with deeply personal data. Inspired by the iOS app *Nice*.

## 2. Principles (ranked)

1. **Privacy by architecture** — the app *cannot* leak data because it has no network access.
2. **User owns the data** — encrypted on-device; only the user can export/move it.
3. **Open source & auditable** — anyone can verify the claims above.
4. **Pleasant to use** — fast logging, attractive stats; privacy shouldn't feel like a chore.

## 3. Personas

- **Primary:** an individual tracking their own intimate life, possibly across multiple
  partners, who cares strongly that this data stays private even from people physically
  near them and from anyone examining the device.

## 4. Functional requirements

### 4.1 Encounters (core)
- **FR-1** Create / edit / delete an encounter.
- **FR-2** Fields (see [DATA_MODEL.md](DATA_MODEL.md) for full list): date & time, duration,
  partner(s), positions, location, protection used, satisfaction/orgasm rating, mood,
  initiator, free-text note, tags.
- **FR-3** Attach one or more **photos** to an encounter (stored encrypted; never in gallery).
- **FR-4** Browse encounters in a reverse-chronological history with quick filters.

### 4.2 Partners
- **FR-5** Create named or **anonymous** partners.
- **FR-6** Per-partner stats and history.
- **FR-7** Merge / archive / delete a partner (with clear handling of their linked encounters).
- **FR-21** Per-partner **demographics**: date of birth (→ derived age), ethnicity, height, body type,
  and location, alongside the existing sex / gender / relationship (D-36).
- **FR-22** A **self profile**: the user's own photo + the same demographics, edited from Settings and a
  "You" card on Partners; stored encrypted on-device like everything else (D-36).

### 4.3 Insights
- **FR-8** Aggregate stats: totals, frequency, streaks, averages, trends over time.
- **FR-9** Charts (e.g., frequency over time, breakdowns by partner/attribute).
- **FR-10** **Achievements / badges** for milestones; defined locally, no network.

### 4.4 Security & access
- **FR-11** App lock via **biometric and/or PIN**; required on launch.
- **FR-12** **Auto-lock** when app is backgrounded or after a configurable timeout.
- **FR-13** Redacted preview in the app switcher; screenshots blocked (`FLAG_SECURE`).
- **FR-14** First-run setup of the app lock — a distinct 6-digit **PIN** (biometric optional). The PIN
  protects the data-encryption key; there is no recovery if it's forgotten (see
  [SECURITY_DESIGN.md](SECURITY_DESIGN.md)). *Implemented as a PIN, not a passphrase (see DECISIONS D-12).*
  The PIN can be **changed** later (Settings → General) without data loss — D-31.
- **FR-18** A **General** settings section: app/how-it-works info, change PIN, auto-lock timeout (FR-12),
  haptics on/off, calendar week start.

### 4.5 Backup & portability
- **FR-15** **Manual encrypted export** to a user-chosen file (password-protected).
- **FR-16** **Import** from a previously exported file (for new-phone migration).
- **FR-17** Full **wipe** ("delete all data") on its own page, gated by a **type-to-confirm** step
  (D-34); returns the app to first-run setup.
- **FR-19** **Unsaved-changes guard:** the partner and encounter editors prompt before discarding
  unsaved edits (incl. attached photos); they don't dismiss on a stray outside-tap or back-swipe (D-33).
- **FR-20** In-app **release notes**: a "What's new" screen and a one-time popup after each app update
  (bundled, no network — D-35).

## 5. Non-functional requirements

- **NFR-1 (privacy)** No `INTERNET` permission; no analytics/ads/crash SDKs; `allowBackup=false`.
- **NFR-2 (security)** Encrypted DB (SQLCipher) + encrypted media; key never stored in plaintext.
  See [SECURITY_DESIGN.md](SECURITY_DESIGN.md).
- **NFR-3 (platform)** Latest Android (`targetSdk 36`); `minSdk 31` (Android 12).
- **NFR-4 (FOSS)** All dependencies open-source-license compatible.
- **NFR-5 (performance)** Logging an encounter is <3 taps to start; app cold-start unlock is snappy.
- **NFR-6 (accessibility)** Compose + Material 3 a11y: TalkBack labels, dynamic type, contrast.
- **NFR-7 (testability)** Repository/domain logic unit-tested; DB migrations tested.
- **NFR-8 (no data loss)** Schema migrations are non-destructive; export round-trips losslessly.

## 6. Explicitly out of scope (v1)

- Cloud sync / accounts / multi-device.
- Communication or sharing with other people.
- Disguise / decoy-PIN mode — **deferred**; leave architectural hooks, don't build.
- Wearable / Health Connect integration.
- Notifications/reminders (revisit later; must stay content-free if added).

## 7. Open decisions

Tracked in [DECISIONS.md](DECISIONS.md) — notably the encryption key model and license/distribution.
