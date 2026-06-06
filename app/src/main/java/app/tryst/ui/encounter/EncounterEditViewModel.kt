package app.tryst.ui.encounter

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tryst.data.db.entity.ActEntity
import app.tryst.data.db.entity.EjaculationLocation
import app.tryst.data.db.entity.EncounterEntity
import app.tryst.data.db.entity.Initiator
import app.tryst.data.db.entity.Kink
import app.tryst.data.db.entity.Mood
import app.tryst.data.db.entity.Occasion
import app.tryst.data.db.entity.PartnerEntity
import app.tryst.data.db.entity.PositionEntity
import app.tryst.data.db.entity.Protection
import app.tryst.data.db.entity.Setting
import app.tryst.data.db.entity.ToyType
import app.tryst.data.repository.ActRepository
import app.tryst.data.repository.EncounterRepository
import app.tryst.data.repository.PartnerRepository
import app.tryst.data.repository.PositionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class EncounterEditViewModel @Inject constructor(
    private val encounters: EncounterRepository,
    partners: PartnerRepository,
    positions: PositionRepository,
    acts: ActRepository,
) : ViewModel() {

    val availablePartners: StateFlow<List<PartnerEntity>> =
        partners.observeActive()
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val customPositions: StateFlow<List<PositionEntity>> =
        positions.observeCustom()
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val customActs: StateFlow<List<ActEntity>> =
        acts.observeCustom()
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var startAt by mutableStateOf(System.currentTimeMillis())
    var durationText by mutableStateOf("")
    var rating by mutableStateOf<Int?>(null)
    var mood by mutableStateOf<Mood?>(null)
    var initiator by mutableStateOf<Initiator?>(null)
    var protection by mutableStateOf<Set<Protection>>(emptySet())
    var orgasmCountSelf by mutableStateOf(0)
        private set
    /** Per-orgasm ejaculation location: orgasm index (0-based) -> location. */
    var ejaculations by mutableStateOf<Map<Int, EjaculationLocation>>(emptyMap())
        private set
    /** Per-partner orgasm counts: partnerId -> count. */
    var partnerOrgasms by mutableStateOf<Map<String, Int>>(emptyMap())
        private set
    var practicesPerformed by mutableStateOf<Set<String>>(emptySet())
    var practicesReceived by mutableStateOf<Set<String>>(emptySet())
    var selectedPositionIds by mutableStateOf<Set<String>>(emptySet())
    var kinks by mutableStateOf<Set<Kink>>(emptySet())
    var contexts by mutableStateOf<Set<Setting>>(emptySet())
    var occasions by mutableStateOf<Set<Occasion>>(emptySet())
    var toys by mutableStateOf<Set<ToyType>>(emptySet())
    var note by mutableStateOf("")
    var selectedPartnerIds by mutableStateOf<Set<String>>(emptySet())

    var isEditing by mutableStateOf(false)
        private set

    private var loadedId: String? = null
    private var createdAt: Long = 0L

    fun load(encounterId: String?) {
        if (encounterId == null || loadedId == encounterId) return
        viewModelScope.launch {
            val details = encounters.get(encounterId) ?: return@launch
            val e = details.encounter
            loadedId = encounterId
            isEditing = true
            createdAt = e.createdAt
            startAt = e.startAt
            durationText = e.durationMin?.toString() ?: ""
            rating = e.satisfactionRating
            mood = e.mood
            initiator = e.initiator
            protection = e.protectionUsed
            orgasmCountSelf = e.orgasmCountSelf ?: 0
            ejaculations = e.ejaculationLocations ?: emptyMap()
            partnerOrgasms = e.partnerOrgasms ?: emptyMap()
            practicesPerformed = e.practicesPerformed ?: emptySet()
            practicesReceived = e.practicesReceived ?: emptySet()
            selectedPositionIds = e.positions ?: emptySet()
            kinks = e.kinks ?: emptySet()
            contexts = e.contexts ?: emptySet()
            occasions = e.occasions ?: emptySet()
            toys = e.toys ?: emptySet()
            note = e.note ?: ""
            selectedPartnerIds = details.partners.map { it.id }.toSet()
        }
    }

    fun togglePartner(id: String) {
        selectedPartnerIds = if (id in selectedPartnerIds) {
            partnerOrgasms = partnerOrgasms - id // drop orgasm count for a de-selected partner
            selectedPartnerIds - id
        } else {
            selectedPartnerIds + id
        }
    }

    /** Sets how many times the user came; trims ejaculation rows beyond the new count. */
    fun setSelfOrgasms(count: Int) {
        orgasmCountSelf = count
        if (ejaculations.keys.any { it >= count }) {
            ejaculations = ejaculations.filterKeys { it < count }
        }
    }

    fun setEjaculation(index: Int, value: EjaculationLocation) {
        ejaculations = ejaculations + (index to value)
    }

    fun setPartnerOrgasms(id: String, count: Int) {
        partnerOrgasms = if (count <= 0) partnerOrgasms - id else partnerOrgasms + (id to count)
    }

    fun toggleProtection(value: Protection) {
        protection = if (value in protection) protection - value else protection + value
    }

    fun togglePerformed(id: String) {
        practicesPerformed =
            if (id in practicesPerformed) practicesPerformed - id else practicesPerformed + id
    }

    fun toggleReceived(id: String) {
        practicesReceived =
            if (id in practicesReceived) practicesReceived - id else practicesReceived + id
    }

    fun togglePosition(id: String) {
        selectedPositionIds =
            if (id in selectedPositionIds) selectedPositionIds - id else selectedPositionIds + id
    }

    fun toggleKink(value: Kink) {
        kinks = if (value in kinks) kinks - value else kinks + value
    }

    fun toggleContext(value: Setting) {
        contexts = if (value in contexts) contexts - value else contexts + value
    }

    fun toggleOccasion(value: Occasion) {
        occasions = if (value in occasions) occasions - value else occasions + value
    }

    fun toggleToy(value: ToyType) {
        toys = if (value in toys) toys - value else toys + value
    }

    fun save(onDone: () -> Unit) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val id = loadedId ?: UUID.randomUUID().toString()
            val entity = EncounterEntity(
                id = id,
                startAt = startAt,
                durationMin = durationText.toIntOrNull(),
                note = note.ifBlank { null },
                satisfactionRating = rating,
                orgasm = null,
                mood = mood,
                initiator = initiator,
                protectionUsed = protection,
                orgasmCountSelf = orgasmCountSelf,
                orgasmCountPartner = null,
                ejaculationLocations = ejaculations.ifEmpty { null },
                practicesPerformed = practicesPerformed,
                practicesReceived = practicesReceived,
                positions = selectedPositionIds,
                kinks = kinks,
                contexts = contexts,
                occasions = occasions,
                partnerOrgasms = partnerOrgasms.ifEmpty { null },
                toys = toys,
                locationId = null,
                createdAt = if (isEditing) createdAt else now,
                updatedAt = now,
            )
            encounters.save(entity, partnerIds = selectedPartnerIds.toList())
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        val id = loadedId ?: return onDone()
        viewModelScope.launch {
            encounters.get(id)?.let { encounters.delete(it.encounter) }
            onDone()
        }
    }
}
