package app.tryst.core.prefs

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Selectable chart look, chosen per Insights card. */
enum class ChartStyle { BARS, LINE, DONUT }

/**
 * Persisted Insights layout/appearance: per-card chart style, plus the order and hidden set for both
 * the Overview stat tiles and the section cards. Not sensitive (no encounter data), so plain
 * SharedPreferences is fine — and like the theme prefs it's excluded from backup/transfer. Exposed as
 * StateFlows so the screen recomposes live as the user customizes.
 */
@Singleton
class InsightsPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("tryst_insights", Context.MODE_PRIVATE)

    // --- Overview stat tiles ---
    private val _statOrder = MutableStateFlow(loadList(KEY_STAT_ORDER))

    /** Ordered tile ids. May omit newly-added tiles; the screen appends unknown ids in catalog order. */
    val statOrder: StateFlow<List<String>> = _statOrder.asStateFlow()

    private val _hiddenStats = MutableStateFlow(loadSet(KEY_STAT_HIDDEN))
    val hiddenStats: StateFlow<Set<String>> = _hiddenStats.asStateFlow()

    // --- Section cards ---
    private val _sectionOrder = MutableStateFlow(loadList(KEY_SECTION_ORDER))
    val sectionOrder: StateFlow<List<String>> = _sectionOrder.asStateFlow()

    private val _hiddenSections = MutableStateFlow(loadSet(KEY_SECTION_HIDDEN))
    val hiddenSections: StateFlow<Set<String>> = _hiddenSections.asStateFlow()

    private val _sectionStyles = MutableStateFlow(loadStyles())

    /** sectionId -> chosen [ChartStyle]; sections absent here use [DEFAULT_STYLE]. */
    val sectionStyles: StateFlow<Map<String, ChartStyle>> = _sectionStyles.asStateFlow()

    fun setStatOrder(order: List<String>) = saveList(KEY_STAT_ORDER, order, _statOrder)

    fun moveStat(order: List<String>, from: Int, to: Int) = move(order, from, to)?.let { setStatOrder(it) }

    fun setStatHidden(id: String, hidden: Boolean) = saveHidden(KEY_STAT_HIDDEN, _hiddenStats, id, hidden)

    fun setSectionOrder(order: List<String>) = saveList(KEY_SECTION_ORDER, order, _sectionOrder)

    fun moveSection(order: List<String>, from: Int, to: Int) = move(order, from, to)?.let { setSectionOrder(it) }

    fun setSectionHidden(id: String, hidden: Boolean) = saveHidden(KEY_SECTION_HIDDEN, _hiddenSections, id, hidden)

    fun styleFor(id: String): ChartStyle = _sectionStyles.value[id] ?: DEFAULT_STYLE

    fun setSectionStyle(id: String, style: ChartStyle) {
        val next = _sectionStyles.value.toMutableMap().apply { put(id, style) }
        prefs.edit().putString(KEY_SECTION_STYLES, next.entries.joinToString(DELIM) { "${it.key}$KV${it.value.name}" }).apply()
        _sectionStyles.value = next
    }

    fun resetLayout() {
        prefs.edit()
            .remove(KEY_STAT_ORDER).remove(KEY_STAT_HIDDEN)
            .remove(KEY_SECTION_ORDER).remove(KEY_SECTION_HIDDEN).remove(KEY_SECTION_STYLES)
            .apply()
        _statOrder.value = emptyList()
        _hiddenStats.value = emptySet()
        _sectionOrder.value = emptyList()
        _hiddenSections.value = emptySet()
        _sectionStyles.value = emptyMap()
    }

    // --- helpers ---

    private fun move(order: List<String>, from: Int, to: Int): List<String>? {
        if (from !in order.indices || to !in order.indices || from == to) return null
        return order.toMutableList().apply { add(to, removeAt(from)) }
    }

    private fun saveList(key: String, order: List<String>, flow: MutableStateFlow<List<String>>) {
        prefs.edit().putString(key, order.joinToString(DELIM)).apply()
        flow.value = order
    }

    private fun saveHidden(key: String, flow: MutableStateFlow<Set<String>>, id: String, hidden: Boolean) {
        val next = flow.value.toMutableSet().apply { if (hidden) add(id) else remove(id) }
        prefs.edit().putStringSet(key, next).apply()
        flow.value = next
    }

    private fun loadList(key: String): List<String> = prefs.getString(key, null)?.split(DELIM)?.filter { it.isNotBlank() } ?: emptyList()

    private fun loadSet(key: String): Set<String> = prefs.getStringSet(key, emptySet())?.toSet() ?: emptySet()

    private fun loadStyles(): Map<String, ChartStyle> = prefs.getString(KEY_SECTION_STYLES, null)
        ?.split(DELIM)
        ?.mapNotNull { entry ->
            val (id, name) = entry.split(KV).takeIf { it.size == 2 } ?: return@mapNotNull null
            runCatching { id to ChartStyle.valueOf(name) }.getOrNull()
        }
        ?.toMap()
        ?: emptyMap()

    private companion object {
        val DEFAULT_STYLE = ChartStyle.BARS
        const val KEY_STAT_ORDER = "stat_order"
        const val KEY_STAT_HIDDEN = "stat_hidden"
        const val KEY_SECTION_ORDER = "section_order"
        const val KEY_SECTION_HIDDEN = "section_hidden"
        const val KEY_SECTION_STYLES = "section_styles"
        const val DELIM = ","
        const val KV = ":"
    }
}
