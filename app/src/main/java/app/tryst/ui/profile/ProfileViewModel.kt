package app.tryst.ui.profile

import android.content.Context
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tryst.core.session.SessionManager
import app.tryst.data.db.entity.BodyType
import app.tryst.data.db.entity.Ethnicity
import app.tryst.data.db.entity.Gender
import app.tryst.data.db.entity.ProfileEntity
import app.tryst.data.db.entity.Sex
import app.tryst.data.repository.ProfileRepository
import app.tryst.ui.common.MediaImages
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Everything the self-profile editor collects, passed to [ProfileViewModel.save] in one shot. */
data class ProfileDraft(
    val displayName: String,
    val sex: Sex?,
    val gender: Gender?,
    val birthDate: Long?,
    val ethnicity: Ethnicity?,
    val height: String,
    val bodyType: BodyType?,
    val location: String,
    val note: String,
    val newPhotoUri: Uri?,
    val removePhoto: Boolean,
    val captureTempFile: File?,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val session: SessionManager,
    private val repository: ProfileRepository,
) : ViewModel() {

    /** Keep the app unlocked across the photo-picker/camera handoff. */
    fun suppressAutoLock() = session.suppressNextAutoLock()

    /** The single self row, or null until loaded / if never set. */
    val profile: StateFlow<ProfileEntity?> =
        repository.observe()
            .catch { emit(null) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun save(draft: ProfileDraft, onDone: () -> Unit) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val existing = repository.get()
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
                ProfileEntity(
                    id = ProfileEntity.SELF_ID,
                    displayName = draft.displayName.trim().ifBlank { null },
                    photoMediaId = photoMediaId,
                    sex = draft.sex,
                    gender = draft.gender,
                    birthDate = draft.birthDate,
                    ethnicity = draft.ethnicity,
                    height = draft.height.trim().ifBlank { null },
                    bodyType = draft.bodyType,
                    location = draft.location.trim().ifBlank { null },
                    note = draft.note.trim().ifBlank { null },
                    updatedAt = now,
                ),
            )
            draft.captureTempFile?.delete() // plaintext camera temp, now encrypted into the blob
            onDone()
        }
    }

    suspend fun decodePhoto(photoMediaId: String, reqPx: Int): ImageBitmap? = MediaImages.decodeSampled(reqPx) { runCatching { repository.openPhoto(photoMediaId) }.getOrNull() }
}
