package com.mrhabibi.usholli.wear.ui.preference

import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Text
import com.mrhabibi.usholli.wear.R
import com.mrhabibi.usholli.wear.data.NotificationType
import com.mrhabibi.usholli.wear.data.Period
import com.mrhabibi.usholli.wear.ui.UsholliViewModel
import com.mrhabibi.usholli.wear.ui.theme.OnBackground
import com.mrhabibi.usholli.wear.ui.theme.Primary
import com.mrhabibi.usholli.wear.ui.theme.SurfaceVariant
import com.mrhabibi.usholli.wear.ui.theme.TextDim

private val reminderOptions = listOf(
    0L to R.string.no_reminder,
    5L * 60_000L to R.string.reminder_5,
    10L * 60_000L to R.string.reminder_10,
    15L * 60_000L to R.string.reminder_15,
    20L * 60_000L to R.string.reminder_20,
    25L * 60_000L to R.string.reminder_25,
    30L * 60_000L to R.string.reminder_30,
)

@Composable
fun NotificationPreferenceScreen(
    viewModel: UsholliViewModel,
    navController: NavHostController,
    periodId: String?,
) {
    val period = periodId?.let { Period.byId(it) } ?: return
    val settings = viewModel.settings
    val currentType = settings.notifTypeFor(period)
    val currentReminder = settings.reminderFor(period)
    val ringtoneTitle = stringResource(R.string.pick_ringtone)

    // Non-adzan periods (Imsak, Terbit, Dhuha) don't offer the Takbir sound.
    val availableTypes = NotificationType.all.filter { type ->
        period.canAdzan || type != NotificationType.TAKBIR
    }

    val ringtoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            result.data?.getParcelableExtra(
                RingtoneManager.EXTRA_RINGTONE_PICKED_URI,
                Uri::class.java,
            )
        } else {
            @Suppress("DEPRECATION")
            result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        }
        if (uri != null) {
            viewModel.setRingtone(uri.toString())
            viewModel.setNotifType(period, NotificationType.RINGTONE)
        }
    }

    val onTypeSelected: (String) -> Unit = { type ->
        if (type == NotificationType.RINGTONE) {
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, ringtoneTitle)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            }
            ringtoneLauncher.launch(intent)
        } else {
            viewModel.setNotifType(period, type)
        }
    }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Text(
                text = stringResource(R.string.preference_title, stringResource(period.labelRes)),
                color = OnBackground,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }
        item {
            SectionLabel(stringResource(R.string.notification))
        }
        items(availableTypes.size) { index ->
            val type = availableTypes[index]
            PreferenceRow(
                label = stringResource(NotificationType.label(type)),
                selected = currentType == type,
                onClick = { onTypeSelected(type) },
            )
        }
        item {
            SectionLabel(stringResource(R.string.pre_reminder))
        }
        items(reminderOptions.size) { index ->
            val (ms, labelRes) = reminderOptions[index]
            PreferenceRow(
                label = stringResource(labelRes),
                selected = currentReminder == ms,
                onClick = { viewModel.setReminder(period, ms) },
            )
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
private fun PreferenceRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .background(if (selected) Primary else SurfaceVariant)
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
        if (selected) {
            Image(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                colorFilter = ColorFilter.tint(OnBackground),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
