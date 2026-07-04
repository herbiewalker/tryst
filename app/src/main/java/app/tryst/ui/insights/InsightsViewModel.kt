package app.tryst.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tryst.core.prefs.ChartStyle
import app.tryst.core.prefs.InsightsPreferences
import app.tryst.data.repository.ActRepository
import app.tryst.data.repository.EjaculationLocationRepository
import app.tryst.data.repository.EncounterRepository
import app.tryst.data.repository.KinkRepository
import app.tryst.data.repository.OccasionRepository
import app.tryst.data.repository.PositionRepository
import app.tryst.data.repository.ToyRepository
import app.tryst.data.stats.Insights
import app.tryst.data.stats.InsightsEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

@HiltViewModel
@Suppress("LongParameterList") // Hilt-injected repositories; each is a distinct custom-catalog source.
class InsightsViewModel @Inject constructor(
    encounterRepository: EncounterRepository,
    actRepository: ActRepository,
    positionRepository: PositionRepository,
    kinkRepository: KinkRepository,
    toyRepository: ToyRepository,
    occasionRepository: OccasionRepository,
    ejaculationLocationRepository: EjaculationLocationRepository,
    private val prefs: InsightsPreferences,
) : ViewModel() {

    val insights: StateFlow<Insights> =
        // Each custom catalog → its id→label map, then combined into one array so we stay within
        // combine's typed arity (all six are Flow<Map<String, String>>); order matches the compute() call.
        combine(
            actRepository.observeCustom().map { rows -> rows.associate { it.id to it.label } },
            positionRepository.observeCustom().map { rows -> rows.associate { it.id to it.label } },
            kinkRepository.observeCustom().map { rows -> rows.associate { it.id to it.label } },
            toyRepository.observeCustom().map { rows -> rows.associate { it.id to it.label } },
            occasionRepository.observeCustom().map { rows -> rows.associate { it.id to it.label } },
            ejaculationLocationRepository.observeCustom().map { rows -> rows.associate { it.id to it.label } },
        ) { maps -> maps.toList() }
            .let { labelMaps ->
                combine(encounterRepository.observeAll(), labelMaps) { encounters, maps ->
                    // Tallying the whole log can be non-trivial; keep it off the main thread.
                    withContext(Dispatchers.Default) {
                        InsightsEngine.compute(
                            encounters,
                            customActLabels = maps[0],
                            customPositionLabels = maps[1],
                            customKinkLabels = maps[2],
                            customToyLabels = maps[3],
                            customOccasionLabels = maps[4],
                            customEjaculationLabels = maps[5],
                        )
                    }
                }
            }
            // The DB closes on lock; swallow the resulting error so we don't crash mid-teardown.
            .catch { emit(Insights.EMPTY) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Insights.EMPTY)

    // --- Customization (persisted via InsightsPreferences) ---
    val statOrder: StateFlow<List<String>> = prefs.statOrder
    val hiddenStats: StateFlow<Set<String>> = prefs.hiddenStats
    val sectionOrder: StateFlow<List<String>> = prefs.sectionOrder
    val hiddenSections: StateFlow<Set<String>> = prefs.hiddenSections
    val sectionStyles: StateFlow<Map<String, ChartStyle>> = prefs.sectionStyles

    fun styleFor(id: String): ChartStyle = prefs.styleFor(id)

    fun setSectionStyle(id: String, style: ChartStyle) = prefs.setSectionStyle(id, style)

    fun moveStat(order: List<String>, from: Int, to: Int) = prefs.moveStat(order, from, to)

    fun setStatHidden(id: String, hidden: Boolean) = prefs.setStatHidden(id, hidden)

    fun moveSection(order: List<String>, from: Int, to: Int) = prefs.moveSection(order, from, to)

    fun setSectionHidden(id: String, hidden: Boolean) = prefs.setSectionHidden(id, hidden)

    fun resetLayout() = prefs.resetLayout()
}
