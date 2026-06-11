package app.tryst.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.widthIn

/**
 * Material 3 window **width** size classes, used to drive Tryst's adaptive layouts (Pass 5).
 *
 * We derive these from [LocalConfiguration]'s `screenWidthDp` rather than pulling in the
 * `material3-window-size-class` artifact: it's a single value at the standard Material
 * breakpoints, it recomposes correctly on configuration change / fold-unfold (a fresh
 * Configuration is provided), and it keeps the dependency surface minimal — consistent with
 * the app's no-network, FOSS-lean ethos. Width alone drives every adaptive decision here
 * (rail vs. bottom bar, single- vs. two-pane), so the height class isn't modelled.
 */
enum class WidthClass { COMPACT, MEDIUM, EXPANDED }

/** Standard Material breakpoints: compact < 600dp, medium 600–839dp, expanded ≥ 840dp. */
@Composable
@ReadOnlyComposable
fun widthClass(): WidthClass {
    val w = LocalConfiguration.current.screenWidthDp
    return when {
        w < 600 -> WidthClass.COMPACT
        w < 840 -> WidthClass.MEDIUM
        else -> WidthClass.EXPANDED
    }
}

/**
 * Cap a single-column screen's content width so it doesn't stretch into long, hard-to-scan
 * lines on tablets / wide windows. A no-op on phones (their width is below [max]); pair with a
 * centering parent (e.g. a `Box` aligning `TopCenter`) so the capped content sits in the middle.
 */
fun Modifier.adaptiveContentWidth(max: Dp = 640.dp): Modifier = this.widthIn(max = max)
