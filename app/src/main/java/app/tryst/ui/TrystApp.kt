package app.tryst.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.tryst.ui.achievements.AchievementsScreen
import app.tryst.ui.encounter.EncounterEditScreen
import app.tryst.ui.history.HistoryScreen
import app.tryst.ui.insights.InsightsScreen
import app.tryst.ui.partner.PartnersScreen
import app.tryst.ui.settings.SettingsScreen

private object Routes {
    const val HISTORY = "history"
    const val INSIGHTS = "insights"
    const val INSIGHTS_CUSTOMIZE = "insights/customize"
    const val ACHIEVEMENTS = "achievements"
    const val PARTNERS = "partners"
    const val SETTINGS = "settings"
    const val ENCOUNTER_NEW = "encounter/new"
    const val ENCOUNTER_EDIT = "encounter/{encounterId}"
    fun encounterEdit(id: String) = "encounter/$id"
}

private data class TopDestination(
    val route: String,
    val icon: ImageVector,
    val label: String,
)

private val topDestinations = listOf(
    TopDestination(Routes.HISTORY, Icons.Filled.Favorite, "Trysts"),
    TopDestination(Routes.INSIGHTS, Icons.Filled.Insights, "Insights"),
    TopDestination(Routes.PARTNERS, Icons.Filled.People, "Partners"),
    TopDestination(Routes.SETTINGS, Icons.Filled.Settings, "Settings"),
)

// Two-pane (list/detail) split ratios on expanded width. The list stays compact; the detail form /
// achievements list gets the larger share.
private const val LIST_WEIGHT = 0.42f
private const val DETAIL_WEIGHT = 0.58f

// Selection encoding for the two-pane detail (rememberSaveable-friendly String). A nonce on "new"
// makes each fresh entry a distinct key, so re-tapping + after a save starts a clean form.
private const val EDIT_PREFIX = "edit:"
private const val NEW_PREFIX = "new:"

