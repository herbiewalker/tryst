package app.tryst.ui.common

import app.tryst.data.db.entity.Position
import app.tryst.data.db.entity.PositionEntity

/** A selectable position in the editor: a built-in or a custom one, unified by [id]. */
data class PositionOption(val id: String, val label: String)

object PositionOptions {
    const val CUSTOM_PREFIX = "custom:"

    /** Built-in position ids shown inline before any selection (the curated "common" set). */
    private val COMMON_IDS = setOf(
        Position.MISSIONARY, Position.DOGGY_STYLE, Position.COWGIRL, Position.REVERSE_COWGIRL,
        Position.SPOONING, Position.SIXTY_NINE, Position.STANDING, Position.SIDE_BY_SIDE,
    ).map { it.name }.toSet()

    val builtIns: List<PositionOption> =
        Position.entries.map { PositionOption(it.name, Format.enumLabel(it)) }

    val common: List<PositionOption> = builtIns.filter { it.id in COMMON_IDS }

    fun custom(rows: List<PositionEntity>): List<PositionOption> =
        rows.map { PositionOption(CUSTOM_PREFIX + it.id, it.label) }
}
