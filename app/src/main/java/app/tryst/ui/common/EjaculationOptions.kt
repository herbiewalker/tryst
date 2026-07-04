package app.tryst.ui.common

import app.tryst.data.db.entity.EjaculationLocation
import app.tryst.data.db.entity.EjaculationLocationEntity

/** A selectable finish location in the editor: a built-in or a custom one, unified by [id]. */
data class EjaculationOption(val id: String, val label: String)

object EjaculationOptions {
    const val CUSTOM_PREFIX = "custom:"

    // Only a minimal non-explicit safe seed ships (FDP-5); the rest are user-added custom entries.
    // built-in == common (the whole seed shows inline).
    val builtIns: List<EjaculationOption> =
        EjaculationLocation.entries.map { EjaculationOption(it.name, it.label) }

    val common: List<EjaculationOption> = builtIns

    fun custom(rows: List<EjaculationLocationEntity>): List<EjaculationOption> = rows.map { EjaculationOption(CUSTOM_PREFIX + it.id, it.label) }
}
