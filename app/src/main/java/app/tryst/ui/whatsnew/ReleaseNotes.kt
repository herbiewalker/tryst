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
            versionName = "0.5.0",
            versionCode = 7,
            date = "2026-07-30",
            highlights = listOf(
                "Photos gallery. A new Photos tab collects every photo you've attached to a tryst in one browsable place — grouped by date, a flat grid, by partner, or a large feed. Tap for a full-screen viewer with pinch-zoom, swipe, slideshow, and a jump-to-tryst button.",
                "Multi-photo albums per person. Add several photos to any partner (or your own profile) from the full-screen partner editor, and pick any of them as their avatar. Portraits appear in the Photos gallery alongside encounter photos.",
                "Photo details on-device. An info button in the viewer shows a photo's embedded date, dimensions, camera, and coordinates when present — read straight from the file, nothing sent anywhere.",
                "Blur photos until tapped (optional). Turn it on in Settings → Gallery so the Photos tab opens blurred behind a Show-photos tap — handy if you might hand your unlocked phone to someone.",
                "Favourite photos and multi-select. Heart a photo, filter to favourites only, or long-press to bulk-favourite, delete, or move photos to a different tryst.",
                "Solo tryst photos are attributed to You. They appear alongside your self-profile portraits when drilling into your own person, instead of hiding in a nameless Solo bucket.",
                "Add-to-person from the viewer. Copy any photo into a partner's or your own portrait album in one tap — the source photo stays where it is.",
                "Fix: restoring an older backup on a fresh install now works (a NOT-NULL constraint on the new photo-favourite column no longer blocks import).",
            ),
        ),
        ReleaseNote(
            versionName = "0.4.0",
            versionCode = 6,
            date = "2026-07-11",
            highlights = listOf(
                "Search your whole history. A search icon on Trysts finds encounters by note, partner, and every category — acts, positions, kinks, and more — ignoring case and accents.",
                "Narrow results with filters: date window, partner, rating, and photos up front, plus a \"More filters\" sheet for place, mood, protection, weekday, time of day, duration, and the rest. Results update as you tap.",
                "Recent searches are remembered on this device, inside your encrypted database — never in plain settings, and never included in a backup.",
                "Insights has a time range. Focus every stat and chart on a year, a quarter, or a custom window; your choice is remembered.",
            ),
        ),
        ReleaseNote(
            versionName = "0.3.2",
            versionCode = 5,
            date = "2026-07-03",
            highlights = listOf(
                "Categories are now fully yours. Tryst ships only a couple of neutral starters and you build the rest — acts, kinks, positions, toys, occasions, and finish locations are all your own entries now.",
                "Occasions and finish locations joined the customizable set, so you can name them however you like.",
                "Each category gets its own polished management page under Settings → Categories: add, rename, or remove entries with room to breathe.",
                "Everything you'd already logged is kept and converted automatically — nothing is lost.",
                "Tip: re-export your backup after updating so a future restore keeps the new naming.",
            ),
        ),
        ReleaseNote(
            versionName = "0.3.1",
            versionCode = 4,
            date = "2026-07-03",
            highlights = listOf(
                "Positions and toys are now yours to customize too — Tryst ships a small, non-explicit starter set and you add or rename your own. Anything you'd already logged is kept and converted automatically.",
                "Add your own toys under Settings → Manage custom toys, just like custom acts, kinks, and positions.",
                "Tip: re-export your backup after updating so a future restore keeps the new naming.",
            ),
        ),
        ReleaseNote(
            versionName = "0.3.0",
            versionCode = 3,
            date = "2026-07-02",
            highlights = listOf(
                "Acts and kinks are now yours to customize — Tryst ships a small, non-explicit starter set and you add or rename your own. Anything you'd already logged is kept and converted automatically.",
                "Add your own kinks, just like custom acts and positions — they count fully in Insights and Achievements.",
                "Rename any custom act, kink, or position in place, and the change follows every logged tryst.",
                "New setting: open Trysts in calendar view by default.",
                "Your most-used options now surface right in the editor, so frequent picks are one tap away.",
                "Tip: re-export your backup after updating so a future restore keeps the new naming.",
            ),
        ),
        ReleaseNote(
            versionName = "0.2.0",
            versionCode = 2,
            date = "2026-06-21",
            highlights = listOf(
                "Redesigned calendar: tonal day chips with an activity heatmap, a month/week toggle, and swipe to change month.",
                "Ejaculation location is now multi-select per orgasm, with an \"in the shower\" option.",
                "New \"Friend / family's place\" location, plus more built-in positions and acts.",
                "One act moved to Kinks & BDSM, and clearer oral-position names — your history is migrated automatically.",
                "Haptics now buzz when enabled in Settings.",
                "Tip: re-export your backup after updating so a future restore keeps the new naming.",
            ),
        ),
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
