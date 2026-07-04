package app.tryst.ui.common

import androidx.annotation.DrawableRes
import app.tryst.R

/**
 * Picks the single "headline" act for an encounter (for the card badge / calendar) and maps acts to
 * a drawable. Acts are string ids: since Tryst ships **no predefined acts** (FDP-5), every act is a
 * user-added custom entry, so there is no built-in priority ranking or per-act artwork — every act
 * uses the generic badge. The ranking seam is kept (deterministic order) so a future themed icon set
 * (design/ICON_PROJECT_PROMPT.md) is a drawable swap here with no caller change.
 */
object ActVisuals {

    /**
     * All present act ids in a stable, deterministic order (sorted). Take(1) for the headline today;
     * take(2) to show a second later.
     */
    fun rankedActs(gave: Set<String>?, received: Set<String>?): List<String> {
        val all = (gave ?: emptySet()) + (received ?: emptySet())
        if (all.isEmpty()) return emptyList()
        return all.sorted()
    }

    fun primaryAct(gave: Set<String>?, received: Set<String>?): String? = rankedActs(gave, received).firstOrNull()

    /** Drawable for an act id. With no built-in acts, every act gets the generic badge. */
    @DrawableRes
    @Suppress("UnusedParameter") // signature kept for callers + a future per-act icon set.
    fun icon(actId: String?): Int = R.drawable.ic_act_custom
}
