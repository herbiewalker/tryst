package app.tryst.ui.whatsnew

/**
 * One released version's user-facing notes. Bundled in-app (the app has no network, so nothing is
 * fetched) and shown both in the What's-new screen and the one-time post-update popup.
 *
 * Keep this in sync with the F-Droid changelog files under
 * `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` and the repo `CHANGELOG.md`:
 * on every release, bump `versionCode`/`versionName` in `app/build.gradle.kts`, add a matching
 * `<versionCode>.txt`, and prepend a new [ReleaseNote] here (newest first).
 */
data class ReleaseNote(
    val versionName: String,
    val versionCode: Long,
    val date: String,
    val highlights: List<String>,
)

object ReleaseNotes {
    /** Newest first. The first entry is treated as the current release. */
    val all: List<ReleaseNote> = listOf(
        ReleaseNote(
            versionName = "0.1.0",
            versionCode = 1,
            date = "2026-06-13",
            highlights = listOf(
                "First public release of Tryst.",
                "Everything stays on this device — no account, no sync, and no internet access at all.",
                "Encrypted database and encrypted photo storage, locked behind your PIN with optional biometric unlock.",
                "Log encounters and partners in rich detail, and explore on-device Insights and Achievements.",
                "Move to a new phone with a single password-encrypted backup file.",
            ),
        ),
    )

    /** Notes for versions newer than [sinceVersionCode], newest first — drives the post-update popup. */
    fun since(sinceVersionCode: Long): List<ReleaseNote> = all.filter { it.versionCode > sinceVersionCode }
}
