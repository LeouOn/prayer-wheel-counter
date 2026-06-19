package com.prayerwheel.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.prayerwheel.app.ui.navigation.PrayerWheelNavHost
import com.prayerwheel.app.ui.theme.PrayerWheelTheme
import com.prayerwheel.app.ui.theme.ThemeMode
import com.prayerwheel.app.viewmodel.WheelViewModel

class MainActivity : ComponentActivity() {

    private var wheelViewModel: WheelViewModel? = null

    private var widgetStartRequested by mutableStateOf(false)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestNotificationPermissionIfNeeded()

        // "start_session" extra is sent by PrayerWheelWidget's Spin button.
        widgetStartRequested = intent?.getBooleanExtra(EXTRA_START_SESSION, false) ?: false

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
                            vibrator = app.vibrator,
                            appContext = applicationContext
                        )
                    )
                    wheelViewModel = viewModel

                    LaunchedEffect(widgetStartRequested) {
                        if (widgetStartRequested) {
                            viewModel.startSessionFromWidget()
                            widgetStartRequested = false
                        }
                    }

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

    override fun onPause() {
        super.onPause()
        wheelViewModel?.setAppInForeground(false)
    }

    override fun onResume() {
        super.onResume()
        wheelViewModel?.setAppInForeground(true)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_START_SESSION, false)) {
            widgetStartRequested = true
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            wheelViewModel = null
        }
    }

    /**
     * Requests POST_NOTIFICATIONS permission on Android 13+ (API 33+).
     * Foreground services that show notifications need this permission.
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    companion object {
        const val EXTRA_START_SESSION = "start_session"
    }
}
