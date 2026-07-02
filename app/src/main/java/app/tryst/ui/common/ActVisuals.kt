package app.tryst.ui.common

import androidx.annotation.DrawableRes
import app.tryst.R
import app.tryst.data.db.entity.Act

/**
 * Picks the single "headline" act for an encounter — the most intense one (gave or received) by
 * [PRIORITY] — and maps acts to a custom drawable for the card badge / calendar. Acts are string
 * ids: a built-in [Act] name or "custom:<uuid>". Custom acts fall back to the generic badge.
 *
 * Icons live in res/drawable/ic_act_*.xml (stylized vectors; see design/act-icons-preview.html).
 * They're a single tintable colour, so swapping in more realistic artwork later is just replacing
 * those files — no code change here.
 */
object ActVisuals {

    /**
     * All present act ids ordered most-intense first: built-ins by [PRIORITY], then any leftovers
     * (custom acts) appended. Take(1) for the headline today; take(2) to show a second later.
     */
    fun rankedActs(gave: Set<String>?, received: Set<String>?): List<String> {
        val all = (gave ?: emptySet()) + (received ?: emptySet())
        if (all.isEmpty()) return emptyList()
        val ranked = PRIORITY.map { it.name }.filter { it in all }
        val leftovers = all - ranked.toSet()
        return ranked + leftovers
    }

    fun primaryAct(gave: Set<String>?, received: Set<String>?): String? = rankedActs(gave, received).firstOrNull()

    /** Custom drawable for an act id. Custom/unknown acts get the generic badge. */
    @DrawableRes
    fun icon(actId: String?): Int = actId?.let { id -> runCatching { Act.valueOf(id) }.getOrNull()?.let { ICONS[it] } }
        ?: R.drawable.ic_act_custom

    /** Most "headline" first; the first match in an encounter's acts wins. Includes every value. */
    private val PRIORITY = listOf(
        Act.ANAL,
        Act.VAGINAL,
        Act.PROSTATE_MASSAGE,
        Act.SIXTY_NINE,
        Act.ORAL,
        Act.FINGERING,
        Act.MANUAL,
        Act.MUTUAL_MASTURBATION,
        Act.MASTURBATION,
        Act.BREAST_PLAY,
        Act.NIPPLE_PLAY,
        Act.MASSAGE,
        Act.MAKING_OUT,
        Act.KISSING,
        Act.CUDDLING,
        Act.OTHER,
    )

    private val ICONS: Map<Act, Int> = mapOf(
        Act.KISSING to R.drawable.ic_act_kiss,
        Act.MAKING_OUT to R.drawable.ic_act_kiss,
        Act.ORAL to R.drawable.ic_act_oral,
        Act.SIXTY_NINE to R.drawable.ic_act_sixtynine,
        Act.VAGINAL to R.drawable.ic_act_vulva,
        Act.ANAL to R.drawable.ic_act_anal,
        Act.MANUAL to R.drawable.ic_act_hand,
        Act.FINGERING to R.drawable.ic_act_hand,
        Act.MUTUAL_MASTURBATION to R.drawable.ic_act_hand,
        Act.MASTURBATION to R.drawable.ic_act_hand,
        Act.PROSTATE_MASSAGE to R.drawable.ic_act_prostate,
        Act.NIPPLE_PLAY to R.drawable.ic_act_breasts,
        Act.BREAST_PLAY to R.drawable.ic_act_breasts,
        Act.MASSAGE to R.drawable.ic_act_massage,
        Act.CUDDLING to R.drawable.ic_act_embrace,
        Act.OTHER to R.drawable.ic_act_custom,
    )
}
