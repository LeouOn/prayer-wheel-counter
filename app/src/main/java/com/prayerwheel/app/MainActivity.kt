package com.prayerwheel.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.prayerwheel.app.ui.navigation.PrayerWheelNavHost
import com.prayerwheel.app.ui.theme.PrayerWheelTheme
import com.prayerwheel.app.ui.theme.ThemeMode
import com.prayerwheel.app.viewmodel.WheelViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as PrayerWheelApp

        setContent {
            val themeName by app.userPreferences.theme.collectAsState(initial = "system")
            val themeMode = when (themeName.lowercase()) {
                "light" -> ThemeMode.LIGHT
                "dark" -> ThemeMode.DARK
                "sepia" -> ThemeMode.SEPIA
                "dawn_dusk", "dawn-dusk" -> ThemeMode.DAWN_DUSK
                else -> ThemeMode.SYSTEM
            }

            PrayerWheelTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: WheelViewModel = viewModel(
                        factory = WheelViewModel.Factory(
                            lifetimeStatsDao = app.database.lifetimeStatsDao(),
                            sessionDao = app.database.sessionDao(),
                            userPreferences = app.userPreferences,
                            vibrator = app.vibrator
                        )
                    )

                    val navController = rememberNavController()

                    PrayerWheelNavHost(
                        navController = navController,
                        viewModel = viewModel,
                        sessionDao = app.database.sessionDao(),
                        lifetimeStatsDao = app.database.lifetimeStatsDao(),
                        userPreferences = app.userPreferences
                    )
                }
            }
        }
    }
}
