package com.puttvision.navigation

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.puttvision.feature.history.SessionHistoryScreen
import com.puttvision.feature.home.HomeScreen
import com.puttvision.feature.lab.LabScreen
import com.puttvision.feature.lab.PuttDetailScreen
import com.puttvision.feature.live.LiveScreen
import com.puttvision.feature.settings.SettingsScreen
import com.puttvision.feature.stats.StatsScreen
import com.puttvision.ui.theme.*

private data class BottomTab(val label: String, val icon: ImageVector, val route: String)

private val TABS = listOf(
    BottomTab("LIVE",     Icons.Filled.PlayCircle,   Screen.Home.route),
    BottomTab("LAB",      Icons.Filled.VideoLibrary, Screen.LabLatest.route),
    BottomTab("STATS",    Icons.Filled.BarChart,     Screen.StatsLatest.route),
    BottomTab("HISTORIAL",Icons.Filled.History,      Screen.History.route)
)

@Composable
fun PuttVisionNavHost(navController: NavHostController = rememberNavController()) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val hideBottomBar = currentRoute?.startsWith("live/")  == true
                     || currentRoute == Screen.Settings.route
                     || currentRoute?.startsWith("putt/")  == true

    Scaffold(
        containerColor = Background,
        bottomBar = {
            if (!hideBottomBar) PuttBottomBar(currentRoute, navController)
        }
    ) { padding ->
        NavHost(
            navController    = navController,
            startDestination = Screen.Home.route,
            modifier         = Modifier.padding(padding)
        ) {
            // ── Home ──────────────────────────────────────────────────────────
            composable(Screen.Home.route) {
                HomeScreen(
                    onStartSession  = { id -> navController.navigate(Screen.Live.createRoute(id)) },
                    onOpenSettings  = { navController.navigate(Screen.Settings.route) },
                    onResumeSession = { id -> navController.navigate(Screen.Lab.createRoute(id)) }
                )
            }

            // ── Settings ──────────────────────────────────────────────────────
            composable(Screen.Settings.route) {
                SettingsScreen(onBack = { navController.popBackStack() })
            }

            // ── History ───────────────────────────────────────────────────────
            composable(Screen.History.route) {
                SessionHistoryScreen(
                    onSessionClick = { id ->
                        navController.navigate(Screen.Lab.createRoute(id))
                    }
                )
            }

            // ── LIVE ──────────────────────────────────────────────────────────
            composable(
                route     = Screen.Live.route,
                arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
            ) { back ->
                val sessionId = back.arguments?.getLong("sessionId") ?: 0L
                LiveScreen(
                    sessionId = sessionId,
                    onGoToLab = {
                        navController.navigate(Screen.Lab.createRoute(sessionId)) {
                            popUpTo(Screen.Home.route)
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            // ── LAB (latest) ──────────────────────────────────────────────────
            composable(Screen.LabLatest.route) {
                LabScreen(
                    sessionId   = null,
                    onPuttClick = { id -> navController.navigate(Screen.PuttDetail.createRoute(id)) }
                )
            }

            // ── LAB (specific) ────────────────────────────────────────────────
            composable(
                route     = Screen.Lab.route,
                arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
            ) { back ->
                LabScreen(
                    sessionId   = back.arguments?.getLong("sessionId"),
                    onPuttClick = { id -> navController.navigate(Screen.PuttDetail.createRoute(id)) }
                )
            }

            // ── Putt Detail ───────────────────────────────────────────────────
            composable(
                route     = Screen.PuttDetail.route,
                arguments = listOf(navArgument("puttId") { type = NavType.LongType })
            ) { back ->
                PuttDetailScreen(
                    puttId = back.arguments?.getLong("puttId") ?: 0L,
                    onBack = { navController.popBackStack() }
                )
            }

            // ── STATS (latest) ────────────────────────────────────────────────
            composable(Screen.StatsLatest.route) {
                StatsScreenWithExport(sessionId = null)
            }

            // ── STATS (specific) ──────────────────────────────────────────────
            composable(
                route     = Screen.Stats.route,
                arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
            ) { back ->
                StatsScreenWithExport(sessionId = back.arguments?.getLong("sessionId"))
            }
        }
    }
}

/** Wrapper that handles the one-shot export intent */
@Composable
private fun StatsScreenWithExport(sessionId: Long?) {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }
    StatsScreen(
        sessionId = sessionId,
        onExport  = { intent -> launcher.launch(Intent.createChooser(intent, "Exportar sesión")) }
    )
}

@Composable
private fun PuttBottomBar(currentRoute: String?, navController: NavHostController) {
    NavigationBar(
        containerColor = SurfaceDeep,
        tonalElevation = androidx.compose.ui.unit.Dp.Unspecified
    ) {
        TABS.forEach { tab ->
            val selected = currentRoute == tab.route ||
                (tab.route == Screen.LabLatest.route   && currentRoute?.startsWith("lab/")   == true) ||
                (tab.route == Screen.StatsLatest.route && currentRoute?.startsWith("stats/") == true)

            NavigationBarItem(
                selected = selected,
                onClick  = {
                    navController.navigate(tab.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState    = true
                    }
                },
                icon     = { Icon(tab.icon, tab.label) },
                label    = {
                    Text(tab.label, style = MaterialTheme.typography.labelSmall)
                },
                colors   = NavigationBarItemDefaults.colors(
                    selectedIconColor   = PuttGreen,
                    selectedTextColor   = PuttGreen,
                    unselectedIconColor = TextSecondary,
                    unselectedTextColor = TextSecondary,
                    indicatorColor      = SurfaceDeep
                )
            )
        }
    }
}
