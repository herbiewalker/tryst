package app.tryst.ui.partner

import android.content.Context
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tryst.data.db.entity.Gender
import app.tryst.data.db.entity.PartnerEntity
import app.tryst.data.db.entity.RelationshipType
import app.tryst.data.db.entity.Sex
import app.tryst.data.repository.PartnerRepository
import app.tryst.ui.common.MediaImages
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class PartnersViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: PartnerRepository,
) : ViewModel() {

    val partners: StateFlow<List<PartnerEntity>> =
        repository.observeActive()
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(
        id: String?,
        name: String,
        anonymous: Boolean,
        note: String,
        sex: Sex?,
        gender: Gender?,
        relationshipType: RelationshipType?,
        newPhotoUri: Uri?,
        removePhoto: Boolean,
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val existing = id?.let { repository.getById(it) }
            val oldPhotoId = existing?.photoMediaId
            val photoMediaId = when {
                newPhotoUri != null -> {
                    val newId = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(newPhotoUri)?.use { repository.savePhoto(it) }
                    }
                    if (newId != null && oldPhotoId != null) repository.deletePhoto(oldPhotoId)
                    newId ?: oldPhotoId
                }
                removePhoto -> {
                    oldPhotoId?.let { repository.deletePhoto(it) }
                    null
                }
                else -> oldPhotoId
            }
            repository.upsert(
                PartnerEntity(
                    id = id ?: UUID.randomUUID().toString(),
                    displayName = if (anonymous) null else name.trim().ifBlank { null },
                    isAnonymous = anonymous,
                    color = existing?.color,
                    note = note.trim().ifBlank { null },
                    sex = sex,
                    gender = gender,
                    relationshipType = relationshipType,
                    photoMediaId = photoMediaId,
                    archivedAt = existing?.archivedAt,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                ),
            )
        }
    }

    fun archive(id: String) {
        viewModelScope.launch { repository.archive(id) }
    }

    suspend fun decodePhoto(photoMediaId: String, reqPx: Int): ImageBitmap? =
        MediaImages.decodeSampled(reqPx) { runCatching { repository.openPhoto(photoMediaId) }.getOrNull() }
}
