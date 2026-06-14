package app.tryst.ui

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.tryst.R
import app.tryst.ui.about.AboutScreen
import app.tryst.ui.achievements.AchievementsScreen
import app.tryst.ui.common.AppVersion
import app.tryst.ui.common.WidthClass
import app.tryst.ui.common.widthClass
import app.tryst.ui.encounter.EncounterEditScreen
import app.tryst.ui.history.HistoryScreen
import app.tryst.ui.insights.InsightsScreen
import app.tryst.ui.lock.ChangePinScreen
import app.tryst.ui.partner.PartnersScreen
import app.tryst.ui.profile.ProfileScreen
import app.tryst.ui.settings.GeneralSettingsViewModel
import app.tryst.ui.settings.ResetDataScreen
import app.tryst.ui.settings.SettingsScreen
import app.tryst.ui.whatsnew.ReleaseNote
import app.tryst.ui.whatsnew.ReleaseNotes
import app.tryst.ui.whatsnew.WhatsNewDialog
import app.tryst.ui.whatsnew.WhatsNewScreen

private object Routes {
    const val HISTORY = "history"
    const val INSIGHTS = "insights"
    const val INSIGHTS_CUSTOMIZE = "insights/customize"
    const val ACHIEVEMENTS = "achievements"
    const val PARTNERS = "partners"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val CHANGE_PIN = "change-pin"
    const val RESET = "settings/reset"
    const val WHATS_NEW = "whats-new"
    const val PROFILE = "profile"
    const val ENCOUNTER_NEW = "encounter/new"
    const val ENCOUNTER_EDIT = "encounter/{encounterId}"
    fun encounterEdit(id: String) = "encounter/$id"
}

private data class TopDestination(
    val route: String,
    val icon: ImageVector,
    @param:StringRes val labelRes: Int,
)

private val topDestinations = listOf(
    TopDestination(Routes.HISTORY, Icons.Filled.Favorite, R.string.nav_trysts),
    TopDestination(Routes.INSIGHTS, Icons.Filled.Insights, R.string.nav_insights),
    TopDestination(Routes.PARTNERS, Icons.Filled.People, R.string.nav_partners),
    TopDestination(Routes.SETTINGS, Icons.Filled.Settings, R.string.nav_settings),
)

