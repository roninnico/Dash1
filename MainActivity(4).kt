package com.dash.radio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.core.app.NotificationManagerCompat
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.*

// ---------------------------------------------------------------- Actividad Principal
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        startService(Intent(this, TripService::class.java))
        setContent {
            DashTheme {
                var screen by remember { mutableStateOf(0) } // 0 = Dash, 1 = Apps, 2+ = Settings
                when {
                    screen == 1 -> AppsScreen { screen = 0 }
                    screen >= 2 -> SettingsScreen(tab = screen - 2) { screen = 0 }
                    else -> Dash(onApps = { screen = 1 }, onSettings = { tab -> screen = 2 + tab })
                }
            }
        }
    }
}

// ---------------------------------------------------------------- NotifListener
class NotifListener : NotificationListenerService()

// ---------------------------------------------------------------- Mantenimiento
data class Maint(val key: String, val lastKm: Double, val everyKm: Int)

val maintNames = linkedMapOf(
    "aceite" to "Aceite del motor",
    "aire" to "Filtro de aire",
    "neum" to "Neumáticos (rotación)",
    "frenos" to "Frenos (revisión)",
    "bateria" to "Batería (revisión)"
)
val maintTips = mapOf(
    "aceite" to "toca cambiar el aceite",
    "aire" to "revisa el filtro de aire",
    "neum" to "toca rotar los neumáticos",
    "frenos" to "revisa los frenos",
    "bateria" to "revisa la batería"
)

// ---------------------------------------------------------------- Store
object Store {
    private lateinit var ctx: Context
    private val sp get() = ctx.getSharedPreferences("dash", Context.MODE_PRIVATE)

    val speed = MutableStateFlow(0f)
    val totalM = MutableStateFlow(0.0)
    val tripM = MutableStateFlow(0.0)
    val model = MutableStateFlow("Mi auto")
    val calib = MutableStateFlow(1.0)
    val layout = MutableStateFlow(0)
    val themeIdx = MutableStateFlow(0)
    val mode = MutableStateFlow(0)
    val userName = MutableStateFlow("Nico")

    fun init(c: Context) {
        ctx = c.applicationContext
        totalM.value = sp.getString("total", "0")?.toDoubleOrNull() ?: 0.0
        tripM.value = sp.getString("trip", "0")?.toDoubleOrNull() ?: 0.0
        model.value = sp.getString("model", "Mi auto") ?: "Mi auto"
        calib.value = sp.getString("calib", "1.0")?.toDoubleOrNull() ?: 1.0
        layout.value = sp.getInt("layout", 0)
        themeIdx.value = sp.getInt("theme", 0)
        mode.value = sp.getInt("mode", 0)
        userName.value = sp.getString("name", "Nico") ?: "Nico"
    }

    fun addDistance(meters: Double) {
        val d = meters * calib.value
        totalM.value += d
        tripM.value += d
        sp.edit().putString("total", totalM.value.toString())
            .putString("trip", tripM.value.toString()).apply()
    }
}

// ---------------------------------------------------------------- MediaHub
object MediaHub {
    class Sess(val pkg: String, val label: String, val ctl: MediaController)

    data class Now(
        val pkg: String = "",
        val label: String = "",
        val title: String = "",
        val artist: String = "",
        val art: Bitmap? = null,
        val playing: Boolean = false,
        val durMs: Long = 0L,
        val posMs: Long = 0L,
        val posAt: Long = 0L,
        val speed: Float = 1f
    )

    val sessions = MutableStateFlow<List<Sess>>(emptyList())
    val picked = MutableStateFlow("")
    val now = MutableStateFlow(Now())

    private var started = false
    private var appCtx: Context? = null
    private var lastKey = ""
    private var lastArt: Bitmap? = null
    private val registered = ArrayList<Pair<MediaController, MediaController.Callback>>()

