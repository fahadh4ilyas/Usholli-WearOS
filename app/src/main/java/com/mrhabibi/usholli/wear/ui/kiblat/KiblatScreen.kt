package com.mrhabibi.usholli.wear.ui.kiblat

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.wear.compose.material.Text
import com.mrhabibi.usholli.wear.R
import com.mrhabibi.usholli.wear.ui.theme.OnBackground
import com.mrhabibi.usholli.wear.ui.theme.TextDim
import com.mrhabibi.usholli.wear.util.Qibla
import java.util.Locale

// Mobile app's compass colours (colorPrimary / colorAccent).
private val CompassBlue = Color(0xFF4F83CC)
private val CompassBlueDark = Color(0xFF01579B)

private const val ALIGN_THRESHOLD_DEG = 5f

@Composable
fun KiblatScreen(navController: NavHostController) {
    val context = LocalContext.current

    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var qibla by remember { mutableStateOf<Double?>(null) }
    var headingDegrees by remember { mutableStateOf(0f) }
    var aligned by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionGranted = granted
        if (!granted) error = context.getString(R.string.location_permission_needed)
    }

    LaunchedEffect(Unit) {
        if (!permissionGranted) permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    // Sensor listener: accelerometer + magnetometer -> smoothed device heading,
    // with a vibration + alignment flag when the watch points at the Kaaba.
    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        if (accelerometer == null || magnetometer == null) {
            error = context.getString(R.string.sensor_not_found)
            onDispose { }
        } else {
            val listener = object : SensorEventListener {
                private val accel = FloatArray(3)
                private val magnet = FloatArray(3)
                private var hasAccel = false
                private var hasMagnet = false
                private var wasAligned = false
                private var lastVibrate = 0L

                override fun onSensorChanged(event: SensorEvent) {
                    when (event.sensor.type) {
                        Sensor.TYPE_ACCELEROMETER -> {
                            System.arraycopy(event.values, 0, accel, 0, event.values.size)
                            hasAccel = true
                        }
                        Sensor.TYPE_MAGNETIC_FIELD -> {
                            System.arraycopy(event.values, 0, magnet, 0, event.values.size)
                            hasMagnet = true
                        }
                    }
                    if (hasAccel && hasMagnet) {
                        val r = FloatArray(9)
                        val i = FloatArray(9)
                        if (SensorManager.getRotationMatrix(r, i, accel, magnet)) {
                            val orientation = FloatArray(3)
                            SensorManager.getOrientation(r, orientation)
                            val raw = Math.toDegrees(orientation[0].toDouble()).toFloat()

                            // Low-pass filter to reduce jitter.
                            val smoothed = smoothAngle(headingDegrees, raw, 0.15f)
                            headingDegrees = smoothed

                            val qiblaDegrees = qibla?.toFloat()
                            if (qiblaDegrees != null) {
                                val diff = angleDiff(smoothed, qiblaDegrees)
                                val isAligned = Math.abs(diff) < ALIGN_THRESHOLD_DEG
                                if (isAligned != wasAligned) {
                                    wasAligned = isAligned
                                    aligned = isAligned
                                    if (isAligned) {
                                        val now = System.currentTimeMillis()
                                        if (now - lastVibrate > 3000L) {
                                            vibrate(context)
                                            lastVibrate = now
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
            sensorManager.registerListener(listener, magnetometer, SensorManager.SENSOR_DELAY_UI)
            onDispose { sensorManager.unregisterListener(listener) }
        }
    }

    // Location -> qibla bearing.
    LaunchedEffect(permissionGranted) {
        if (!permissionGranted) return@LaunchedEffect
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val last = lastKnownLocation(lm)
        if (last != null) {
            qibla = Qibla.bearing(last.latitude, last.longitude)
        } else {
            requestLocationUpdate(context, lm) { loc ->
                qibla = Qibla.bearing(loc.latitude, loc.longitude)
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))

        val qiblaDegrees = qibla?.toFloat() ?: 0f

        // Position the Kaaba icon at the edge of the compass, in the qibla direction.
        val qiblaRad = Math.toRadians((qiblaDegrees - 90.0).toDouble())
        val kaabaOffsetX = (72.0 * Math.cos(qiblaRad)).dp
        val kaabaOffsetY = (72.0 * Math.sin(qiblaRad)).dp

        Box(
            modifier = Modifier.size(190.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Rotating dial: cardinal labels rotate with the device orientation,
            // so "U" (north) always points to true north.
            Box(Modifier.fillMaxSize().rotate(-headingDegrees)) {
                Canvas(Modifier.fillMaxSize()) {
                    val radius = size.minDimension / 2f
                    val center = Offset(size.width / 2f, size.height / 2f)

                    // Outer + inner rings (blue compass theme).
                    drawCircle(color = CompassBlue, radius = radius - 2.dp.toPx(), style = Stroke(width = 4.dp.toPx()))
                    drawCircle(color = CompassBlueDark, radius = radius * 0.42f, style = Stroke(width = 2.dp.toPx()))

                    // Diagonal tick marks.
                    val inner = radius * 0.55f
                    val outer = radius * 0.62f
                    for (deg in listOf(45f, 135f, 225f, 315f)) {
                        val rad = Math.toRadians(deg.toDouble())
                        val start = Offset(
                            center.x + inner * Math.cos(rad).toFloat(),
                            center.y + inner * Math.sin(rad).toFloat(),
                        )
                        val end = Offset(
                            center.x + outer * Math.cos(rad).toFloat(),
                            center.y + outer * Math.sin(rad).toFloat(),
                        )
                        drawLine(color = CompassBlue, start = start, end = end, strokeWidth = 3.dp.toPx())
                    }

                    // Blue line from center to the Kaaba when aligned.
                    if (aligned) {
                        val kaabaRadius = 72.dp.toPx()
                        val end = Offset(
                            center.x + kaabaRadius * Math.cos(qiblaRad).toFloat(),
                            center.y + kaabaRadius * Math.sin(qiblaRad).toFloat(),
                        )
                        drawLine(color = CompassBlue, start = center, end = end, strokeWidth = 5.dp.toPx())
                    }
                }
                Text("U", color = OnBackground, modifier = Modifier.align(Alignment.TopCenter).padding(top = 6.dp))
                Text("S", color = OnBackground, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp))
                Text("B", color = OnBackground, modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp))
                Text("T", color = OnBackground, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp))
                // Kaaba icon: placed at the edge of the dial, pointing toward the qibla.
                Image(
                    painter = painterResource(R.drawable.ic_kiblat),
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(36.dp)
                        .offset(x = kaabaOffsetX, y = kaabaOffsetY)
                        .rotate(qiblaDegrees),
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        when {
            error != null -> Text(
                text = error!!,
                color = TextDim,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            qibla == null -> Text(
                text = stringResource(R.string.detecting_location),
                color = TextDim,
                fontSize = 14.sp,
            )
            else -> Text(
                text = stringResource(
                    R.string.degree_angle,
                    String.format(Locale.US, "%.2f", qibla!!),
                ),
                color = OnBackground,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** Smooth a heading value, handling the -180/180 wrap-around. */
private fun smoothAngle(current: Float, target: Float, alpha: Float): Float {
    var diff = target - current
    while (diff > 180f) diff -= 360f
    while (diff < -180f) diff += 360f
    return current + diff * alpha
}

/** Shortest signed angular difference (degrees) in [-180, 180]. */
private fun angleDiff(a: Float, b: Float): Float {
    var diff = (a - b) % 360f
    if (diff > 180f) diff -= 360f
    if (diff < -180f) diff += 360f
    return diff
}

private fun vibrate(context: Context) {
    val vibrator = context.getSystemService(Vibrator::class.java) ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(120)
    }
}

private fun lastKnownLocation(lm: LocationManager): Location? {
    val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
    for (p in providers) {
        runCatching {
            lm.getLastKnownLocation(p)?.let { return it }
        }
    }
    return null
}

private fun requestLocationUpdate(context: Context, lm: LocationManager, onResult: (Location) -> Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val executor = ContextCompat.getMainExecutor(context)
        val consumer = java.util.function.Consumer<Location> { location ->
            if (location != null) onResult(location)
        }
        runCatching {
            lm.getCurrentLocation(LocationManager.NETWORK_PROVIDER, null, executor, consumer)
            lm.getCurrentLocation(LocationManager.GPS_PROVIDER, null, executor, consumer)
        }
    } else {
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                onResult(location)
                runCatching { lm.removeUpdates(this) }
            }
            override fun onProviderDisabled(provider: String) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }
        @Suppress("DEPRECATION")
        runCatching {
            lm.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, listener, null)
            lm.requestSingleUpdate(LocationManager.GPS_PROVIDER, listener, null)
        }
    }
}
