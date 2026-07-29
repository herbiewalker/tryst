package app.tryst.ui.gallery

import androidx.lifecycle.ViewModel
import app.tryst.core.prefs.GalleryPreferences
import app.tryst.data.gallery.GalleryLayout
import app.tryst.data.gallery.GallerySort
import app.tryst.data.gallery.GridSpacing
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/** Backs the Settings → Gallery screen: every persistent look + behaviour preference for the Photos tab. */
@HiltViewModel
class GallerySettingsViewModel @Inject constructor(
    private val preferences: GalleryPreferences,
) : ViewModel() {
    val layout: StateFlow<GalleryLayout> = preferences.layout
    val columns: StateFlow<Int> = preferences.columns
    val sort: StateFlow<GallerySort> = preferences.sort
    val blurUntilRevealed: StateFlow<Boolean> = preferences.blurUntilRevealed
    val blurGraceSeconds: StateFlow<Int> = preferences.blurGraceSeconds
    val slideshowIntervalSeconds: StateFlow<Int> = preferences.slideshowIntervalSeconds
    val slideshowShuffle: StateFlow<Boolean> = preferences.slideshowShuffle
    val gridSpacing: StateFlow<GridSpacing> = preferences.gridSpacing
    val showTileCaptions: StateFlow<Boolean> = preferences.showTileCaptions
    val defaultToFavoritesOnly: StateFlow<Boolean> = preferences.defaultToFavoritesOnly
    val cameraKeepCapturing: StateFlow<Boolean> = preferences.cameraKeepCapturing

    fun setLayout(layout: GalleryLayout) = preferences.setLayout(layout)
    fun setColumns(columns: Int) = preferences.setColumns(columns)
    fun setSort(sort: GallerySort) = preferences.setSort(sort)
    fun setBlurUntilRevealed(enabled: Boolean) = preferences.setBlurUntilRevealed(enabled)
    fun setBlurGraceSeconds(seconds: Int) = preferences.setBlurGraceSeconds(seconds)
    fun setSlideshowIntervalSeconds(seconds: Int) = preferences.setSlideshowIntervalSeconds(seconds)
    fun setSlideshowShuffle(enabled: Boolean) = preferences.setSlideshowShuffle(enabled)
    fun setGridSpacing(value: GridSpacing) = preferences.setGridSpacing(value)
    fun setShowTileCaptions(enabled: Boolean) = preferences.setShowTileCaptions(enabled)
    fun setDefaultToFavoritesOnly(enabled: Boolean) = preferences.setDefaultToFavoritesOnly(enabled)
    fun setCameraKeepCapturing(enabled: Boolean) = preferences.setCameraKeepCapturing(enabled)
}
