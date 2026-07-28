package app.tryst.ui.gallery

import androidx.lifecycle.ViewModel
import app.tryst.core.prefs.GalleryPreferences
import app.tryst.data.gallery.GalleryLayout
import app.tryst.data.gallery.GallerySort
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/** Backs the Settings → Gallery screen: the persistent layout/density/sort preferences (GAL-1). */
@HiltViewModel
class GallerySettingsViewModel @Inject constructor(
    private val preferences: GalleryPreferences,
) : ViewModel() {
    val layout: StateFlow<GalleryLayout> = preferences.layout
    val columns: StateFlow<Int> = preferences.columns
    val sort: StateFlow<GallerySort> = preferences.sort

    fun setLayout(layout: GalleryLayout) = preferences.setLayout(layout)
    fun setColumns(columns: Int) = preferences.setColumns(columns)
    fun setSort(sort: GallerySort) = preferences.setSort(sort)
}
