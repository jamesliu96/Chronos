package net.carapax.chronos

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.content.Context
import android.content.Context.LOCATION_SERVICE
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.location.LocationListenerCompat
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
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
import kotlin.time.Duration

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
    val context = LocalContext.current

    val locationManager = context.getSystemService(LOCATION_SERVICE) as LocationManager

    var verbose by remember {
        mutableStateOf(false)
    }
    var magic by remember {
        mutableStateOf(false)
    }

    val settings = context.settings
    val magicKey = booleanPreferencesKey("magic")
    LaunchedEffect(Unit) {
        settings.data.collect {
            magic = it[magicKey] ?: false
        }
    }
    LaunchedEffect(magic) {
        settings.edit {
            it[magicKey] = magic
        }
    }

    val now = rememberNow()

    val locationPermissionsState =
        rememberMultiplePermissionsState(listOf(ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION))

    var gpsLocationTime by remember {
        mutableStateOf<LocationTime?>(null)
    }
    var netLocationTime by remember {
        mutableStateOf<LocationTime?>(null)
    }

    if (locationPermissionsState.allPermissionsGranted) DisposableEffect(locationPermissionsState) {
        val gpsListener = LocationListenerCompat {
            Log.d("MainActivity", "Location(GPS): $it")
            gpsLocationTime = LocationTime(it)
        }
        val netListener = LocationListenerCompat {
            Log.d("MainActivity", "Location(Net): $it")
            netLocationTime = LocationTime(it)
        }
        if (ActivityCompat.checkSelfPermission(
                context, ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                context, ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                0,
                0F,
                gpsListener,
            )
            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                0,
                0F,
                netListener,
            )
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?.let { gpsListener.onLocationChanged(it) }
            locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?.let { netListener.onLocationChanged(it) }
        }
        onDispose {
            locationManager.removeUpdates(gpsListener)
            locationManager.removeUpdates(netListener)
        }
    } else LaunchedEffect(Unit) {
        locationPermissionsState.launchMultiplePermissionRequest()
    }

    KeepScreenOn()

    ChronosTheme {
        Scaffold(modifier = Modifier.fillMaxSize(), floatingActionButton = {
            Column {
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
                    local = true,
                    verbose = verbose,
                )
                Spacer(modifier = Modifier.height(8.dp))
                LocationTime(
                    now = now,
                    label = stringResource(R.string.gps),
                    locationTime = gpsLocationTime,
                    unique = true,
                    verbose = verbose,
                    tick = magic,
                )
                AnimatedVisibility(verbose) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LocationTime(
                            now = now,
                            label = stringResource(R.string.network),
                            locationTime = netLocationTime,
                            verbose = verbose,
                        )
                    }
                }
                AnimatedVisibility(!locationPermissionsState.allPermissionsGranted) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Button({
                            if (locationPermissionsState.shouldShowRationale) locationPermissionsState.launchMultiplePermissionRequest()
                            else context.startAppSettings()
                        }) {
                            Text(
                                if (locationPermissionsState.permissions.size == locationPermissionsState.revokedPermissions.size) stringResource(
                                    R.string.grant_permissions
                                ) else stringResource(R.string.allow_fine_location)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LocationTime(
    now: Instant,
    label: String,
    locationTime: LocationTime? = null,
    local: Boolean = false,
    unique: Boolean = false,
    verbose: Boolean = false,
    tick: Boolean = false,
) {
    val view = LocalView.current

    val fixedLength by animateIntAsState(if (verbose) 3 else 1, label = "")

    AnimatedVisibility(verbose) {
        Text(label, fontSize = 16.sp)
    }
    if (local) {
        AnimatedVisibility(verbose) {
            Text(
                now.formatLocalDate(),
                fontSize = 16.sp,
            )
        }
        Text(
            now.formatLocalTime(fixedLength).annotateMs(),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
        )
    } else if (locationTime != null) {
        val elapsed = now - locationTime.then
        val timestamp = locationTime.time + elapsed
        val delta = now - timestamp
        AnimatedVisibility(verbose) {
            Text(
                timestamp.formatLocalDate(),
                fontSize = 16.sp,
            )
        }
        Text(
            timestamp.formatLocalTime(fixedLength).annotateMs(),
            fontSize = (if (unique) 40 else 32).sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            delta.formatSecond(fixedLength),
            color = MaterialTheme.colorScheme.primary,
            fontSize = (if (unique) 24 else 16).sp,
        )
        Text(
            elapsed.formatSecond(fixedLength),
            color = MaterialTheme.colorScheme.secondary,
            fontSize = 8.sp,
            fontStyle = FontStyle.Italic,
            lineHeight = 1.em,
        )
        Text(
            locationTime.location.format(verbose),
            color = MaterialTheme.colorScheme.tertiary,
            fontSize = 6.sp,
            textAlign = TextAlign.Center,
            lineHeight = 1.em,
        )
        if (tick) LaunchedEffect(timestamp.epochSeconds) {
            view.performHapticFeedback(HapticFeedbackConstantsCompat.CLOCK_TICK)
        }
    } else Text(
        if (!verbose) stringResource(
            R.string.x_not_available, label
        ) else stringResource(R.string.not_available),
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.error,
    )
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
private fun rememberNow(): Instant {
    var now by remember {
        mutableStateOf(now())
    }
    LaunchedEffect(Unit) {
        while (true) {
            awaitFrame()
            now = now()
        }
    }
    return now
}

private data class LocationTime(
    val location: Location,
    val time: Long = location.time + (elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000L,
    val then: Instant = now(),
)

private fun now() = Clock.System.now()
private fun elapsedRealtimeNanos() = SystemClock.elapsedRealtimeNanos()

private fun String.annotateMs(): AnnotatedString {
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

private fun Duration.formatSecond(fixedLength: Int = 3) = "${
    when {
        isPositive() -> "+"
        isNegative() -> "-"
        else -> ""
    }
}${this.inWholeMilliseconds.formatSecond(fixedLength)}"

private fun Double.fixed(fixedLength: Int = 3) = String.format("%.${fixedLength}f", this)

private val Long.instant get() = Instant.fromEpochMilliseconds(this)
private operator fun Long.plus(duration: Duration) = this.instant + duration
private fun Long.formatSecond(fixedLength: Int = 3) = (this / 1E3).fixed(fixedLength)

private fun Location.format(verbose: Boolean = false) = buildString {
    append("${latitude}°/$longitude°")
    if (verbose) {
        append(' ')
        append('(')
        append(if (hasAccuracy()) "${accuracy}m" else "N/A")
        append('/')
        append(if (hasVerticalAccuracy()) "${verticalAccuracyMeters}m" else "N/A")
        append(')')
    }
    append('\n')
    append(if (hasAltitude()) "${altitude}m" else "N/A")
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        append('\n')
        append(if (hasMslAltitude()) "${mslAltitudeMeters}m" else "N/A")
        if (verbose) {
            append(' ')
            append('(')
            append(if (hasMslAltitudeAccuracy()) "${mslAltitudeAccuracyMeters}m" else "N/A")
            append(')')
        }
    }
    append('\n')
    append(if (hasBearing()) "${bearing}°" else "N/A")
    if (verbose) {
        append(' ')
        append('(')
        append(if (hasBearingAccuracy()) "${bearingAccuracyDegrees}°" else "N/A")
        append(')')
    }
    append('\n')
    append(if (hasSpeed()) "${speed}m/s" else "N/A")
    if (verbose) {
        append(' ')
        append('(')
        append(if (hasSpeedAccuracy()) "${speedAccuracyMetersPerSecond}m/s" else "N/A")
        append(')')
    }
}

private val Context.settings by preferencesDataStore("settings")
private fun Context.startAppSettings() = startActivity(
    Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)
    )
)