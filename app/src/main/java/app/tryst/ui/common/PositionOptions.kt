package app.tryst.ui.common

import app.tryst.data.db.entity.Position
import app.tryst.data.db.entity.PositionEntity

/** A selectable position in the editor: a built-in or a custom one, unified by [id]. */
data class PositionOption(val id: String, val label: String)

object PositionOptions {
    const val CUSTOM_PREFIX = "custom:"

    // No predefined positions ship (F-Droid content policy, FDP-5); built-in/common are empty and every
    // position is a user-added custom entry.
    val builtIns: List<PositionOption> =
        Position.entries.map { PositionOption(it.name, it.label) }

    val common: List<PositionOption> = builtIns

    fun custom(rows: List<PositionEntity>): List<PositionOption> = rows.map { PositionOption(CUSTOM_PREFIX + it.id, it.label) }
}
