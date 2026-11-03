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
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
data class Theme(
    val name: String,
    val bg: Color, val card: Color, val accent: Color, val accent2: Color, val hot: Color,
    val text: Color, val dim: Color, val map: FloatArray,
    val corner: Int = 0, val bw: Float = 1f,
    val light: Boolean = false, val onAccent: Color = Color.Black,
    val speedTag: String = "VELOCIDAD", val deco: Int = 0,
    val cardAlpha: Float = 0.86f, val customCorner: Int = -1, val cornerDp: Float = 16f
)

// Matriz para oscurecer/invertir el mapa y teñirlo con el color del tema
fun tint(r: Float, g: Float, b: Float) = floatArrayOf(
    -r, 0f, 0f, 0f, 255f * r,
    0f, -g, 0f, 0f, 255f * g,
    0f, 0f, -b, 0f, 255f * b,
    0f, 0f, 0f, 1f, 0f
)

val Cyberpunk = Theme(
    "Cyberpunk",
    Color(0xFF050507),
    Color(0xFF0D1017),
    Color(0xFF00F5FF),
    Color(0xFFFF0080),
    Color(0xFF8B00FF),
    Color.White,
    Color(0xFF999999),
    tint(0.35f, 0.8f, 1f),
    corner = 0,
    bw = 1.2f,
    speedTag = "NETWORK",
    deco = 0
)

val Nerv = Theme(
    "NERV",
    Color(0xFF050505),
    Color(0xFF101010),
    Color(0xFFFFC300),
    Color(0xFFE10600),
    Color(0xFFFF6A00),
    Color.White,
    Color(0xFFA6A6A6),
    tint(0.85f, 0.35f, 0.25f),
    corner = 1,
    bw = 2f,
    speedTag = "SYSTEM",
    deco = 1
)

val Midnight = Theme(
    "Midnight",
    Color(0xFF030408),
    Color(0xFF0A0E17),
    Color(0xFF3388FF),
    Color(0xFF00E5FF),
    Color(0xFF7A00FF),
    Color.White,
    Color(0xFF8892B0),
    tint(0.3f, 0.5f, 0.9f),
    corner = 2,
    bw = 1.2f,
    speedTag = "TURBO",
    deco = 0
)

val Vanilla = Theme(
    "Vanilla",
    Color(0xFF111317),
    Color(0xFF1A1D24),
    Color(0xFF5B95FF),
    Color(0xFF9EC4FF),
    Color(0xFFFF6060),
    Color.White,
    Color(0xFF97A0AA),
    tint(0.45f, 0.55f, 0.75f),
    corner = 2,
    bw = 1f,
    speedTag = "VELOCIDAD",
    deco = 0
)

val Themes = listOf(Vanilla, Midnight, Nerv, Cyberpunk)

// modo: 0 = oscuro (el del tema), 1 = AMOLED (negro puro), 2 = claro
fun Theme.withMode(mode: Int): Theme = when (mode) {
    1 -> copy(bg = Color.Black, card = Color(0xFF07070B), light = false, onAccent = Color.Black)
    2 -> copy(
        bg = Color(0xFFE9EBF0), card = Color.White,
        accent = lerp(accent, Color.Black, 0.45f), accent2 = lerp(accent2, Color.Black, 0.5f),
        hot = lerp(hot, Color.Black, 0.25f), text = Color(0xFF14151A), dim = Color(0xFF5B6070),
        light = true, onAccent = Color.White
    )
    else -> this
}

fun ovColor(v: Long, light: Boolean): Color {
    val c = Color(v.toInt())
    return if (light) lerp(c, Color.Black, 0.45f) else c
}

fun themeShape(t: Theme, small: Boolean = false): Shape {
    val k = if (small) 0.5f else 1f
    if (t.customCorner >= 0) {
        val d = (t.cornerDp * k).dp
        return when (t.customCorner) {
            0 -> RoundedCornerShape(0.dp)
            1 -> CutCornerShape(d)
            else -> RoundedCornerShape(d)
        }
    }
    return when (t.corner) {
        1 -> CutCornerShape((10 * k).dp)
        2 -> RoundedCornerShape((18 * k).dp)
        else -> RoundedCornerShape(0.dp)
    }
}

val LocalTheme = compositionLocalOf { Cyberpunk }
val Mono = FontFamily.Monospace