    fun hasAccess(c: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(c).contains(c.packageName)

    fun ensure(c: Context) {
        if (started || !hasAccess(c)) return
        appCtx = c.applicationContext
        try {
            val m = c.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val comp = ComponentName(c, NotifListener::class.java)
            m.addOnActiveSessionsChangedListener({ list -> reload(list) }, comp)
            reload(m.getActiveSessions(comp))
            started = true
        } catch (e: Exception) {}
    }

    private fun reload(list: List<MediaController>?) {
        for ((c, cb) in registered) {
            try { c.unregisterCallback(cb) } catch (e: Exception) {}
        }
        registered.clear()
        val c = appCtx ?: return
        val pm = c.packageManager
        val out = ArrayList<Sess>()
        for (k in list.orEmpty()) {
            val cb = object : MediaController.Callback() {
                override fun onMetadataChanged(metadata: MediaMetadata?) { refresh() }
                override fun onPlaybackStateChanged(state: PlaybackState?) { refresh() }
            }
            try { k.registerCallback(cb) } catch (e: Exception) {}
            registered.add(Pair(k, cb))
            val label = try {
                pm.getApplicationLabel(pm.getApplicationInfo(k.packageName, 0)).toString()
            } catch (e: Exception) {
                k.packageName
            }
            out.add(Sess(k.packageName, label, k))
        }
        sessions.value = out
        refresh()
    }

    private fun chosen(): Sess? {
        val l = sessions.value
        val p = picked.value
        if (p.isNotEmpty()) {
            val m = l.firstOrNull { it.pkg == p }
            if (m != null) return m
        }
        return l.firstOrNull()
    }

    fun refresh() {
        val s = chosen()
        if (s == null) {
            now.value = Now()
            return
        }
        val meta = s.ctl.metadata
        val state = s.ctl.playbackState
        val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
        val artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
        val dur = meta?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val art = meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_ART)

        val kStr = s.pkg + "|" + title + "|" + artist
        val finalArt = if (kStr == lastKey) lastArt else {
            lastKey = kStr
            lastArt = art
            art
        }

        val playing = state?.state == PlaybackState.STATE_PLAYING
        val pos = state?.position ?: 0L
        val posAt = state?.lastPositionUpdateTime ?: SystemClock.elapsedRealtime()
        val spd = state?.playbackSpeed ?: 1f

        now.value = Now(s.pkg, s.label, title, artist, finalArt, playing, dur, pos, posAt, spd)
    }

    fun play() { chosen()?.ctl?.transportControls?.play() }
    fun pause() { chosen()?.ctl?.transportControls?.pause() }
    fun next() { chosen()?.ctl?.transportControls?.skipToNext() }
    fun prev() { chosen()?.ctl?.transportControls?.skipToPrevious() }
    fun seekTo(pos: Long) { chosen()?.ctl?.transportControls?.seekTo(pos) }
}

// ---------------------------------------------------------------- TripService
class TripService : Service() {
    private var locMgr: LocationManager? = null
    private var locList: LocationListener? = null
    private var lastLoc: Location? = null

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val chan = NotificationChannel("dash_trip", "Viaje", NotificationManager.IMPORTANCE_LOW)
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(chan)
        val note = Notification.Builder(this, "dash_trip")
            .setContentTitle("Dash Radio activo")
            .setContentText("Registrando velocidad y GPS")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, note, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(1, note)
        }
        locMgr = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        locList = object : LocationListener {
            override fun onLocationChanged(l: Location) {
                val now = SystemClock.elapsedRealtime()
                val prev = lastLoc
                lastLoc = l
                Store.speed.value = if (l.hasSpeed()) l.speed * 3.6f else 0f
                if (prev != null) {
                    val dist = l.distanceTo(prev).toDouble()
                    if (dist in 0.5..100.0) Store.addDistance(dist)
                }
            }
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        try {
            locMgr?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 800L, 1f, locList!!)
        } catch (e: Exception) {}
    }

    override fun onDestroy() {
        try { if (locList != null) locMgr?.removeUpdates(locList!!) } catch (e: Exception) {}
        super.onDestroy()
    }
}

// ---------------------------------------------------------------- Tema y UI
data class ThemeColors(
    val bg: Color = Color.Black,
    val card: Color = Color.DarkGray,
    val text: Color = Color.White,
    val dim: Color = Color.LightGray,
    val accent: Color = Color.Green,
    val speedTag: String = "VELOCIDAD"
)

val LocalTheme = compositionLocalOf { ThemeColors() }
val Mono = FontFamily.Monospace

@Composable
fun DashTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalTheme provides ThemeColors()) {
        content()
    }
}

