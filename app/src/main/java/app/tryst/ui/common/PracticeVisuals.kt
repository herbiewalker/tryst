package app.tryst.ui.common

import androidx.annotation.DrawableRes
import app.tryst.R
import app.tryst.data.db.entity.Practice

/**
 * Picks the single "headline" act for an encounter — the most intense one (gave or received) by
 * [PRIORITY] — and maps acts to a custom drawable for the card badge / calendar. Acts are string
 * ids: a built-in [Practice] name or "custom:<uuid>". Custom acts fall back to the generic badge.
 *
 * Icons live in res/drawable/ic_act_*.xml (stylized vectors; see design/act-icons-preview.html).
 * They're a single tintable colour, so swapping in more realistic artwork later is just replacing
 * those files — no code change here.
 */
object PracticeVisuals {

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

    fun primaryPractice(gave: Set<String>?, received: Set<String>?): String? =
        rankedActs(gave, received).firstOrNull()

    /** Custom drawable for an act id. Custom/unknown acts get the generic badge. */
    @DrawableRes
    fun icon(actId: String?): Int =
        actId?.let { id -> runCatching { Practice.valueOf(id) }.getOrNull()?.let { ICONS[it] } }
            ?: R.drawable.ic_act_custom

    /** Most "headline" first; the first match in an encounter's acts wins. Includes every value. */
    private val PRIORITY = listOf(
        Practice.DOUBLE_PENETRATION,
        Practice.FISTING,
        Practice.PEGGING,
        Practice.ANAL_CREAMPIE,
        Practice.ASS_TO_MOUTH,
        Practice.ANAL,
        Practice.VAGINAL,
        Practice.PROSTATE_MASSAGE,
        Practice.SIXTY_NINE,
        Practice.DEEP_THROAT,
        Practice.FACE_FUCKING,
        Practice.BLOWJOB,
        Practice.BALL_SUCKING,
        Practice.CUNNILINGUS,
        Practice.CLIT_SUCKING,
        Practice.ORAL,
        Practice.RIMMING,
        Practice.FACE_SITTING,
        Practice.CREAMPIE,
        Practice.FACIAL,
        Practice.SQUIRTING,
        Practice.SCISSORING,
        Practice.ANAL_FINGERING,
        Practice.FINGERING,
        Practice.MANUAL,
        Practice.MUTUAL_MASTURBATION,
        Practice.MASTURBATION,
        Practice.FROTTAGE,
        Practice.TITJOB,
        Practice.BREAST_PLAY,
        Practice.NIPPLE_PLAY,
        Practice.FOOT_PLAY,
        Practice.SPIT_PLAY,
        Practice.MASSAGE,
        Practice.MAKING_OUT,
        Practice.KISSING,
        Practice.CUDDLING,
        Practice.OTHER,
    )

    private val ICONS: Map<Practice, Int> = mapOf(
        Practice.KISSING to R.drawable.ic_act_kiss,
        Practice.MAKING_OUT to R.drawable.ic_act_kiss,
        Practice.ORAL to R.drawable.ic_act_oral,
        Practice.BLOWJOB to R.drawable.ic_act_oral,
        Practice.BALL_SUCKING to R.drawable.ic_act_oral,
        Practice.CUNNILINGUS to R.drawable.ic_act_oral,
        Practice.CLIT_SUCKING to R.drawable.ic_act_oral,
        Practice.DEEP_THROAT to R.drawable.ic_act_oral,
        Practice.FACE_FUCKING to R.drawable.ic_act_oral,
        Practice.SIXTY_NINE to R.drawable.ic_act_sixtynine,
        Practice.RIMMING to R.drawable.ic_act_rimming,
        Practice.VAGINAL to R.drawable.ic_act_vulva,
        Practice.SCISSORING to R.drawable.ic_act_scissoring,
        Practice.ANAL to R.drawable.ic_act_anal,
        Practice.ANAL_CREAMPIE to R.drawable.ic_act_anal,
        Practice.ASS_TO_MOUTH to R.drawable.ic_act_a2m,
        Practice.PEGGING to R.drawable.ic_act_pegging,
        Practice.DOUBLE_PENETRATION to R.drawable.ic_act_dp,
        Practice.FISTING to R.drawable.ic_act_fisting,
        Practice.MANUAL to R.drawable.ic_act_hand,
        Practice.FINGERING to R.drawable.ic_act_hand,
        Practice.MUTUAL_MASTURBATION to R.drawable.ic_act_hand,
        Practice.MASTURBATION to R.drawable.ic_act_hand,
        Practice.ANAL_FINGERING to R.drawable.ic_act_prostate,
        Practice.PROSTATE_MASSAGE to R.drawable.ic_act_prostate,
        Practice.NIPPLE_PLAY to R.drawable.ic_act_breasts,
        Practice.BREAST_PLAY to R.drawable.ic_act_breasts,
        Practice.TITJOB to R.drawable.ic_act_breasts,
        Practice.FOOT_PLAY to R.drawable.ic_act_foot,
        Practice.MASSAGE to R.drawable.ic_act_massage,
        Practice.CREAMPIE to R.drawable.ic_act_squirt,
        Practice.FACIAL to R.drawable.ic_act_squirt,
        Practice.SQUIRTING to R.drawable.ic_act_squirt,
        Practice.SPIT_PLAY to R.drawable.ic_act_squirt,
        Practice.FROTTAGE to R.drawable.ic_act_embrace,
        Practice.FACE_SITTING to R.drawable.ic_act_embrace,
        Practice.CUDDLING to R.drawable.ic_act_embrace,
        Practice.OTHER to R.drawable.ic_act_custom,
    )
}
