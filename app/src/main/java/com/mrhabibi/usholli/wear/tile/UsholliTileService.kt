package com.mrhabibi.usholli.wear.tile

import android.content.Context
import androidx.compose.ui.graphics.toArgb
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.mrhabibi.usholli.wear.MainActivity
import com.mrhabibi.usholli.wear.data.Period
import com.mrhabibi.usholli.wear.data.Schedule
import com.mrhabibi.usholli.wear.data.ScheduleRepository
import com.mrhabibi.usholli.wear.data.ScheduleUtil
import com.mrhabibi.usholli.wear.data.SettingsStore
import java.time.LocalDateTime

/**
 * Shared base for all Usholli tiles: the full-screen tile and the Samsung compact
 * (card) tiles. Samsung sizes each card via a separate service + the
 * `com.samsung.android.wearable.tiles.LAYOUT_TYPE` manifest metadata.
 */
abstract class BaseUsholliTileService : TileService() {

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest,
    ): ListenableFuture<TileBuilders.Tile> {
        // Corner radius = half the tile's short side (Samsung's capsule ends).
        val config = requestParams.deviceConfiguration
        val widthDp = config.screenWidthDp
        val heightDp = config.screenHeightDp
        val cornerRadiusDp = if (widthDp > 0 && heightDp > 0) minOf(widthDp, heightDp) / 2f else 28f

        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion("1")
            .setFreshnessIntervalMillis(5 * 60 * 1000L)
            .setTileTimeline(
                TimelineBuilders.Timeline.Builder()
                    .addTimelineEntry(
                        TimelineBuilders.TimelineEntry.Builder()
                            .setLayout(
                                LayoutElementBuilders.Layout.Builder()
                                    .setRoot(buildRootElement(cornerRadiusDp))
                                    .build(),
                            )
                            .build(),
                    )
                    .build(),
            )
            .build()
        return Futures.immediateFuture(tile)
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> {
        return Futures.immediateFuture(
            ResourceBuilders.Resources.Builder().setVersion("1").build(),
        )
    }

    /** The layout to render for this tile service. */
    protected abstract fun buildRootElement(cornerRadiusDp: Float): LayoutElementBuilders.LayoutElement

    protected fun launchAction(): ActionBuilders.LaunchAction =
        ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder()
                    .setPackageName(packageName)
                    .setClassName(MainActivity::class.java.name)
                    .build(),
            )
            .build()

    protected fun clickable(): ModifiersBuilders.Clickable =
        ModifiersBuilders.Clickable.Builder().setOnClick(launchAction()).build()

    /** Vertical gradient matching the home menu's sky, with pill-shaped corners. */
    protected fun background(
        topColor: Int,
        bottomColor: Int,
        cornerRadiusDp: Float,
    ): ModifiersBuilders.Background {
        val gradient = ColorBuilders.LinearGradient.Builder(
            ColorBuilders.argb(topColor),
            ColorBuilders.argb(bottomColor),
        ).build()

        return ModifiersBuilders.Background.Builder()
            .setBrush(gradient)
            .setCorner(
                ModifiersBuilders.Corner.Builder()
                    .setRadius(DimensionBuilders.dp(cornerRadiusDp))
                    .build(),
            )
            .build()
    }

    /** Current prayer's sky colors (same as the home menu gradient). */
    protected fun currentSkyColors(
        schedule: Schedule?,
        settings: com.mrhabibi.usholli.wear.data.AppSettings,
        now: LocalDateTime,
    ): Pair<Int, Int> {
        val period = schedule?.let { ScheduleUtil.currentPeriod(it, settings, now) } ?: Period.ISYA
        return period.skyColor.toArgb() to period.skyDarkColor.toArgb()
    }

    /**
     * Full schedule layout. [compact] uses tighter spacing/fonts so the whole
     * schedule fits inside the smaller 2x2 card.
     */
    protected fun buildScheduleRoot(
        compact: Boolean,
        cornerRadiusDp: Float,
    ): LayoutElementBuilders.LayoutElement {
        val settings = SettingsStore(this).load()
        val schedule = ScheduleRepository(this).loadCachedSchedule()
        val now = LocalDateTime.now()
        val (topColor, bottomColor) = currentSkyColors(schedule, settings, now)

        val hPad = if (compact) 36f else 32f
        val vPad = if (compact) 4f else 12f
        val citySize = if (compact) 11f else 12f
        val rowSize = if (compact) 12f else 13f
        val rowGap = if (compact) 0f else 1f

        val columnModifiers = ModifiersBuilders.Modifiers.Builder()
            .setPadding(
                ModifiersBuilders.Padding.Builder()
                    .setStart(DimensionBuilders.dp(hPad))
                    .setEnd(DimensionBuilders.dp(hPad))
                    .setTop(DimensionBuilders.dp(vPad))
                    .setBottom(DimensionBuilders.dp(vPad))
                    .build(),
            )
            .build()

        val column = LayoutElementBuilders.Column.Builder()
            .setModifiers(columnModifiers)
            .setWidth(DimensionBuilders.expand())

        if (schedule == null || !settings.hasLocation) {
            column.addContent(text("Buka Usholli untuk memilih kota", size = 13f))
        } else {
            val location = settings.regionName.ifBlank { settings.regionProv }
            if (compact) {
                // One line saves vertical space so the full schedule fits.
                column.addContent(text(location, size = citySize, color = 0xFFB0B0B0.toInt()))
            } else {
                val (line1, line2) = splitLocation(location)
                column.addContent(text(line1, size = citySize, color = 0xFFB0B0B0.toInt()))
                if (line2.isNotEmpty()) {
                    column.addContent(text(line2, size = citySize, color = 0xFFB0B0B0.toInt()))
                }
            }
            column.addContent(verticalSpacer(if (compact) 2f else 4f))

            val next = ScheduleUtil.nextPrayer(schedule, settings, now)
            val entries = ScheduleUtil.todayEntries(schedule, settings, now.toLocalDate())
            val today = now.toLocalDate().toString()

            for ((period, time) in entries) {
                val isNext = next?.period == period && next.date == today
                column.addContent(scheduleRow(getString(period.labelRes), time, isNext, rowSize))
                column.addContent(verticalSpacer(rowGap))
            }
        }

        return LayoutElementBuilders.Box.Builder()
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(clickable())
                    .setBackground(background(topColor, bottomColor, cornerRadiusDp))
                    .build(),
            )
            .setWidth(DimensionBuilders.expand())
            .setHeight(DimensionBuilders.expand())
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .addContent(column.build())
            .build()
    }

    /** Compact wide card (2x1) layout: city + next prayer name + time. */
    protected fun buildCompactRoot(cornerRadiusDp: Float): LayoutElementBuilders.LayoutElement {
        val settings = SettingsStore(this).load()
        val schedule = ScheduleRepository(this).loadCachedSchedule()
        val now = LocalDateTime.now()
        val (topColor, bottomColor) = currentSkyColors(schedule, settings, now)

        val columnModifiers = ModifiersBuilders.Modifiers.Builder()
            .setPadding(
                ModifiersBuilders.Padding.Builder()
                    .setStart(DimensionBuilders.dp(12f))
                    .setEnd(DimensionBuilders.dp(12f))
                    .setTop(DimensionBuilders.dp(8f))
                    .setBottom(DimensionBuilders.dp(8f))
                    .build(),
            )
            .build()

        val column = LayoutElementBuilders.Column.Builder()
            .setModifiers(columnModifiers)
            .setWidth(DimensionBuilders.expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)

        if (schedule == null || !settings.hasLocation) {
            column.addContent(text("Buka Usholli", size = 12f))
        } else {
            val location = settings.regionName.ifBlank { settings.regionProv }
            column.addContent(text(location, size = 11f, color = 0xFFB0B0B0.toInt()))
            column.addContent(verticalSpacer(2f))

            val next = ScheduleUtil.nextPrayer(schedule, settings, now)
            if (next != null) {
                column.addContent(text(getString(next.period.labelRes), size = 14f))
                column.addContent(verticalSpacer(2f))
                column.addContent(
                    text(
                        "%02d:%02d".format(next.hour, next.minute),
                        size = 22f,
                        weight = LayoutElementBuilders.FONT_WEIGHT_BOLD,
                    ),
                )
            } else {
                column.addContent(
                    text("--:--", size = 20f, weight = LayoutElementBuilders.FONT_WEIGHT_BOLD),
                )
            }
        }

        return LayoutElementBuilders.Box.Builder()
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(clickable())
                    .setBackground(background(topColor, bottomColor, cornerRadiusDp))
                    .build(),
            )
            .setWidth(DimensionBuilders.expand())
            .setHeight(DimensionBuilders.expand())
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .addContent(column.build())
            .build()
    }

    protected fun scheduleRow(
        label: String,
        time: String,
        highlight: Boolean,
        size: Float = 13f,
    ): LayoutElementBuilders.Row {
        val color = if (highlight) 0xFFFFC107.toInt() else 0xFFFFFFFF.toInt()
        return LayoutElementBuilders.Row.Builder()
            .setWidth(DimensionBuilders.expand())
            .addContent(
                text(label, size = size, weight = LayoutElementBuilders.FONT_WEIGHT_MEDIUM, color = color),
            )
            .addContent(
                LayoutElementBuilders.Spacer.Builder()
                    .setWidth(DimensionBuilders.expand())
                    .build(),
            )
            .addContent(
                text(time, size = size, weight = LayoutElementBuilders.FONT_WEIGHT_BOLD, color = color),
            )
            .build()
    }

    protected fun text(
        value: String,
        size: Float,
        weight: Int = LayoutElementBuilders.FONT_WEIGHT_NORMAL,
        color: Int = 0xFFFFFFFF.toInt(),
        maxLines: Int = 1,
    ): LayoutElementBuilders.Text {
        return LayoutElementBuilders.Text.Builder()
            .setText(value)
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(DimensionBuilders.sp(size))
                    .setWeight(weight)
                    .setColor(ColorBuilders.argb(color))
                    .build(),
            )
            .setMaxLines(maxLines)
            .setOverflow(LayoutElementBuilders.TEXT_OVERFLOW_ELLIPSIZE)
            .build()
    }

    protected fun verticalSpacer(heightDp: Float): LayoutElementBuilders.Spacer {
        return LayoutElementBuilders.Spacer.Builder()
            .setHeight(DimensionBuilders.dp(heightDp))
            .build()
    }

    /** Split a long location name into at most two lines. */
    protected fun splitLocation(location: String): Pair<String, String> {
        val words = location.split(" ").filter { it.isNotBlank() }
        if (words.size <= 1) return location to ""
        val mid = (words.size + 1) / 2
        return words.take(mid).joinToString(" ") to words.drop(mid).joinToString(" ")
    }
}