@Composable
fun NeonCard(modifier: Modifier = Modifier, tag: String = "", content: @Composable () -> Unit) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = LocalTheme.current.card)) {
        Box(Modifier.fillMaxSize().padding(8.dp)) {
            content()
        }
    }
}

@Composable
fun NeonButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = modifier) {
        Text(text)
    }
}

@Composable
fun CtlBtn(iconIdx: Int, active: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Text("⚙️")
    }
}

fun themeShape(t: Any, small: Boolean = false) = RoundedCornerShape(if (small) 8.dp else 16.dp)

@Composable
fun Dash(onApps: () -> Unit, onSettings: (Int) -> Unit) {
    val t = LocalTheme.current
    val layout by Store.layout.collectAsState()
    val ctx = LocalContext.current

    LaunchedEffect(Unit) {
        MediaHub.ensure(ctx)
    }

    Box(Modifier.fillMaxSize().background(t.bg)) {
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                when (layout) {
                    1 -> {
                        MapCard(Modifier.weight(1.6f).fillMaxHeight())
                        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            TimeWeatherCard(Modifier.weight(0.8f))
                            MusicCard(Modifier.weight(1f))
                            GaugeCard(Modifier.weight(1.5f)) { onSettings(3) }
                        }
                    }
                    else -> {
                        Column(Modifier.weight(1.7f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            MapCard(Modifier.weight(2f))
                            MusicCard(Modifier.weight(1f))
                        }
                        Column(Modifier.weight(0.8f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            TimeWeatherCard(Modifier.weight(1f))
                            GaugeCard(Modifier.weight(1.6f)) { onSettings(3) }
                        }
                    }
                }
            }
            Dock(onApps, onSettings)
        }
    }
}

@Composable
fun MapCard(modifier: Modifier = Modifier) {
    NeonCard(modifier, tag = "MAPA Y UBICACIÓN") {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Vista de Mapa", color = LocalTheme.current.dim, fontFamily = Mono)
        }
    }
}

@Composable
fun TimeWeatherCard(modifier: Modifier = Modifier) {
    NeonCard(modifier, tag = "TIEMPO") {
        var timeStr by remember { mutableStateOf("") }
        LaunchedEffect(Unit) {
            while (true) {
                timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                delay(1000)
            }
        }
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            Text(timeStr, fontSize = 32.sp, fontFamily = Mono, fontWeight = FontWeight.Bold, color = LocalTheme.current.text)
        }
    }
}

@Composable
fun MusicCard(modifier: Modifier = Modifier) {
    val now by MediaHub.now.collectAsState()
    NeonCard(modifier, tag = "AUDIO") {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            Text(if (now.title.isNotEmpty()) now.title else "Sin reproducción", color = LocalTheme.current.text, fontFamily = Mono, maxLines = 1)
            Text(if (now.artist.isNotEmpty()) now.artist else "Toca para abrir música", color = LocalTheme.current.dim, fontSize = 12.sp, fontFamily = Mono, maxLines = 1)
        }
    }
}

@Composable
fun GaugeCard(modifier: Modifier = Modifier, onClick: () -> Unit) {
    val speed by Store.speed.collectAsState()
    val t = LocalTheme.current
    NeonCard(modifier, tag = t.speedTag) {
        Box(Modifier.fillMaxSize().clickable(onClick = onClick), contentAlignment = Alignment.Center) {
            Text("${speed.roundToInt()} km/h", fontSize = 42.sp, fontFamily = Mono, fontWeight = FontWeight.Bold, color = t.accent)
        }
    }
}

@Composable
fun Dock(onApps: () -> Unit, onSettings: (Int) -> Unit) {
    val t = LocalTheme.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).background(t.card, themeShape(t, small = true)).padding(8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CtlBtn(5, false, onApps)
        CtlBtn(0, false) { onSettings(0) }
    }
}

@Composable
fun AppsScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Box(Modifier.fillMaxSize().background(LocalTheme.current.bg), contentAlignment = Alignment.Center) {
        NeonButton("Volver", Modifier.width(150.dp), onClick = onBack)
    }
}

@Composable
fun SettingsScreen(tab: Int, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Box(Modifier.fillMaxSize().background(LocalTheme.current.bg), contentAlignment = Alignment.Center) {
        NeonButton("Volver", Modifier.width(150.dp), onClick = onBack)
    }
}
