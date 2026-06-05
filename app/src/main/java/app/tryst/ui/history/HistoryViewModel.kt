package app.tryst.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tryst.data.db.relation.EncounterWithDetails
import app.tryst.data.repository.EncounterRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    repository: EncounterRepository,
) : ViewModel() {

    val encounters: StateFlow<List<EncounterWithDetails>> =
        repository.observeAll()
            // The DB closes on lock; swallow the resulting error so we don't crash mid-teardown.
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
