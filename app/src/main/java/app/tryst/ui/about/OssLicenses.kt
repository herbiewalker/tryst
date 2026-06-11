package app.tryst.ui.about

/**
 * Open-source notices for the third-party components Tryst ships.
 *
 * Hand-maintained (matching the project's "no extra deps" ethos — we don't pull in a license-
 * aggregation Gradle plugin just for this). Keep it in sync with [gradle/libs.versions.toml] and the
 * repo-root `THIRD_PARTY_NOTICES.md` when dependencies change. Every entry below is GPLv3-compatible;
 * Tryst itself is GPLv3 (see the repo `LICENSE`). Audited in pre-release Pass 10.
 *
 * Entries are grouped logically (the `-android`/`-jvm`/BOM artifact splits collapse into one row).
 */
data class OssComponent(
    val name: String,
    val copyright: String,
    val license: String,
)

object OssLicenses {
    /** Short license blurbs, referenced by [OssComponent.license]. */
    const val APACHE_2 = "Apache License 2.0"
    const val BSD_3 = "BSD 3-Clause License"
    const val BSD_ZETETIC = "BSD-style (Zetetic / SQLCipher)"

    val components: List<OssComponent> = listOf(
        OssComponent(
            name = "AndroidX / Jetpack (Core, Activity, Compose, Material 3, Lifecycle, " +
                "Navigation, Room, Biometric, Window, SQLite, …)",
            copyright = "© The Android Open Source Project",
            license = APACHE_2,
        ),
        OssComponent(
            name = "Kotlin standard library & kotlinx (Coroutines, Serialization)",
            copyright = "© JetBrains s.r.o. and contributors",
            license = APACHE_2,
        ),
        OssComponent(
            name = "Dagger & Hilt",
            copyright = "© Google LLC",
            license = APACHE_2,
        ),
        OssComponent(
            name = "Google Tink (tink-android)",
            copyright = "© Google LLC",
            license = APACHE_2,
        ),
        OssComponent(
            name = "Gson",
            copyright = "© Google LLC",
            license = APACHE_2,
        ),
        OssComponent(
            name = "Error Prone annotations · Guava ListenableFuture",
            copyright = "© Google LLC",
            license = APACHE_2,
        ),
        OssComponent(
            name = "JSpecify annotations",
            copyright = "© The JSpecify Authors",
            license = APACHE_2,
        ),
        OssComponent(
            name = "Jakarta / javax Inject API",
            copyright = "© Eclipse Foundation / contributors",
            license = APACHE_2,
        ),
        OssComponent(
            name = "JSR-305 annotations",
            copyright = "© FindBugs project contributors",
            license = BSD_3,
        ),
        OssComponent(
            name = "SQLCipher for Android (bundles SQLite — public domain — and OpenSSL 3.x, " +
                "Apache 2.0)",
            copyright = "© Zetetic LLC",
            license = BSD_ZETETIC,
        ),
    )
}
