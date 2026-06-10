package app.tryst.ui

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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

/** The unlocked app: bottom-nav shell over Trysts / Insights / Partners / Settings, plus the editor. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun TrystApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in topDestinations.map { it.route }

    Scaffold(
        // The bottom NavigationBar consumes the navigation-bar inset itself; each inner screen's
        // Scaffold + TopAppBar consumes the status-bar inset (drawing under it edge-to-edge). So the
        // shell adds no insets of its own — it only forwards the bottom-bar height via consumeWindowInsets
        // below, which keeps the per-screen Scaffolds from double-padding the navigation bar.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    topDestinations.forEach { dest ->
                        val selected = currentRoute == dest.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(dest.icon, contentDescription = dest.label) },
                            label = { Text(dest.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        // One SharedTransitionLayout spanning every destination so a card / FAB on the list can
        // morph into the encounter editor (container transform) and back. Destinations cross-fade;
        // the editor's shared container drives the headline motion. The fades are also what the
        // system's predictive-back gesture seeks through as you swipe to dismiss.
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
                    HistoryScreen(
                        onAddEncounter = { navController.navigate(Routes.ENCOUNTER_NEW) },
                        onOpenEncounter = { id -> navController.navigate(Routes.encounterEdit(id)) },
                        sharedScope = this@SharedTransitionLayout,
                        animatedScope = this,
                    )
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
                composable(Routes.PARTNERS) { PartnersScreen() }
                composable(Routes.SETTINGS) {
                    SettingsScreen(onCustomizeInsights = { navController.navigate(Routes.INSIGHTS_CUSTOMIZE) })
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
