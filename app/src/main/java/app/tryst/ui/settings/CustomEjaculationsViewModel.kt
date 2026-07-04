package app.tryst.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tryst.data.db.entity.EjaculationLocationEntity
import app.tryst.data.repository.EjaculationLocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class CustomEjaculationsViewModel @Inject constructor(
    private val repository: EjaculationLocationRepository,
) : ViewModel() {

    val customEjaculations: StateFlow<List<EjaculationLocationEntity>> =
        repository.observeCustom()
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun add(label: String) {
        viewModelScope.launch { repository.addCustom(label) }
    }

    fun rename(id: String, label: String) {
        viewModelScope.launch { repository.rename(id, label) }
    }

    fun delete(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }
}
