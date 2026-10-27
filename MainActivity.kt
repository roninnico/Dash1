package com.dash.radio

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.AlarmClock
import android.provider.Settings
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

// ============================================================ Temas
class Theme(
    val name: String,
    val bg: Color, val card: Color, val accent: Color, val accent2: Color, val hot: Color,
    val text: Color, val dim: Color, val map: FloatArray,
    val corner: Int = 0, val bw: Float = 1f,
    val light: Boolean = false, val onAccent: Color = Color.Black
)

// Matriz para oscurecer/invertir el mapa y teñirlo con el color del tema
fun tint(r: Float, g: Float, b: Float) = floatArrayOf(
    -r, 0f, 0f, 0f, 255f * r,
    0f, -g, 0f, 0f, 255f * g,
    0f, 0f, -b, 0f, 255f * b,
    0f, 0f, 0f, 1f, 0f
)

val Cyber = Theme(
    "Cyberpunk", Color(0xFF04050A), Color(0xFF0D0F17),
    Color(0xFFFCEE0A), Color(0xFF00F0FF), Color(0xFFFF2A6D),
    Color(0xFFF2F2F2), Color(0xFF8A8F9C), tint(0.45f, 0.8f, 1f), corner = 0, bw = 1f
)
val Nerv = Theme(
    "Evangelion", Color(0xFF050308), Color(0xFF110A1A),
    Color(0xFFFF7A00), Color(0xFF8CFF2E), Color(0xFFFF2D2D),
    Color(0xFFF4EEFF), Color(0xFF8D7FA3), tint(0.7f, 0.35f, 0.9f), corner = 1, bw = 1.2f
)
val Naruto = Theme(
    "Naruto", Color(0xFF0A0D16), Color(0xFF121826),
    Color(0xFFFF7A1A), Color(0xFF35A7FF), Color(0xFFE5322D),
    Color(0xFFF5F1EA), Color(0xFF8B93A7), tint(0.8f, 0.45f, 0.25f), corner = 2, bw = 1.5f
)
val DragonBall = Theme(
    "Dragon Ball", Color(0xFF070A16), Color(0xFF0F1530),
    Color(0xFFFF8A00), Color(0xFFFFE04A), Color(0xFFFF3B1F),
    Color(0xFFFFF7E6), Color(0xFF9AA0B8), tint(0.9f, 0.6f, 0.2f), corner = 2, bw = 2f
)
val Street = Theme(
    "Street Fighter", Color(0xFF0B0709), Color(0xFF1A0E12),
    Color(0xFFE4002B), Color(0xFFFFD400), Color(0xFFFF6A1F),
    Color(0xFFFFF4F4), Color(0xFFA08888), tint(0.9f, 0.25f, 0.25f), corner = 3, bw = 2f
)
val Turbo = Theme(
    "Turbo", Color(0xFF08090A), Color(0xFF121416),
    Color(0xFFC6FF00), Color(0xFFFFFFFF), Color(0xFFFF3D00),
    Color(0xFFEDEFF2), Color(0xFF7D838C), tint(0.55f, 0.85f, 0.2f), corner = 4, bw = 1.5f
)
val Static = Theme(
    "Static", Color(0xFF04060F), Color(0xFF0B1022),
    Color(0xFF33A8FF), Color(0xFFFFE600), Color(0xFFB057FF),
    Color(0xFFEAF4FF), Color(0xFF7C8AA6), tint(0.3f, 0.6f, 1f), corner = 5, bw = 1.5f
)

val Themes = listOf(Cyber, Nerv, Naruto, DragonBall, Street, Turbo, Static)

// modo: 0 = oscuro (el del tema), 1 = AMOLED (negro puro), 2 = claro
fun Theme.withMode(mode: Int): Theme = when (mode) {
    1 -> Theme(
        name, Color.Black, Color(0xFF07070B), accent, accent2, hot, text, dim, map,
        corner, bw, false, Color.Black
    )
    2 -> Theme(
        name, Color(0xFFE9EBF0), Color.White,
        lerp(accent, Color.Black, 0.45f), lerp(accent2, Color.Black, 0.5f), lerp(hot, Color.Black, 0.25f),
        Color(0xFF14151A), Color(0xFF5B6070), map, corner, bw, true, Color.White
    )
    else -> this
}

