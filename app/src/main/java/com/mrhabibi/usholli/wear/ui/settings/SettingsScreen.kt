package com.mrhabibi.usholli.wear.ui.settings

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Switch
import androidx.wear.compose.material.Text
import com.mrhabibi.usholli.wear.R
import com.mrhabibi.usholli.wear.data.Period
import com.mrhabibi.usholli.wear.ui.Dest
import com.mrhabibi.usholli.wear.ui.UsholliViewModel
import com.mrhabibi.usholli.wear.ui.theme.OnBackground
import com.mrhabibi.usholli.wear.ui.theme.SurfaceVariant
import com.mrhabibi.usholli.wear.ui.theme.TextDim

@Composable
fun SettingsScreen(viewModel: UsholliViewModel, navController: NavHostController) {
    val settings = viewModel.settings
    val context = LocalContext.current

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.autoDetect()
        } else {
            viewModel.setAutoDetect(false)
        }
    }

    val onAutoDetectToggled: (Boolean) -> Unit = { enabled ->
        viewModel.setAutoDetect(enabled)
        if (enabled) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                viewModel.autoDetect()
            } else {
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    // Exact-alarm permission (SCHEDULE_EXACT_ALARM), denied by default on Wear OS 6.
    var canExactAlarm by remember { mutableStateOf(canScheduleExactAlarms(context)) }
    val exactAlarmLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        canExactAlarm = canScheduleExactAlarms(context)
        if (canExactAlarm) {
            // Re-schedule so the next alarms use the now-granted exact-alarm path.
            viewModel.setAlarmMode(settings.alarmMode)
        }
    }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Text(
                text = stringResource(R.string.pengaturan),
                color = OnBackground,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }

        item { SectionLabel(stringResource(R.string.location)) }
        item {
            CityRow(
                cityName = settings.regionName.ifEmpty { stringResource(R.string.select_city_short) },
                onClick = { navController.navigate(Dest.LOCATION) },
            )
        }
        item {
            ToggleRow(
                label = stringResource(R.string.auto_detect),
                checked = settings.autoDetect,
                onCheckedChange = onAutoDetectToggled,
            )
        }

        item { SectionLabel(stringResource(R.string.notification)) }
        item {
            ToggleRow(
                label = stringResource(R.string.alarm_mode),
                checked = settings.alarmMode,
                onCheckedChange = { viewModel.setAlarmMode(it) },
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !canExactAlarm) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .clip(CircleShape)
                        .clickable {
                            exactAlarmLauncher.launch(
                                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                            )
                        }
                        .background(SurfaceVariant)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.exact_alarm_permission),
                        color = OnBackground,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .clip(CircleShape)
                    .clickable { viewModel.simulateNotification() }
                    .background(SurfaceVariant)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.simulate),
                    color = OnBackground,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item { SectionLabel(stringResource(R.string.other_schedules)) }
        item {
            ToggleRow(
                label = stringResource(Period.IMSAK.labelRes),
                checked = settings.showImsak,
                onCheckedChange = { viewModel.setShow(Period.IMSAK, it) },
            )
        }
        item {
            ToggleRow(
                label = stringResource(Period.TERBIT.labelRes),
                checked = settings.showTerbit,
                onCheckedChange = { viewModel.setShow(Period.TERBIT, it) },
            )
        }
        item {
            ToggleRow(
                label = stringResource(Period.DHUHA.labelRes),
                checked = settings.showDhuha,
                onCheckedChange = { viewModel.setShow(Period.DHUHA, it) },
            )
        }

        item { SectionLabel(stringResource(R.string.complication_section)) }
        item {
            ToggleRow(
                label = stringResource(R.string.complication_swap),
                checked = settings.complicationSwap,
                onCheckedChange = { viewModel.setComplicationSwap(it) },
            )
        }

        item { SectionLabel(stringResource(R.string.hijri_correction)) }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .clip(CircleShape)
                    .clickable { navController.navigate(Dest.HIJRI) }
                    .background(SurfaceVariant)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.hijri_correction),
                    color = OnBackground,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(hijriCorrectionLabel(settings.hijriCorrection)),
                    color = TextDim,
                    fontSize = 13.sp,
                )
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = TextDim,
        fontSize = 13.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun CityRow(cityName: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .background(SurfaceVariant)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            imageVector = Icons.Filled.Place,
            contentDescription = null,
            colorFilter = ColorFilter.tint(OnBackground),
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = cityName,
            color = OnBackground,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(CircleShape)
            .background(SurfaceVariant)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            color = OnBackground,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun hijriCorrectionLabel(correction: Int): Int = when (correction) {
    -2 -> R.string.hijri_correction_m2
    -1 -> R.string.hijri_correction_m1
    1 -> R.string.hijri_correction_p1
    2 -> R.string.hijri_correction_p2
    else -> R.string.hijri_correction_0
}

private fun canScheduleExactAlarms(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
