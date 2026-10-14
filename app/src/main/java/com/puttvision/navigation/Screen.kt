package com.puttvision.navigation

sealed class Screen(val route: String) {
    object Onboarding  : Screen("onboarding")
    object Home        : Screen("home")
    object History     : Screen("history")
    object Settings    : Screen("settings")
    object Live        : Screen("live/{sessionId}") {
        fun createRoute(sessionId: Long) = "live/$sessionId"
    }
    object Lab         : Screen("lab/{sessionId}") {
        fun createRoute(sessionId: Long) = "lab/$sessionId"
    }
    object PuttDetail  : Screen("putt/{puttId}") {
        fun createRoute(puttId: Long) = "putt/$puttId"
    }
    object Stats       : Screen("stats/{sessionId}") {
        fun createRoute(sessionId: Long) = "stats/$sessionId"
    }
    object LabLatest   : Screen("lab_latest")
    object StatsLatest : Screen("stats_latest")
}