/**
 * The unlocked app. An adaptive shell:
 *  - **compact** width → bottom [NavigationBar], single-pane screens (full container-transform morph
 *    from a history card / the + FAB into the editor).
 *  - **medium** width → side [NavigationRail], single-pane screens centered in a readable lane so they
 *    don't stretch.
 *  - **expanded** width → side [NavigationRail] + genuine two-pane list/detail (Trysts ↔ editor,
 *    Insights ↔ Achievements).
 *
 * State survives fold/rotate/resize: the size class is recomputed by the caller, ViewModels outlive
 * configuration changes, and pane selection is held in [rememberSaveable].
 */
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun TrystApp(widthSizeClass: WindowWidthSizeClass) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showNav = currentRoute in topDestinations.map { it.route }

    // Medium + expanded get a side rail; compact keeps the bottom bar. Only expanded is wide enough
    // for two panes side by side.
    val useRail = widthSizeClass != WindowWidthSizeClass.Compact
    val twoPane = widthSizeClass == WindowWidthSizeClass.Expanded

    Row(Modifier.fillMaxSize()) {
        if (showNav && useRail) {
            // The rail owns its start/top/bottom system-bar insets; inner screens own the top/end.
            NavigationRail {
                topDestinations.forEach { dest ->
                    NavigationRailItem(
                        selected = currentRoute == dest.route,
                        onClick = { navController.navigateTopLevel(dest.route) },
                        icon = { Icon(dest.icon, contentDescription = dest.label) },
                        label = { Text(dest.label) },
                    )
                }
            }
        }
        Scaffold(
            // Fill the width left of/beside the optional rail.
            modifier = Modifier.weight(1f).fillMaxHeight(),
            // The bottom NavigationBar (compact) consumes the navigation-bar inset itself; each inner
            // screen's Scaffold + TopAppBar consumes the status-bar inset (drawing under it edge-to-edge).
            // So the shell adds no insets of its own — it only forwards the bottom-bar height via
            // consumeWindowInsets below, which keeps the per-screen Scaffolds from double-padding.
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (showNav && !useRail) {
                    NavigationBar {
                        topDestinations.forEach { dest ->
                            NavigationBarItem(
                                selected = currentRoute == dest.route,
                                onClick = { navController.navigateTopLevel(dest.route) },
                                icon = { Icon(dest.icon, contentDescription = dest.label) },
                                label = { Text(dest.label) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            // One SharedTransitionLayout spanning every destination so a card / FAB on the list can
            // morph into the encounter editor (container transform) and back, in single-pane mode.
            // Destinations cross-fade; the fades are also what the predictive-back gesture seeks through.
            SharedTransitionLayout {
                NavHost(
                    navController = navController,
                    startDestination = Routes.HISTORY,
                    modifier = Modifier.padding(padding).consumeWindowInsets(padding),
                    enterTransition = { fadeIn(tween(220)) },
                    exitTransition = { fadeOut(tween(180)) },
                    popEnterTransition = { fadeIn(tween(220)) },
                    popExitTransition = { fadeOut(tween(180)) },
                ) {
                    composable(Routes.HISTORY) {
                        HistoryPane(
                            twoPane = twoPane,
                            widthSizeClass = widthSizeClass,
                            navController = navController,
                            sharedScope = this@SharedTransitionLayout,
                            animatedScope = this,
                        )
                    }
                    composable(Routes.INSIGHTS) {
                        InsightsPane(twoPane = twoPane, widthSizeClass = widthSizeClass, navController = navController)
                    }
                    composable(Routes.INSIGHTS_CUSTOMIZE) {
                        CenteredPane(widthSizeClass) {
                            InsightsScreen(
                                startInEditMode = true,
                                onBack = { navController.popBackStack() },
                            )
                        }
                    }
                    composable(Routes.ACHIEVEMENTS) {
                        CenteredPane(widthSizeClass) {
                            AchievementsScreen(onBack = { navController.popBackStack() })
                        }
                    }
                    composable(Routes.PARTNERS) {
                        CenteredPane(widthSizeClass) { PartnersScreen() }
                    }
                    composable(Routes.SETTINGS) {
                        CenteredPane(widthSizeClass) {
                            SettingsScreen(onCustomizeInsights = { navController.navigate(Routes.INSIGHTS_CUSTOMIZE) })
                        }
                    }
                    composable(Routes.ENCOUNTER_NEW) {
                        CenteredPane(widthSizeClass) {
                            EncounterEditScreen(
                                encounterId = null,
                                onClose = { navController.popBackStack() },
                                sharedScope = this@SharedTransitionLayout,
                                animatedScope = this,
                            )
                        }
                    }
                    composable(Routes.ENCOUNTER_EDIT) { entry ->
                        CenteredPane(widthSizeClass) {
                            EncounterEditScreen(
                                encounterId = entry.arguments?.getString("encounterId"),
                                onClose = { navController.popBackStack() },
                                sharedScope = this@SharedTransitionLayout,
                                animatedScope = this,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Trysts list ↔ encounter editor. Two-pane on expanded width; otherwise the existing morph flow. */
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
private fun HistoryPane(
    twoPane: Boolean,
    widthSizeClass: WindowWidthSizeClass,
    navController: NavController,
    sharedScope: SharedTransitionScope,
    animatedScope: AnimatedVisibilityScope,
) {
    if (!twoPane) {
        CenteredPane(widthSizeClass) {
            HistoryScreen(
                onAddEncounter = { navController.navigate(Routes.ENCOUNTER_NEW) },
                onOpenEncounter = { id -> navController.navigate(Routes.encounterEdit(id)) },
                sharedScope = sharedScope,
                animatedScope = animatedScope,
            )
        }
        return
    }

    // null = nothing selected; "edit:<id>" or "new:<nonce>" otherwise. The detail VM is keyed on this
    // string so each selection (including a fresh "new") gets a clean editor.
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var newNonce by rememberSaveable { mutableStateOf(0) }

    Row(Modifier.fillMaxSize()) {
        Box(Modifier.weight(LIST_WEIGHT).fillMaxHeight()) {
            HistoryScreen(
                onAddEncounter = { newNonce += 1; selected = "$NEW_PREFIX$newNonce" },
                onOpenEncounter = { id -> selected = "$EDIT_PREFIX$id" },
                // No container-transform morph in two-pane — the detail is already on screen.
                sharedScope = null,
                animatedScope = null,
            )
        }
        VerticalDivider()
        Box(Modifier.weight(DETAIL_WEIGHT).fillMaxHeight()) {
            val sel = selected
            if (sel == null) {
                DetailPlaceholder(
                    title = "No tryst selected",
                    body = "Pick an entry on the left, or tap + to log a new one.",
                )
            } else {
                key(sel) {
                    EncounterEditScreen(
                        encounterId = if (sel.startsWith(EDIT_PREFIX)) sel.removePrefix(EDIT_PREFIX) else null,
                        onClose = { selected = null },
                        sharedScope = null,
                        animatedScope = null,
                        viewModel = hiltViewModel(key = sel),
                    )
                }
            }
        }
    }
}

/** Insights dashboard ↔ Achievements. Side-by-side on expanded width; otherwise single-pane. */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
private fun InsightsPane(
    twoPane: Boolean,
    widthSizeClass: WindowWidthSizeClass,
    navController: NavController,
) {
    if (!twoPane) {
        CenteredPane(widthSizeClass) {
            InsightsScreen(onOpenAchievements = { navController.navigate(Routes.ACHIEVEMENTS) })
        }
        return
    }
    Row(Modifier.fillMaxSize()) {
        Box(Modifier.weight(0.5f).fillMaxHeight()) {
            // twoPane hides the now-redundant "Achievements" affordances (the list sits in the next pane).
            InsightsScreen(twoPane = true)
        }
        VerticalDivider()
        Box(Modifier.weight(0.5f).fillMaxHeight()) {
            AchievementsScreen(onBack = {}, showBack = false)
        }
    }
}

/**
 * Centers single-pane content in a readable max-width lane on medium/expanded width so it doesn't
 * stretch across a tablet; on compact it's a transparent pass-through (full width, morph-friendly).
 */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
private fun CenteredPane(
    widthSizeClass: WindowWidthSizeClass,
    maxWidth: Dp = 840.dp,
    content: @Composable () -> Unit,
) {
    if (widthSizeClass == WindowWidthSizeClass.Compact) {
        content()
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.widthIn(max = maxWidth).fillMaxSize()) { content() }
        }
    }
}

@Composable
private fun DetailPlaceholder(title: String, body: String) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Single-top navigation to a bottom/rail destination, preserving each tab's saved back stack. */
private fun NavController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
