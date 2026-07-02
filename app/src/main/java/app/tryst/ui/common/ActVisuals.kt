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
        Act.DOUBLE_PENETRATION,
        Act.FISTING,
        Act.PEGGING,
        Act.ANAL_CREAMPIE,
        Act.ASS_TO_MOUTH,
        Act.ANAL,
        Act.VAGINAL,
        Act.PROSTATE_MASSAGE,
        Act.SIXTY_NINE,
        Act.DEEP_THROAT,
        Act.FACE_FUCKING,
        Act.BLOWJOB,
        Act.BALL_SUCKING,
        Act.CUNNILINGUS,
        Act.LICK_PUSSY_AFTER,
        Act.CLIT_SUCKING,
        Act.ORAL,
        Act.RIMMING,
        Act.FACE_SITTING,
        Act.CREAMPIE,
        Act.EAT_OWN_CREAMPIE,
        Act.FACIAL,
        Act.SQUIRTING,
        Act.SCISSORING,
        Act.ANAL_FINGERING,
        Act.FINGERING,
        Act.MANUAL,
        Act.MUTUAL_MASTURBATION,
        Act.MASTURBATION,
        Act.FROTTAGE,
        Act.TITJOB,
        Act.BREAST_PLAY,
        Act.NIPPLE_PLAY,
        Act.FOOT_PLAY,
        Act.SPIT_PLAY,
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
        Act.BLOWJOB to R.drawable.ic_act_oral,
        Act.BALL_SUCKING to R.drawable.ic_act_oral,
        Act.CUNNILINGUS to R.drawable.ic_act_oral,
        Act.CLIT_SUCKING to R.drawable.ic_act_oral,
        Act.DEEP_THROAT to R.drawable.ic_act_oral,
        Act.FACE_FUCKING to R.drawable.ic_act_oral,
        Act.SIXTY_NINE to R.drawable.ic_act_sixtynine,
        Act.RIMMING to R.drawable.ic_act_rimming,
        Act.VAGINAL to R.drawable.ic_act_vulva,
        Act.SCISSORING to R.drawable.ic_act_scissoring,
        Act.ANAL to R.drawable.ic_act_anal,
        Act.ANAL_CREAMPIE to R.drawable.ic_act_anal,
        Act.ASS_TO_MOUTH to R.drawable.ic_act_a2m,
        Act.PEGGING to R.drawable.ic_act_pegging,
        Act.DOUBLE_PENETRATION to R.drawable.ic_act_dp,
        Act.FISTING to R.drawable.ic_act_fisting,
        Act.MANUAL to R.drawable.ic_act_hand,
        Act.FINGERING to R.drawable.ic_act_hand,
        Act.MUTUAL_MASTURBATION to R.drawable.ic_act_hand,
        Act.MASTURBATION to R.drawable.ic_act_hand,
        Act.ANAL_FINGERING to R.drawable.ic_act_prostate,
        Act.PROSTATE_MASSAGE to R.drawable.ic_act_prostate,
        Act.NIPPLE_PLAY to R.drawable.ic_act_breasts,
        Act.BREAST_PLAY to R.drawable.ic_act_breasts,
        Act.TITJOB to R.drawable.ic_act_breasts,
        Act.FOOT_PLAY to R.drawable.ic_act_foot,
        Act.MASSAGE to R.drawable.ic_act_massage,
        Act.CREAMPIE to R.drawable.ic_act_squirt,
        Act.FACIAL to R.drawable.ic_act_squirt,
        Act.SQUIRTING to R.drawable.ic_act_squirt,
        Act.SPIT_PLAY to R.drawable.ic_act_squirt,
        Act.FROTTAGE to R.drawable.ic_act_embrace,
        Act.FACE_SITTING to R.drawable.ic_act_embrace,
        Act.CUDDLING to R.drawable.ic_act_embrace,
        Act.OTHER to R.drawable.ic_act_custom,
    )
}