fun themeShape(t: Theme, small: Boolean = false): Shape {
    val k = if (small) 0.5f else 1f
    return when (t.corner) {
        1 -> CutCornerShape((10 * k).dp)
        2 -> RoundedCornerShape((18 * k).dp)
        3 -> CutCornerShape(topStart = 0.dp, topEnd = (16 * k).dp, bottomEnd = 0.dp, bottomStart = (16 * k).dp)
        4 -> CutCornerShape(topStart = (28 * k).dp, topEnd = 0.dp, bottomEnd = (28 * k).dp, bottomStart = 0.dp)
        5 -> RoundedCornerShape((6 * k).dp)
        else -> CutCornerShape(topStart = (16 * k).dp, topEnd = 0.dp, bottomEnd = (16 * k).dp, bottomStart = 0.dp)
    }
}

val LocalTheme = compositionLocalOf { Cyber }
val Mono = FontFamily.Monospace

fun greeting(name: String): String {
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val s = if (h < 12) "Buenos días" else if (h < 19) "Buenas tardes" else "Buenas noches"
    val n = name.trim()
    return (if (n.isEmpty()) s else s + ", " + n) + ". ¿Listos para un nuevo viaje?"
}

// ============================================================ Actividad
class MainActivity : ComponentActivity() {
    private val perm = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { startTrip() }

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pendingSpeech: String? = null

    private val screenOn = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) { greet() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        setContent {
            val idx by Store.themeIdx.collectAsState()
            val mode by Store.mode.collectAsState()
            val theme = remember(idx, mode) { Themes[idx.coerceIn(0, Themes.size - 1)].withMode(mode) }
            CompositionLocalProvider(LocalTheme provides theme) {
                MaterialTheme(
                    colorScheme = if (theme.light) lightColorScheme(
                        primary = theme.accent, onPrimary = Color.White, secondary = theme.accent2,
                        background = theme.bg, surface = theme.card, onSurface = theme.text
                    ) else darkColorScheme(
                        primary = theme.accent, onPrimary = Color.Black, secondary = theme.accent2,
                        background = theme.bg, surface = theme.card, onSurface = theme.text
                    )
                ) {
                    var screen by remember { mutableStateOf(0) }
                    when (screen) {
                        1 -> AppsScreen { screen = 0 }
                        2 -> SettingsScreen { screen = 0 }
                        else -> Dash(onApps = { screen = 1 }, onSettings = { screen = 2 })
                    }
                }
            }
        }
        val need = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) need.add(Manifest.permission.POST_NOTIFICATIONS)
        perm.launch(need.toTypedArray())
        registerReceiver(screenOn, IntentFilter(Intent.ACTION_SCREEN_ON))
        greet()
    }

    override fun onResume() {
        super.onResume()
        startTrip()
    }

    override fun onDestroy() {
        try { unregisterReceiver(screenOn) } catch (e: Exception) { }
        tts?.shutdown()
        super.onDestroy()
    }

    private fun startTrip() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) ContextCompat.startForegroundService(this, Intent(this, TripService::class.java))
    }

    // Saluda al abrir la app y al encender la pantalla (máximo una vez cada 20 minutos)
    private fun greet() {
        val now = System.currentTimeMillis()
        if (Store.greetAt.value != 0L && now - Store.greetAt.value < 20 * 60 * 1000L) return
        Store.greetAt.value = now
        val msg = greeting(Store.userName.value)
        Store.greetText.value = msg
        Store.greetTick.value = now
        if (Store.voice.value) speak(msg)
    }

    private fun speak(text: String) {
        val engine = tts
        if (engine == null) {
            pendingSpeech = text
            tts = TextToSpeech(this) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.setLanguage(Locale("es"))
                    ttsReady = true
                    val p = pendingSpeech
                    pendingSpeech = null
                    if (p != null) tts?.speak(p, TextToSpeech.QUEUE_FLUSH, null, "greet")
                }
            }
        } else if (ttsReady) {
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "greet")
        } else {
            pendingSpeech = text
        }
    }
}

// ============================================================ Utilidades
fun launchPkg(c: Context, pkg: String) {
    val i = c.packageManager.getLaunchIntentForPackage(pkg) ?: return
    try { c.startActivity(i) } catch (e: Exception) { }
}

fun openClock(c: Context) {
    val pm = c.packageManager
    val tries = listOf(
        Intent(AlarmClock.ACTION_SHOW_ALARMS),
        pm.getLaunchIntentForPackage("com.google.android.deskclock"),
        pm.getLaunchIntentForPackage("com.android.deskclock")
    )
    for (i in tries) {
        if (i == null) continue
        try {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            c.startActivity(i)
            return
        } catch (e: Exception) { }
    }
}

