package com.mrhabibi.usholli.wear

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.mrhabibi.usholli.wear.ui.UsholliApp
import com.mrhabibi.usholli.wear.ui.UsholliViewModel
import com.mrhabibi.usholli.wear.ui.theme.UsholliTheme

class MainActivity : ComponentActivity() {

    private val viewModel: UsholliViewModel by viewModels()

    // Incremented each time the activity is (re)launched from a tile/complication,
    // so the app always returns to the home menu instead of the last opened screen.
    private var openHomeTrigger by mutableStateOf(0)

    private val requestNotification =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            requestLocationIfNeeded()
        }

    private val requestLocation =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            requestExactAlarmIfNeeded()
            if (granted) viewModel.autoDetectOnLaunch()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            UsholliTheme {
                UsholliApp(openHomeTrigger = openHomeTrigger)
            }
        }

        // Onboarding permission order: notification -> location -> exact alarm.
        requestNotificationIfNeeded()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openHomeTrigger++
    }

    private fun requestNotificationIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !hasPermission(Manifest.permission.POST_NOTIFICATIONS)
        ) {
            requestNotification.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            requestLocationIfNeeded()
        }
    }

    private fun requestLocationIfNeeded() {
        if (!hasLocationPermission()) {
            requestLocation.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            requestExactAlarmIfNeeded()
            viewModel.autoDetectOnLaunch()
        }
    }

    private fun requestExactAlarmIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(AlarmManager::class.java)
            if (!alarmManager.canScheduleExactAlarms()) {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.parse("package:$packageName")
                    }
                )
            }
        }
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun hasLocationPermission(): Boolean = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
}
