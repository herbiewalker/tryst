# Tryst — Project Context for Claude Code

> **Tryst** is a private, local-only, open-source Android app for tracking intimate
> encounters (inspired by the iOS app *Nice*). Sensitive subject → privacy is the
> headline feature, not an afterthought.

App name **Tryst**, package **`app.tryst`** (prefix with your own domain/handle later if desired).

---

## Hard constraints (these OVERRIDE default behavior — do not violate)

1. **No network. Ever.** The app declares **no `android.permission.INTERNET`** in the
   manifest. If a feature seems to need the network, it is out of scope — flag it,
   do not add the permission. This is the single strongest "data cannot leak" guarantee.
2. **No third-party telemetry/ads/crash SDKs.** No Firebase, Crashlytics, Google
   Analytics, ad networks, attribution SDKs. Crash handling, if any, is fully local.
3. **Encrypted at rest.** All persistent user data lives in an encrypted SQLCipher DB;
   photo/media blobs are stored encrypted in app-internal storage (never MediaStore /
   shared storage / system gallery).
4. **No plaintext sensitive data in logs.** No PII, entry contents, or partner names in
   logcat. Logging is stripped/neutered in release builds.
5. **`android:allowBackup="false"`** and excluded from cloud auto-backup. The only backup
   path is the app's own **manual encrypted export**.
6. **`FLAG_SECURE`** on all windows: blocks screenshots and redacts the app-switcher preview.
7. **Open source.** Keep dependencies FOSS-compatible; no proprietary blobs.

If a request conflicts with the above, say so and propose an alternative — don't quietly comply.

---

## What the app does (v1 scope)

- **Solo user, multiple partners.** No accounts, no login, no sync, no second device.
  Partners can be named or anonymous; per-partner stats supported.
- **Rich encounter logging** + optional **photo attachments** (encrypted).
- **Insights:** stats, charts, streaks/trends, and **achievements/badges**.
- **App lock:** biometric / PIN (changeable in Settings → General), auto-lock on background
  (configurable delay; default immediate).
- **Manual encrypted export/import** for migrating to a new phone.

Out of scope for v1: cross-device sync, cloud anything, disguise/decoy mode (hook left
for later), social/sharing features. See [docs/REQUIREMENTS.md](docs/REQUIREMENTS.md).

---

## Tech stack

- **Language:** Kotlin (JDK 17)
- **UI:** Jetpack Compose + Material 3
- **SDK:** `minSdk 31` (Android 12) · `compileSdk`/`targetSdk 36` (Android 16, latest)
- **DB:** Room + **SQLCipher** (encrypted)
- **Media crypto:** AES-256-GCM streaming (Google Tink) into app-internal storage
- **Key derivation:** PBKDF2-HMAC-SHA256 (600k iters, OWASP) for the app PIN; a hardware-backed
  Android Keystore key (StrongBox when available) double-wraps the DEK; biometric via a second
  auth-gated Keystore key. (Argon2id reserved for the M5 export passphrase.)
- **DI:** Hilt · **Async:** Coroutines + Flow
- **Charts:** none — hand-drawn in Compose layout (M6, D-25); no chart dependency
- **Build:** Gradle Kotlin DSL + version catalog (`gradle/libs.versions.toml`)
- **Test:** JUnit, Turbine, Robolectric, Compose UI tests, Room migration tests

## Architecture

MVVM + repository pattern, unidirectional data flow. Package-by-feature. Start as a single
`:app` module; split into `:core`, `:data`, `:feature-*` if/when it pays off. Details in
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Build & run commands

Windows (build env not on PATH): set the JVM first, then use the wrapper from the repo root.
```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleDebug              # build debug APK
.\gradlew.bat checkNoNetworkDebug        # anti-leak guard (no network permission)
.\gradlew.bat connectedDebugAndroidTest  # instrumented tests on a running emulator/device
.\gradlew.bat lint
.\gradlew.bat detekt ktlintCheck         # M8 quality gates (Detekt + ktlint; ktlintFormat to auto-fix)
```
Install + launch on the emulator (adb at `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`):
`adb install -r app\build\outputs\apk\debug\app-debug.apk` then
`adb shell am start -n app.tryst/.MainActivity`. Screenshots are black by design (`FLAG_SECURE`).
See [docs/SETUP_WINDOWS.md](docs/SETUP_WINDOWS.md) for toolchain details.

## Key documents

- [docs/REQUIREMENTS.md](docs/REQUIREMENTS.md) — functional + non-functional requirements
- [docs/THREAT_MODEL.md](docs/THREAT_MODEL.md) — adversaries, mitigations, residual risk
- [docs/SECURITY_DESIGN.md](docs/SECURITY_DESIGN.md) — encryption & key-management design
- [docs/DATA_MODEL.md](docs/DATA_MODEL.md) — entities & fields
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — stack & module layout
- [docs/FLOWCHARTS.md](docs/FLOWCHARTS.md) — visual maps of the core logic flows
- [docs/ROADMAP.md](docs/ROADMAP.md) — milestones
- [docs/DECISIONS.md](docs/DECISIONS.md) — decision log + open questions
- [docs/RELEASE.md](docs/RELEASE.md) — cut-a-release checklist + F-Droid submission guide
