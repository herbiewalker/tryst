package app.tryst.ui.common

import app.tryst.data.db.entity.ToyEntity
import app.tryst.data.db.entity.ToyType

/** A selectable toy in the editor: a built-in or a custom one, unified by [id]. */
data class ToyOption(val id: String, val label: String)

object ToyOptions {
    const val CUSTOM_PREFIX = "custom:"

    /** Built-in toy ids shown inline before any selection (the curated "common" set). */
    private val COMMON_IDS = setOf(
        ToyType.NONE,
        ToyType.VIBRATOR,
        ToyType.WAND,
        ToyType.DILDO,
        ToyType.STRAP_ON,
    ).map { it.name }.toSet()

    val builtIns: List<ToyOption> =
        ToyType.entries.map { ToyOption(it.name, it.label) }

    val common: List<ToyOption> = builtIns.filter { it.id in COMMON_IDS }

    fun custom(rows: List<ToyEntity>): List<ToyOption> = rows.map { ToyOption(CUSTOM_PREFIX + it.id, it.label) }
}
