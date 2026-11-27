// SPDX-License-Identifier: GPL-3.0-or-later
// ╭──────────────────────────────────────────────────────────────────╮
// │  ┌●───●───●───●───●───●───●───●───●───●───●───●───●───●───●───┐  │
// │  │                                                            │  │
// │  ●   ██╗  ██╗    ██╗    ██╗                                   ●  │
// │  │   ██║  ██║    ██║    ██║           herbiewalker            │  │
// │  │   ███████║    ██║ █╗ ██║──●──●──●──┐                       │  │
// │  │   ██╔══██║    ██║███╗██║           │                       │  │
// │  ●   ██║  ██║    ╚███╔███╔╝           ●───⏣  code · tools     ●  │
// │  │   ╚═╝  ╚═╝     ╚══╝╚══╝                    homelab         │  │
// │  │                                                            │  │
// │  └●───●───●───●───●───●───●───●───●───●───●───●───●───●───●───┘  │
// ╰──────────────────────────────────────────────────────────────────╯
package app.tryst.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tryst.core.prefs.ChartStyle
import app.tryst.core.prefs.GeneralPreferences
import app.tryst.core.prefs.InsightsPreferences
import app.tryst.data.db.entity.CycleDayEntity
import app.tryst.data.db.entity.CyclePeriodEntity
import app.tryst.data.db.entity.CycleSymptomLogEntity
import app.tryst.data.db.entity.PartnerEntity
import app.tryst.data.db.entity.ProfileEntity
import app.tryst.data.db.relation.EncounterWithDetails
import app.tryst.data.filter.DateScope
import app.tryst.data.repository.ActRepository
import app.tryst.data.repository.CycleOwner
import app.tryst.data.repository.CycleRepository
import app.tryst.data.repository.EjaculationLocationRepository
import app.tryst.data.repository.EncounterRepository
import app.tryst.data.repository.KinkRepository
import app.tryst.data.repository.OccasionRepository
import app.tryst.data.repository.PartnerRepository
import app.tryst.data.repository.PositionRepository
import app.tryst.data.repository.ToyRepository
import app.tryst.data.stats.CycleStats
import app.tryst.data.stats.CycleStatsEngine
import app.tryst.data.stats.Insights
import app.tryst.data.stats.InsightsEngine
import app.tryst.ui.common.Format
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

/** One person with periods logged, as a chip on the Cycle card. [name] is null for the user ("You"). */
data class CyclePerson(val owner: CycleOwner, val name: String?)

/** The Insights Cycle card (0.7.6): whose charts show and their figures. */
data class CycleCard(val people: List<CyclePerson>, val selected: CycleOwner?, val stats: CycleStats)

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
    private val partnerRepository: PartnerRepository,
    private val prefs: InsightsPreferences,
    private val cycleRepository: CycleRepository,
    generalPreferences: GeneralPreferences,
) : ViewModel() {

    /** Picked partner colors by the label People shows ("Anonymous" for unnamed), for the People chart. */
    val partnerColors: StateFlow<Map<String, String>> = partnerRepository.observeAll()
        .map { list -> list.mapNotNull { p -> p.color?.let { app.tryst.ui.common.Format.partnerName(p) to it } }.toMap() }
        .catch { emit(emptyMap()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** The INS-2 time window. Remembered across visits (`InsightsPreferences`). */
    val scope: StateFlow<DateScope> = prefs.scope

    private val encounters = encounterRepository.observeAll()

    /**
     * Years the log actually covers, newest first — the scope picker offers exactly these, so it can
     * never propose a year with nothing in it.
     */
    val availableYears: StateFlow<List<Int>> = encounters
        .map { list ->
            withContext(Dispatchers.Default) {
                list.map { Instant.ofEpochMilli(it.encounter.startAt).atZone(ZoneId.systemDefault()).year }
                    .distinct()
                    .sortedDescending()
            }
        }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
                combine(encounters, labelMaps, prefs.scope) { encounters, maps, scope ->
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
                            scope = scope.range(),
                        )
                    }
                }
            }
            // The DB closes on lock; swallow the resulting error so we don't crash mid-teardown.
            .catch { emit(Insights.EMPTY) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Insights.EMPTY)

    fun setScope(scope: DateScope) = prefs.setScope(scope)

    // --- Cycle section (CYC-1, 0.7.6, D-75) ---

    private val pickedCycleOwner = MutableStateFlow<CycleOwner?>(null)

    /** Picks whose charts the Cycle card shows. */
    fun selectCycleOwner(owner: CycleOwner) {
        pickedCycleOwner.value = owner
    }

    private data class CycleData(
        val periods: List<CyclePeriodEntity>,
        val days: List<CycleDayEntity>,
        val logs: List<CycleSymptomLogEntity>,
        val symptomLabels: Map<String, String>,
    )

    /**
     * The Cycle card: everyone with periods logged (the user first), whose charts are showing, and their
     * figures under the current scope. Null while cycle tracking is off, which hides the card everywhere.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val cycleCard: StateFlow<CycleCard?> = generalPreferences.cycleEnabled.flatMapLatest { enabled ->
        if (!enabled) {
            flowOf(null)
        } else {
            combine(
                combine(
                    cycleRepository.observeAllPeriods(),
                    cycleRepository.observeAllDays(),
                    cycleRepository.observeAllSymptomLogs(),
                    cycleRepository.observeSymptoms(),
                ) { periods, days, logs, symptoms -> CycleData(periods, days, logs, symptoms.associate { it.id to it.label }) },
                encounters,
                prefs.scope,
                pickedCycleOwner,
                partnerRepository.observeAll(),
            ) { data, encounters, scope, picked, partners ->
                withContext(Dispatchers.Default) { buildCycleCard(data, encounters, scope, picked, partners) }
            }
        }
    }
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun buildCycleCard(
        data: CycleData,
        encounters: List<EncounterWithDetails>,
        scope: DateScope,
        picked: CycleOwner?,
        partners: List<PartnerEntity>,
    ): CycleCard {
        val owners = data.periods.mapTo(HashSet()) { CycleOwner(it.ownerKind, it.ownerId) }
        val self = CycleOwner(CyclePeriodEntity.OWNER_PROFILE, ProfileEntity.SELF_ID)
        val people = buildList {
            if (self in owners) add(CyclePerson(self, null))
            partners.sortedBy { Format.partnerName(it).lowercase() }.forEach { p ->
                val owner = CycleOwner(CyclePeriodEntity.OWNER_PARTNER, p.id)
                if (owner in owners) add(CyclePerson(owner, Format.partnerName(p)))
            }
        }
        val selected = picked?.takeIf { p -> people.any { it.owner == p } } ?: people.firstOrNull()?.owner
        val stats = selected?.let {
            CycleStatsEngine.compute(it, data.periods, data.days, data.logs, data.symptomLabels, encounters, scope.range())
        } ?: CycleStats.EMPTY
        return CycleCard(people, selected, stats)
    }

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

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
