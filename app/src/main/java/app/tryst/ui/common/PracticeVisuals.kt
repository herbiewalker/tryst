package app.tryst.ui.common

import app.tryst.data.db.entity.Practice

/**
 * Maps practices to emoji and picks the single "headline" practice for an encounter (the most
 * notable one performed or received), used for the card badge. Approximate by design.
 */
object PracticeVisuals {

    fun primaryPractice(performed: Set<Practice>?, received: Set<Practice>?): Practice? {
        val all = (performed ?: emptySet()) + (received ?: emptySet())
        return PRIORITY.firstOrNull { it in all }
    }

    fun emoji(practice: Practice?): String = practice?.let { EMOJI[it] } ?: FALLBACK

    private const val FALLBACK = "❤️‍🔥"

    /** Most "headline" first; the first match in an encounter's practices wins. */
    private val PRIORITY = listOf(
        Practice.ANAL,
        Practice.VAGINAL,
        Practice.SIXTY_NINE,
        Practice.DEEP_THROAT,
        Practice.ORAL,
        Practice.RIMMING,
        Practice.FACE_SITTING,
        Practice.HANDJOB,
        Practice.FINGERING,
        Practice.MANUAL,
        Practice.MUTUAL_MASTURBATION,
        Practice.MASTURBATION,
        Practice.TOYS,
        Practice.FROTTAGE,
        Practice.NIPPLE_PLAY,
        Practice.SPANKING,
        Practice.CHOKING,
        Practice.HAIR_PULLING,
        Practice.BONDAGE,
        Practice.ROLEPLAY,
        Practice.DIRTY_TALK,
        Practice.SENSORY_PLAY,
        Practice.EDGING,
        Practice.MASSAGE,
        Practice.MAKING_OUT,
        Practice.KISSING,
        Practice.AFTERCARE,
        Practice.CUDDLING,
        Practice.OTHER,
    )

    private val EMOJI = mapOf(
        Practice.KISSING to "💋",
        Practice.MAKING_OUT to "😘",
        Practice.ORAL to "👅",
        Practice.SIXTY_NINE to "♋",
        Practice.DEEP_THROAT to "👅",
        Practice.RIMMING to "🍑",
        Practice.MANUAL to "✋",
        Practice.FINGERING to "👆",
        Practice.HANDJOB to "✊",
        Practice.VAGINAL to "🍆",
        Practice.ANAL to "🍑",
        Practice.TOYS to "🔌",
        Practice.MUTUAL_MASTURBATION to "🙌",
        Practice.MASTURBATION to "✊",
        Practice.FROTTAGE to "🤼",
        Practice.NIPPLE_PLAY to "🤏",
        Practice.MASSAGE to "💆",
        Practice.SPANKING to "👋",
        Practice.CHOKING to "🫦",
        Practice.HAIR_PULLING to "💇",
        Practice.BONDAGE to "⛓️",
        Practice.ROLEPLAY to "🎭",
        Practice.DIRTY_TALK to "🗣️",
        Practice.SENSORY_PLAY to "🪶",
        Practice.EDGING to "⏳",
        Practice.FACE_SITTING to "🪑",
        Practice.AFTERCARE to "🫂",
        Practice.CUDDLING to "🫂",
        Practice.OTHER to "❤️‍🔥",
    )
}
