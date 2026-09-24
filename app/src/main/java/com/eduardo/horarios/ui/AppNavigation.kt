package com.eduardo.horarios.ui

import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.eduardo.horarios.ui.editor.EditorScreen
import com.eduardo.horarios.ui.home.HomeScreen
import com.eduardo.horarios.ui.schedules.SchedulesScreen
import com.eduardo.horarios.ui.settings.NotificationsScreen

private const val ANIM = 320

@Composable
fun AppNavigation() {
    val nav = rememberNavController()
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        NavHost(
            navController = nav,
            startDestination = "home",
            enterTransition = { slideIntoContainer(SlideDirection.Start, tween(ANIM)) + fadeIn(tween(ANIM)) },
            exitTransition = { fadeOut(tween(ANIM / 2)) },
            popEnterTransition = { fadeIn(tween(ANIM)) },
            popExitTransition = { slideOutOfContainer(SlideDirection.End, tween(ANIM)) + fadeOut(tween(ANIM)) },
        ) {
            composable("home") {
                HomeScreen(
                    onOpenSchedules = { nav.navigate("schedules") },
                    onAddActivity = { day -> nav.navigate("editor?day=$day") },
                    onEditActivity = { id -> nav.navigate("editor?activityId=$id") },
                    onOpenNotifications = { nav.navigate("notifications") },
                )
            }
            composable("notifications") {
                NotificationsScreen(onBack = { nav.popBackStack() })
            }
            composable("schedules") {
                SchedulesScreen(onBack = { nav.popBackStack() })
            }
            composable(
                route = "editor?activityId={activityId}&day={day}",
                arguments = listOf(
                    navArgument("activityId") { type = NavType.LongType; defaultValue = -1L },
                    navArgument("day") { type = NavType.IntType; defaultValue = -1 },
                ),
            ) {
                EditorScreen(onBack = { nav.popBackStack() })
            }
        }
    }
}
