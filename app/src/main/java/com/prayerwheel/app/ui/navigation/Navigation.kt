package com.prayerwheel.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.prayerwheel.app.data.datastore.UserPreferences
import com.prayerwheel.app.data.db.dao.LifetimeStatsDao
import com.prayerwheel.app.data.db.dao.SessionDao
import com.prayerwheel.app.ui.wheel.HistoryScreen
import com.prayerwheel.app.ui.wheel.OnboardingScreen
import com.prayerwheel.app.ui.wheel.SettingsScreen
import com.prayerwheel.app.ui.wheel.StatsScreen
import com.prayerwheel.app.ui.wheel.WheelScreen
import com.prayerwheel.app.ui.wheel.CalculatorScreen
import com.prayerwheel.app.ui.wheel.CalendarScreen
import com.prayerwheel.app.ui.wheel.ExportScreen
import com.prayerwheel.app.ui.wheel.AchievementsScreen
import com.prayerwheel.app.viewmodel.WheelViewModel

/**
 * Navigation routes for the app.
 */
sealed class Screen(val route: String) {
    data object Wheel : Screen("wheel")
    data object History : Screen("history")
    data object Settings : Screen("settings")
    data object Onboarding : Screen("onboarding")
    data object Stats : Screen("stats")
    data object Calculator : Screen("calculator")
    data object Calendar : Screen("calendar")
    data object Export : Screen("export")
    data object Achievements : Screen("achievements")
}

/**
 * Main navigation host for the app.
 */
@Composable
fun PrayerWheelNavHost(
    navController: NavHostController,
    viewModel: WheelViewModel,
    sessionDao: SessionDao,
    lifetimeStatsDao: LifetimeStatsDao,
    userPreferences: UserPreferences,
    startDestination: String = Screen.Wheel.route,
    modifier: Modifier = Modifier
) {
    val hasCompletedOnboarding by userPreferences.hasCompletedOnboarding.collectAsState(initial = false)
    val savedWheels by userPreferences.savedWheels.collectAsState(initial = emptyList())
    val actualStartDestination = if (hasCompletedOnboarding) Screen.Wheel.route else Screen.Onboarding.route

    NavHost(
        navController = navController,
        startDestination = actualStartDestination,
        modifier = modifier
    ) {
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                userPreferences = userPreferences,
                onOnboardingComplete = {
                    navController.navigate(Screen.Wheel.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Wheel.route) {
            WheelScreen(
                viewModel = viewModel,
                onNavigateToHistory = {
                    navController.navigate(Screen.History.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onNavigateToStats = {
                    navController.navigate(Screen.Stats.route)
                },
                onNavigateToCalculator = {
                    navController.navigate(Screen.Calculator.route)
                }
            )
        }
        
        composable(Screen.History.route) {
            HistoryScreen(
                viewModel = viewModel,
                sessions = sessionDao.getAllSessions(),
                lifetimeStats = lifetimeStatsDao.observeStats(),
                sessionDao = sessionDao,
                savedWheels = savedWheels,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToStats = {
                    navController.navigate(Screen.Stats.route)
                },
                onNavigateToExport = {
                    navController.navigate(Screen.Export.route)
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                userPreferences = userPreferences,
                viewModel = viewModel,
                sessionDao = sessionDao,
                lifetimeStatsDao = lifetimeStatsDao,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToCalculator = {
                    navController.navigate(Screen.Calculator.route)
                }
            )
        }

        composable(Screen.Stats.route) {
            val lifetimeStats by lifetimeStatsDao.observeStats().collectAsState(initial = null)
            val mantraStats by sessionDao.getMantraStats().collectAsState(initial = emptyList())
            val wheelStats by sessionDao.getWheelStats().collectAsState(initial = emptyList())
            val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
            val thirtyDaysAgo = System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000L)
            val last7DaysStats by sessionDao.getStatsSince(sevenDaysAgo).collectAsState(initial = null)
            val last30DaysStats by sessionDao.getStatsSince(thirtyDaysAgo).collectAsState(initial = null)
            val allTimeStats by sessionDao.getStatsSince(0L).collectAsState(initial = null)
            val recentSessions by sessionDao.getRecentSessions(100).collectAsState(initial = emptyList())
            
            StatsScreen(
                lifetimeStats = lifetimeStats,
                mantraStats = mantraStats,
                wheelStats = wheelStats,
                savedWheels = savedWheels,
                last7DaysStats = last7DaysStats,
                last30DaysStats = last30DaysStats,
                allTimeStats = allTimeStats,
                recentSessions = recentSessions,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToCalendar = {
                    navController.navigate(Screen.Calendar.route)
                },
                onNavigateToAchievements = {
                    navController.navigate(Screen.Achievements.route)
                }
            )
        }

        composable(Screen.Calculator.route) {
            val averageRpm by viewModel.averageRpm.collectAsState()
            val mantrasPerRotation by viewModel.mantrasPerRotation.collectAsState()
            CalculatorScreen(
                defaultRpm = averageRpm,
                defaultMantrasPerRotation = mantrasPerRotation,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Calendar.route) {
            CalendarScreen(
                viewModel = viewModel,
                sessionDao = sessionDao,
                savedWheels = savedWheels,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Export.route) {
            ExportScreen(
                sessionDao = sessionDao,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Achievements.route) {
            val unlockedSet by userPreferences.unlockedAchievements.collectAsState(initial = emptySet())
            AchievementsScreen(
                unlockedAchievements = unlockedSet,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
