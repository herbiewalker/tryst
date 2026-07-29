package app.tryst.ui.partner

import android.content.Context
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tryst.core.session.SessionManager
import app.tryst.data.db.entity.BodyType
import app.tryst.data.db.entity.Ethnicity
import app.tryst.data.db.entity.Gender
import app.tryst.data.db.entity.PartnerEntity
import app.tryst.data.db.entity.RelationshipType
import app.tryst.data.db.entity.Sex
import app.tryst.data.repository.PartnerRepository
import app.tryst.ui.common.MediaImages
import app.tryst.ui.gallery.GalleryDeepLink
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Everything the partner editor collects, passed to [PartnersViewModel.save] in one shot. */
data class PartnerDraft(
    val name: String,
    val anonymous: Boolean,
    val note: String,
    val sex: Sex?,
    val gender: Gender?,
    val relationshipType: RelationshipType?,
    val birthDate: Long?,
    val ethnicity: Ethnicity?,
    val height: String,
    val bodyType: BodyType?,
    val location: String,
    val newPhotoUri: Uri?,
    val removePhoto: Boolean,
    val captureTempFile: File?,
)

@HiltViewModel
class PartnersViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val session: SessionManager,
    private val repository: PartnerRepository,
    private val galleryDeepLink: GalleryDeepLink,
) : ViewModel() {

    /** Keep the app unlocked across the photo-picker/camera handoff. */
    fun suppressAutoLock() = session.suppressNextAutoLock()

    /** Opens the Photos tab pre-filtered to this partner (GAL-5); the caller navigates to the tab. */
    fun viewPhotosFor(partnerId: String) = galleryDeepLink.requestPartner(partnerId)

    val partners: StateFlow<List<PartnerEntity>> =
        repository.observeActive()
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(id: String?, draft: PartnerDraft) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val existing = id?.let { repository.getById(it) }
            val oldPhotoId = existing?.photoMediaId
            val photoMediaId = when {
                draft.newPhotoUri != null -> {
                    val newId = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(draft.newPhotoUri)?.use { repository.savePhoto(it) }
                    }
                    if (newId != null && oldPhotoId != null) repository.deletePhoto(oldPhotoId)
                    newId ?: oldPhotoId
                }
                draft.removePhoto -> {
                    oldPhotoId?.let { repository.deletePhoto(it) }
                    null
                }
                else -> oldPhotoId
            }
            repository.upsert(
                PartnerEntity(
                    id = id ?: UUID.randomUUID().toString(),
                    displayName = if (draft.anonymous) null else draft.name.trim().ifBlank { null },
                    isAnonymous = draft.anonymous,
                    color = existing?.color,
                    note = draft.note.trim().ifBlank { null },
                    sex = draft.sex,
                    gender = draft.gender,
                    relationshipType = draft.relationshipType,
                    photoMediaId = photoMediaId,
                    birthDate = draft.birthDate,
                    ethnicity = draft.ethnicity,
                    height = draft.height.trim().ifBlank { null },
                    bodyType = draft.bodyType,
                    location = draft.location.trim().ifBlank { null },
                    archivedAt = existing?.archivedAt,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                ),
            )
            draft.captureTempFile?.delete() // plaintext camera temp, now encrypted into the blob
        }
    }

    fun archive(id: String) {
        viewModelScope.launch { repository.archive(id) }
    }

    suspend fun decodePhoto(photoMediaId: String, reqPx: Int): ImageBitmap? = MediaImages.decodeSampled(reqPx) { runCatching { repository.openPhoto(photoMediaId) }.getOrNull() }
}
