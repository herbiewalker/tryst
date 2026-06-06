package app.tryst.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.BackHand
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.ui.graphics.vector.ImageVector
import app.tryst.data.db.entity.Practice

/**
 * Picks the single "headline" act for an encounter — the most intense one (gave or received) by
 * [PRIORITY] — and maps acts to a curated icon for the card badge. Acts are string ids: a built-in
 * [Practice] name or "custom:<uuid>". Custom acts fall back to the generic badge.
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

    /** A curated, distinct icon for an act id. Custom/unknown acts get the generic badge. */
    fun icon(actId: String?): ImageVector =
        actId?.let { id -> runCatching { Practice.valueOf(id) }.getOrNull()?.let { ICONS[it] } } ?: FALLBACK_ICON

    private val FALLBACK_ICON = Icons.Filled.Star

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

    private val ICONS: Map<Practice, ImageVector> = mapOf(
        // Penetration / most intense
        Practice.DOUBLE_PENETRATION to Icons.Filled.LocalFireDepartment,
        Practice.FISTING to Icons.Filled.LocalFireDepartment,
        Practice.ANAL to Icons.Filled.Whatshot,
        Practice.ANAL_CREAMPIE to Icons.Filled.Whatshot,
        Practice.ASS_TO_MOUTH to Icons.Filled.Whatshot,
        Practice.PEGGING to Icons.Filled.Whatshot,
        Practice.VAGINAL to Icons.Filled.Bolt,
        Practice.SCISSORING to Icons.Filled.Bolt,
        // Oral
        Practice.ORAL to Icons.Filled.Face,
        Practice.DEEP_THROAT to Icons.Filled.Face,
        Practice.FACE_FUCKING to Icons.Filled.Face,
        Practice.SIXTY_NINE to Icons.Filled.Face,
        Practice.RIMMING to Icons.Filled.Face,
        Practice.FACE_SITTING to Icons.Filled.Face,
        // Manual
        Practice.MANUAL to Icons.Filled.BackHand,
        Practice.FINGERING to Icons.Filled.BackHand,
        Practice.ANAL_FINGERING to Icons.Filled.BackHand,
        Practice.PROSTATE_MASSAGE to Icons.Filled.BackHand,
        Practice.MUTUAL_MASTURBATION to Icons.Filled.BackHand,
        Practice.MASTURBATION to Icons.Filled.BackHand,
        // Cum / fluids
        Practice.CREAMPIE to Icons.Filled.WaterDrop,
        Practice.FACIAL to Icons.Filled.WaterDrop,
        Practice.SQUIRTING to Icons.Filled.WaterDrop,
        Practice.SPIT_PLAY to Icons.Filled.WaterDrop,
        // Body / contact
        Practice.FROTTAGE to Icons.Filled.TouchApp,
        Practice.TITJOB to Icons.Filled.TouchApp,
        Practice.BREAST_PLAY to Icons.Filled.TouchApp,
        Practice.NIPPLE_PLAY to Icons.Filled.TouchApp,
        Practice.FOOT_PLAY to Icons.Filled.TouchApp,
        Practice.MASSAGE to Icons.Filled.Spa,
        // Affection
        Practice.KISSING to Icons.Filled.Favorite,
        Practice.MAKING_OUT to Icons.Filled.Favorite,
        Practice.CUDDLING to Icons.Filled.Bedtime,
        Practice.OTHER to Icons.Filled.Air,
    )
}
