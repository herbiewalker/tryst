package app.tryst.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tryst.data.db.entity.ActEntity
import app.tryst.data.repository.ActRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CustomActsViewModel @Inject constructor(
    private val repository: ActRepository,
) : ViewModel() {

    val customActs: StateFlow<List<ActEntity>> =
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
