package app.tryst.ui.achievements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tryst.data.achievements.AchievementEngine
import app.tryst.data.achievements.AchievementStatus
import app.tryst.data.achievements.AchievementSummary
import app.tryst.data.repository.EncounterRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

@HiltViewModel
class AchievementsViewModel @Inject constructor(
    encounterRepository: EncounterRepository,
) : ViewModel() {

    val achievements: StateFlow<List<AchievementStatus>> =
        encounterRepository.observeAll()
            .map { encounters ->
                // Replaying the whole log per achievement; keep it off the main thread.
                withContext(Dispatchers.Default) { AchievementEngine.evaluate(encounters) }
            }
            // The DB closes on lock; swallow the resulting error so we don't crash mid-teardown.
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Compact rollup for the Insights teaser card. */
    val summary: StateFlow<AchievementSummary> =
        achievements
            .map { withContext(Dispatchers.Default) { AchievementEngine.summarize(it) } }
            .catch { emit(AchievementSummary(0, 0, emptyList(), emptyList())) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                AchievementSummary(0, 0, emptyList(), emptyList()),
            )
}
