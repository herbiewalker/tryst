package app.tryst.core.prefs

import android.content.Context
import app.tryst.data.gallery.GalleryLayout
import app.tryst.data.gallery.GallerySort
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persisted Photos-gallery layout preference (GAL-1): the chosen [GalleryLayout], the grid column count,
 * and the [GallerySort] order. Not sensitive (no encounter data — just how tiles are arranged), so plain
 * SharedPreferences is fine, and like the theme/Insights prefs it's excluded from backup/transfer.
 * Exposed as StateFlows so both the gallery and its Settings section recompose live.
 */
@Singleton
class GalleryPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("tryst_gallery", Context.MODE_PRIVATE)

    private val _layout = MutableStateFlow(loadLayout())
    val layout: StateFlow<GalleryLayout> = _layout.asStateFlow()

    private val _columns = MutableStateFlow(prefs.getInt(KEY_COLUMNS, DEFAULT_COLUMNS).coerceIn(MIN_COLUMNS, MAX_COLUMNS))
    val columns: StateFlow<Int> = _columns.asStateFlow()

    private val _sort = MutableStateFlow(loadSort())
    val sort: StateFlow<GallerySort> = _sort.asStateFlow()

    /** When on, the Photos tab opens blurred behind a tap-to-reveal, so it never renders by accident (SEC-2). */
    private val _blurUntilRevealed = MutableStateFlow(prefs.getBoolean(KEY_BLUR, false))
    val blurUntilRevealed: StateFlow<Boolean> = _blurUntilRevealed.asStateFlow()

    fun setBlurUntilRevealed(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BLUR, enabled).apply()
        _blurUntilRevealed.value = enabled
    }

    fun setLayout(layout: GalleryLayout) {
        prefs.edit().putString(KEY_LAYOUT, layout.name).apply()
        _layout.value = layout
    }

    fun setColumns(columns: Int) {
        val clamped = columns.coerceIn(MIN_COLUMNS, MAX_COLUMNS)
        prefs.edit().putInt(KEY_COLUMNS, clamped).apply()
        _columns.value = clamped
    }

    fun setSort(sort: GallerySort) {
        prefs.edit().putString(KEY_SORT, sort.name).apply()
        _sort.value = sort
    }

    private fun loadLayout(): GalleryLayout = prefs.getString(KEY_LAYOUT, null)?.let { runCatching { GalleryLayout.valueOf(it) }.getOrNull() } ?: DEFAULT_LAYOUT

    private fun loadSort(): GallerySort = prefs.getString(KEY_SORT, null)?.let { runCatching { GallerySort.valueOf(it) }.getOrNull() } ?: DEFAULT_SORT

    companion object {
        val DEFAULT_LAYOUT = GalleryLayout.JUSTIFIED_DATE
        val DEFAULT_SORT = GallerySort.NEWEST
        const val MIN_COLUMNS = 2
        const val MAX_COLUMNS = 4
        const val DEFAULT_COLUMNS = 3
        private const val KEY_LAYOUT = "layout"
        private const val KEY_COLUMNS = "columns"
        private const val KEY_SORT = "sort"
        private const val KEY_BLUR = "blur_until_revealed"
    }
}
