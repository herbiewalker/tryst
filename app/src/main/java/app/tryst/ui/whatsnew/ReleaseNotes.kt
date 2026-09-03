// SPDX-License-Identifier: GPL-3.0-or-later
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
            versionName = "0.5.4",
            versionCode = 11,
            date = "2026-08-31",
            highlights = listOf(
                "Edit photos in place. From the photo viewer, tap the pencil to rotate 90° left or right, or open a full-screen cropper with corner handles and Free / 1 : 1 / 4 : 3 / 16 : 9 aspect presets.",
                "Edits replace the photo in place, still encrypted; embedded EXIF metadata (camera, GPS) is dropped in the re-encode.",
            ),
        ),
        ReleaseNote(
            versionName = "0.5.3",
            versionCode = 10,
            date = "2026-08-31",
            highlights = listOf(
                "Photo captions. Add a short note to any of your tryst photos from the photo viewer — searchable across the app, and included in your encrypted backup. Pick where the caption editor lives in Settings → Gallery → Photo captions: the info panel, a new top-toolbar button, both (the default), or off.",
                "Search now looks at photo captions too. Typing a caption phrase surfaces the tryst it belongs to.",
            ),
        ),
        ReleaseNote(
            versionName = "0.5.2",
            versionCode = 9,
            date = "2026-07-30",
            highlights = listOf(
                "Add new categories on the fly. Every catalog list in the encounter editor — acts, positions, kinks, toys, occasions, finish locations — now has an \"Add a new one…\" field at the bottom of its More… sheet. Type a name, tap Add, and it's saved AND selected for the current tryst — no more trip to Settings for a one-off entry.",
                "Fix: drilling into \"You\" from the People layout now has a back button and honours the system back gesture, so you can leave the self drill without closing the Photos tab.",
                "Fix: partner and profile pictures from older backups now show correctly in the partner editor and Your profile page. The strip used to render empty when the avatar came from a v13/v14 backup because it wasn't yet tracked as a portrait; the app now auto-adopts those blobs into the portrait album on first open.",
                "Import backup: new \"Replace my current data with the backup (recommended)\" checkbox — on by default — makes restore atomic and predictable. Turn it off if you deliberately want to merge two datasets.",
            ),
        ),
        ReleaseNote(
            versionName = "0.5.1",
            versionCode = 8,
            date = "2026-07-30",
            highlights = listOf(
                "Fix: drilling into \"You\" from the People layout no longer shows every partner's portrait album — only your own portraits and solo tryst photos appear, as intended.",
                "Fix: the Photos search bar now filters portraits too. Typing a partner's name returns their tryst photos and their portraits — not everyone else's portraits.",
                "Fix: \"Set as avatar for {partner}\" from the photo viewer now stores the new avatar in that partner's portrait album, so the strip in the partner editor shows a checkmark on it and lets you delete it later.",
                "Fix: deleting the portrait that is the current avatar now clears the avatar reference too, instead of leaving the partner (or your profile) pointing at a deleted picture.",
                "Fix: restoring an encrypted backup is now atomic — a mid-restore failure (disk full, corrupted file) leaves your existing data untouched instead of half-swapping the database.",
                "Privacy hardening: the import-diagnostic log line is now fully stripped from release builds.",
            ),
        ),
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
