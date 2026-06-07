package app.tryst.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tryst.core.prefs.ChartStyle
import app.tryst.core.prefs.InsightsPreferences
import app.tryst.data.repository.ActRepository
import app.tryst.data.repository.EncounterRepository
import app.tryst.data.repository.PositionRepository
import app.tryst.data.stats.Insights
import app.tryst.data.stats.InsightsEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class InsightsViewModel @Inject constructor(
    encounterRepository: EncounterRepository,
    actRepository: ActRepository,
    positionRepository: PositionRepository,
    private val prefs: InsightsPreferences,
) : ViewModel() {

    val insights: StateFlow<Insights> =
        combine(
            encounterRepository.observeAll(),
            actRepository.observeCustom(),
            positionRepository.observeCustom(),
        ) { encounters, acts, positions ->
            Triple(encounters, acts, positions)
        }
            .map { (encounters, acts, positions) ->
                // Tallying the whole log can be non-trivial; keep it off the main thread.
                withContext(Dispatchers.Default) {
                    InsightsEngine.compute(
                        encounters,
                        customActLabels = acts.associate { it.id to it.label },
                        customPositionLabels = positions.associate { it.id to it.label },
                    )
                }
            }
            // The DB closes on lock; swallow the resulting error so we don't crash mid-teardown.
            .catch { emit(Insights.EMPTY) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Insights.EMPTY)

    // --- Customization (persisted via InsightsPreferences) ---
    val chartStyle: StateFlow<ChartStyle> = prefs.chartStyle
    val statOrder: StateFlow<List<String>> = prefs.statOrder
    val hiddenStats: StateFlow<Set<String>> = prefs.hiddenStats

    fun setChartStyle(style: ChartStyle) = prefs.setChartStyle(style)

    fun moveStat(order: List<String>, from: Int, to: Int) = prefs.moveStat(order, from, to)

    fun setStatHidden(id: String, hidden: Boolean) = prefs.setStatHidden(id, hidden)

    fun resetLayout() = prefs.resetLayout()
}
