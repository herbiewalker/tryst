package app.tryst.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tryst.data.db.entity.PositionEntity
import app.tryst.data.repository.PositionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class CustomPositionsViewModel @Inject constructor(
    private val repository: PositionRepository,
) : ViewModel() {

    val customPositions: StateFlow<List<PositionEntity>> =
        repository.observeCustom()
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun add(label: String) {
        viewModelScope.launch { repository.addCustom(label) }
    }

    fun delete(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }
}