fun lastKnown(c: Context): Location? {
    val lm = c.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return try {
        lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
    } catch (e: Exception) {
        null
    }
}

fun fmt(ms: Long): String {
    val s = max(0L, ms) / 1000
    return (s / 60).toString() + ":" + (s % 60).toString().padStart(2, '0')
}

val knownMusic = listOf(
    "com.spotify.music" to "Spotify",
    "com.google.android.apps.youtube.music" to "YT Music",
    "org.videolan.vlc" to "VLC",
    "com.maxmpz.audioplayer" to "Poweramp",
    "com.google.android.youtube" to "YouTube",
    "com.android.music" to "Música"
)

val knownNav = listOf(
    "com.google.android.apps.maps" to "Maps",
    "com.waze" to "Waze",
    "net.osmand" to "OsmAnd",
    "net.osmand.plus" to "OsmAnd+",
    "app.organicmaps" to "Organic"
)

// ============================================================ Piezas visuales
@Composable
fun NeonCard(
    modifier: Modifier = Modifier,
    tag: String? = null,
    pad: Dp = 14.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val t = LocalTheme.current
    val shape = themeShape(t)
    Column(
        modifier.clip(shape).background(t.card, shape)
            .border(t.bw.dp, t.accent.copy(alpha = 0.6f), shape).padding(pad)
    ) {
        if (tag != null) Tag(tag)
        content()
    }
}

@Composable
fun Tag(text: String) {
    val t = LocalTheme.current
    Text(text, fontSize = 11.sp, color = t.accent, fontFamily = Mono, letterSpacing = 2.sp)
}

@Composable
fun NeonButton(text: String, modifier: Modifier, onClick: () -> Unit) {
    val t = LocalTheme.current
    val shape = themeShape(t, small = true)
    Box(
        modifier.height(46.dp).clip(shape).background(t.card, shape)
            .border(t.bw.dp, t.accent, shape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text, color = t.accent, fontSize = 15.sp, fontFamily = Mono,
            fontWeight = FontWeight.Bold, letterSpacing = 2.sp
        )
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val t = LocalTheme.current
    val shape = themeShape(t, small = true)
    Box(
        Modifier.clip(shape)
            .background(if (selected) t.accent else Color.Transparent, shape)
            .border(1.dp, t.accent, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text.uppercase(), fontSize = 12.sp, fontFamily = Mono, maxLines = 1,
            color = if (selected) t.onAccent else t.accent
        )
    }
}

// Iconos de reproductor dibujados (no dependen de la fuente de la radio)
@Composable
fun Glyph(kind: Int, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        when (kind) {
            0 -> drawPath(Path().apply {
                moveTo(w * 0.25f, h * 0.15f); lineTo(w * 0.85f, h * 0.5f)
                lineTo(w * 0.25f, h * 0.85f); close()
            }, color)
            1 -> {
                drawRect(color, Offset(w * 0.22f, h * 0.15f), Size(w * 0.2f, h * 0.7f))
                drawRect(color, Offset(w * 0.58f, h * 0.15f), Size(w * 0.2f, h * 0.7f))
            }
            2 -> drawRect(color, Offset(w * 0.2f, h * 0.2f), Size(w * 0.6f, h * 0.6f))
            3 -> {
                drawPath(Path().apply {
                    moveTo(w * 0.15f, h * 0.2f); lineTo(w * 0.7f, h * 0.5f)
                    lineTo(w * 0.15f, h * 0.8f); close()
                }, color)
                drawRect(color, Offset(w * 0.72f, h * 0.2f), Size(w * 0.13f, h * 0.6f))
            }
            else -> {
                drawPath(Path().apply {
                    moveTo(w * 0.85f, h * 0.2f); lineTo(w * 0.3f, h * 0.5f)
                    lineTo(w * 0.85f, h * 0.8f); close()
                }, color)
                drawRect(color, Offset(w * 0.15f, h * 0.2f), Size(w * 0.13f, h * 0.6f))
            }
        }
    }
}

