package app.tryst.ui.partner

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tryst.core.session.SessionManager
import app.tryst.data.db.entity.BodyType
import app.tryst.data.db.entity.Ethnicity
import app.tryst.data.db.entity.Gender
import app.tryst.data.db.entity.PartnerEntity
import app.tryst.data.db.entity.PersonPhotoEntity
import app.tryst.data.db.entity.RelationshipType
import app.tryst.data.db.entity.Sex
import app.tryst.data.repository.PartnerRepository
import app.tryst.data.repository.PersonPhotoRepository
import app.tryst.ui.common.MediaImages
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Editor state for the partner page (QOL-3). Field edits mutate this state; the photo strip below
 * (portraits + current-avatar promotion) is auto-saved to `person_photo` / `partners.photoMediaId` on
 * each add / delete / promote — no staging, no risk of losing photos to a discard.
 */
data class PartnerEditUiState(
    val name: String = "",
    val anonymous: Boolean = false,
    val note: String = "",
    val sex: Sex? = null,
    val gender: Gender? = null,
    val relationshipType: RelationshipType? = null,
    val birthDate: Long? = null,
    val ethnicity: Ethnicity? = null,
    val height: String = "",
    val bodyType: BodyType? = null,
    val location: String = "",
    val existing: PartnerEntity? = null,
) {
    val hasAnyName: Boolean get() = anonymous || name.isNotBlank()
}

@HiltViewModel
@Suppress("LongParameterList") // Hilt-injected repos, one per concern.
class PartnerEditViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val session: SessionManager,
    private val partnerRepository: PartnerRepository,
    private val personPhotoRepository: PersonPhotoRepository,
) : ViewModel() {

    /** null when creating a new partner. Set once on load; drives auto-save for photo actions. */
    val partnerId: String? = savedStateHandle.get<String>("partnerId")?.takeIf { it != "new" }

    var uiState by mutableStateOf(PartnerEditUiState())
        private set

    private var baseline = uiState

    /** Portrait album for the partner. When [partnerId] is null, no album exists yet. */
    val photos: StateFlow<List<PersonPhotoEntity>> = if (partnerId == null) {
        flowOf(emptyList<PersonPhotoEntity>()).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    } else {
        personPhotoRepository.observeForOwner(PersonPhotoRepository.KIND_PARTNER, partnerId)
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    }

    fun suppressAutoLock() = session.suppressNextAutoLock()

    init {
        viewModelScope.launch {
            val id = partnerId ?: return@launch
            val existing = partnerRepository.getById(id) ?: return@launch
            uiState = PartnerEditUiState(
                name = existing.displayName.orEmpty(),
                anonymous = existing.isAnonymous,
                note = existing.note.orEmpty(),
                sex = existing.sex,
                gender = existing.gender,
                relationshipType = existing.relationshipType,
                birthDate = existing.birthDate,
                ethnicity = existing.ethnicity,
                height = existing.height.orEmpty(),
                bodyType = existing.bodyType,
                location = existing.location.orEmpty(),
                existing = existing,
            )
            baseline = uiState
        }
    }

    fun hasUnsavedChanges(): Boolean = uiState != baseline

    fun setName(value: String) {
        uiState = uiState.copy(name = value)
    }
    fun setAnonymous(value: Boolean) {
        uiState = uiState.copy(anonymous = value)
    }
    fun setNote(value: String) {
        uiState = uiState.copy(note = value)
    }
    fun setSex(value: Sex?) {
        uiState = uiState.copy(sex = value)
    }
    fun setGender(value: Gender?) {
        uiState = uiState.copy(gender = value)
    }
    fun setRelationship(value: RelationshipType?) {
        uiState = uiState.copy(relationshipType = value)
    }
    fun setBirthDate(value: Long?) {
        uiState = uiState.copy(birthDate = value)
    }
    fun setEthnicity(value: Ethnicity?) {
        uiState = uiState.copy(ethnicity = value)
    }
    fun setHeight(value: String) {
        uiState = uiState.copy(height = value)
    }
    fun setBodyType(value: BodyType?) {
        uiState = uiState.copy(bodyType = value)
    }
    fun setLocation(value: String) {
        uiState = uiState.copy(location = value)
    }

    /** Persist the field edits + return the (possibly new) partner id for downstream actions. */
    fun save(onDone: (String) -> Unit) {
        val s = uiState
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val existing = s.existing
            val id = existing?.id ?: UUID.randomUUID().toString()
            partnerRepository.upsert(
                PartnerEntity(
                    id = id,
                    displayName = if (s.anonymous) null else s.name.trim().ifBlank { null },
                    isAnonymous = s.anonymous,
                    color = existing?.color,
                    note = s.note.trim().ifBlank { null },
                    sex = s.sex,
                    gender = s.gender,
                    relationshipType = s.relationshipType,
                    photoMediaId = existing?.photoMediaId,
                    birthDate = s.birthDate,
                    ethnicity = s.ethnicity,
                    height = s.height.trim().ifBlank { null },
                    bodyType = s.bodyType,
                    location = s.location.trim().ifBlank { null },
                    archivedAt = existing?.archivedAt,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                ),
            )
            baseline = uiState
            onDone(id)
        }
    }

    /**
     * Add multiple picked photos to the partner's portrait album. Requires the partner to have been
     * saved (i.e. has an id). The caller passes the URIs from the multi-picker or a single camera capture.
     */
    fun addPhotos(uris: List<Uri>) {
        val id = partnerId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            uris.forEach { uri ->
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        personPhotoRepository.add(PersonPhotoRepository.KIND_PARTNER, id, stream)
                    }
                }
            }
        }
    }

    fun deletePhoto(photo: PersonPhotoEntity) {
        viewModelScope.launch { personPhotoRepository.delete(photo) }
    }

    /** Promote a portrait to be the partner's current avatar (updates `partners.photoMediaId`). */
    fun setAsAvatar(photo: PersonPhotoEntity) {
        val id = partnerId ?: return
        viewModelScope.launch {
            val existing = partnerRepository.getById(id) ?: return@launch
            partnerRepository.upsert(existing.copy(photoMediaId = photo.mediaBlobId))
            uiState = uiState.copy(existing = existing.copy(photoMediaId = photo.mediaBlobId))
            baseline = uiState
        }
    }

    suspend fun decodePartnerPhoto(mediaId: String, reqPx: Int): ImageBitmap? = MediaImages.decodeSampled(reqPx) { runCatching { personPhotoRepository.openBlob(mediaId) }.getOrNull() }

    /** For staged-uri previews before the pick lands in the DB (used when Save-first-then-photos flow). */
    suspend fun decodeUri(uri: Uri, reqPx: Int): ImageBitmap? = MediaImages.decodeSampled(reqPx) { context.contentResolver.openInputStream(uri) }
}
