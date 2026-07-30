# Lens 5 — Security invariants — audit pass

- **Date:** 2026-07-30
- **Base:** `main @ 6706500` (compared against tag `v0.4.0` = `245d56e`)
- **Auditor:** Claude (pb-audit-pass, Lens 5)
- **Status:** 1 finding (1 needs-fix)

Verified against the "Hard constraints" list in `CLAUDE.md` and the finding bar in
`docs/AUDIT_PLAN.md` § Lens 5.

## What was verified clean

- **`app/src/main/AndroidManifest.xml`** — declares zero permissions in-source; explicit
  comment forbidding INTERNET; `android:allowBackup="false"` on `<application>`;
  `dataExtractionRules` still wired; only one `<activity>` (`MainActivity`) and one
  private `FileProvider`. No new components, no new permissions.
- **Merged release manifest** (`app/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml`)
  — only permissions present are `USE_BIOMETRIC`, `USE_FINGERPRINT`, and the
  signature-scoped `app.tryst.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. No
  `INTERNET`/`ACCESS_NETWORK_STATE`/`ACCESS_WIFI_STATE`. `allowBackup="false"` survives
  manifest merge.
- **`androidx.exifinterface:exifinterface:1.3.7`** (the only new dep since v0.4.0, added
  in commit `12e3d4a` for META-1) — declares no permissions and contributes nothing to
  the merged manifest above. Sole change to `app/build.gradle.kts` /
  `gradle/libs.versions.toml` in the window; no toolchain/proguard changes.
- **Anti-leak guard** (`app/build.gradle.kts:129-163`) — unchanged since v0.4.0.
  `CheckNoNetworkPermissionTask` is registered for every AGP variant via `onVariants`
  (so both `checkNoNetworkDebug` and `checkNoNetworkRelease` exist) and each guard task
  is wired to `check` via `tasks.named("check").configure { dependsOn(guard) }`. Input
  is `variant.artifacts.get(SingleArtifact.MERGED_MANIFEST)`, i.e. the post-merge
  manifest — so a leaked-in dep permission would trip it. Not disabled, not gated.
- **`FLAG_SECURE`** (`app/src/main/java/app/tryst/MainActivity.kt:43-46`) — applied
  unconditionally inside `onCreate` before `setContent`, no `BuildConfig.DEBUG` /
  `isDebuggable` / build-flavour guard. `MainActivity` is the app's sole `Activity`,
  and Compose windows inherit the flag, so every user-visible surface is covered.
- **Non-`android.util.Log` logging channels** — grep across `app/src/main/java` for
  `println`, `System.err`, `System.out`, `printStackTrace`: zero hits.
- **`android.util.Log` call sites added since v0.4.0** — exactly one, at
  `BackupViewModel.kt:60`. All other files touched by the range
  `git log v0.4.0..HEAD -- '*.kt'` are Log-free. (Finding below.)

## Finding 1 — `Log.e("TRYSTIMPORT", …)` survives release builds

- **Severity:** needs-fix
- **File:** `app/src/main/java/app/tryst/ui/settings/BackupViewModel.kt:60`
- **Introduced:** commit `13b37f2` (2026-07-30 session), same commit that fixed the
  `media.favorite` NOT-NULL restore bug.

**Claim.** The line

```
Log.e("TRYSTIMPORT", "import failed: ${e.javaClass.name}: ${e.message}", e)
```

executes in **release** builds. `app/proguard-rules.pro:5-9` strips only
`android.util.Log.v/d/i` via `-assumenosideeffects` — `Log.e` (and `Log.w`) are not
listed and therefore survive R8. CLAUDE.md hard-constraint #4 states "No plaintext
sensitive data in logs" and the intent stated there is "Logging is stripped/neutered in
release builds." A `Log.e` that runs on a shipped APK breaks that neutered-in-release
posture even if today's specific message body is benign.

**Failure scenario.** A user's real v13 backup fails to import on a phone with a
future schema bug or corrupted blob. The `Throwable.message` and full stack trace are
written to logcat with a stable tag `TRYSTIMPORT`. Concretely:

- The current call chain (`BackupManager.import` → SQLCipher / Room migration validate /
  `PRAGMA table_info` backfill) most often yields `SQLiteConstraintException` or
  `IllegalStateException`, whose Android-side messages typically don't quote column
  *values*. So the specific bug that the audit plan flags — `media.favorite`-style
  `SQLiteException` leaking a column value — is not obviously exploitable today.
- But `e.message` here is untyped: any future call inside `BackupManager.import` (or a
  wrapped `IOException`, `IllegalArgumentException`, JSON parser message, etc.) whose
  `message` includes a partner name, filename, or note fragment would be laundered
  straight to logcat on release. The tag `TRYSTIMPORT` makes it trivially greppable by
  anyone with adb access. Under a hostile-with-device threat (T2 in
  `docs/THREAT_MODEL.md`), that is exactly the kind of side-channel the invariant is
  meant to close.
- The bundled full `Throwable` (`, e` as the fourth arg) also emits the stack of every
  wrapped cause, which can drag `SQLCipher` / `Room` internals plus any user-derived
  strings that a `require(...)`/`check(...)` call along the way chose to interpolate.

**Remediation.** Either:

1. Strip `Log.e`/`Log.w` in release the same way `v/d/i` are stripped — add them to
   `proguard-rules.pro`'s `-assumenosideeffects` block. Cheapest, keeps the debug
   diagnostic. Note: this alone doesn't help someone reading debug logs of a shipped
   dev build, but the release APK is the constraint.
2. Or gate the call: `if (BuildConfig.DEBUG) Log.e(...)`. Explicit and can't be undone
   by a future proguard edit.
3. Or restrict the payload: `Log.e("TRYSTIMPORT", "import failed: ${e.javaClass.name}")`
   — drop `e.message` and the `Throwable` param. Loses fidelity for debugging but is
   the option that provably can't leak a value.

Recommend option 2 combined with option 3 for the release path: a raw diagnostic
in debug, a class-name-only line in release, no `Throwable` payload either way.

## Notes for the next lens

- Nothing observed here overlaps with Lens 6 (adb-untestable UI). The one finding is
  code-only and can be verified without touching the emulator.
- The anti-leak guard's banned list is intentionally the three canonical network
  permissions (`INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`). Broader
  network-adjacent permissions (`CHANGE_WIFI_STATE`, `NEARBY_WIFI_DEVICES`,
  `BLUETOOTH_*`) aren't checked; that's a design choice, not a regression from v0.4.0,
  so not raised here. If it deserves review it belongs in a hardening pass, not this
  delta audit.