/** Full-screen tile (the standard Wear OS tile). */
class UsholliTileService : BaseUsholliTileService() {

    companion object {
        /** Ask the system to re-render all Usholli tiles (full + compact cards). */
        fun requestUpdate(context: Context) {
            runCatching {
                val updater = TileService.getUpdater(context)
                updater.requestUpdate(UsholliTileService::class.java)
                updater.requestUpdate(UsholliTileService2x2::class.java)
                updater.requestUpdate(UsholliTileService2x1::class.java)
            }
        }
    }

    override fun buildRootElement(cornerRadiusDp: Float): LayoutElementBuilders.LayoutElement =
        buildScheduleRoot(compact = false, cornerRadiusDp)
}

/** Samsung compact card (2x2): full schedule, tightened to fit. */
class UsholliTileService2x2 : BaseUsholliTileService() {
    override fun buildRootElement(cornerRadiusDp: Float): LayoutElementBuilders.LayoutElement =
        buildScheduleRoot(compact = true, cornerRadiusDp)
}

/** Samsung compact card (2x1, wide): city + next prayer name + time. */
class UsholliTileService2x1 : BaseUsholliTileService() {
    override fun buildRootElement(cornerRadiusDp: Float): LayoutElementBuilders.LayoutElement =
        buildCompactRoot(cornerRadiusDp)
}
