# Tryst — Windows Dev Setup Checklist

Goal: get from a fresh Windows machine to a successful `assembleDebug` and the app running
on an emulator or phone. Do these roughly in order; check each box as you go.

## 1. Install Android Studio
- [ ] Download **Android Studio** (latest stable) from <https://developer.android.com/studio>
      and run the installer with defaults.
- [ ] Android Studio bundles a compatible JDK (JBR 17/21) and Gradle — you do **not** need a
      separate JDK install for building in the IDE. (Only install a standalone Temurin JDK 17
      if you want to run `./gradlew` from a plain terminal without Studio's environment.)

## 2. Install SDK components (Android Studio → Settings → Languages & Frameworks → Android SDK)
- [ ] **SDK Platforms** tab: install **Android 16 (API 36)** — this is our `compileSdk`/`targetSdk`.
- [ ] (Optional) also install **API 31–35** platforms for testing across versions (`minSdk` is 31).
- [ ] **SDK Tools** tab: ensure **Android SDK Build-Tools**, **Android SDK Platform-Tools**,
      **Android Emulator**, and **Android SDK Command-line Tools** are installed.

## 3. Open the project
- [ ] **File → Open** and select `E:\ClaudeFolder\Git\CodingProjects\tryst`.
- [ ] The **Gradle wrapper is already committed** (`gradlew`, `gradlew.bat`,
      `gradle/wrapper/gradle-wrapper.jar`), so a fresh clone builds without regenerating it. On open,
      Android Studio runs **Gradle Sync** — accept any prompts to download the Gradle distribution and
      missing SDK packages.
- [ ] The toolchain is pinned in `gradle/libs.versions.toml` (AGP 9.2.1 / Kotlin 2.2.10 / KSP 2.3.2 /
      Gradle 9.5). Let Studio drive any upgrades and keep those four mutually compatible; **do not** bump
      Room past the pinned 2.7.1 without also moving the Kotlin toolchain (see the note in that file).

## 4. Create a device
- [ ] **Device Manager** (right toolbar) → **Create Device** → pick a phone → system image
      **API 34 or 36** (must be ≥ 31) → Finish. Start the emulator.
- [ ] *Or* use a physical phone: enable **Developer options** → **USB debugging**, plug in,
      and authorize the computer.

## 5. Build & run
- [ ] **Build → Make Project** (or run `.\gradlew assembleDebug` in the Studio terminal).
- [ ] Press **Run ▶** to install and launch on the emulator/phone.
- [ ] On first run you'll see the **PIN setup screen** (the app's first-run lock setup); after that,
      the lock screen. Note that screenshots are blocked (FLAG_SECURE) and the app-switcher preview is
      redacted — that's intentional, so screen captures of the running app appear black.

## 6. Sanity checks (optional but recommended)
- [ ] `.\gradlew testDebugUnitTest` → the JVM unit tests pass (stats/achievements engines, CSV parser, Insights catalogs).
- [ ] `.\gradlew checkNoNetworkDebug` → prints "Anti-leak guard OK". Try temporarily adding
      `<uses-permission android:name="android.permission.INTERNET"/>` to the manifest and
      re-running — the build should **fail**. (Then remove it.)

## Notes / gotchas
- **Don't add a network permission.** The anti-leak guard (and CI) will fail the build by design.
- First Gradle sync downloads a lot (Gradle dist, AGP, SDK bits) — give it time on first run.
- The launcher icon is a placeholder flame; a refreshed icon set is tracked as **QOL-4** in [ROADMAP_FUTURE.md](ROADMAP_FUTURE.md).
- Running from a plain terminal (no Studio env): set `JAVA_HOME` to the bundled JBR first, e.g.
  `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"`, then `.\gradlew.bat assembleDebug`.
