package app.tryst.ui.common

import app.tryst.data.db.entity.Act
import app.tryst.data.db.entity.ActEntity

/** A selectable act in the editor: a built-in or a custom one, unified by [id]. */
data class ActOption(val id: String, val label: String)

object ActOptions {
    const val CUSTOM_PREFIX = "custom:"

    // Only a minimal non-explicit safe seed ships (F-Droid content policy, FDP-5); the rest are
    // user-added custom entries. built-in == common (the whole seed shows inline).
    val builtIns: List<ActOption> =
        Act.entries.map { ActOption(it.name, it.label) }

    val common: List<ActOption> = builtIns

    fun custom(rows: List<ActEntity>): List<ActOption> = rows.map { ActOption(CUSTOM_PREFIX + it.id, it.label) }
}
