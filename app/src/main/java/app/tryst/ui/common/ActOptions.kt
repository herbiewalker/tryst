package app.tryst.ui.common

import app.tryst.data.db.entity.Act
import app.tryst.data.db.entity.ActEntity

/** A selectable act in the editor: a built-in or a custom one, unified by [id]. */
data class ActOption(val id: String, val label: String)

object ActOptions {
    const val CUSTOM_PREFIX = "custom:"

    /** Built-in act ids shown inline before any selection (the curated "common" set). */
    private val COMMON_IDS = setOf(
        Act.KISSING,
        Act.ORAL,
        Act.SIXTY_NINE,
        Act.VAGINAL,
        Act.ANAL,
        Act.MANUAL,
        Act.FINGERING,
        Act.MUTUAL_MASTURBATION,
    ).map { it.name }.toSet()

    val builtIns: List<ActOption> =
        Act.entries.map { ActOption(it.name, it.label) }

    val common: List<ActOption> = builtIns.filter { it.id in COMMON_IDS }

    fun custom(rows: List<ActEntity>): List<ActOption> = rows.map { ActOption(CUSTOM_PREFIX + it.id, it.label) }
}
