package app.tryst.ui.common

import app.tryst.data.db.entity.ToyEntity
import app.tryst.data.db.entity.ToyType

/** A selectable toy in the editor: a built-in or a custom one, unified by [id]. */
data class ToyOption(val id: String, val label: String)

object ToyOptions {
    const val CUSTOM_PREFIX = "custom:"

    // No predefined toys ship (F-Droid content policy, FDP-5); built-in/common are empty and every toy
    // is a user-added custom entry.
    val builtIns: List<ToyOption> =
        ToyType.entries.map { ToyOption(it.name, it.label) }

    val common: List<ToyOption> = builtIns

    fun custom(rows: List<ToyEntity>): List<ToyOption> = rows.map { ToyOption(CUSTOM_PREFIX + it.id, it.label) }
}
