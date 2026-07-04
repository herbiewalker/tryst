package app.tryst.ui.common

import app.tryst.data.db.entity.Kink
import app.tryst.data.db.entity.KinkEntity

/** A selectable kink in the editor: a built-in or a custom one, unified by [id]. */
data class KinkOption(val id: String, val label: String)

object KinkOptions {
    const val CUSTOM_PREFIX = "custom:"

    // No predefined kinks ship (F-Droid content policy, FDP-5); built-in/common are empty and every
    // kink is a user-added custom entry.
    val builtIns: List<KinkOption> =
        Kink.entries.map { KinkOption(it.name, it.label) }

    val common: List<KinkOption> = builtIns

    fun custom(rows: List<KinkEntity>): List<KinkOption> = rows.map { KinkOption(CUSTOM_PREFIX + it.id, it.label) }
}