/**
 * The unlocked app: an adaptive nav shell over Trysts / Insights / Partners / Settings, plus the editor.
 *
 * Pass 5 (adaptive layouts) drives the chrome off the window **width** class:
 * - **Compact** (phone): bottom [NavigationBar]; History → editor is a full-screen destination
 *   with the card/FAB shared-element container transform.
 * - **Medium** (small tablet / unfolded foldable): a side [NavigationRail] replaces the bottom bar.
 * - **Expanded** (large tablet / wide window): rail **plus** a two-pane list/detail on History —
 *   the tryst list and the encounter editor sit side by side instead of navigating.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun TrystApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isTopDestination = currentRoute in topDestinations.map { it.route }

    val width = widthClass()
    val useRail = width != WidthClass.COMPACT
    val showBottomBar = isTopDestination && !useRail
    val showRail = isTopDestination && useRail

    // First launch after an update: surface the new version's notes once. A fresh install (lastSeen 0)
    // shows nothing — there's no prior version to announce — it just records the current code.
    val context = LocalContext.current
    val generalViewModel: GeneralSettingsViewModel = hiltViewModel()
    var whatsNewNotes by remember { mutableStateOf<List<ReleaseNote>>(emptyList()) }
    LaunchedEffect(Unit) {
        val lastSeen = generalViewModel.lastSeenVersionCode()
        val current = AppVersion.code(context)
        if (lastSeen != 0L && current > lastSeen) {
            whatsNewNotes = ReleaseNotes.since(lastSeen)
        }
        generalViewModel.markVersionSeen(current)
    }

    Scaffold(
        // The bottom NavigationBar / side NavigationRail consume the system-bar insets on their own
        // edge; each inner screen's Scaffold + TopAppBar consumes the status-bar inset (drawing under
        // it edge-to-edge). So the shell adds no insets of its own — it only forwards the bottom-bar
        // height via consumeWindowInsets below, which keeps the per-screen Scaffolds from double-padding.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    topDestinations.forEach { dest ->
                        val label = stringResource(dest.labelRes)
                        NavigationBarItem(
                            selected = currentRoute == dest.route,
                            onClick = { navController.navigateTop(dest.route) },
                            icon = { Icon(dest.icon, contentDescription = label) },
                            label = { Text(label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Row(Modifier.padding(padding).consumeWindowInsets(padding)) {
            if (showRail) {
                NavigationRail {
                    topDestinations.forEach { dest ->
                        val label = stringResource(dest.labelRes)
                        NavigationRailItem(
                            selected = currentRoute == dest.route,
                            onClick = { navController.navigateTop(dest.route) },
                            icon = { Icon(dest.icon, contentDescription = label) },
                            label = { Text(label) },
                        )
                    }
                }
            }

            // One SharedTransitionLayout spanning every destination so a card / FAB on the list can
            // morph into the encounter editor (container transform) and back. Destinations cross-fade;
            // the editor's shared container drives the headline motion. The fades are also what the
            // system's predictive-back gesture seeks through as you swipe to dismiss.
            SharedTransitionLayout(Modifier.weight(1f).fillMaxHeight()) {
                NavHost(
                    navController = navController,
                    startDestination = Routes.HISTORY,
                    modifier = Modifier.fillMaxSize(),
                    enterTransition = { fadeIn(tween(220)) },
                    exitTransition = { fadeOut(tween(180)) },
                    popEnterTransition = { fadeIn(tween(220)) },
                    popExitTransition = { fadeOut(tween(180)) },
                ) {
                    composable(Routes.HISTORY) {
                        if (width == WidthClass.EXPANDED) {
                            HistoryTwoPane(
                                sharedScope = this@SharedTransitionLayout,
                                animatedScope = this,
                            )
                        } else {
                            HistoryScreen(
                                onAddEncounter = { navController.navigate(Routes.ENCOUNTER_NEW) },
                                onOpenEncounter = { id -> navController.navigate(Routes.encounterEdit(id)) },
                                sharedScope = this@SharedTransitionLayout,
                                animatedScope = this,
                            )
                        }
                    }
                    composable(Routes.INSIGHTS) {
                        InsightsScreen(onOpenAchievements = { navController.navigate(Routes.ACHIEVEMENTS) })
                    }
                    composable(Routes.INSIGHTS_CUSTOMIZE) {
                        InsightsScreen(
                            startInEditMode = true,
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable(Routes.ACHIEVEMENTS) {
                        AchievementsScreen(onBack = { navController.popBackStack() })
                    }
                    composable(Routes.PARTNERS) {
                        PartnersScreen(onOpenProfile = { navController.navigate(Routes.PROFILE) })
                    }
                    composable(Routes.SETTINGS) {
                        SettingsScreen(
                            onCustomizeInsights = { navController.navigate(Routes.INSIGHTS_CUSTOMIZE) },
                            onOpenAbout = { navController.navigate(Routes.ABOUT) },
                            onChangePin = { navController.navigate(Routes.CHANGE_PIN) },
                            onOpenReset = { navController.navigate(Routes.RESET) },
                            onOpenWhatsNew = { navController.navigate(Routes.WHATS_NEW) },
                            onOpenProfile = { navController.navigate(Routes.PROFILE) },
                        )
                    }
                    composable(Routes.ABOUT) {
                        AboutScreen(onBack = { navController.popBackStack() })
                    }
                    composable(Routes.RESET) {
                        ResetDataScreen(onBack = { navController.popBackStack() })
                    }
                    composable(Routes.WHATS_NEW) {
                        WhatsNewScreen(onBack = { navController.popBackStack() })
                    }
                    composable(Routes.PROFILE) {
                        ProfileScreen(onBack = { navController.popBackStack() })
                    }
                    composable(Routes.CHANGE_PIN) {
                        ChangePinScreen(onClose = { navController.popBackStack() })
                    }
                    composable(Routes.ENCOUNTER_NEW) {
                        EncounterEditScreen(
                            encounterId = null,
                            onClose = { navController.popBackStack() },
                            sharedScope = this@SharedTransitionLayout,
                            animatedScope = this,
                        )
                    }
                    composable(Routes.ENCOUNTER_EDIT) { entry ->
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

    if (whatsNewNotes.isNotEmpty()) {
        WhatsNewDialog(notes = whatsNewNotes, onDismiss = { whatsNewNotes = emptyList() })
    }
}

/**
 * Expanded-width two-pane for History: the tryst list on the left, the encounter editor on the right.
 * Selecting a card (or +) fills the detail pane instead of navigating, so list and detail stay visible
 * together. The selection is [rememberSaveable] so it survives configuration change / fold-unfold.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun HistoryTwoPane(
    sharedScope: SharedTransitionScope,
    animatedScope: AnimatedVisibilityScope,
) {
    // detailId == null && !detailNew → nothing selected (placeholder). detailNew → blank "new" form.
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    var detailNew by rememberSaveable { mutableStateOf(false) }

    Row(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxHeight()) {
            HistoryScreen(
                onAddEncounter = {
                    detailNew = true
                    detailId = null
                },
                onOpenEncounter = {
                    detailId = it
                    detailNew = false
                },
                sharedScope = sharedScope,
                animatedScope = animatedScope,
            )
        }
        VerticalDivider()
        Box(Modifier.weight(1f).fillMaxHeight()) {
            if (detailNew || detailId != null) {
                // key() so each selection starts the editor's local UI state (dialogs, scroll) fresh.
                androidx.compose.runtime.key(detailNew, detailId) {
                    EncounterEditScreen(
                        encounterId = detailId,
                        onClose = {
                            detailNew = false
                            detailId = null
                        },
                        sharedScope = null,
                        animatedScope = null,
                    )
                }
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.history_detail_placeholder),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Tab navigation with the standard single-top / restore-state behaviour. */
private fun NavController.navigateTop(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
