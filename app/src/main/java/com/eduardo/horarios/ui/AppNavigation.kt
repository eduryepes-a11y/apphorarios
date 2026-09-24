package com.eduardo.horarios.ui

import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarViewWeek
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.eduardo.horarios.R
import com.eduardo.horarios.ui.backup.ImportHandler
import com.eduardo.horarios.ui.editor.BulkAddScreen
import com.eduardo.horarios.ui.editor.EditorScreen
import com.eduardo.horarios.ui.home.TodayScreen
import com.eduardo.horarios.ui.home.WeekScreen
import com.eduardo.horarios.ui.onboarding.OnboardingScreen
import com.eduardo.horarios.ui.schedules.SchedulesScreen
import com.eduardo.horarios.ui.settings.NotificationsScreen
import com.eduardo.horarios.ui.settings.SettingsScreen
import com.eduardo.horarios.ui.stats.StatsScreen

private const val ANIM = 300

/** Pestañas del menú inferior. */
private enum class Tab(val route: String, val labelRes: Int, val icon: ImageVector) {
    TODAY("today", R.string.tab_today, Icons.Rounded.Today),
    WEEK("week", R.string.tab_week, Icons.Rounded.CalendarViewWeek),
    SCHEDULES("schedules", R.string.tab_schedules, Icons.AutoMirrored.Rounded.EventNote),
    STATS("stats", R.string.tab_progress, Icons.Rounded.Insights),
    SETTINGS("settings", R.string.tab_settings, Icons.Rounded.Settings),
}

private val TAB_ROUTES = Tab.entries.map { it.route }.toSet()

/** Ir a una pestaña sin apilar pantallas (como en Google Play). */
private fun NavHostController.goToTab(route: String) {
    navigate(route) {
        popUpTo(Tab.TODAY.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun AppNavigation(showOnboarding: Boolean) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBar = route in TAB_ROUTES

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            AnimatedVisibility(
                visible = showBar,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = { if (route != tab.route) nav.goToTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.labelRes)) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = if (showOnboarding) "onboarding" else Tab.TODAY.route,
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding),
            enterTransition = { slideIntoContainer(SlideDirection.Start, tween(ANIM)) + fadeIn(tween(ANIM)) },
            exitTransition = { fadeOut(tween(ANIM / 2)) },
            popEnterTransition = { fadeIn(tween(ANIM)) },
            popExitTransition = { slideOutOfContainer(SlideDirection.End, tween(ANIM)) + fadeOut(tween(ANIM)) },
        ) {
            composable("onboarding") {
                OnboardingScreen(onDone = {
                    nav.navigate(Tab.TODAY.route) { popUpTo("onboarding") { inclusive = true } }
                })
            }

            // ---------- Pestañas (cambio con fundido suave) ----------
            composable(
                Tab.TODAY.route,
                enterTransition = { fadeIn(tween(ANIM)) },
                exitTransition = { fadeOut(tween(ANIM / 2)) },
            ) {
                TodayScreen(
                    onOpenSchedules = { nav.goToTab(Tab.SCHEDULES.route) },
                    onAddActivity = { day -> nav.navigate("editor?day=$day") },
                    onEditActivity = { id -> nav.navigate("editor?activityId=$id") },
                    onBulkAdd = { day -> nav.navigate("bulk?day=$day") },
                )
            }
            composable(
                Tab.WEEK.route,
                enterTransition = { fadeIn(tween(ANIM)) },
                exitTransition = { fadeOut(tween(ANIM / 2)) },
            ) {
                WeekScreen(
                    onOpenDay = { nav.goToTab(Tab.TODAY.route) },
                    onAddActivity = { day -> nav.navigate("editor?day=$day") },
                    onEditActivity = { id -> nav.navigate("editor?activityId=$id") },
                )
            }
            composable(
                Tab.SCHEDULES.route,
                enterTransition = { fadeIn(tween(ANIM)) },
                exitTransition = { fadeOut(tween(ANIM / 2)) },
            ) {
                SchedulesScreen(onBack = null)
            }
            composable(
                Tab.STATS.route,
                enterTransition = { fadeIn(tween(ANIM)) },
                exitTransition = { fadeOut(tween(ANIM / 2)) },
            ) {
                StatsScreen(onBack = null)
            }
            composable(
                Tab.SETTINGS.route,
                enterTransition = { fadeIn(tween(ANIM)) },
                exitTransition = { fadeOut(tween(ANIM / 2)) },
            ) {
                SettingsScreen(
                    onBack = null,
                    onOpenNotifications = { nav.navigate("notifications") },
                )
            }

            // ---------- Pantallas completas (sin menú inferior) ----------
            composable("notifications") {
                NotificationsScreen(onBack = { nav.popBackStack() })
            }
            composable(
                route = "editor?activityId={activityId}&day={day}",
                arguments = listOf(
                    navArgument("activityId") { type = NavType.LongType; defaultValue = -1L },
                    navArgument("day") { type = NavType.IntType; defaultValue = -1 },
                ),
            ) { entry ->
                val day = entry.arguments?.getInt("day") ?: -1
                EditorScreen(
                    onBack = { nav.popBackStack() },
                    onBulkAdd = {
                        nav.navigate("bulk?day=$day") { popUpTo("editor?activityId={activityId}&day={day}") { inclusive = true } }
                    },
                )
            }
            composable(
                route = "bulk?day={day}",
                arguments = listOf(navArgument("day") { type = NavType.IntType; defaultValue = -1 }),
            ) { entry ->
                val day = entry.arguments?.getInt("day") ?: -1
                BulkAddScreen(
                    initialDay = if (day in 0..6) day else java.time.LocalDate.now().dayOfWeek.value - 1,
                    onBack = { nav.popBackStack() },
                )
            }
        }
    }
    ImportHandler()
}
