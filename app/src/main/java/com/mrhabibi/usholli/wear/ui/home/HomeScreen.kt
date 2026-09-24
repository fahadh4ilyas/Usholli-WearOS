package com.mrhabibi.usholli.wear.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Text
import com.mrhabibi.usholli.wear.R
import com.mrhabibi.usholli.wear.data.NotificationType
import com.mrhabibi.usholli.wear.data.Period
import com.mrhabibi.usholli.wear.data.ScheduleUtil
import com.mrhabibi.usholli.wear.ui.Dest
import com.mrhabibi.usholli.wear.ui.UsholliViewModel
import com.mrhabibi.usholli.wear.ui.theme.OnBackground
import com.mrhabibi.usholli.wear.ui.theme.SurfaceVariant
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormatter = DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy", Locale("id", "ID"))

@Composable
fun HomeScreen(viewModel: UsholliViewModel, navController: NavHostController) {
    val settings = viewModel.settings
    val schedule = viewModel.schedule
    val hijri = viewModel.hijri

    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = LocalDateTime.now()
        }
    }

    // When the Gregorian date rolls over at midnight, re-fetch the Hijri date
    // so it doesn't stay stuck on yesterday.
    LaunchedEffect(now.toLocalDate()) {
        viewModel.refreshHijri()
    }

    val currentPeriod = schedule?.let { ScheduleUtil.currentPeriod(it, settings, now) } ?: Period.ISYA
    val next = schedule?.let { ScheduleUtil.nextPrayer(it, settings, now) }
    val entries = schedule?.let { ScheduleUtil.todayEntries(it, settings, now.toLocalDate()) } ?: emptyList()

    // Day/night for the sun/moon icon, based on sunrise (Terbit) and sunset (Maghrib).
    val today = now.toLocalDate().toString()
    val terbit = schedule?.day(today)?.timeOf(Period.TERBIT)?.let { ScheduleUtil.parseTime(it) }
    val maghrib = schedule?.day(today)?.timeOf(Period.MAGHRIB)?.let { ScheduleUtil.parseTime(it) }
    val isNight = if (terbit != null && maghrib != null) {
        val nowTime = now.toLocalTime()
        val terbitTime = LocalTime.of(terbit.first, terbit.second)
        val maghribTime = LocalTime.of(maghrib.first, maghrib.second)
        nowTime.isBefore(terbitTime) || !nowTime.isBefore(maghribTime)
    } else {
        currentPeriod == Period.IMSAK || currentPeriod == Period.SHUBUH ||
            currentPeriod == Period.MAGHRIB || currentPeriod == Period.ISYA
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(currentPeriod.skyColor, currentPeriod.skyDarkColor))),
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                SkyHeader(
                    location = settings.regionName.ifEmpty { null },
                    hijri = hijri,
                    now = now,
                    next = next,
                    currentPeriod = currentPeriod,
                    isNight = isNight,
                )
            }

            if (schedule == null) {
                item { EmptyState(onOpenSettings = { navController.navigate(Dest.SETTINGS) }) }
            } else if (entries.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.no_schedule),
                        color = OnBackground,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            } else {
                items(entries.size) { index ->
                    val (period, time) = entries[index]
                    ScheduleRow(
                        period = period,
                        time = time,
                        notifType = settings.notifTypeFor(period),
                        reminderMs = settings.reminderFor(period),
                        isNext = next?.period == period && next.date == now.toLocalDate().toString(),
                        onClick = { navController.navigate(Dest.preference(period.id)) },
                    )
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ActionButton(
                        icon = {
                            Image(
                                painter = painterResource(R.drawable.ic_compass),
                                contentDescription = null,
                                colorFilter = ColorFilter.tint(OnBackground),
                                modifier = Modifier.size(16.dp),
                            )
                        },
                        label = stringResource(R.string.kiblat),
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { navController.navigate(Dest.KIBLAT) },
                    )
                    ActionButton(
                        icon = { IconImage(Icons.Filled.Settings) },
                        label = stringResource(R.string.pengaturan),
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { navController.navigate(Dest.SETTINGS) },
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun SkyHeader(
    location: String?,
    hijri: String?,
    now: LocalDateTime,
    next: ScheduleUtil.PrayerTime?,
    currentPeriod: Period,
    isNight: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Sun during the day, moon at night.
        Image(
            painter = painterResource(if (isNight) R.drawable.ic_moon else R.drawable.ic_sun),
            contentDescription = null,
            colorFilter = ColorFilter.tint(OnBackground),
            modifier = Modifier.size(40.dp),
        )

        Spacer(Modifier.height(10.dp))

        location?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconImage(Icons.Filled.Place, tint = OnBackground, size = 14.dp)
                Spacer(Modifier.width(4.dp))
                Text(
                    text = it,
                    color = OnBackground,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Text(
            text = now.format(dateFormatter),
            color = OnBackground,
            fontSize = 12.sp,
            maxLines = 1,
        )

        hijri?.let {
            Text(
                text = it,
                color = OnBackground.copy(alpha = 0.8f),
                fontSize = 12.sp,
                maxLines = 1,
            )
        }

        Spacer(Modifier.height(12.dp))

        if (next != null) {
            val periodName = stringResource(next.period.labelRes)
            Text(
                text = "$periodName ${stringResource(R.string.period_time, next.hour, next.minute)}",
                color = OnBackground,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.next_prayer, formatCountdown(now, next.at)),
                color = OnBackground.copy(alpha = 0.9f),
                fontSize = 15.sp,
            )
        } else {
            Text(
                text = stringResource(R.string.prayer_time, stringResource(currentPeriod.labelRes)),
                color = OnBackground,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun ScheduleRow(
    period: Period,
    time: String,
    notifType: String,
    reminderMs: Long,
    isNext: Boolean,
    onClick: () -> Unit,
) {
    val accent = if (isNext) period.skyColor else SurfaceVariant
    // Amber marks the next prayer, matching the tile (visible even for dark periods like Imsak).
    val nameColor = if (isNext) Color(0xFFFFC107) else OnBackground
    val timeColor = if (isNext) Color(0xFFFFC107) else OnBackground
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(CircleShape)
            .background(accent)
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            // Line 1: schedule name + time.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(period.labelRes),
                    color = nameColor,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = time,
                    color = timeColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            // Line 2: notification type (left) + reminder (right, compact).
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(NotificationType.label(notifType)),
                    color = OnBackground.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (reminderMs > 0) {
                    Text(
                        text = "${reminderMs / 60_000L}m",
                        color = OnBackground.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                    )
                    Spacer(Modifier.width(3.dp))
                    IconImage(
                        imageVector = Icons.Filled.Notifications,
                        tint = OnBackground.copy(alpha = 0.7f),
                        size = 12.dp,
                    )
                } else {
                    Image(
                        painter = painterResource(R.drawable.ic_notification_off),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(OnBackground.copy(alpha = 0.7f)),
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
        Button(
            onClick = onClick,
            modifier = Modifier
                .padding(start = 4.dp)
                .size(28.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = Color.Transparent),
        ) {
            IconImage(Icons.Filled.Notifications, tint = OnBackground, size = 18.dp)
        }
    }
}

@Composable
private fun ActionButton(
    icon: @Composable () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(backgroundColor = SurfaceVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            icon()
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                color = OnBackground,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun EmptyState(onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.select_city_first),
            color = OnBackground,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onOpenSettings,
            colors = ButtonDefaults.buttonColors(backgroundColor = SurfaceVariant),
        ) {
            Text(text = stringResource(R.string.pengaturan), color = OnBackground)
        }
    }
}

@Composable
private fun IconImage(
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color = OnBackground,
    size: androidx.compose.ui.unit.Dp = 16.dp,
) {
    Image(
        imageVector = imageVector,
        contentDescription = null,
        colorFilter = ColorFilter.tint(tint),
        modifier = Modifier.size(size),
    )
}

private fun formatCountdown(now: LocalDateTime, at: LocalDateTime): String {
    val seconds = Duration.between(now, at).seconds.coerceAtLeast(0)
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return "%02d:%02d:%02d".format(h, m, s)
}