@Composable
fun CtlBtn(kind: Int, big: Boolean, onClick: () -> Unit) {
    val t = LocalTheme.current
    val s = if (big) 50.dp else 40.dp
    Box(
        Modifier.size(s).clip(CircleShape)
            .border(if (big) 2.dp else 1.dp, if (big) t.accent2 else t.accent.copy(alpha = 0.6f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Glyph(kind, if (big) t.accent2 else t.text, Modifier.size(s * 0.55f))
    }
}

// Barra de progreso compacta: toca o arrastra para saltar a otro punto de la canción
@Composable
fun SeekBar(frac: Float, enabled: Boolean, modifier: Modifier, onSeek: (Float) -> Unit) {
    val t = LocalTheme.current
    var drag by remember { mutableStateOf<Float?>(null) }
    val f = drag ?: frac
    val seek by rememberUpdatedState(onSeek)
    Canvas(
        modifier.height(22.dp)
            .pointerInput(enabled) {
                if (enabled) {
                    detectTapGestures { o -> seek((o.x / size.width).coerceIn(0f, 1f)) }
                }
            }
            .pointerInput(enabled) {
                if (enabled) {
                    detectHorizontalDragGestures(
                        onDragStart = { o -> drag = (o.x / size.width).coerceIn(0f, 1f) },
                        onDragEnd = {
                            val d = drag
                            if (d != null) seek(d)
                            drag = null
                        },
                        onDragCancel = { drag = null },
                        onHorizontalDrag = { change, _ ->
                            drag = (change.position.x / size.width).coerceIn(0f, 1f)
                        }
                    )
                }
            }
    ) {
        val cy = size.height / 2f
        val w = 3.dp.toPx()
        drawLine(t.dim.copy(alpha = 0.35f), Offset(0f, cy), Offset(size.width, cy), strokeWidth = w)
        drawLine(t.accent, Offset(0f, cy), Offset(size.width * f, cy), strokeWidth = w)
        drawCircle(t.accent, 5.dp.toPx(), Offset(size.width * f, cy))
    }
}

// ============================================================ Pantalla principal
@Composable
fun Dash(onApps: () -> Unit, onSettings: () -> Unit) {
    val t = LocalTheme.current
    val total by Store.totalM.collectAsState()
    val trip by Store.tripM.collectAsState()
    Box(Modifier.fillMaxSize().background(t.bg)) {
        Row(
            Modifier.fillMaxSize().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GaugeCard(Modifier.weight(1f).fillMaxHeight())
            Column(
                Modifier.weight(1.25f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MapCard(Modifier.weight(1f).fillMaxWidth())
                MusicCard(Modifier.weight(1.25f).fillMaxWidth())
            }
            Column(
                Modifier.weight(0.9f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TimeWeatherCard(Modifier.fillMaxWidth().weight(1f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stat("TOTAL", total, 0, Modifier.weight(1f))
                    Stat("VIAJE", trip, 1, Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NeonButton("APPS", Modifier.weight(1f), onApps)
                    NeonButton("⚙", Modifier.width(56.dp), onSettings)
                }
            }
        }
        GreetBanner(Modifier.align(Alignment.TopCenter).padding(top = 18.dp))
    }
}

@Composable
fun GreetBanner(modifier: Modifier) {
    val t = LocalTheme.current
    val text by Store.greetText.collectAsState()
    val tick by Store.greetTick.collectAsState()
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(tick) {
        if (tick != 0L) {
            show = true
            delay(7000)
            show = false
        }
    }
    val a by animateFloatAsState(targetValue = if (show) 1f else 0f, animationSpec = tween(600))
    if (a > 0.01f && text.isNotEmpty()) {
        val shape = themeShape(t)
        Box(
            modifier.alpha(a).widthIn(max = 560.dp).clip(shape).background(t.card, shape)
                .border((t.bw + 1f).dp, t.accent, shape)
                .clickable { show = false }
                .padding(horizontal = 26.dp, vertical = 14.dp)
        ) {
            Text(
                text, fontSize = 20.sp, color = t.accent2, fontFamily = Mono,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun Stat(label: String, meters: Double, decimals: Int, modifier: Modifier) {
    val t = LocalTheme.current
    NeonCard(modifier, pad = 10.dp) {
        Tag(label)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                String.format(Locale.US, "%." + decimals + "f", meters / 1000), fontSize = 20.sp,
                color = t.text, fontFamily = Mono, fontWeight = FontWeight.Bold, maxLines = 1
            )
            Spacer(Modifier.width(4.dp))
            Text(
                "km", fontSize = 11.sp, color = t.accent2, fontFamily = Mono,
                modifier = Modifier.padding(bottom = 3.dp)
            )
        }
    }
}

// ------------------------------------------------------------ Velocímetro
@Composable
fun GaugeCard(modifier: Modifier) {
    val t = LocalTheme.current
    val speed by Store.speed.collectAsState()
    val model by Store.model.collectAsState()
    val mph by Store.mph.collectAsState()
    var gpsOk by remember { mutableStateOf(false) }
    var gpsText by remember { mutableStateOf("BUSCANDO GPS…") }
    LaunchedEffect(Unit) {
        while (true) {
            val fix = Store.lastFix.value
            gpsOk = fix != 0L && System.currentTimeMillis() - fix < 5000
            gpsText = if (gpsOk) "GPS ±" + Store.gpsAcc.value.roundToInt() + " M" else "BUSCANDO GPS…"
            delay(1000)
        }
    }
    NeonCard(modifier, tag = "VELOCIDAD") {
        Text(
            model.uppercase(), fontSize = 20.sp, color = t.text, fontFamily = Mono,
            fontWeight = FontWeight.Bold, maxLines = 1
        )
        Gauge(if (gpsOk) speed else 0f, mph, Modifier.weight(1f).fillMaxWidth()) {
            Store.setMph(!mph)
        }
        Text(
            gpsText, fontSize = 13.sp, color = if (gpsOk) t.dim else t.hot, fontFamily = Mono,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Text(
            "TOCA PARA CAMBIAR KM/H ↔ MPH", fontSize = 10.sp, color = t.dim, fontFamily = Mono,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

@Composable
fun Gauge(kmh: Float, mph: Boolean, modifier: Modifier, onToggle: () -> Unit) {
    val t = LocalTheme.current
    val v = if (mph) kmh * 0.621371f else kmh
    val top = if (mph) 120f else 200f
    val a by animateFloatAsState(
        targetValue = (v / top).coerceIn(0f, 1f),
        animationSpec = tween(500)
    )
    Box(modifier.clickable(onClick = onToggle), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val d = minOf(size.width, size.height)
            val stroke = d * 0.06f
            val r = d / 2f - stroke
            val c = center
            val tl = Offset(c.x - r, c.y - r)
            val sz = Size(r * 2f, r * 2f)
            drawArc(
                color = t.dim.copy(alpha = 0.25f), startAngle = 135f, sweepAngle = 270f,
                useCenter = false, topLeft = tl, size = sz, style = Stroke(width = stroke)
            )
            if (a > 0.005f) {
                val col = lerp(t.accent2, t.hot, a)
                drawArc(
                    color = col.copy(alpha = 0.25f), startAngle = 135f, sweepAngle = 270f * a,
                    useCenter = false, topLeft = tl, size = sz, style = Stroke(width = stroke * 1.9f)
                )
                drawArc(
                    color = col, startAngle = 135f, sweepAngle = 270f * a,
                    useCenter = false, topLeft = tl, size = sz, style = Stroke(width = stroke)
                )
            }
            for (i in 0..10) {
                val ang = Math.toRadians(135.0 + 27.0 * i)
                val cs = cos(ang).toFloat()
                val sn = sin(ang).toFloat()
                val r1 = r - stroke * 0.9f
                val r2 = r - stroke * (if (i % 5 == 0) 2.4f else 1.7f)
                drawLine(
                    t.dim, Offset(c.x + cs * r1, c.y + sn * r1),
                    Offset(c.x + cs * r2, c.y + sn * r2), strokeWidth = 1.5.dp.toPx()
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                v.roundToInt().toString(), fontSize = 66.sp, color = t.text, fontFamily = Mono,
                fontWeight = FontWeight.Bold
            )
            Text(
                if (mph) "MPH" else "KM/H", fontSize = 16.sp, color = t.accent2,
                fontFamily = Mono, letterSpacing = 3.sp
            )
        }
    }
}

// ------------------------------------------------------------ Mapa
@Composable
fun MapCard(modifier: Modifier) {
    val t = LocalTheme.current
    val ctx = LocalContext.current
    var lat by remember { mutableStateOf(Double.NaN) }
    var lon by remember { mutableStateOf(Double.NaN) }
    var brg by remember { mutableStateOf(0f) }
    var sheet by remember { mutableStateOf<MapTiles.Sheet?>(null) }
    var offline by remember { mutableStateOf(false) }
    val navApps = remember { knownNav.filter { ctx.packageManager.getLaunchIntentForPackage(it.first) != null } }

    LaunchedEffect(Unit) {
        while (true) {
            var la = Store.lat.value
            var lo = Store.lon.value
            if (la.isNaN()) {
                val l = lastKnown(ctx)
                if (l != null) { la = l.latitude; lo = l.longitude }
            }
            lat = la
            lon = lo
            brg = Store.bearing.value
            if (!la.isNaN()) {
                val s = sheet
                val tx = floor(MapTiles.worldX(lo)).toInt()
                val ty = floor(MapTiles.worldY(la)).toInt()
                if (s == null || s.tx != tx || s.ty != ty) {
                    val n = withContext(Dispatchers.IO) { MapTiles.load(la, lo) }
                    if (n != null) {
                        sheet = n
                        offline = false
                    } else {
                        offline = true
                        delay(20000)
                        continue
                    }
                }
            }
            delay(1000)
        }
    }

    val img = remember(sheet) { sheet?.bmp?.asImageBitmap() }
    val filter = remember(t) { if (t.light) null else ColorFilter.colorMatrix(ColorMatrix(t.map)) }
    val shape = themeShape(t)
    Box(
        modifier
            .clickable { if (navApps.isNotEmpty()) launchPkg(ctx, navApps[0].first) }
            .clip(shape).background(t.card, shape)
            .border(t.bw.dp, t.accent.copy(alpha = 0.6f), shape)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val u = 1.dp.toPx()
            val s = sheet
            if (img != null && s != null && !lat.isNaN()) {
                val px = ((MapTiles.worldX(lon) - (s.tx - 1)) * 256.0).toFloat()
                val py = ((MapTiles.worldY(lat) - (s.ty - 1)) * 256.0).toFloat()
                drawImage(img, topLeft = Offset(c.x - px, c.y - py), colorFilter = filter)
            } else {
                var x = 0f
                while (x < size.width) {
                    drawLine(t.dim.copy(alpha = 0.18f), Offset(x, 0f), Offset(x, size.height), 1f)
                    x += 40f * u
                }
                var y = 0f
                while (y < size.height) {
                    drawLine(t.dim.copy(alpha = 0.18f), Offset(0f, y), Offset(size.width, y), 1f)
                    y += 40f * u
                }
            }
            if (!lat.isNaN()) {
                val p = Path().apply {
                    moveTo(c.x, c.y - 18f * u); lineTo(c.x + 11f * u, c.y + 13f * u)
                    lineTo(c.x, c.y + 7f * u); lineTo(c.x - 11f * u, c.y + 13f * u); close()
                }
                rotate(degrees = brg, pivot = c) {
                    drawPath(p, t.accent)
                    drawPath(p, Color.Black, style = Stroke(width = 1.5f * u))
                }
            }
        }
        Text(
            "NAVEGACIÓN", fontSize = 11.sp, color = t.accent, fontFamily = Mono, letterSpacing = 2.sp,
            modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
        )
        if (lat.isNaN()) {
            Text(
                "ESPERANDO GPS…", fontSize = 14.sp, color = t.hot, fontFamily = Mono,
                modifier = Modifier.align(Alignment.Center)
            )
        } else if (offline && sheet == null) {
            Text(
                "SIN MAPA · REVISA INTERNET", fontSize = 11.sp, color = t.hot, fontFamily = Mono,
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
            )
        }
        Row(
            Modifier.align(Alignment.BottomStart).padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            for ((pkg, name) in navApps) Chip(name, false) { launchPkg(ctx, pkg) }
        }
    }
}

// ------------------------------------------------------------ Reproductor
@Composable
fun MusicCard(modifier: Modifier) {
    val t = LocalTheme.current
    val ctx = LocalContext.current
    val sessions by MediaHub.sessions.collectAsState()
    val now by MediaHub.now.collectAsState()
    val picked by MediaHub.picked.collectAsState()
    var access by remember { mutableStateOf(MediaHub.hasAccess(ctx)) }
    var tick by remember { mutableStateOf(0L) }
    val installed = remember { knownMusic.filter { ctx.packageManager.getLaunchIntentForPackage(it.first) != null }.take(4) }

    LaunchedEffect(Unit) {
        while (true) {
            access = MediaHub.hasAccess(ctx)
            MediaHub.ensure(ctx)
            tick = SystemClock.elapsedRealtime()
            delay(500)
        }
    }

    val dur = now.durMs
    var pos = max(0L, now.posMs)
    if (now.playing && now.posAt > 0L) pos += ((tick - now.posAt) * now.speed).toLong()
    if (dur > 0L && pos > dur) pos = dur
    val frac = if (dur > 0L) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
    val artImg: ImageBitmap? = remember(now.art) { now.art?.asImageBitmap() }

    NeonCard(modifier, tag = "REPRODUCTOR", pad = 12.dp) {
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val compact = maxHeight < 165.dp
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                if (!compact) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (sessions.isNotEmpty()) {
                            Chip("Auto", picked.isEmpty()) { MediaHub.pick("") }
                            for (s in sessions) Chip(s.label, picked == s.pkg) { MediaHub.pick(s.pkg) }
                        } else {
                            for ((pkg, name) in installed) Chip(name, false) { launchPkg(ctx, pkg) }
                        }
                    }
                }
                if (!access) {
                    Text(
                        "Activa Dash en «Acceso a notificaciones» para controlar la música.",
                        fontSize = 13.sp, color = t.dim
                    )
                    NeonButton("ACTIVAR ACCESO", Modifier.fillMaxWidth()) {
                        try { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } catch (e: Exception) { }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(if (compact) 44.dp else 54.dp).clip(CircleShape).background(t.bg)
                                .border(2.dp, t.accent, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (artImg != null) {
                                Image(artImg, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            } else {
                                Text("♪", fontSize = 22.sp, color = t.accent)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f).clickable { if (now.pkg.isNotEmpty()) launchPkg(ctx, now.pkg) }) {
                            Text(
                                if (now.title.isBlank()) "Sin reproducción" else now.title,
                                fontSize = 17.sp, color = t.text, fontWeight = FontWeight.Bold, maxLines = 1
                            )
                            Text(now.artist, fontSize = 13.sp, color = t.accent2, maxLines = 1)
                        }
                    }
                    Column {
                        SeekBar(frac, dur > 0L, Modifier.fillMaxWidth()) { f ->
                            if (dur > 0L) MediaHub.seek((f * dur).toLong())
                        }
                        if (!compact) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(fmt(pos), fontSize = 11.sp, color = t.dim, fontFamily = Mono)
                                Text(fmt(dur), fontSize = 11.sp, color = t.dim, fontFamily = Mono)
                            }
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CtlBtn(4, false) { MediaHub.prev(ctx) }
                        CtlBtn(if (now.playing) 1 else 0, true) { MediaHub.playPause(ctx) }
                        CtlBtn(2, false) { MediaHub.stop(ctx) }
                        CtlBtn(3, false) { MediaHub.next(ctx) }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------ Hora y clima
@Composable
fun TimeWeatherCard(modifier: Modifier) {
    val t = LocalTheme.current
    val ctx = LocalContext.current
    var now by remember { mutableStateOf(Date()) }
    var w by remember { mutableStateOf<Weather.W?>(null) }
    val f1 = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val f2 = remember { SimpleDateFormat("EEE d MMM", Locale.getDefault()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(1000)
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            var la = Store.lat.value
            var lo = Store.lon.value
            if (la.isNaN()) {
                val l = lastKnown(ctx)
                if (l != null) { la = l.latitude; lo = l.longitude }
            }
            if (!la.isNaN()) {
                val r = withContext(Dispatchers.IO) { Weather.fetch(la, lo) }
                if (r != null) {
                    w = r
                    delay(15 * 60 * 1000L)
                    continue
                }
            }
            delay(15000)
        }
    }
    NeonCard(modifier, tag = "HORA · CLIMA", pad = 10.dp) {
        val x = w
        Row(
            Modifier.fillMaxWidth().clickable { openClock(ctx) },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    f1.format(now), fontSize = 34.sp, color = t.accent2, fontFamily = Mono,
                    fontWeight = FontWeight.Bold
                )
                Text(f2.format(now).uppercase(), fontSize = 12.sp, color = t.dim, fontFamily = Mono)
            }
            if (x != null) {
                val d = Weather.describe(x.code)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(d.second, fontSize = 22.sp, color = t.accent)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        x.temp.toString() + "°", fontSize = 30.sp, color = t.accent2,
                        fontFamily = Mono, fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        if (x == null) {
            Spacer(Modifier.height(6.dp))
            Text("Clima: esperando ubicación e internet…", fontSize = 11.sp, color = t.dim)
        } else {
            val d = Weather.describe(x.code)
            Spacer(Modifier.height(4.dp))
            Text(d.first, fontSize = 13.sp, color = t.text)
            Text("MÁX " + x.hi + "°  MÍN " + x.lo + "°", fontSize = 11.sp, color = t.dim, fontFamily = Mono)
            Text("HUM " + x.hum + "%  VIENTO " + x.wind + " KM/H", fontSize = 11.sp, color = t.dim, fontFamily = Mono)
        }
    }
}

// ============================================================ Apps
class AppItem(val name: String, val pkg: String, val icon: ImageBitmap)

fun launchable(c: Context): List<AppItem> {
    val pm = c.packageManager
    val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(i, 0)
        .filter { it.activityInfo.packageName != c.packageName }
        .map {
            AppItem(
                it.loadLabel(pm).toString(), it.activityInfo.packageName,
                it.loadIcon(pm).toBitmap(96, 96).asImageBitmap()
            )
        }
        .sortedBy { it.name.lowercase() }
}

@Composable
fun AppsScreen(onClose: () -> Unit) {
    val t = LocalTheme.current
    val ctx = LocalContext.current
    BackHandler(onBack = onClose)
    var apps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    LaunchedEffect(Unit) { apps = withContext(Dispatchers.Default) { launchable(ctx) } }
    val shape = themeShape(t, small = true)
    Column(
        Modifier.fillMaxSize().background(t.bg).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text(
                "APLICACIONES", fontSize = 22.sp, color = t.text, fontFamily = Mono,
                fontWeight = FontWeight.Bold, letterSpacing = 3.sp
            )
            NeonButton("VOLVER", Modifier.width(140.dp), onClose)
        }
        if (apps.isEmpty()) Text("Cargando…", color = t.dim, fontFamily = Mono)
        LazyVerticalGrid(
            GridCells.Adaptive(104.dp), Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(apps) { a ->
                Column(
                    Modifier.fillMaxWidth().clip(shape).background(t.card)
                        .border(1.dp, t.accent.copy(alpha = 0.4f), shape)
                        .clickable { launchPkg(ctx, a.pkg); onClose() }
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(a.icon, null, Modifier.size(56.dp))
                    Text(
                        a.name, fontSize = 12.sp, color = t.text, maxLines = 1,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

// ============================================================ Ajustes
@Composable
fun SettingsScreen(onClose: () -> Unit) {
    val t = LocalTheme.current
    BackHandler(onBack = onClose)
    val themeIdx by Store.themeIdx.collectAsState()
    val mode by Store.mode.collectAsState()
    val mph by Store.mph.collectAsState()
    val voice by Store.voice.collectAsState()
    var model by remember { mutableStateOf(Store.model.value) }
    var calib by remember { mutableStateOf(Store.calib.value.toString()) }
    var total by remember { mutableStateOf(String.format(Locale.US, "%.1f", Store.totalM.value / 1000)) }
    var name by remember { mutableStateOf(Store.userName.value) }
    Column(
        Modifier.fillMaxSize().background(t.bg).verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            "AJUSTES", fontSize = 24.sp, color = t.text, fontFamily = Mono,
            fontWeight = FontWeight.Bold, letterSpacing = 3.sp
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(model, { model = it }, label = { Text("Modelo del auto") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(total, { total = it }, label = { Text("Km total") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(calib, { calib = it }, label = { Text("Calibración (1.00)") }, singleLine = true, modifier = Modifier.weight(1f))
        }
        OutlinedTextField(
            name, { name = it }, label = { Text("Tu nombre (para el saludo)") },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            Tag("TEMA")
            for (i in Themes.indices) Chip(Themes[i].name, themeIdx == i) { Store.setTheme(i) }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            Tag("MODO")
            Chip("Oscuro", mode == 0) { Store.setMode(0) }
            Chip("AMOLED", mode == 1) { Store.setMode(1) }
            Chip("Claro", mode == 2) { Store.setMode(2) }
            Spacer(Modifier.width(16.dp))
            Tag("UNIDAD")
            Chip("km/h", !mph) { Store.setMph(false) }
            Chip("mph", mph) { Store.setMph(true) }
            Spacer(Modifier.width(16.dp))
            Tag("SALUDO POR VOZ")
            Chip("Sí", voice) { Store.setVoice(true) }
            Chip("No", !voice) { Store.setVoice(false) }
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            NeonButton("GUARDAR", Modifier.width(150.dp)) {
                Store.saveSettings(model, calib.toDoubleOrNull() ?: 1.0, total.toDoubleOrNull())
                Store.setName(name)
                onClose()
            }
            NeonButton("REINICIAR VIAJE", Modifier.width(210.dp)) { Store.resetTrip() }
            NeonButton("CANCELAR", Modifier.width(150.dp), onClose)
        }
    }
}
