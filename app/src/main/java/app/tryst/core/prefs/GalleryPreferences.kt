// SPDX-License-Identifier: GPL-3.0-or-later
package app.tryst.core.prefs

import android.content.Context
import app.tryst.data.gallery.GalleryLayout
import app.tryst.data.gallery.GallerySort
import app.tryst.data.gallery.GridSpacing
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

    /**
     * How long a reveal survives after the user leaves the Photos tab before it re-blurs (seconds).
     * `0` re-blurs the instant you leave; a bigger value stays revealed for that long across quick tab
     * switches. Only meaningful when [blurUntilRevealed] is on.
     */
    private val _blurGraceSeconds = MutableStateFlow(
        prefs.getInt(KEY_BLUR_GRACE_S, DEFAULT_BLUR_GRACE_S).coerceIn(0, MAX_BLUR_GRACE_S),
    )
    val blurGraceSeconds: StateFlow<Int> = _blurGraceSeconds.asStateFlow()

    fun setBlurGraceSeconds(seconds: Int) {
        val clamped = seconds.coerceIn(0, MAX_BLUR_GRACE_S)
        prefs.edit().putInt(KEY_BLUR_GRACE_S, clamped).apply()
        _blurGraceSeconds.value = clamped
    }

    /** How many seconds between slides during a viewer slideshow. */
    private val _slideshowIntervalSeconds = MutableStateFlow(prefs.getInt(KEY_SLIDESHOW_INT_S, DEFAULT_SLIDESHOW_INT_S))
    val slideshowIntervalSeconds: StateFlow<Int> = _slideshowIntervalSeconds.asStateFlow()

    fun setSlideshowIntervalSeconds(seconds: Int) {
        prefs.edit().putInt(KEY_SLIDESHOW_INT_S, seconds.coerceAtLeast(1)).apply()
        _slideshowIntervalSeconds.value = seconds.coerceAtLeast(1)
    }

    /** When on, the slideshow advances in a shuffled order (no repeats until every photo has played). */
    private val _slideshowShuffle = MutableStateFlow(prefs.getBoolean(KEY_SLIDESHOW_SHUFFLE, false))
    val slideshowShuffle: StateFlow<Boolean> = _slideshowShuffle.asStateFlow()

    fun setSlideshowShuffle(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SLIDESHOW_SHUFFLE, enabled).apply()
        _slideshowShuffle.value = enabled
    }

    /** How much room to leave between tiles in grid/mosaic layouts (feed is untouched). */
    private val _gridSpacing = MutableStateFlow(loadGridSpacing())
    val gridSpacing: StateFlow<GridSpacing> = _gridSpacing.asStateFlow()

    fun setGridSpacing(value: GridSpacing) {
        prefs.edit().putString(KEY_GRID_SPACING, value.name).apply()
        _gridSpacing.value = value
    }

    /** When on, grid/mosaic tiles get a small date · partner caption below each thumbnail. */
    private val _showTileCaptions = MutableStateFlow(prefs.getBoolean(KEY_TILE_CAPTIONS, false))
    val showTileCaptions: StateFlow<Boolean> = _showTileCaptions.asStateFlow()

    fun setShowTileCaptions(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TILE_CAPTIONS, enabled).apply()
        _showTileCaptions.value = enabled
    }

    /**
     * When on, the Photos tab opens with the favourites-only filter enabled. Turning the app-bar heart off
     * during a session doesn't clobber the pref — the pref just re-applies on fresh VM construction.
     */
    private val _defaultToFavoritesOnly = MutableStateFlow(prefs.getBoolean(KEY_DEFAULT_FAV_ONLY, false))
    val defaultToFavoritesOnly: StateFlow<Boolean> = _defaultToFavoritesOnly.asStateFlow()

    fun setDefaultToFavoritesOnly(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DEFAULT_FAV_ONLY, enabled).apply()
        _defaultToFavoritesOnly.value = enabled
    }

    /**
     * When on, the encounter editor's in-app camera auto-relaunches after each successful capture,
     * so you can take several partner photos in a row and end by tapping the camera's back button.
     */
    private val _cameraKeepCapturing = MutableStateFlow(prefs.getBoolean(KEY_CAMERA_LOOP, false))
    val cameraKeepCapturing: StateFlow<Boolean> = _cameraKeepCapturing.asStateFlow()

    fun setCameraKeepCapturing(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CAMERA_LOOP, enabled).apply()
        _cameraKeepCapturing.value = enabled
    }

    /** Where the per-photo caption editor is reachable from in the viewer (CAP-1, D-55). */
    private val _captionEntryPoint = MutableStateFlow(loadCaptionEntryPoint())
    val captionEntryPoint: StateFlow<CaptionEntryPoint> = _captionEntryPoint.asStateFlow()

    fun setCaptionEntryPoint(value: CaptionEntryPoint) {
        prefs.edit().putString(KEY_CAPTION_ENTRY, value.name).apply()
        _captionEntryPoint.value = value
    }

    private fun loadCaptionEntryPoint(): CaptionEntryPoint = prefs.getString(KEY_CAPTION_ENTRY, null)
        ?.let { runCatching { CaptionEntryPoint.valueOf(it) }.getOrNull() }
        ?: DEFAULT_CAPTION_ENTRY_POINT

    /**
     * Require a biometric/device-credential re-auth to enter the Photos tab (SEC-2 tier 2). Off by
     * default; the primary app lock already covers the phone-lost/stolen threat, this is the
     * stronger opt-in for "phone unlocked in someone's hand." Paired with [reauthGraceSeconds] so a
     * quick tab switch away and back doesn't re-prompt.
     */
    private val _requireReauthForPhotos = MutableStateFlow(prefs.getBoolean(KEY_REAUTH, false))
    val requireReauthForPhotos: StateFlow<Boolean> = _requireReauthForPhotos.asStateFlow()

    fun setRequireReauthForPhotos(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_REAUTH, enabled).apply()
        _requireReauthForPhotos.value = enabled
    }

    /** How long a re-auth stays valid after leaving the Photos tab, in seconds (SEC-2 tier 2). */
    private val _reauthGraceSeconds = MutableStateFlow(
        prefs.getInt(KEY_REAUTH_GRACE_S, DEFAULT_REAUTH_GRACE_S).coerceIn(0, MAX_BLUR_GRACE_S),
    )
    val reauthGraceSeconds: StateFlow<Int> = _reauthGraceSeconds.asStateFlow()

    fun setReauthGraceSeconds(seconds: Int) {
        val clamped = seconds.coerceIn(0, MAX_BLUR_GRACE_S)
        prefs.edit().putInt(KEY_REAUTH_GRACE_S, clamped).apply()
        _reauthGraceSeconds.value = clamped
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

    private fun loadGridSpacing(): GridSpacing = prefs.getString(KEY_GRID_SPACING, null)?.let { runCatching { GridSpacing.valueOf(it) }.getOrNull() } ?: DEFAULT_GRID_SPACING

    companion object {
        val DEFAULT_LAYOUT = GalleryLayout.JUSTIFIED_DATE
        val DEFAULT_SORT = GallerySort.NEWEST
        val DEFAULT_GRID_SPACING = GridSpacing.NORMAL
        val DEFAULT_CAPTION_ENTRY_POINT = CaptionEntryPoint.BOTH
        const val MIN_COLUMNS = 2
        const val MAX_COLUMNS = 4
        const val DEFAULT_COLUMNS = 3
        const val DEFAULT_BLUR_GRACE_S = 30
        const val DEFAULT_REAUTH_GRACE_S = 30
        const val MAX_BLUR_GRACE_S = 3600
        const val DEFAULT_SLIDESHOW_INT_S = 3
        private const val KEY_LAYOUT = "layout"
        private const val KEY_COLUMNS = "columns"
        private const val KEY_SORT = "sort"
        private const val KEY_BLUR = "blur_until_revealed"
        private const val KEY_BLUR_GRACE_S = "blur_grace_seconds"
        private const val KEY_SLIDESHOW_INT_S = "slideshow_interval_seconds"
        private const val KEY_SLIDESHOW_SHUFFLE = "slideshow_shuffle"
        private const val KEY_GRID_SPACING = "grid_spacing"
        private const val KEY_TILE_CAPTIONS = "show_tile_captions"
        private const val KEY_DEFAULT_FAV_ONLY = "default_to_favorites_only"
        private const val KEY_CAMERA_LOOP = "camera_keep_capturing"
        private const val KEY_CAPTION_ENTRY = "caption_entry_point"
        private const val KEY_REAUTH = "require_reauth_for_photos"
        private const val KEY_REAUTH_GRACE_S = "reauth_grace_seconds"
    }
}

/**
 * Where the per-photo caption editor lives in the viewer (CAP-1, D-55). Users pick their favourite —
 * the top-row icon is fastest, the info-panel field is quieter, both is discoverable, off hides captions
 * entirely (they still persist and still fold into search — just no viewer surface to add/edit them).
 */
enum class CaptionEntryPoint {
    /** A "Caption" field at the top of the (i) info panel; tap opens the edit dialog. */
    INFO_PANEL,

    /** A dedicated icon in the viewer's top action row; tap opens the edit dialog directly. */
    TOP_ROW,

    /** Both entry points are shown (the default). */
    BOTH,

    /** No caption UI at all — existing captions still ride in the backup + still fold into search. */
    OFF,
    ;

    val showsInfoPanelField: Boolean get() = this == INFO_PANEL || this == BOTH
    val showsTopRowButton: Boolean get() = this == TOP_ROW || this == BOTH
    val anyEntryVisible: Boolean get() = this != OFF
}
