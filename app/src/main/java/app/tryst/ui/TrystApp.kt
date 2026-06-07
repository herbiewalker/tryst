package app.tryst.ui

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
import app.tryst.ui.encounter.EncounterEditScreen
import app.tryst.ui.history.HistoryScreen
import app.tryst.ui.insights.InsightsScreen
import app.tryst.ui.partner.PartnersScreen
import app.tryst.ui.settings.SettingsScreen

private object Routes {
    const val HISTORY = "history"
    const val INSIGHTS = "insights"
    const val INSIGHTS_CUSTOMIZE = "insights/customize"
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
@Composable
fun TrystApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in topDestinations.map { it.route }

    Scaffold(
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
        NavHost(
            navController = navController,
            startDestination = Routes.HISTORY,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.HISTORY) {
                HistoryScreen(
                    onAddEncounter = { navController.navigate(Routes.ENCOUNTER_NEW) },
                    onOpenEncounter = { id -> navController.navigate(Routes.encounterEdit(id)) },
                )
            }
            composable(Routes.INSIGHTS) { InsightsScreen() }
            composable(Routes.INSIGHTS_CUSTOMIZE) {
                InsightsScreen(
                    startInEditMode = true,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.PARTNERS) { PartnersScreen() }
            composable(Routes.SETTINGS) {
                SettingsScreen(onCustomizeInsights = { navController.navigate(Routes.INSIGHTS_CUSTOMIZE) })
            }
            composable(Routes.ENCOUNTER_NEW) {
                EncounterEditScreen(encounterId = null, onClose = { navController.popBackStack() })
            }
            composable(Routes.ENCOUNTER_EDIT) { entry ->
                EncounterEditScreen(
                    encounterId = entry.arguments?.getString("encounterId"),
                    onClose = { navController.popBackStack() },
                )
            }
        }
    }
}
