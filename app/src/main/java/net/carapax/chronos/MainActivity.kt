package net.carapax.chronos

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.content.Context
import android.content.Context.LOCATION_SERVICE
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.location.GnssStatusCompat
import androidx.core.location.LocationCompat
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.android.awaitFrame
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import net.carapax.chronos.ui.theme.ChronosTheme
import kotlin.math.absoluteValue
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
    var ttff by rememberSaveable {
        mutableStateOf<Int?>(null)
    }
    var satelliteCount by rememberSaveable {
        mutableIntStateOf(0)
    }
    var satelliteUsedInFixCount by rememberSaveable {
        mutableIntStateOf(0)
    }
    if (locationPermissionsState.allPermissionsGranted) DisposableEffect(Unit) {
        debug("DisposableEffect", "allPermissionsGranted")
        val locationListener = LocationListenerCompat {
            debug("Location(GPS)", it)
            locationTime = LocationTime(it)
        }
        val gnssStatusCallback = object : GnssStatusCompat.Callback() {
            override fun onStarted() = debug("GnssStatusCallback", "onStarted")
            override fun onStopped() = debug("GnssStatusCallback", "onStopped")
            override fun onFirstFix(ttffMillis: Int) {
                debug("GnssStatusCallback", "onFirstFix", ttffMillis)
                ttff = ttffMillis
            }

            override fun onSatelliteStatusChanged(status: GnssStatusCompat) {
                debug(
                    "GnssStatusCallback",
                    "onSatelliteStatusChanged",
                    status.satelliteUsedInFixCount,
                    status.satelliteCount,
                )
                satelliteCount = status.satelliteCount
                satelliteUsedInFixCount = status.satelliteUsedInFixCount
            }
        }
        if (ActivityCompat.checkSelfPermission(
                context, ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                context, ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            LocationManagerCompat.registerGnssStatusCallback(
                locationManager, ContextCompat.getMainExecutor(context), gnssStatusCallback
            )
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                0,
                0F,
                locationListener,
            )
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?.let { locationListener.onLocationChanged(it) }
        }
        onDispose {
            debug("DisposableEffect", "onDispose")
            LocationManagerCompat.unregisterGnssStatusCallback(locationManager, gnssStatusCallback)
            locationManager.removeUpdates(locationListener)
        }
    } else LaunchedEffect(Unit) {
        debug("LaunchedEffect", "launchMultiplePermissionRequest")
        locationPermissionsState.launchMultiplePermissionRequest()
    }
    ChronosTheme {
        Scaffold(modifier = Modifier.fillMaxSize(), floatingActionButton = {
            if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) Row {
                AnimatedVisibility(
                    verbose,
                    enter = slideInHorizontally { it } + fadeIn(),
                    exit = slideOutHorizontally { it } + fadeOut(),
                ) {
                    SmallFloatingActionButton({ magic = !magic }) {
                        AnimatedContent(
                            if (magic) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            transitionSpec = { scaleIn() + fadeIn() togetherWith scaleOut() + fadeOut() },
                            label = ""
                        ) {
                            Icon(it, null)
                        }
                    }
                }
                SmallFloatingActionButton({ verbose = !verbose }) {
                    AnimatedContent(
                        if (verbose) Icons.Default.Close else Icons.Default.Settings,
                        transitionSpec = { scaleIn() + fadeIn() togetherWith scaleOut() + fadeOut() },
                        label = ""
                    ) {
                        Icon(it, null)
                    }
                }
            } else Column {
                AnimatedVisibility(
                    verbose,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                ) {
                    SmallFloatingActionButton({ magic = !magic }) {
                        AnimatedContent(
                            if (magic) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            transitionSpec = { scaleIn() + fadeIn() togetherWith scaleOut() + fadeOut() },
                            label = ""
                        ) {
                            Icon(it, null)
                        }
                    }
                }
                SmallFloatingActionButton({ verbose = !verbose }) {
                    AnimatedContent(
                        if (verbose) Icons.Default.Close else Icons.Default.Settings,
                        transitionSpec = { scaleIn() + fadeIn() togetherWith scaleOut() + fadeOut() },
                        label = ""
                    ) {
                        Icon(it, null)
                    }
                }
            }
        }) { innerPadding ->
            AnimatedVisibility(verbose, enter = fadeIn(), exit = fadeOut()) {
                Column(modifier = Modifier.padding(innerPadding)) {
                    Text(
                        text = "FPS: ${(1.seconds / duration).roundToInt()}",
                        color = MaterialTheme.colorScheme.secondary,
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
                    ttff = ttff,
                    satelliteCount = satelliteCount,
                    satelliteUsedInFixCount = satelliteUsedInFixCount,
                    verbose = verbose,
                    tick = magic,
                    progress = magic,
                )
                AnimatedVisibility(!locationPermissionsState.allPermissionsGranted) {
                    Button(
                        { if (locationPermissionsState.shouldShowRationale) locationPermissionsState.launchMultiplePermissionRequest() else context.startAppSettings() },
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
    ttff: Int? = null,
    satelliteCount: Int = 0,
    satelliteUsedInFixCount: Int = 0,
    system: Boolean = false,
    verbose: Boolean = false,
    tick: Boolean = false,
    progress: Boolean = false,
) {
    val view = LocalView.current
    val fixedLength by animateIntAsState(if (verbose) 3 else 1, label = "")
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!label.isNullOrBlank()) AnimatedVisibility(verbose) {
            Text(
                label,
                fontSize = 14.sp,
                lineHeight = 1.em,
            )
        }
        if (system) {
            AnimatedVisibility(verbose) {
                Text(
                    now.formatLocalDate(),
                    fontSize = 14.sp,
                    lineHeight = 1.em,
                )
            }
            Text(
                now.formatLocalTime(fixedLength).annotateMilliseconds(),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 1.em,
            )
        } else if (locationTime != null) {
            if (locationTime.location.age <= 2.seconds) {
                val time = locationTime.time + (now - locationTime.then)
                if (tick) LaunchedEffect(time.epochSeconds) {
                    debug("LaunchedEffect", "tick")
                    ViewCompat.performHapticFeedback(view, HapticFeedbackConstantsCompat.CLOCK_TICK)
                }
                AnimatedVisibility(verbose) {
                    Text(
                        time.formatLocalDate(),
                        fontSize = 14.sp,
                        lineHeight = 1.em,
                    )
                }
                Text(
                    time.formatLocalTime(fixedLength).annotateMilliseconds(),
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 1.em,
                )
                AnimatedVisibility(progress) {
                    LinearProgressIndicator(
                        { ((time.nanosecondsOfSecond.nanoseconds % 1.seconds) / 1.seconds).toFloat() },
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                Text(
                    (now - time).formatSeconds(fixedLength),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 16.sp,
                    lineHeight = 1.em,
                )
            } else AnimatedContent(
                if (!verbose && !label.isNullOrBlank()) stringResource(
                    R.string.x_no_signal, label
                ) else stringResource(R.string.no_signal),
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = ""
            ) {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 1.em,
                )
            }
            AnimatedVisibility(verbose) {
                Column(
                    modifier = Modifier.padding(top = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        locationTime.location.age.formatSeconds(fixedLength),
                        color = MaterialTheme.colorScheme.tertiary,
                        fontSize = 8.sp,
                        lineHeight = 1.em,
                    )
                    Text(
                        "${
                            pluralStringResource(
                                R.plurals.x_of_y_satellite_signals,
                                satelliteUsedInFixCount,
                                satelliteUsedInFixCount,
                                satelliteCount
                            )
                        }${if (ttff != null) " @${ttff.milliseconds.formatSeconds(fixedLength)}" else ""}",
                        color = MaterialTheme.colorScheme.secondary,
                        fontSize = 8.sp,
                        lineHeight = 1.em,
                    )
                    Text(
                        locationTime.location.string,
                        color = MaterialTheme.colorScheme.secondary,
                        fontSize = 8.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 1.em,
                    )
                }
            }
        } else AnimatedContent(
            if (!verbose && !label.isNullOrBlank()) stringResource(
                R.string.x_not_available, label
            ) else stringResource(R.string.not_available),
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = ""
        ) {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 1.em,
            )
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
private fun rememberNow(): Pair<Instant, Duration> {
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
    return Pair(now, duration.nanoseconds)
}

private data class LocationTime(
    val location: Location,
    val time: Instant = location.time + location.age,
    val then: Instant = now(),
)

private fun now() = Clock.System.now()
private fun elapsedRealtimeNanos() = SystemClock.elapsedRealtimeNanos()

private fun String.annotateMilliseconds(): AnnotatedString {
    val parts = this.split('.')
    if (parts.size < 2) return AnnotatedString(this)
    return buildAnnotatedString {
        append(parts[0])
        append('.')
        withStyle(style = SpanStyle(color = Color.Red)) {
            append(parts[1])
        }
    }
}

private operator fun Duration.rem(duration: Duration) =
    (this.inWholeNanoseconds % duration.inWholeNanoseconds).nanoseconds

private fun Duration.formatSeconds(fixedLength: Int = 3) = "${
    when {
        isPositive() -> '+'
        isNegative() -> '-'
        else -> ""
    }
}${this.inWholeMilliseconds.absoluteValue.formatSeconds(fixedLength)}"

private val Instant.localDateTime get() = this.toLocalDateTime(TimeZone.currentSystemDefault())
private fun Instant.formatLocalDate() = LocalDateTime.Format {
    year()
    char('-')
    monthNumber()
    char('-')
    dayOfMonth()
}.format(this.localDateTime)

private fun Instant.formatLocalTime(fixedLength: Int = 3) = LocalDateTime.Format {
    hour()
    char(':')
    minute()
    char(':')
    second()
    char('.')
    secondFraction(fixedLength)
}.format(this.localDateTime)

private fun Double.fixed(fixedLength: Int = 3) =
    String.format("%.${fixedLength.coerceAtLeast(0)}f", this)

private operator fun Long.plus(duration: Duration) = Instant.fromEpochMilliseconds(this) + duration
private fun Long.formatSeconds(fixedLength: Int = 3) = (this / 1E3).fixed(fixedLength)

private val Location.age get() = (elapsedRealtimeNanos() - this.elapsedRealtimeNanos).nanoseconds
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
                if (hasVerticalAccuracy()) {
                    append(' ')
                    append("${getVerticalAccuracyMeters()}m")
                }
            }
            if (hasAltitude()) {
                append('\n')
                append("${altitude}m")
                if (hasMslAltitude()) {
                    append(' ')
                    append("${getMslAltitudeMeters()}m")
                    if (hasMslAltitudeAccuracy()) {
                        append(' ')
                        append("${getMslAltitudeAccuracyMeters()}m")
                    }
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
        for (i in 0..<satelliteCount) {
            if (usedInFix(i)) count++
        }
        count
    }

private fun Context.startAppSettings() = startActivity(
    Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)
    )
)

private const val TAG = "MainActivity"
private fun debug(vararg msg: Any?) {
    Log.d(TAG, msg.joinToString(" "))
}