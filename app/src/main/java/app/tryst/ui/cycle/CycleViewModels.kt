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
package app.tryst.ui.cycle

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tryst.data.cycle.CycleStatus
import app.tryst.data.db.entity.CycleDayEntity
import app.tryst.data.db.entity.CycleFlow
import app.tryst.data.db.entity.CyclePeriodEntity
import app.tryst.data.db.entity.ProfileEntity
import app.tryst.data.repository.CycleOwner
import app.tryst.data.repository.CycleRepository
import app.tryst.data.repository.PartnerRepository
import app.tryst.data.repository.ProfileRepository
import app.tryst.ui.common.Format
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal fun todayEpochDay(): Long = LocalDate.now().toEpochDay()

/** One person on the Cycle tab. [name] is null for the self profile with no name (the UI says "You"). */
data class CyclePerson(val owner: CycleOwner, val name: String?, val status: CycleStatus)

/** Why a period couldn't be saved; the screen turns it into text. */
enum class CycleProblem { OVERLAP, END_BEFORE_START }

@HiltViewModel
class CycleViewModel @Inject constructor(
    private val repository: CycleRepository,
    profileRepository: ProfileRepository,
    partnerRepository: PartnerRepository,
) : ViewModel() {

    val people: StateFlow<List<CyclePerson>> = combine(
        profileRepository.observe(),
        partnerRepository.observeActive(),
        repository.observeAllPeriods(),
    ) { profile, partners, periods ->
        val today = todayEpochDay()
        val byOwner = periods.groupBy { CycleOwner(it.ownerKind, it.ownerId) }
        val self = CycleOwner(CyclePeriodEntity.OWNER_PROFILE, ProfileEntity.SELF_ID)
        buildList {
            add(CyclePerson(self, profile?.displayName?.takeIf { it.isNotBlank() }, CycleStatus.of(byOwner[self].orEmpty(), today)))
            for (p in partners) {
                val owner = CycleOwner(CyclePeriodEntity.OWNER_PARTNER, p.id)
                add(CyclePerson(owner, Format.partnerName(p), CycleStatus.of(byOwner[owner].orEmpty(), today)))
            }
        }
    }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Set when the last quick action was refused; the screen shows it once then clears it. */
    var problem by mutableStateOf<CycleProblem?>(null)
        private set

    fun clearProblem() {
        problem = null
    }

    /** "Period started" for [owner] on [epochDay] (today by default), with the most likely length and flow. */
    fun start(owner: CycleOwner, epochDay: Long = todayEpochDay()) {
        viewModelScope.launch {
            if (repository.startPeriod(owner, epochDay) == null) problem = CycleProblem.OVERLAP
        }
    }

    /** "Period ended" today: the real end replaces the guess, and any days past it are dropped. */
    fun endToday(period: CyclePeriodEntity) {
        viewModelScope.launch {
            val today = todayEpochDay().coerceAtLeast(period.startEpochDay)
            val ended = period.copy(endEpochDay = today, endEstimated = false, updatedAt = System.currentTimeMillis())
            repository.save(ended, repository.refitDays(ended, repository.getDays(period.id)))
        }
    }
}

@HiltViewModel
class CyclePersonViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: CycleRepository,
    profileRepository: ProfileRepository,
    partnerRepository: PartnerRepository,
) : ViewModel() {

    val owner = CycleOwner(
        kind = checkNotNull(savedStateHandle.get<String>(ARG_KIND)),
        id = checkNotNull(savedStateHandle.get<String>(ARG_ID)),
    )

    /** Null means the self profile with no name set; the screen says "You". */
    val name: StateFlow<String?> = if (owner.isSelf) {
        profileRepository.observe().map { it?.displayName?.takeIf { n -> n.isNotBlank() } }
    } else {
        partnerRepository.observeActive().map { list -> list.firstOrNull { it.id == owner.id }?.let(Format::partnerName) }
    }
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val periods: StateFlow<List<CyclePeriodEntity>> = repository.observePeriods(owner)
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var problem by mutableStateOf<CycleProblem?>(null)
        private set

    fun clearProblem() {
        problem = null
    }

    /** Logs a period starting on [epochDay] and hands its id to [onCreated] to open the editor. */
    fun log(epochDay: Long, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val id = repository.startPeriod(owner, epochDay)
            if (id == null) problem = CycleProblem.OVERLAP else onCreated(id)
        }
    }

    companion object {
        const val ARG_KIND = "kind"
        const val ARG_ID = "id"
    }
}

/** Editable copy of one period. Nothing is written until [CyclePeriodViewModel.save]. */
data class PeriodDraft(
    val period: CyclePeriodEntity,
    val days: List<CycleDayEntity>,
)

@HiltViewModel
class CyclePeriodViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: CycleRepository,
) : ViewModel() {

    private val periodId: String = checkNotNull(savedStateHandle.get<String>(ARG_PERIOD_ID))

    var draft by mutableStateOf<PeriodDraft?>(null)
        private set

    var problem by mutableStateOf<CycleProblem?>(null)
        private set

    fun clearProblem() {
        problem = null
    }

    /** True once the period turned out not to exist (deleted elsewhere); the screen closes. */
    var missing by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            val period = repository.getPeriod(periodId)
            if (period == null) missing = true else draft = PeriodDraft(period, repository.getDays(periodId))
        }
    }

    fun setStart(epochDay: Long) = reshape { it.copy(startEpochDay = epochDay) }

    /** Picking an end on purpose makes it real, not a guess. */
    fun setEnd(epochDay: Long) = reshape { it.copy(endEpochDay = epochDay, endEstimated = false) }

    fun setFlow(epochDay: Long, flow: CycleFlow) {
        val d = draft ?: return
        draft = d.copy(days = d.days.map { if (it.epochDay == epochDay) it.copy(flow = flow.name, estimated = false) else it })
    }

    fun setNote(note: String) {
        val d = draft ?: return
        draft = d.copy(period = d.period.copy(note = note))
    }

    private fun reshape(change: (CyclePeriodEntity) -> CyclePeriodEntity) {
        val d = draft ?: return
        val next = change(d.period)
        problem = null
        if (next.endEpochDay < next.startEpochDay) {
            problem = CycleProblem.END_BEFORE_START
            return
        }
        viewModelScope.launch { draft = PeriodDraft(next, repository.refitDays(next, d.days)) }
    }

    fun save(onDone: () -> Unit) {
        val d = draft ?: return
        viewModelScope.launch {
            val period = d.period.copy(note = d.period.note?.trim()?.ifEmpty { null }, updatedAt = System.currentTimeMillis())
            if (repository.overlapsOther(period)) {
                problem = CycleProblem.OVERLAP
                return@launch
            }
            repository.save(period, d.days)
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.deletePeriod(periodId)
            onDone()
        }
    }

    companion object {
        const val ARG_PERIOD_ID = "periodId"
    }
}

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
