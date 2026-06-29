package app.tryst.ui.common

import app.tryst.data.db.entity.Kink
import app.tryst.data.db.entity.KinkEntity

/** A selectable kink in the editor: a built-in or a custom one, unified by [id]. */
data class KinkOption(val id: String, val label: String)

object KinkOptions {
    const val CUSTOM_PREFIX = "custom:"

    /** Built-in kink ids shown inline before any selection (the curated "common" set). */
    private val COMMON_IDS = setOf(
        Kink.DOMINATION,
        Kink.SUBMISSION,
        Kink.BONDAGE,
        Kink.SPANKING,
        Kink.CHOKING,
        Kink.DIRTY_TALK,
        Kink.ROLEPLAY,
        Kink.EDGING,
    ).map { it.name }.toSet()

    val builtIns: List<KinkOption> =
        Kink.entries.map { KinkOption(it.name, it.label) }

    val common: List<KinkOption> = builtIns.filter { it.id in COMMON_IDS }

    fun custom(rows: List<KinkEntity>): List<KinkOption> = rows.map { KinkOption(CUSTOM_PREFIX + it.id, it.label) }
}
