# Tryst test dataset

A realistic, coverage-complete dataset for exercising the app, shipped as an encrypted
backup container that you restore through the normal import flow.

- **File:** [`samples/tryst-test-dataset.tryst`](../../samples/tryst-test-dataset.tryst)
- **Import password:** `Tryst-Test-2026`

## How to load it

1. Install/run the app and get past the lock (set up a PIN if it's a fresh install).
2. **Settings → Import / Restore backup**, pick `tryst-test-dataset.tryst`, enter the password above.

> Restore uses `INSERT OR REPLACE`, so importing into a populated app merges the rows. Import
> into a fresh install (or after "delete all data") for a clean view.

## What's in it

| | |
|---|---|
| Partners | **7** — 6 named + 1 anonymous. One **spouse** (Jordan); the rest are sensible for an open setup (poly partner, FWB, casual, ex, one-night-stand, anonymous stranger). |
| Photos | A distinct avatar on each of the 6 named partners; the anonymous one has none (tests the no-avatar path). |
| Encounters | **156**, spanning 2025-12 → 2026-06. |
| Solo | **13** solo encounters (no partner; partner-only fields omitted). |
| Multi-partner | **7** encounters linked to 2 partners (tests the M:N path + group kinks). |
| Same-day | **41** days carry 2+ encounters (tests multiple-per-day). |
| Encounter photos | ~half the encounters (78) carry photos — 1–3 each, 107 blobs total. |

Durations, ratings, moods, protection, and acts are chosen to fit each encounter (quickies are
short, anniversaries long; the spouse skews vanilla/romantic, casual partners spicier).

**Every value in every category is used at least once** — Mood (34), Protection (23),
Ejaculation location (22), Acts/Practice (39), Kink (51), Setting (34), Occasion (18), Toy (28),
Position (48), Initiator (3). Verified by the generator's coverage report.

> **v10 note (FDP-2/D-41):** the generator's act/kink pools were trimmed to the non-explicit v10
> starter catalogs (Act 16, Kink 16 — counts above describe the committed pre-v10 samples). The old
> samples still restore fine: import adopts their removed ids into custom entries automatically.
> Regenerate to get samples that exercise the trimmed catalogs.

## Rebuilding

```bash
tools/dataset/build.sh                       # default password Tryst-Test-2026
tools/dataset/build.sh 'my-pw' out.tryst     # custom password / path
```

The build is two stages:

1. `generate_dataset.py <dir>` — emits the **plaintext** backup payload (`data.json`, the generic
   table dump matching schema v6, plus `media/<id>` PNG blobs). Stdlib only; deterministic (seeded).
2. `Pack.java` — wraps that payload in the real `.tryst` container using the **same Tink
   `AesGcmHkdfStreaming`** the app uses, so the output is byte-compatible with `BackupManager`.
   `Verify.java` then decrypts it back (mirrors import) as a self-check.

Format reference: [`docs/EXPORT_FORMAT.md`](../../docs/EXPORT_FORMAT.md).
