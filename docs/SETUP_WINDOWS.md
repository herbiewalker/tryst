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
- [ ] On first open, Android Studio will **generate the Gradle wrapper**
      (`gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`) and run **Gradle Sync**.
      Accept any prompts to download the Gradle distribution and missing SDK packages.
- [ ] If Studio's **AGP Upgrade Assistant** or version suggestions pop up, it's fine to accept
      compatible updates — see the note in `gradle/libs.versions.toml`. If a sync error mentions
      an AGP/Gradle/Kotlin version mismatch, let Studio update to its recommended versions.
- [ ] After a clean sync, **commit the generated wrapper files** so CI can use them:
      `git add gradlew gradlew.bat gradle/wrapper/gradle-wrapper.jar && git commit -m "Add Gradle wrapper"`

## 4. Create a device
- [ ] **Device Manager** (right toolbar) → **Create Device** → pick a phone → system image
      **API 34 or 36** (must be ≥ 31) → Finish. Start the emulator.
- [ ] *Or* use a physical phone: enable **Developer options** → **USB debugging**, plug in,
      and authorize the computer.

## 5. Build & run
- [ ] **Build → Make Project** (or run `.\gradlew assembleDebug` in the Studio terminal).
- [ ] Press **Run ▶** to install and launch on the emulator/phone.
- [ ] You should see a screen reading **"Tryst — M0 scaffold"**. Note that screenshots are
      blocked (FLAG_SECURE) — that's intentional.

## 6. Sanity checks (optional but recommended)
- [ ] `.\gradlew testDebugUnitTest` → the placeholder unit test passes.
- [ ] `.\gradlew checkNoNetworkDebug` → prints "Anti-leak guard OK". Try temporarily adding
      `<uses-permission android:name="android.permission.INTERNET"/>` to the manifest and
      re-running — the build should **fail**. (Then remove it.)

## Notes / gotchas
- **Don't add a network permission.** The anti-leak guard (and CI) will fail the build by design.
- First Gradle sync downloads a lot (Gradle dist, AGP, SDK bits) — give it time on first run.
- The launcher icon is a placeholder flame; we'll design a real one at M8.
- This scaffold was authored without a local build, so the **first sync may request a version
  bump or two** — that's expected; accept Studio's compatible recommendations.