fun greeting(name: String, idx: Int, notes: List<String>): String {
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val s = if (h < 12) "Buenos días" else if (h < 19) "Buenas tardes" else "Buenas noches"
    val n = name.trim()
    val who = if (n.isEmpty()) "" else ", " + n
    val head = when (idx) {
        1 -> "$s, piloto. Sistema NERV operativo."
        2 -> "$s$who. Red Cyberpunk conectada."
        3 -> "$s$who. Modo Midnight preparado."
        else -> s + who + ". Conectado a la ciudad."
    }
    var msg = head + " ¿Listos para un nuevo viaje?"
    if (notes.isNotEmpty()) msg += " Ojo: " + notes.joinToString(" y ") + "."
    return msg
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
            val aOv by Store.accentOv.collectAsState()
            val a2Ov by Store.accent2Ov.collectAsState()
            val alphaPct by Store.cardAlpha.collectAsState()
            val ck by Store.cornerKind.collectAsState()
            val cd by Store.cornerDp.collectAsState()
            val bd by Store.borderDp.collectAsState()
            val scale by Store.uiScale.collectAsState()
            val theme = remember(idx, mode, aOv, a2Ov, alphaPct, ck, cd, bd) {
                var th = Themes[idx.coerceIn(0, Themes.size - 1)].withMode(mode)
                if (aOv != 0L) th = th.copy(accent = ovColor(aOv, th.light))
                if (a2Ov != 0L) th = th.copy(accent2 = ovColor(a2Ov, th.light))
                th.copy(
                    cardAlpha = alphaPct / 100f, customCorner = ck, cornerDp = cd.toFloat(),
                    bw = if (bd >= 0) bd.toFloat() else th.bw
                )
            }
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalTheme provides theme,
                LocalDensity provides Density(base.density * scale / 100f, base.fontScale)
            ) {
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
                    var tab by remember { mutableStateOf(0) }
                    when (screen) {
                        1 -> AppsScreen { screen = 0 }
                        2 -> SettingsScreen(tab) { screen = 0 }
                        else -> Dash(onApps = { screen = 1 }, onSettings = { t -> tab = t; screen = 2 })
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

    private fun greet() {
        val now = System.currentTimeMillis()
        if (Store.greetAt.value != 0L && now - Store.greetAt.value < 20 * 60 * 1000L) return
        Store.greetAt.value = now
        val msg = greeting(Store.userName.value, Store.themeIdx.value, Store.dueNotes())
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
        modifier.clip(shape).background(t.card.copy(alpha = t.cardAlpha), shape)
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
        modifier.height(46.dp).clip(shape).background(t.card.copy(alpha = t.cardAlpha), shape)
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
            4 -> {
                drawPath(Path().apply {
                    moveTo(w * 0.85f, h * 0.2f); lineTo(w * 0.3f, h * 0.5f)
                    lineTo(w * 0.85f, h * 0.8f); close()
                }, color)
                drawRect(color, Offset(w * 0.15f, h * 0.2f), Size(w * 0.13f, h * 0.6f))
            }
            5 -> {
                for (r in 0..2) for (c in 0..2) {
                    drawRect(
                        color, Offset(w * (0.12f + 0.3f * c), h * (0.12f + 0.3f * r)),
                        Size(w * 0.22f, h * 0.22f)
                    )
                }
            }
            else -> {
                val p = Path().apply {
                    moveTo(w * 0.25f, h * 0.30f); lineTo(w * 0.72f, h * 0.70f)
                    lineTo(w * 0.50f, h * 0.90f); lineTo(w * 0.50f, h * 0.10f)
                    lineTo(w * 0.72f, h * 0.30f); lineTo(w * 0.25f, h * 0.70f)
                }
                drawPath(p, color, style = Stroke(width = w * 0.09f))
            }
        }
    }
}

@Composable
fun CtlBtn(kind: Int, big: Boolean, onClick: () -> Unit, k: Float = 1f) {
    val t = LocalTheme.current
    val s = (if (big) 50.dp else 40.dp) * k
    Box(
        Modifier.size(s).clip(CircleShape)
            .border(if (big) 2.dp else 1.dp, if (big) t.accent2 else t.accent.copy(alpha = 0.6f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Glyph(kind, if (big) t.accent2 else t.text, Modifier.size(s * 0.55f))
    }
}

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
        val y = size.height / 2f
        drawLine(t.dim.copy(alpha = 0.3f), Offset(0f, y), Offset(size.width, y), 4.dp.toPx())
        if (f > 0f) {
            drawLine(t.accent, Offset(0f, y), Offset(size.width * f, y), 4.dp.toPx())
        }
        drawCircle(t.accent2, 6.dp.toPx(), Offset(size.width * f, y))
    }
}
