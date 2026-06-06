package app.tryst.ui.common

import app.tryst.data.db.entity.Practice

/**
 * Maps acts to emoji and picks the single "headline" act for an encounter (the most notable one
 * gave or received), used for the card badge. Acts are string ids: a built-in [Practice] name or
 * "custom:<uuid>". Custom acts fall back to the generic badge. Approximate by design.
 */
object PracticeVisuals {

    /** Returns the highest-priority built-in act id present, or any id (e.g. a custom one), or null. */
    fun primaryPractice(gave: Set<String>?, received: Set<String>?): String? {
        val all = (gave ?: emptySet()) + (received ?: emptySet())
        if (all.isEmpty()) return null
        return PRIORITY.firstOrNull { it.name in all }?.name ?: all.first()
    }

    fun emoji(actId: String?): String =
        actId?.let { id -> runCatching { Practice.valueOf(id) }.getOrNull()?.let { EMOJI[it] } } ?: FALLBACK

    private const val FALLBACK = "❤️‍🔥"

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

    private val EMOJI = mapOf(
        Practice.KISSING to "💋",
        Practice.MAKING_OUT to "😘",
        Practice.ORAL to "👅",
        Practice.DEEP_THROAT to "👅",
        Practice.SIXTY_NINE to "♋",
        Practice.RIMMING to "🍑",
        Practice.FACE_FUCKING to "👅",
        Practice.MANUAL to "✋",
        Practice.FINGERING to "👆",
        Practice.ANAL_FINGERING to "👈",
        Practice.VAGINAL to "🍆",
        Practice.ANAL to "🍑",
        Practice.ASS_TO_MOUTH to "🍑",
        Practice.SCISSORING to "✂️",
        Practice.DOUBLE_PENETRATION to "✌️",
        Practice.PEGGING to "🍆",
        Practice.FISTING to "🤜",
        Practice.PROSTATE_MASSAGE to "🍑",
        Practice.FROTTAGE to "🤼",
        Practice.FACE_SITTING to "🪑",
        Practice.NIPPLE_PLAY to "🤏",
        Practice.BREAST_PLAY to "🍈",
        Practice.TITJOB to "🍈",
        Practice.FOOT_PLAY to "🦶",
        Practice.MASSAGE to "💆",
        Practice.MUTUAL_MASTURBATION to "🙌",
        Practice.MASTURBATION to "✊",
        Practice.SPIT_PLAY to "💧",
        Practice.CREAMPIE to "💦",
        Practice.ANAL_CREAMPIE to "💦",
        Practice.FACIAL to "💦",
        Practice.SQUIRTING to "💦",
        Practice.CUDDLING to "🫂",
        Practice.OTHER to "❤️‍🔥",
    )
}
