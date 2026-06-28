package app.tryst.ui.encounter

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tryst.core.session.SessionManager
import app.tryst.data.db.entity.ActEntity
import app.tryst.data.db.entity.EjaculationLocation
import app.tryst.data.db.entity.EncounterEntity
import app.tryst.data.db.entity.Initiator
import app.tryst.data.db.entity.Kink
import app.tryst.data.db.entity.MediaEntity
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
import app.tryst.data.stats.OptionUsage
import app.tryst.ui.common.MediaImages
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A photo picked but not yet attached (attached on Save). [tempFile] is set only for in-app camera
 * captures — a plaintext file in private cache that must be deleted once it's encrypted (or discarded).
 */
data class PendingPhoto(val uri: Uri, val mimeType: String, val tempFile: File? = null)

/**
 * The editor's full form state, held immutably and replaced wholesale on every edit (the VM owns the
 * only [androidx.compose.runtime.MutableState], so the screen never mutates a field directly). Repo-backed
 * option lists (partners / custom positions / acts) live on the VM as their own [StateFlow]s, not here.
 */
data class EncounterEditUiState(
    val startAt: Long = System.currentTimeMillis(),
    val durationText: String = "",
    val rating: Int? = null,
    val mood: Mood? = null,
    val initiator: Initiator? = null,
    val protection: Set<Protection> = emptySet(),
    val orgasmCountSelf: Int = 0,
    /** Per-orgasm ejaculation location(s): orgasm index (0-based) -> locations (multi-select). */
    val ejaculations: Map<Int, Set<EjaculationLocation>> = emptyMap(),
    /** Per-partner orgasm counts: partnerId -> count. */
    val partnerOrgasms: Map<String, Int> = emptyMap(),
    val practicesPerformed: Set<String> = emptySet(),
    val practicesReceived: Set<String> = emptySet(),
    val selectedPositionIds: Set<String> = emptySet(),
    val kinks: Set<Kink> = emptySet(),
    val contexts: Set<Setting> = emptySet(),
    val occasions: Set<Occasion> = emptySet(),
    val toys: Set<ToyType> = emptySet(),
    val note: String = "",
    val selectedPartnerIds: Set<String> = emptySet(),
    /** Photos already saved on this encounter (minus any the user removed this session). */
    val existingPhotos: List<MediaEntity> = emptyList(),
    /** Photos picked this session, attached (encrypted) on Save. */
    val pendingPhotos: List<PendingPhoto> = emptyList(),
    val isEditing: Boolean = false,
) {
    /** No partner selected: hide the partner-only fields and drop their values on save. */
    val solo: Boolean get() = selectedPartnerIds.isEmpty()
}

