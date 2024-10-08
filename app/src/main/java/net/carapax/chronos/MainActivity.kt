package net.carapax.chronos

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.content.Context
import android.content.Context.LOCATION_SERVICE
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import android.location.Location
import android.location.LocationManager
import android.location.LocationManager.GPS_PROVIDER
import android.net.Uri.fromParts
import android.os.Bundle
import android.os.SystemClock.elapsedRealtimeNanos
import android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color.Companion.Red
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight.Companion.Bold
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat.checkSelfPermission
import androidx.core.content.ContextCompat.getMainExecutor
import androidx.core.location.GnssStatusCompat
import androidx.core.location.LocationCompat
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat.registerGnssStatusCallback
import androidx.core.location.LocationManagerCompat.unregisterGnssStatusCallback
import androidx.core.view.HapticFeedbackConstantsCompat.CLOCK_TICK
import androidx.core.view.ViewCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.lyft.kronos.AndroidClockFactory
import com.lyft.kronos.KronosClock
import com.lyft.kronos.SyncListener
import kotlinx.coroutines.android.awaitFrame
import kotlinx.datetime.Clock.System.now
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import net.carapax.chronos.ui.theme.ChronosTheme
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            App()
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun App() {
    KeepScreenOn()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val locationManager = context.getSystemService(LOCATION_SERVICE) as LocationManager
    var verbose by rememberSaveable {
        mutableStateOf(false)
    }
    var magic by rememberSaveable {
        mutableStateOf(false)
    }
    val (now, duration) = rememberNow()
    val locationPermissionsState =
        rememberMultiplePermissionsState(listOf(ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION))
    var locationTime by remember {
        mutableStateOf<LocationTime?>(null)
    }
    var timeToFirstFix by remember {
        mutableStateOf<Duration?>(null)
    }
    var satelliteStatus by remember {
        mutableStateOf<GnssStatusCompat?>(null)
    }
    if (locationPermissionsState.allPermissionsGranted) DisposableEffect(Unit) {
        debug("DisposableEffect", "allPermissionsGranted")
        val locationListener = LocationListenerCompat {
            debug("Location(GPS)", it)
            locationTime = LocationTime(it)
        }
        val gpsStatusCallback = object : GnssStatusCompat.Callback() {
            override fun onStarted() = debug("GPSStatusCallback", "onStarted")
            override fun onStopped() = debug("GPSStatusCallback", "onStopped")
            override fun onFirstFix(ttffMillis: Int) {
                debug("GPSStatusCallback", "onFirstFix", ttffMillis)
                timeToFirstFix = ttffMillis.milliseconds
            }

            override fun onSatelliteStatusChanged(status: GnssStatusCompat) {
                debug(
                    "GPSStatusCallback",
                    "onSatelliteStatusChanged",
                    status.satelliteUsedInFixCount,
                    status.satelliteCount,
                )
                satelliteStatus = status
            }
        }
        if (checkSelfPermission(
                context,
                ACCESS_COARSE_LOCATION,
            ) == PERMISSION_GRANTED && checkSelfPermission(
                context,
                ACCESS_FINE_LOCATION,
            ) == PERMISSION_GRANTED
        ) {
            registerGnssStatusCallback(
                locationManager,
                getMainExecutor(context),
                gpsStatusCallback,
            )
            locationManager.requestLocationUpdates(
                GPS_PROVIDER,
                0,
                0f,
                locationListener,
            )
            locationManager.getLastKnownLocation(GPS_PROVIDER)
                ?.let { locationListener.onLocationChanged(it) }
        }
        onDispose {
            debug("DisposableEffect", "onDispose")
            unregisterGnssStatusCallback(locationManager, gpsStatusCallback)
            locationManager.removeUpdates(locationListener)
        }
    } else LaunchedEffect(Unit) {
        debug("LaunchedEffect", "launchMultiplePermissionRequest")
        locationPermissionsState.launchMultiplePermissionRequest()
    }
    val kronosClock = remember {
        AndroidClockFactory.createKronosClock(
            context.applicationContext,
            syncListener = object : SyncListener {
                override fun onError(host: String, throwable: Throwable) =
                    debug("SyncListener", "onError", host, throwable)

                override fun onStartSync(host: String) = debug("SyncListener", "onStartSync", host)
                override fun onSuccess(ticksDelta: Long, responseTimeMs: Long) =
                    debug("SyncListener", "onSuccess", ticksDelta, responseTimeMs)
            })
    }
    LaunchedEffect(Unit) {
        debug("LaunchedEffect", "syncInBackground")
        kronosClock.syncInBackground()
    }
    ChronosTheme {
        Scaffold(modifier = Modifier.fillMaxSize(), floatingActionButton = {
            if (configuration.orientation == ORIENTATION_LANDSCAPE) Row {
                FloatActionButtons(verbose = verbose,
                    onVerboseChange = { verbose = it },
                    magic = magic,
                    onMagicChange = { magic = it },
                    landscape = true,
                    onRefresh = {
                        debug("onRefresh", "syncInBackground")
                        kronosClock.syncInBackground()
                    })
            } else Column {
                FloatActionButtons(verbose = verbose,
                    onVerboseChange = { verbose = it },
                    magic = magic,
                    onMagicChange = { magic = it },
                    landscape = false,
                    onRefresh = {
                        debug("onRefresh", "syncInBackground")
                        kronosClock.syncInBackground()
                    })
            }
        }) { innerPadding ->
            AnimatedVisibility(
                verbose,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Column(modifier = Modifier.padding(innerPadding)) {
                    Text(
                        text = "FPS: ${(1.seconds / duration).roundToInt()}",
                        color = colorScheme.secondary,
                        fontSize = 8.sp,
                        lineHeight = 1.em,
                    )
                }
            }
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                LocationTime(
                    now = now,
                    label = stringResource(R.string.system),
                    system = true,
                    verbose = verbose,
                )
                AnimatedVisibility(verbose) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
                LocationTime(
                    now = now,
                    label = stringResource(R.string.gps),
                    locationTime = locationTime,
                    timeToFirstFix = timeToFirstFix,
                    satelliteStatus = satelliteStatus,
                    verbose = verbose,
                    tick = magic,
                    progress = magic,
                )
                AnimatedVisibility(verbose) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
                val locationTimeUnavailable =
                    locationTime?.location?.age?.let { it > 2.seconds } ?: false
                LocationTime(
                    now = now,
                    label = stringResource(R.string.network),
                    kronosClock = kronosClock,
                    verbose = verbose,
                    tick = magic && locationTimeUnavailable,
                    progress = magic && locationTimeUnavailable,
                )
                AnimatedVisibility(!locationPermissionsState.allPermissionsGranted) {
                    Button(
                        {
                            if (locationPermissionsState.shouldShowRationale) locationPermissionsState.launchMultiplePermissionRequest()
                            else context.startAppSettings()
                        },
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Text(
                            if (locationPermissionsState.permissions.size == locationPermissionsState.revokedPermissions.size) stringResource(
                                R.string.grant_permissions
                            ) else stringResource(R.string.allow_fine_location),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LocationTime(
    now: Instant,
    label: String? = null,
    locationTime: LocationTime? = null,
    timeToFirstFix: Duration? = null,
    satelliteStatus: GnssStatusCompat? = null,
    kronosClock: KronosClock? = null,
    system: Boolean = false,
    verbose: Boolean = false,
    tick: Boolean = false,
    progress: Boolean = false,
) {
    val view = LocalView.current
    val fixedLength by animateIntAsState(if (verbose) 3 else 1, label = "")
    if (!label.isNullOrBlank()) AnimatedVisibility(verbose) {
        Text(
            label,
            fontSize = 12.sp,
            lineHeight = 1.em,
        )
    }
    if (system) {
        AnimatedVisibility(verbose) {
            Text(
                now.formatLocalDate(),
                fontSize = 12.sp,
                lineHeight = 1.em,
            )
        }
        Text(
            now.formatLocalTime(fixedLength).annotatedMilliseconds,
            fontSize = 20.sp,
            fontWeight = Bold,
            lineHeight = 1.em,
        )
    } else {
        AnimatedVisibility(locationTime != null) {
            if (locationTime == null) return@AnimatedVisibility
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val age = locationTime.location.age
                AnimatedVisibility(age <= 2.seconds) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val time = locationTime.time + (now - locationTime.then)
                        if (tick) LaunchedEffect(time.epochSeconds) {
                            debug("LaunchedEffect", "tick")
                            ViewCompat.performHapticFeedback(view, CLOCK_TICK)
                        }
                        AnimatedVisibility(verbose) {
                            Text(
                                time.formatLocalDate(),
                                fontSize = 12.sp,
                                lineHeight = 1.em,
                            )
                        }
                        Text(
                            time.formatLocalTime(fixedLength).annotatedMilliseconds,
                            fontSize = 48.sp,
                            fontWeight = Bold,
                            lineHeight = 1.em,
                        )
                        AnimatedVisibility(progress) {
                            LinearProgressIndicator(
                                { (time.nanosecondsOfSecond.nanoseconds / 1.seconds).toFloat() },
                                modifier = Modifier.padding(bottom = 6.dp),
                                strokeCap = StrokeCap.Butt,
                                gapSize = 0.dp,
                                drawStopIndicator = {},
                            )
                        }
                        Text(
                            (time - now).formatSeconds(fixedLength),
                            color = colorScheme.primary,
                            fontSize = 16.sp,
                            lineHeight = 1.em,
                        )
                    }
                }
                AnimatedVisibility(age > 2.seconds) {
                    AnimatedContent(
                        if (!verbose && !label.isNullOrBlank()) stringResource(
                            R.string.x_no_signal, label
                        ) else stringResource(R.string.no_signal),
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "",
                    ) {
                        Text(
                            it,
                            color = colorScheme.error,
                            fontSize = 24.sp,
                            fontWeight = Bold,
                            lineHeight = 1.em,
                        )
                    }
                }
                AnimatedVisibility(verbose) {
                    Column(
                        modifier = Modifier.padding(top = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            age.formatSeconds(fixedLength),
                            color = colorScheme.tertiary,
                            fontSize = 8.sp,
                            lineHeight = 1.em,
                        )
                        Text(
                            "${satelliteStatus?.satelliteUsedInFixCount ?: 0}/${satelliteStatus?.satelliteCount ?: 0}${
                                if (timeToFirstFix != null) " ${
                                    timeToFirstFix.formatSeconds(fixedLength)
                                }" else ""
                            }",
                            color = colorScheme.secondary,
                            fontSize = 8.sp,
                            lineHeight = 1.em,
                        )
                        Text(
                            locationTime.location.string,
                            color = colorScheme.secondary,
                            fontSize = 8.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 1.em,
                        )
                    }
                }
            }
        }
        val kronosClockTime = kronosClock?.getCurrentNtpTimeMs()
        AnimatedVisibility(kronosClockTime != null) {
            if (kronosClockTime == null) return@AnimatedVisibility
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val time = kronosClockTime.instant
                if (tick) LaunchedEffect(time.epochSeconds) {
                    debug("LaunchedEffect", "tick")
                    ViewCompat.performHapticFeedback(view, CLOCK_TICK)
                }
                AnimatedVisibility(verbose) {
                    Text(
                        time.formatLocalDate(),
                        fontSize = 12.sp,
                        lineHeight = 1.em,
                    )
                }
                Text(
                    time.formatLocalTime(fixedLength).annotatedMilliseconds,
                    fontSize = 30.sp,
                    fontWeight = Bold,
                    lineHeight = 1.em,
                )
                AnimatedVisibility(progress) {
                    LinearProgressIndicator(
                        { (time.nanosecondsOfSecond.nanoseconds / 1.seconds).toFloat() },
                        modifier = Modifier.padding(bottom = 3.dp),
                        strokeCap = StrokeCap.Butt,
                        gapSize = 0.dp,
                        drawStopIndicator = {},
                    )
                }
                Text(
                    (time - now).formatSeconds(fixedLength),
                    color = colorScheme.primary,
                    fontSize = 16.sp,
                    lineHeight = 1.em,
                )
                AnimatedVisibility(verbose) {
                    Text(
                        (kronosClock.getCurrentTime().timeSinceLastNtpSyncMs
                            ?: 0).milliseconds.formatSeconds(fixedLength),
                        color = colorScheme.tertiary,
                        fontSize = 8.sp,
                        lineHeight = 1.em,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
        AnimatedVisibility(locationTime == null && kronosClockTime == null) {
            AnimatedContent(
                if (!verbose && !label.isNullOrBlank()) stringResource(
                    R.string.x_not_available, label
                ) else stringResource(R.string.not_available),
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "",
            ) {
                Text(
                    it,
                    color = colorScheme.error,
                    fontSize = 24.sp,
                    fontWeight = Bold,
                    lineHeight = 1.em,
                )
            }
        }
    }
}

@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
        }
    }
}

@Composable
private fun rememberNow() = run {
    var now by remember {
        mutableStateOf(now())
    }
    var frame by remember {
        mutableLongStateOf(0)
    }
    var duration by remember {
        mutableLongStateOf(0)
    }
    LaunchedEffect(Unit) {
        while (true) {
            val prev = frame
            frame = awaitFrame()
            duration = frame - prev
            now = now()
        }
    }
    now to duration.nanoseconds
}

@Composable
private fun FloatActionButtons(
    verbose: Boolean = false,
    onVerboseChange: (Boolean) -> Unit = {},
    magic: Boolean = false,
    onMagicChange: (Boolean) -> Unit = {},
    landscape: Boolean = false,
    onRefresh: () -> Unit = {}
) {
    AnimatedVisibility(
        verbose,
        enter = (if (landscape) slideInHorizontally { it } else slideInVertically { it }) + fadeIn(),
        exit = (if (landscape) slideOutHorizontally { it } else slideOutVertically { it }) + fadeOut(),
    ) {
        SmallFloatingActionButton({ onRefresh() }) {
            Icon(Icons.Default.Refresh, null)
        }
    }
    AnimatedVisibility(
        verbose,
        enter = (if (landscape) slideInHorizontally { it } else slideInVertically { it }) + fadeIn(),
        exit = (if (landscape) slideOutHorizontally { it } else slideOutVertically { it }) + fadeOut(),
    ) {
        SmallFloatingActionButton({ onMagicChange(!magic) }) {
            AnimatedContent(
                if (magic) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                transitionSpec = { scaleIn() + fadeIn() togetherWith scaleOut() + fadeOut() },
                label = "",
            ) {
                Icon(it, null)
            }
        }
    }
    SmallFloatingActionButton({ onVerboseChange(!verbose) }) {
        AnimatedContent(
            if (verbose) Icons.Default.Close else Icons.Default.Settings,
            transitionSpec = { scaleIn() + fadeIn() togetherWith scaleOut() + fadeOut() },
            label = "",
        ) {
            Icon(it, null)
        }
    }
}

private data class LocationTime(
    val location: Location,
    val time: Instant = location.time + location.age,
    val then: Instant = now(),
)

private val String.annotatedMilliseconds
    get() = run {
        val parts = split('.', limit = 2)
        if (parts.size < 2) AnnotatedString(this) else buildAnnotatedString {
            append(parts[0])
            append('.')
            withStyle(style = SpanStyle(color = Red)) {
                append(parts[1])
            }
        }
    }

private fun Duration.formatSeconds(fixedLength: Int = 3) = "${
    when {
        isPositive() -> '+'
        isNegative() -> '-'
        else -> ""
    }
}${(absoluteValue.inWholeMilliseconds / 1E3).fixed(fixedLength)}"

