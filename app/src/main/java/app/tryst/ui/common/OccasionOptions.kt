package app.tryst.ui.common

import app.tryst.data.db.entity.Occasion
import app.tryst.data.db.entity.OccasionEntity

/** A selectable occasion in the editor: a built-in or a custom one, unified by [id]. */
data class OccasionOption(val id: String, val label: String)

object OccasionOptions {
    const val CUSTOM_PREFIX = "custom:"

    // Only a minimal non-explicit safe seed ships (FDP-5); the rest are user-added custom entries.
    // built-in == common (the whole seed shows inline).
    val builtIns: List<OccasionOption> =
        Occasion.entries.map { OccasionOption(it.name, it.label) }

    val common: List<OccasionOption> = builtIns

    fun custom(rows: List<OccasionEntity>): List<OccasionOption> = rows.map { OccasionOption(CUSTOM_PREFIX + it.id, it.label) }
}