@HiltViewModel
class EncounterEditViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val session: SessionManager,
    private val encounters: EncounterRepository,
    partners: PartnerRepository,
    positions: PositionRepository,
    acts: ActRepository,
) : ViewModel() {

    /** Keep the app unlocked across the photo-picker/camera handoff. */
    fun suppressAutoLock() = session.suppressNextAutoLock()

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

    /**
     * How often each option has been picked across the log, so the editor can surface the user's
     * most-used choices inline (ENC-1). Derived off the main thread; falls back to empty (the curated
     * sets) if the DB is mid-teardown on lock.
     */
    val optionUsage: StateFlow<OptionUsage> =
        encounters.observeAll()
            .map { list -> withContext(Dispatchers.Default) { OptionUsage.from(list.map { it.encounter }) } }
            .catch { emit(OptionUsage.EMPTY) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OptionUsage.EMPTY)

    var uiState by mutableStateOf(EncounterEditUiState())
        private set

    // The form as it stood when first shown (a blank new entry, or the loaded encounter). Edits are
    // compared against this so an accidental back-press / swipe on an *untouched* form closes silently,
    // while a touched one prompts before discarding. EncounterEditUiState is a value type, so structural
    // equality covers every field including the pending/removed photo lists.
    private var baseline = uiState

    private val removedExisting = mutableListOf<MediaEntity>()
    private var loadedId: String? = null
    private var createdAt: Long = 0L

    /** True once the user has changed anything not yet saved — drives the "Discard changes?" guard. */
    fun hasUnsavedChanges(): Boolean = uiState != baseline

    @Suppress("CyclomaticComplexMethod") // straight-line field mapping from the loaded entity.
    fun load(encounterId: String?) {
        if (encounterId == null || loadedId == encounterId) return
        viewModelScope.launch {
            val details = encounters.get(encounterId) ?: return@launch
            val e = details.encounter
            loadedId = encounterId
            createdAt = e.createdAt
            uiState = EncounterEditUiState(
                startAt = e.startAt,
                durationText = e.durationMin?.toString() ?: "",
                rating = e.satisfactionRating,
                mood = e.mood,
                initiator = e.initiator,
                protection = e.protectionUsed,
                orgasmCountSelf = e.orgasmCountSelf ?: 0,
                ejaculations = e.ejaculationLocations ?: emptyMap(),
                partnerOrgasms = e.partnerOrgasms ?: emptyMap(),
                practicesPerformed = e.practicesPerformed ?: emptySet(),
                practicesReceived = e.practicesReceived ?: emptySet(),
                selectedPositionIds = e.positions ?: emptySet(),
                kinks = e.kinks ?: emptySet(),
                contexts = e.contexts ?: emptySet(),
                occasions = e.occasions ?: emptySet(),
                toys = e.toys ?: emptySet(),
                note = e.note ?: "",
                selectedPartnerIds = details.partners.map { it.id }.toSet(),
                existingPhotos = details.media,
                isEditing = true,
            )
            baseline = uiState
        }
    }

    fun setStartAt(value: Long) {
        uiState = uiState.copy(startAt = value)
    }
    fun setDuration(text: String) {
        uiState = uiState.copy(durationText = text.filter(Char::isDigit))
    }
    fun setRating(value: Int?) {
        uiState = uiState.copy(rating = value)
    }
    fun setMood(value: Mood?) {
        uiState = uiState.copy(mood = value)
    }
    fun setInitiator(value: Initiator?) {
        uiState = uiState.copy(initiator = value)
    }
    fun setNote(value: String) {
        uiState = uiState.copy(note = value)
    }

    fun addPhoto(uri: Uri) {
        val mime = context.contentResolver.getType(uri) ?: "image/*"
        uiState = uiState.copy(pendingPhotos = uiState.pendingPhotos + PendingPhoto(uri, mime))
    }

    /** From the in-app camera: [file] is the plaintext capture in cache, deleted once encrypted. */
    fun addCapturedPhoto(uri: Uri, file: File) {
        uiState = uiState.copy(pendingPhotos = uiState.pendingPhotos + PendingPhoto(uri, "image/jpeg", file))
    }

    fun removePending(photo: PendingPhoto) {
        uiState = uiState.copy(pendingPhotos = uiState.pendingPhotos - photo)
        photo.tempFile?.delete()
    }

    fun removeExisting(media: MediaEntity) {
        uiState = uiState.copy(existingPhotos = uiState.existingPhotos - media)
        removedExisting += media
    }

    suspend fun decodeExisting(media: MediaEntity, reqPx: Int): ImageBitmap? = MediaImages.decodeSampled(reqPx) { runCatching { encounters.openMedia(media) }.getOrNull() }

    suspend fun decodePending(uri: Uri, reqPx: Int): ImageBitmap? = MediaImages.decodeSampled(reqPx) { context.contentResolver.openInputStream(uri) }

    fun togglePartner(id: String) {
        uiState = if (id in uiState.selectedPartnerIds) {
            uiState.copy(
                selectedPartnerIds = uiState.selectedPartnerIds - id,
                partnerOrgasms = uiState.partnerOrgasms - id, // drop orgasm count for a de-selected partner
            )
        } else {
            uiState.copy(selectedPartnerIds = uiState.selectedPartnerIds + id)
        }
    }

    /** Sets how many times the user came; trims ejaculation rows beyond the new count. */
    fun setSelfOrgasms(count: Int) {
        uiState = uiState.copy(
            orgasmCountSelf = count,
            ejaculations = uiState.ejaculations.filterKeys { it < count },
        )
    }

    fun toggleEjaculation(index: Int, value: EjaculationLocation) {
        val current = uiState.ejaculations[index] ?: emptySet()
        val updated = if (value in current) current - value else current + value
        uiState = uiState.copy(ejaculations = uiState.ejaculations + (index to updated))
    }

    fun setPartnerOrgasms(id: String, count: Int) {
        uiState = uiState.copy(
            partnerOrgasms = if (count <= 0) uiState.partnerOrgasms - id else uiState.partnerOrgasms + (id to count),
        )
    }

    fun toggleProtection(value: Protection) {
        uiState = uiState.copy(protection = uiState.protection.toggle(value))
    }

    fun togglePerformed(id: String) {
        uiState = uiState.copy(practicesPerformed = uiState.practicesPerformed.toggle(id))
    }

    fun toggleReceived(id: String) {
        uiState = uiState.copy(practicesReceived = uiState.practicesReceived.toggle(id))
    }

    fun togglePosition(id: String) {
        uiState = uiState.copy(selectedPositionIds = uiState.selectedPositionIds.toggle(id))
    }

    fun toggleKink(value: Kink) {
        uiState = uiState.copy(kinks = uiState.kinks.toggle(value))
    }

    fun toggleContext(value: Setting) {
        uiState = uiState.copy(contexts = uiState.contexts.toggle(value))
    }

    fun toggleOccasion(value: Occasion) {
        uiState = uiState.copy(occasions = uiState.occasions.toggle(value))
    }

    fun toggleToy(value: ToyType) {
        uiState = uiState.copy(toys = uiState.toys.toggle(value))
    }

    fun save(onDone: () -> Unit) {
        val s = uiState
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val id = loadedId ?: UUID.randomUUID().toString()
            // Solo (no partner) hides the "who initiated" / "acts received" fields in the editor —
            // drop their values so a solo row never carries stale partner-only data.
            val solo = s.solo
            val entity = EncounterEntity(
                id = id,
                startAt = s.startAt,
                durationMin = s.durationText.toIntOrNull(),
                note = s.note.ifBlank { null },
                satisfactionRating = s.rating,
                orgasm = null,
                mood = s.mood,
                initiator = if (solo) null else s.initiator,
                protectionUsed = s.protection,
                orgasmCountSelf = s.orgasmCountSelf,
                orgasmCountPartner = null,
                ejaculationLocations = s.ejaculations.filterValues { it.isNotEmpty() }.ifEmpty { null },
                practicesPerformed = s.practicesPerformed,
                practicesReceived = if (solo) emptySet() else s.practicesReceived,
                positions = s.selectedPositionIds,
                kinks = s.kinks,
                contexts = s.contexts,
                occasions = s.occasions,
                partnerOrgasms = s.partnerOrgasms.ifEmpty { null },
                toys = s.toys,
                locationId = null,
                createdAt = if (s.isEditing) createdAt else now,
                updatedAt = now,
            )
            encounters.save(entity, partnerIds = s.selectedPartnerIds.toList())
            // Encrypt + attach newly-picked photos and remove any the user dropped (after the
            // encounter row exists, so the media FK is satisfied).
            withContext(Dispatchers.IO) {
                s.pendingPhotos.forEach { photo ->
                    context.contentResolver.openInputStream(photo.uri)?.use {
                        encounters.attachMedia(id, photo.mimeType, it, now)
                    }
                }
                removedExisting.forEach { encounters.deleteMedia(it) }
                s.pendingPhotos.forEach { it.tempFile?.delete() } // remove plaintext camera temps
            }
            onDone()
        }
    }

    override fun onCleared() {
        // Best-effort cleanup of any uncommitted camera temps (e.g. on Cancel / back).
        uiState.pendingPhotos.forEach { it.tempFile?.delete() }
    }

    fun delete(onDone: () -> Unit) {
        val id = loadedId ?: return onDone()
        viewModelScope.launch {
            encounters.get(id)?.let { encounters.delete(it.encounter) }
            onDone()
        }
    }
}

/** Add [value] to the set if absent, remove it if present. */
private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value
