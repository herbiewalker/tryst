package app.tryst.core.prefs

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Selectable chart look on the Insights screen. */
enum class ChartStyle { BARS, LINE, DONUT }

/**
 * Persisted Insights layout/appearance: chart style, the order of the Overview stat tiles, and
 * which tiles are hidden. Not sensitive (no encounter data), so plain SharedPreferences is fine —
 * and like the theme prefs it's excluded from backup/transfer. Exposed as StateFlows so the
 * screen recomposes live as the user customizes.
 */
@Singleton
class InsightsPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("tryst_insights", Context.MODE_PRIVATE)

    private val _chartStyle = MutableStateFlow(loadChartStyle())
    val chartStyle: StateFlow<ChartStyle> = _chartStyle.asStateFlow()

    private val _statOrder = MutableStateFlow(loadOrder())
    /** Ordered tile ids. May omit newly-added tiles; the screen appends unknown ids in catalog order. */
    val statOrder: StateFlow<List<String>> = _statOrder.asStateFlow()

    private val _hiddenStats = MutableStateFlow(loadHidden())
    val hiddenStats: StateFlow<Set<String>> = _hiddenStats.asStateFlow()

    fun setChartStyle(style: ChartStyle) {
        prefs.edit().putString(KEY_CHART, style.name).apply()
        _chartStyle.value = style
    }

    fun setOrder(order: List<String>) {
        prefs.edit().putString(KEY_ORDER, order.joinToString(DELIM)).apply()
        _statOrder.value = order
    }

    /** Moves the tile at [from] to [to] within the current order and persists. */
    fun moveStat(order: List<String>, from: Int, to: Int) {
        if (from !in order.indices || to !in order.indices || from == to) return
        val mutable = order.toMutableList()
        mutable.add(to, mutable.removeAt(from))
        setOrder(mutable)
    }

    fun setStatHidden(id: String, hidden: Boolean) {
        val next = _hiddenStats.value.toMutableSet().apply { if (hidden) add(id) else remove(id) }
        prefs.edit().putStringSet(KEY_HIDDEN, next).apply()
        _hiddenStats.value = next
    }

    fun resetLayout() {
        prefs.edit().remove(KEY_ORDER).remove(KEY_HIDDEN).apply()
        _statOrder.value = emptyList()
        _hiddenStats.value = emptySet()
    }

    private fun loadChartStyle(): ChartStyle =
        prefs.getString(KEY_CHART, null)
            ?.let { runCatching { ChartStyle.valueOf(it) }.getOrNull() }
            ?: ChartStyle.BARS

    private fun loadOrder(): List<String> =
        prefs.getString(KEY_ORDER, null)
            ?.split(DELIM)
            ?.filter { it.isNotBlank() }
            ?: emptyList()

    private fun loadHidden(): Set<String> =
        prefs.getStringSet(KEY_HIDDEN, emptySet())?.toSet() ?: emptySet()

    private companion object {
        const val KEY_CHART = "chart_style"
        const val KEY_ORDER = "stat_order"
        const val KEY_HIDDEN = "stat_hidden"
        const val DELIM = ","
    }
}