private val Instant.localDateTime get() = toLocalDateTime(TimeZone.currentSystemDefault())
private fun Instant.formatLocalDate() = LocalDateTime.Format {
    year()
    char('-')
    monthNumber()
    char('-')
    dayOfMonth()
}.format(localDateTime)

private fun Instant.formatLocalTime(fixedLength: Int = 3) = LocalDateTime.Format {
    hour()
    char(':')
    minute()
    char(':')
    second()
    char('.')
    secondFraction(fixedLength)
}.format(localDateTime)

private fun Double.fixed(fixedLength: Int = 3) =
    String.format("%.${fixedLength.coerceAtLeast(0)}f", this)

private val Long.instant get() = Instant.fromEpochMilliseconds(this)
private operator fun Long.plus(duration: Duration) = this.instant + duration
private val Location.age get() = (elapsedRealtimeNanos() - elapsedRealtimeNanos).nanoseconds
private val Location.string
    get() = run {
        val hasVerticalAccuracy = { LocationCompat.hasVerticalAccuracy(this) }
        val getVerticalAccuracyMeters = { LocationCompat.getVerticalAccuracyMeters(this) }
        val hasMslAltitude = { LocationCompat.hasMslAltitude(this) }
        val getMslAltitudeMeters = { LocationCompat.getMslAltitudeMeters(this) }
        val hasMslAltitudeAccuracy = { LocationCompat.hasMslAltitudeAccuracy(this) }
        val getMslAltitudeAccuracyMeters = { LocationCompat.getMslAltitudeAccuracyMeters(this) }
        val hasBearingAccuracy = { LocationCompat.hasBearingAccuracy(this) }
        val getBearingAccuracyDegrees = { LocationCompat.getBearingAccuracyDegrees(this) }
        val hasSpeedAccuracy = { LocationCompat.hasSpeedAccuracy(this) }
        val getSpeedAccuracyMetersPerSecond =
            { LocationCompat.getSpeedAccuracyMetersPerSecond(this) }
        buildString {
            append("$latitude° $longitude°")
            if (hasAccuracy()) {
                append(' ')
                append("${accuracy}m")
            }
            if (hasAltitude()) {
                append('\n')
                append("${altitude}m")
                if (hasVerticalAccuracy()) {
                    append(' ')
                    append("${getVerticalAccuracyMeters()}m")
                }
            }
            if (hasMslAltitude()) {
                append('\n')
                append("${getMslAltitudeMeters()}m")
                if (hasMslAltitudeAccuracy()) {
                    append(' ')
                    append("${getMslAltitudeAccuracyMeters()}m")
                }
            }
            if (hasBearing()) {
                append('\n')
                append("$bearing°")
                if (hasBearingAccuracy()) {
                    append(' ')
                    append("${getBearingAccuracyDegrees()}°")
                }
            }
            if (hasSpeed()) {
                append('\n')
                append("${speed}m/s")
                if (hasSpeedAccuracy()) {
                    append(' ')
                    append("${getSpeedAccuracyMetersPerSecond()}m/s")
                }
            }
        }
    }

private val GnssStatusCompat.satelliteUsedInFixCount
    get() = run {
        var count = 0
        for (i in 0..<satelliteCount) if (usedInFix(i)) count++
        count
    }

private fun Context.startAppSettings() = startActivity(
    Intent(
        ACTION_APPLICATION_DETAILS_SETTINGS,
        fromParts("package", packageName, null),
    )
)

private const val TAG = "MainActivity"
private fun debug(vararg msg: Any?) {
    Log.d(TAG, msg.joinToString(" "))
}
