// SPDX-License-Identifier: GPL-3.0-or-later
// ╭──────────────────────────────────────────────────────────────────╮
// │  ┌●───●───●───●───●───●───●───●───●───●───●───●───●───●───●───┐  │
// │  │                                                            │  │
// │  ●   ██╗  ██╗    ██╗    ██╗                                   ●  │
// │  │   ██║  ██║    ██║    ██║           herbiewalker            │  │
// │  │   ███████║    ██║ █╗ ██║──●──●──●──┐                       │  │
// │  │   ██╔══██║    ██║███╗██║           │                       │  │
// │  ●   ██║  ██║    ╚███╔███╔╝           ●───⏣  code · tools     ●  │
// │  │   ╚═╝  ╚═╝     ╚══╝╚══╝                    homelab         │  │
// │  │                                                            │  │
// │  └●───●───●───●───●───●───●───●───●───●───●───●───●───●───●───┘  │
// ╰──────────────────────────────────────────────────────────────────╯
package app.tryst.core.prefs

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Every settings store the backup carries (the same four as `PrefsBackup.STORE_NAMES`), so a restore can
 * refresh them all at once. Each store keeps its values in memory; a restore writes underneath them, so
 * without [reloadAll] restored settings would only show after the app restarted.
 */
@Singleton
class PreferenceStores @Inject constructor(
    private val theme: ThemePreferences,
    private val general: GeneralPreferences,
    private val insights: InsightsPreferences,
    private val gallery: GalleryPreferences,
) {
    fun reloadAll() {
        theme.reload()
        general.reload()
        insights.reload()
        gallery.reload()
    }
}

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
