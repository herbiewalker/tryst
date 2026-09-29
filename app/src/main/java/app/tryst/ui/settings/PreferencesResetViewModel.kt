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
package app.tryst.ui.settings

import androidx.lifecycle.ViewModel
import app.tryst.core.prefs.GalleryPreferences
import app.tryst.core.prefs.GeneralPreferences
import app.tryst.core.prefs.InsightsPreferences
import app.tryst.core.prefs.ThemePreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * "Reset preferences to default" (QOL-5) — Settings → Danger zone's lower-severity sibling to
 * "Delete all data". Only touches the plain, non-sensitive layout/appearance prefs; unlike the data
 * wipe this is trivially reversible (just re-pick your settings), so it needs no type-the-word gate,
 * just a confirm dialog.
 */
@HiltViewModel
class PreferencesResetViewModel @Inject constructor(
    private val theme: ThemePreferences,
    private val general: GeneralPreferences,
    private val insights: InsightsPreferences,
    private val gallery: GalleryPreferences,
) : ViewModel() {

    fun resetAll() {
        theme.resetToDefaults()
        general.resetToDefaults()
        insights.resetLayout()
        gallery.resetToDefaults()
    }
}

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
