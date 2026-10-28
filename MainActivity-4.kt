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
import androidx.compose.ui.text.style.TextOverflow
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

val Cyber = Theme(
    "Cyberpunk", Color(0xFF04050A), Color(0xFF0D0F17),
    Color(0xFFFCEE0A), Color(0xFF00F0FF), Color(0xFFFF2A6D),
    Color(0xFFF2F2F2), Color(0xFF8A8F9C), tint(0.45f, 0.8f, 1f),
    corner = 0, bw = 1f, speedTag = "VELOCIDAD", deco = 0
)
val Nerv = Theme(
    "Evangelion", Color(0xFF050308), Color(0xFF110A1A),
    Color(0xFFFF7A00), Color(0xFF8CFF2E), Color(0xFFFF2D2D),
    Color(0xFFF4EEFF), Color(0xFF8D7FA3), tint(0.7f, 0.35f, 0.9f),
    corner = 1, bw = 1.2f, speedTag = "SINCRONIZACIÓN", deco = 1
)
val Naruto = Theme(
    "Naruto", Color(0xFF0A0D16), Color(0xFF121826),
    Color(0xFFFF7A1A), Color(0xFF35A7FF), Color(0xFFE5322D),
    Color(0xFFF5F1EA), Color(0xFF8B93A7), tint(0.8f, 0.45f, 0.25f),
    corner = 2, bw = 1.5f, speedTag = "CHAKRA", deco = 2
)
val DragonBall = Theme(
    "Dragon Ball", Color(0xFF070A16), Color(0xFF0F1530),
    Color(0xFFFF8A00), Color(0xFFFFE04A), Color(0xFFFF3B1F),
    Color(0xFFFFF7E6), Color(0xFF9AA0B8), tint(0.9f, 0.6f, 0.2f),
    corner = 2, bw = 2f, speedTag = "NIVEL DE PODER", deco = 3
)
val Street = Theme(
    "Street Fighter", Color(0xFF0B0709), Color(0xFF1A0E12),
    Color(0xFFE4002B), Color(0xFFFFD400), Color(0xFFFF6A1F),
    Color(0xFFFFF4F4), Color(0xFFA08888), tint(0.9f, 0.25f, 0.25f),
    corner = 3, bw = 2f, speedTag = "ROUND 1", deco = 4
)
val Turbo = Theme(
    "Turbo", Color(0xFF08090A), Color(0xFF121416),
    Color(0xFFC6FF00), Color(0xFFFFFFFF), Color(0xFFFF3D00),
    Color(0xFFEDEFF2), Color(0xFF7D838C), tint(0.55f, 0.85f, 0.2f),
    corner = 4, bw = 1.5f, speedTag = "TURBO", deco = 5
)
val Static = Theme(
    "Static", Color(0xFF04060F), Color(0xFF0B1022),
    Color(0xFF33A8FF), Color(0xFFFFE600), Color(0xFFB057FF),
    Color(0xFFEAF4FF), Color(0xFF7C8AA6), tint(0.3f, 0.6f, 1f),
    corner = 5, bw = 1.5f, speedTag = "VOLTAJE", deco = 6
)

val Themes = listOf(Cyber, Nerv, Naruto, DragonBall, Street, Turbo, Static)

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
        3 -> CutCornerShape(topStart = 0.dp, topEnd = (16 * k).dp, bottomEnd = 0.dp, bottomStart = (16 * k).dp)
        4 -> CutCornerShape(topStart = (28 * k).dp, topEnd = 0.dp, bottomEnd = (28 * k).dp, bottomStart = 0.dp)
        5 -> RoundedCornerShape((6 * k).dp)
        else -> CutCornerShape(topStart = (16 * k).dp, topEnd = 0.dp, bottomEnd = (16 * k).dp, bottomStart = 0.dp)
    }
}

val LocalTheme = compositionLocalOf { Cyber }
val Mono = FontFamily.Monospace

fun greeting(name: String, idx: Int, notes: List<String>): String {
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val s = if (h < 12) "Buenos días" else if (h < 19) "Buenas tardes" else "Buenas noches"
    val n = name.trim()
    val who = if (n.isEmpty()) "" else ", " + n
    val head = when (idx) {
        1 -> s + ", piloto" + (if (n.isEmpty()) "" else " " + n) + ". Sincronización lista."
        2 -> s + who + ". ¡Que el chakra nos acompañe!"
        3 -> s + who + ". ¡Tu nivel de poder está por las nubes!"
        4 -> s + who + ". ¡Round uno contra el tráfico!"
        5 -> s + who + ". Turbo listo."
        6 -> s + who + ". Voltaje al máximo."
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
            val tOv by Store.textOv.collectAsState()
            val tScale by Store.textScale.collectAsState()
            val theme = remember(idx, mode, aOv, a2Ov, tOv, alphaPct, ck, cd, bd) {
                var th = Themes[idx.coerceIn(0, Themes.size - 1)].withMode(mode)
                if (aOv != 0L) th = th.copy(accent = ovColor(aOv, th.light))
                if (a2Ov != 0L) th = th.copy(accent2 = ovColor(a2Ov, th.light))
                if (tOv != 0L) th = th.copy(text = Color(tOv.toInt()))
                th.copy(
                    cardAlpha = alphaPct / 100f, customCorner = ck, cornerDp = cd.toFloat(),
                    bw = if (bd >= 0) bd.toFloat() else th.bw
                )
            }
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalTheme provides theme,
                LocalDensity provides Density(base.density * scale / 100f, base.fontScale * tScale / 100f)
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

    // Saluda al abrir la app y al encender la pantalla (máximo una vez cada 20 minutos)
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

// Acepta "107000", "107.000", "107,000" y "112000,5"
fun parseNum(s: String): Double? {
    val t = s.trim().replace(" ", "")
    if (t.isEmpty()) return null
    if (Regex("^\\d{1,3}([.,]\\d{3})+$").matches(t)) {
        return t.replace(".", "").replace(",", "").toDoubleOrNull()
    }
    return t.replace(',', '.').toDoubleOrNull()
}

fun fmtKm(d: Double): String = String.format(Locale("es", "CL"), "%,.0f", d)

fun wt(base: Float, pct: Int): Float = max(0.2f, base * pct / 100f)

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

// Texto de una sola línea que se encoge solo si no cabe (no se corta)
@Composable
fun FitText(
    text: String, maxSize: Float, color: Color, modifier: Modifier = Modifier,
    weight: FontWeight? = null, family: FontFamily = Mono, spacing: Float = 0f,
    align: TextAlign? = null
) {
    val dens = LocalDensity.current
    var size by remember(text.length, maxSize, dens.density, dens.fontScale) { mutableStateOf(maxSize) }
    Text(
        text, modifier = modifier, color = color, fontSize = size.sp, fontFamily = family,
        fontWeight = weight, letterSpacing = spacing.sp, maxLines = 1, softWrap = false,
        textAlign = align,
        onTextLayout = { r -> if (r.didOverflowWidth && size > 7f) size *= 0.92f }
    )
}

@Composable
fun Tag(text: String) {
    val t = LocalTheme.current
    FitText(text, 11f, t.accent, Modifier, null, Mono, 2f)
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
        FitText(text, 15f, t.accent, Modifier.padding(horizontal = 6.dp), FontWeight.Bold, Mono, 2f)
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
            4 -> {
                drawPath(Path().apply {
                    moveTo(w * 0.85f, h * 0.2f); lineTo(w * 0.3f, h * 0.5f)
                    lineTo(w * 0.85f, h * 0.8f); close()
                }, color)
                drawRect(color, Offset(w * 0.15f, h * 0.2f), Size(w * 0.13f, h * 0.6f))
            }
            5 -> { // cuadrícula de aplicaciones
                for (r in 0..2) for (c in 0..2) {
                    drawRect(
                        color, Offset(w * (0.12f + 0.3f * c), h * (0.12f + 0.3f * r)),
                        Size(w * 0.22f, h * 0.22f)
                    )
                }
            }
            else -> { // símbolo de Bluetooth
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

// ============================================================ Fondo: imagen propia o diseño del tema
@Composable
fun Backdrop(themeIdx: Int) {
    val t = LocalTheme.current
    val ctx = LocalContext.current
    val rev by Store.bgRev.collectAsState()
    val blur by Store.blur.collectAsState()
    val dim by Store.dim.collectAsState()
    var img by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(themeIdx, rev, blur) {
        img = withContext(Dispatchers.IO) { Backgrounds.load(ctx, themeIdx, blur)?.asImageBitmap() }
    }
    Box(Modifier.fillMaxSize()) {
        val b = img
        if (b != null) {
            Image(b, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(t.bg.copy(alpha = dim / 100f)))
        } else {
            Canvas(Modifier.fillMaxSize()) { drawDeco(t) }
        }
    }
}

// Diseños originales (formas y patrones) inspirados en el ambiente de cada tema
fun DrawScope.drawDeco(t: Theme) {
    val u = 1.dp.toPx()
    val w = size.width
    val h = size.height
    val rnd = java.util.Random(11)
    when (t.deco) {
        0 -> { // Cyberpunk: horizonte con rejilla, líneas de escaneo y barras glitch
            val hz = h * 0.6f
            for (k in 0..8) {
                val yy = hz + (h - hz) * (k / 8f) * (k / 8f)
                drawLine(t.accent2.copy(alpha = 0.10f), Offset(0f, yy), Offset(w, yy), u)
            }
            for (i in -12..12) {
                drawLine(
                    t.accent2.copy(alpha = 0.10f), Offset(w / 2f + i * w / 10f, h),
                    Offset(w / 2f + i * w / 60f, hz), u
                )
            }
            var sy = 0f
            while (sy < h) {
                drawLine(Color.White.copy(alpha = 0.025f), Offset(0f, sy), Offset(w, sy), u)
                sy += 4f * u
            }
            for (i in 0..5) {
                val gy = rnd.nextFloat() * h
                drawRect(
                    t.hot.copy(alpha = 0.07f), Offset(rnd.nextFloat() * w * 0.6f, gy),
                    Size(w * (0.1f + rnd.nextFloat() * 0.3f), 3f * u)
                )
            }
        }
        1 -> { // Evangelion: panal hexagonal y franjas de advertencia
            val r = 34f * u
            val dx = r * 1.732f
            val dy = r * 1.5f
            var row = 0
            var hy = 0f
            while (hy < h + r) {
                var hx = if (row % 2 == 0) 0f else dx / 2f
                while (hx < w + r) {
                    val p = Path()
                    for (k in 0..5) {
                        val a = Math.toRadians(60.0 * k + 30.0)
                        val px = hx + r * 0.95f * cos(a).toFloat()
                        val py = hy + r * 0.95f * sin(a).toFloat()
                        if (k == 0) p.moveTo(px, py) else p.lineTo(px, py)
                    }
                    p.close()
                    drawPath(p, t.accent.copy(alpha = 0.09f), style = Stroke(width = u))
                    hx += dx
                }
                hy += dy
                row++
            }
            val bh = 8f * u
            var sx = -bh
            while (sx < w) {
                val p1 = Path().apply {
                    moveTo(sx, 0f); lineTo(sx + bh, 0f); lineTo(sx + 2f * bh, bh); lineTo(sx + bh, bh); close()
                }
                drawPath(p1, t.accent.copy(alpha = 0.35f))
                val p2 = Path().apply {
                    moveTo(sx, h - bh); lineTo(sx + bh, h - bh); lineTo(sx + 2f * bh, h); lineTo(sx + bh, h); close()
                }
                drawPath(p2, t.accent.copy(alpha = 0.35f))
                sx += 2f * bh
            }
        }
        2 -> { // Naruto: gran espiral y puntos de chakra
            val c = Offset(w * 0.82f, h * 0.78f)
            val rmax = h * 0.75f
            val p = Path()
            var th = 0.0
            var first = true
            while (th < 6 * Math.PI) {
                val rr = (th / (6 * Math.PI)).toFloat() * rmax
                val px = c.x + rr * cos(th).toFloat()
                val py = c.y + rr * sin(th).toFloat()
                if (first) { p.moveTo(px, py); first = false } else p.lineTo(px, py)
                th += 0.12
            }
            drawPath(p, t.accent.copy(alpha = 0.12f), style = Stroke(width = 5f * u))
            for (i in 0..11) {
                drawCircle(
                    t.accent2.copy(alpha = 0.09f), (2f + rnd.nextFloat() * 4f) * u,
                    Offset(rnd.nextFloat() * w, rnd.nextFloat() * h)
                )
            }
        }
        3 -> { // Dragon Ball: rayos de aura y siete esferas
            val c = Offset(w * 0.5f, h * 0.55f)
            val n = 28
            val far = w + h
            var i = 0
            while (i < n) {
                val a1 = Math.toRadians(360.0 / n * i)
                val a2 = Math.toRadians(360.0 / n * (i + 1))
                val p = Path().apply {
                    moveTo(c.x, c.y)
                    lineTo(c.x + far * cos(a1).toFloat(), c.y + far * sin(a1).toFloat())
                    lineTo(c.x + far * cos(a2).toFloat(), c.y + far * sin(a2).toFloat())
                    close()
                }
                drawPath(p, t.accent2.copy(alpha = 0.06f))
                i += 2
            }
            for (k in 0..6) {
                val cx = w * (0.12f + 0.13f * k)
                drawCircle(t.accent.copy(alpha = 0.14f), 9f * u, Offset(cx, h - 14f * u))
                drawCircle(t.hot.copy(alpha = 0.20f), 3f * u, Offset(cx, h - 14f * u))
            }
        }
        4 -> { // Street Fighter: semitono y líneas de velocidad
            var py = 0f
            var row = 0
            while (py < h) {
                var px = if (row % 2 == 0) 0f else 9f * u
                while (px < w) {
                    val k = (1f - px / w) * (py / h)
                    if (k > 0.05f) drawCircle(t.accent.copy(alpha = 0.16f), (1f + 4f * k) * u, Offset(px, py))
                    px += 18f * u
                }
                py += 16f * u
                row++
            }
            for (i in 0..14) {
                val ly = rnd.nextFloat() * h
                val len = w * (0.15f + rnd.nextFloat() * 0.35f)
                val x0 = w - rnd.nextFloat() * w * 0.5f
                drawLine(t.accent2.copy(alpha = 0.10f), Offset(x0, ly), Offset(x0 - len, ly + len * 0.1f), 2f * u)
            }
        }
        5 -> { // Turbo: fibra de carbono, estelas y arco de tacómetro
            val s = 10f * u
            var cy = 0f
            var r = 0
            while (cy < h) {
                var cx = if (r % 2 == 0) 0f else s
                while (cx < w) {
                    drawRect(Color.White.copy(alpha = 0.035f), Offset(cx, cy), Size(s, s))
                    cx += 2f * s
                }
                cy += s
                r++
            }
            for (i in 0..9) {
                val ly = h * (0.55f + 0.45f * rnd.nextFloat())
                val len = w * (0.2f + rnd.nextFloat() * 0.4f)
                val x0 = rnd.nextFloat() * w
                drawLine(t.accent.copy(alpha = 0.12f), Offset(x0, ly), Offset(x0 + len, ly), 2f * u)
            }
            drawArc(
                color = t.accent.copy(alpha = 0.10f), startAngle = 135f, sweepAngle = 270f,
                useCenter = false, topLeft = Offset(-h * 0.2f, h * 0.35f),
                size = Size(h * 1.2f, h * 1.2f), style = Stroke(width = 4f * u)
            )
        }
        else -> { // Static: rayos eléctricos
            for (b in 0..3) {
                var bx = w * (0.12f + 0.25f * b) + rnd.nextFloat() * 30f * u
                var bolty = 0f
                val p = Path()
                p.moveTo(bx, bolty)
                while (bolty < h) {
                    bx += (rnd.nextFloat() - 0.5f) * 60f * u
                    bolty += (20f + rnd.nextFloat() * 40f) * u
                    p.lineTo(bx, bolty)
                }
                drawPath(p, t.accent2.copy(alpha = 0.05f), style = Stroke(width = 8f * u))
                drawPath(p, t.accent2.copy(alpha = 0.18f), style = Stroke(width = 1.5f * u))
            }
        }
    }
}

// ============================================================ Pantalla principal
@Composable
fun Dash(onApps: () -> Unit, onSettings: (Int) -> Unit) {
    val t = LocalTheme.current
    val idx by Store.themeIdx.collectAsState()
    val layout by Store.layout.collectAsState()
    val c0 by Store.colL.collectAsState()
    val c1 by Store.colM.collectAsState()
    val c2 by Store.colR.collectAsState()
    Box(Modifier.fillMaxSize().background(t.bg)) {
        Backdrop(idx)
        Column(
            Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (layout) {
                    1 -> { // Clásico
                        GaugeCard(Modifier.weight(wt(1f, c0)).fillMaxHeight()) { onSettings(4) }
                        Column(
                            Modifier.weight(wt(1.25f, c1)).fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            MapCard(Modifier.weight(1f).fillMaxWidth())
                            MusicCard(Modifier.weight(1.25f).fillMaxWidth())
                        }
                        TimeWeatherCard(Modifier.weight(wt(0.9f, c2)).fillMaxHeight())
                    }
                    2 -> { // Mapa al centro
                        MusicCard(Modifier.weight(wt(1f, c0)).fillMaxHeight())
                        MapCard(Modifier.weight(wt(1.3f, c1)).fillMaxHeight())
                        Column(
                            Modifier.weight(wt(0.95f, c2)).fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            TimeWeatherCard(Modifier.fillMaxWidth().weight(0.8f))
                            GaugeCard(Modifier.fillMaxWidth().weight(1.7f)) { onSettings(4) }
                        }
                    }
                    else -> { // Reproductor al centro
                        Column(
                            Modifier.weight(wt(1f, c0)).fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            TimeWeatherCard(Modifier.fillMaxWidth().weight(0.8f))
                            GaugeCard(Modifier.fillMaxWidth().weight(1.7f)) { onSettings(4) }
                        }
                        MusicCard(Modifier.weight(wt(1.1f, c1)).fillMaxHeight())
                        MapCard(Modifier.weight(wt(1f, c2)).fillMaxHeight())
                    }
                }
            }
            Dock(onApps, onSettings)
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
        if (tick != 0L && tick != Store.greetShown.value) {
            Store.greetShown.value = tick
            show = true
            delay(8000)
            show = false
        }
    }
    val a by animateFloatAsState(targetValue = if (show) 1f else 0f, animationSpec = tween(600))
    if (a > 0.01f && text.isNotEmpty()) {
        val shape = themeShape(t)
        Box(
            modifier.alpha(a).widthIn(max = 620.dp).clip(shape).background(t.card, shape)
                .border((t.bw + 1f).dp, t.accent, shape)
                .clickable { show = false }
                .padding(horizontal = 26.dp, vertical = 14.dp)
        ) {
            Text(
                text, fontSize = 18.sp, color = t.accent2, fontFamily = Mono,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
            )
        }
    }
}

// ------------------------------------------------------------ Barra inferior configurable
class Slot(val spec: String, val kind: String, val pkg: String, val label: String, val icon: ImageBitmap?)

fun appSlot(c: Context, spec: String, pkg: String): Slot {
    val pm = c.packageManager
    return try {
        val ai = pm.getApplicationInfo(pkg, 0)
        val label = pm.getApplicationLabel(ai).toString()
        val icon = pm.getApplicationIcon(pkg).toBitmap(72, 72).asImageBitmap()
        Slot(spec, "app", pkg, label, icon)
    } catch (e: Exception) {
        Slot(spec, "none", "", "?", null)
    }
}

val autoPackages = mapOf(
    "radio" to listOf(
        "com.android.fmradio", "com.mediatek.fmradio", "com.syu.radio", "com.microntek.radio",
        "com.nwd.radio", "com.hct.radio", "com.ts.radio", "com.fyt.radio"
    ),
    "bluetooth" to listOf("com.syu.bt", "com.nwd.bt", "com.microntek.bluetooth", "com.hct.bt"),
    "youtube" to listOf(
        "com.google.android.youtube", "com.teamsmart.videomanager.tv", "org.schabi.newpipe"
    )
)

fun resolveSlot(c: Context, spec: String): Slot {
    val pm = c.packageManager
    if (spec == "apps") return Slot(spec, "apps", "", "APPS", null)
    if (spec == "dash") return Slot(spec, "dash", "", "AJUSTES", null)
    if (spec == "btset") return Slot(spec, "btset", "", "BLUETOOTH", null)
    if (spec.startsWith("app:")) return appSlot(c, spec, spec.substring(4))
    if (spec.startsWith("auto:")) {
        val what = spec.substring(5)
        for (p in autoPackages[what].orEmpty()) {
            if (pm.getLaunchIntentForPackage(p) != null) return appSlot(c, spec, p)
        }
        // búsqueda por nombre de la app (por ejemplo "Radio", "Bluetooth")
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        for (ri in pm.queryIntentActivities(i, 0)) {
            val pkg = ri.activityInfo.packageName
            if (pkg == c.packageName) continue
            val name = ri.loadLabel(pm).toString().lowercase()
            if (name.contains(what)) return appSlot(c, spec, pkg)
        }
        if (what == "bluetooth") return Slot(spec, "btset", "", "BLUETOOTH", null)
        return Slot(spec, "none", "", what.uppercase(), null)
    }
    return Slot(spec, "none", "", "", null)
}

@Composable
fun Dock(onApps: () -> Unit, onSettings: (Int) -> Unit) {
    val t = LocalTheme.current
    val ctx = LocalContext.current
    val dock by Store.dock.collectAsState()
    var slots by remember { mutableStateOf<List<Slot>>(emptyList()) }
    LaunchedEffect(dock) {
        slots = withContext(Dispatchers.Default) { dock.map { resolveSlot(ctx, it) } }
    }
    val shape = themeShape(t)
    Row(
        Modifier.fillMaxWidth().height(58.dp).clip(shape)
            .background(t.card.copy(alpha = t.cardAlpha), shape)
            .border(t.bw.dp, t.accent.copy(alpha = 0.6f), shape).padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (s in slots) {
            Column(
                Modifier.weight(1f).fillMaxHeight().clickable {
                    when (s.kind) {
                        "apps" -> onApps()
                        "dash" -> onSettings(0)
                        "btset" -> try {
                            ctx.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                        } catch (e: Exception) { }
                        "app" -> launchPkg(ctx, s.pkg)
                    }
                },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (s.icon != null) {
                    Image(s.icon, null, Modifier.size(28.dp))
                } else if (s.kind == "apps") {
                    Glyph(5, t.accent, Modifier.size(26.dp))
                } else if (s.kind == "btset") {
                    Glyph(6, t.accent, Modifier.size(26.dp))
                } else if (s.kind == "dash") {
                    Text("⚙", fontSize = 22.sp, color = t.accent)
                } else {
                    Spacer(Modifier.size(26.dp))
                }
                FitText(s.label.uppercase(), 9f, t.dim, Modifier.padding(horizontal = 2.dp), null, Mono, 0f, TextAlign.Center)
            }
        }
    }
}

// ------------------------------------------------------------ Velocímetro, kilometraje y servicio
@Composable
fun MiniStat(label: String, value: String, color: Color, modifier: Modifier, onClick: (() -> Unit)? = null) {
    val t = LocalTheme.current
    val m = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    Column(m) {
        FitText(label, 9f, t.accent, Modifier.fillMaxWidth(), null, Mono, 1f)
        FitText(value, 15f, color, Modifier.fillMaxWidth(), FontWeight.Bold)
    }
}

@Composable
fun GaugeCard(modifier: Modifier, onService: () -> Unit) {
    val t = LocalTheme.current
    val speed by Store.speed.collectAsState()
    val model by Store.model.collectAsState()
    val mph by Store.mph.collectAsState()
    val total by Store.totalM.collectAsState()
    val trip by Store.tripM.collectAsState()
    val maint by Store.maint.collectAsState()
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
    val next = remember(total, maint) { Store.nextDue() }
    var svcLabel = "SERVICIO"
    var svcValue = "—"
    var svcColor = t.dim
    if (next != null) {
        val rem = next.second
        svcLabel = maintShort[next.first.key] ?: "SERVICIO"
        svcValue = if (rem <= 0.0) "VENCIDO" else rem.roundToInt().toString() + " km"
        svcColor = if (rem <= 0.0) t.hot else if (rem <= 500.0) t.accent else t.text
    }
    NeonCard(modifier, tag = t.speedTag, pad = 12.dp) {
        FitText(model.uppercase(), 18f, t.text, Modifier.fillMaxWidth(), FontWeight.Bold)
        Gauge(if (gpsOk) speed else 0f, mph, Modifier.weight(1f).fillMaxWidth()) {
            Store.setMph(!mph)
        }
        FitText(
            gpsText + " · TOCA: KM/H ↔ MPH", 10f, if (gpsOk) t.dim else t.hot,
            Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniStat("TOTAL", String.format(Locale.US, "%.0f", total / 1000) + " km", t.text, Modifier.weight(1f))
            MiniStat("VIAJE", String.format(Locale.US, "%.1f", trip / 1000) + " km", t.text, Modifier.weight(1f))
            MiniStat(svcLabel, svcValue, svcColor, Modifier.weight(1f), onService)
        }
    }
}

@Composable
fun Gauge(kmh: Float, mph: Boolean, modifier: Modifier, onToggle: () -> Unit) {
    val t = LocalTheme.current
    val ringPct by Store.gaugeSize.collectAsState()
    val numPct by Store.numSize.collectAsState()
    val fs = LocalDensity.current.fontScale
    val v = if (mph) kmh * 0.621371f else kmh
    val top = if (mph) 120f else 200f
    val a by animateFloatAsState(
        targetValue = (v / top).coerceIn(0f, 1f),
        animationSpec = tween(500)
    )
    BoxWithConstraints(modifier.clickable(onClick = onToggle), contentAlignment = Alignment.Center) {
        val dia = minOf(maxWidth, maxHeight) * (ringPct / 100f)
        Canvas(Modifier.size(dia)) {
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
        // el número y la unidad se calculan a partir del diámetro del anillo
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                v.roundToInt().toString(), fontSize = (dia.value * 0.30f * numPct / 100f / fs).sp,
                color = t.text, fontFamily = Mono, fontWeight = FontWeight.Bold,
                maxLines = 1, softWrap = false
            )
            Text(
                if (mph) "MPH" else "KM/H", fontSize = (dia.value * 0.085f / fs).sp,
                color = t.accent2, fontFamily = Mono, letterSpacing = 3.sp, maxLines = 1
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
            .clip(shape).background(t.card.copy(alpha = t.cardAlpha), shape)
            .border(t.bw.dp, t.accent.copy(alpha = 0.6f), shape)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val u = 1.dp.toPx()
            val s = sheet
            if (img != null && s != null && !lat.isNaN()) {
                val px = ((MapTiles.worldX(lon) - (s.tx - 1)) * 256.0).toFloat()
                val py = ((MapTiles.worldY(lat) - (s.ty - 1)) * 256.0).toFloat()
                drawImage(img, topLeft = Offset(c.x - px, c.y - py), alpha = 0.35f + 0.65f * t.cardAlpha, colorFilter = filter)
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
fun ArtBox(img: ImageBitmap?, modifier: Modifier, circle: Boolean) {
    val t = LocalTheme.current
    val shape: Shape = if (circle) CircleShape else themeShape(t, small = true)
    Box(
        modifier.clip(shape).background(t.bg).border(2.dp, t.accent, shape),
        contentAlignment = Alignment.Center
    ) {
        if (img != null) {
            Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Text("♪", fontSize = if (circle) 22.sp else 52.sp, color = t.accent)
        }
    }
}

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
    val title = if (now.title.isBlank()) "Sin reproducción" else now.title

    NeonCard(modifier, tag = "REPRODUCTOR", pad = 12.dp) {
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val tall = maxHeight >= 270.dp
            val compact = maxHeight < 170.dp
            Column(Modifier.fillMaxSize()) {
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
                    Spacer(Modifier.height(6.dp))
                }
                if (!access) {
                    Text(
                        "Activa Dash en «Acceso a notificaciones» para controlar la música.",
                        fontSize = 13.sp, color = t.dim
                    )
                    Spacer(Modifier.height(8.dp))
                    NeonButton("ACTIVAR ACCESO", Modifier.fillMaxWidth()) {
                        try { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } catch (e: Exception) { }
                    }
                } else if (tall) {
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        val side = minOf(maxWidth, maxHeight)
                        ArtBox(artImg, Modifier.size(side), false)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        title, fontSize = 20.sp, color = t.text, fontWeight = FontWeight.Bold,
                        maxLines = 1, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().clickable { if (now.pkg.isNotEmpty()) launchPkg(ctx, now.pkg) }
                    )
                    Text(
                        now.artist, fontSize = 14.sp, color = t.accent2, maxLines = 1,
                        textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(4.dp))
                    SeekBar(frac, dur > 0L, Modifier.fillMaxWidth()) { f ->
                        if (dur > 0L) MediaHub.seek((f * dur).toLong())
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(fmt(pos), fontSize = 11.sp, color = t.dim, fontFamily = Mono)
                        Text(fmt(dur), fontSize = 11.sp, color = t.dim, fontFamily = Mono)
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CtlBtn(4, false, { MediaHub.prev(ctx) }, 1.2f)
                        CtlBtn(if (now.playing) 1 else 0, true, { MediaHub.playPause(ctx) }, 1.25f)
                        CtlBtn(2, false, { MediaHub.stop(ctx) }, 1.2f)
                        CtlBtn(3, false, { MediaHub.next(ctx) }, 1.2f)
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ArtBox(artImg, Modifier.size(if (compact) 44.dp else 54.dp), true)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f).clickable { if (now.pkg.isNotEmpty()) launchPkg(ctx, now.pkg) }) {
                            Text(title, fontSize = 17.sp, color = t.text, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(now.artist, fontSize = 13.sp, color = t.accent2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    SeekBar(frac, dur > 0L, Modifier.fillMaxWidth()) { f ->
                        if (dur > 0L) MediaHub.seek((f * dur).toLong())
                    }
                    if (!compact) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(fmt(pos), fontSize = 11.sp, color = t.dim, fontFamily = Mono)
                            Text(fmt(dur), fontSize = 11.sp, color = t.dim, fontFamily = Mono)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CtlBtn(4, false, { MediaHub.prev(ctx) })
                        CtlBtn(if (now.playing) 1 else 0, true, { MediaHub.playPause(ctx) })
                        CtlBtn(2, false, { MediaHub.stop(ctx) })
                        CtlBtn(3, false, { MediaHub.next(ctx) })
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
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val hh = maxHeight
            val x = w
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().clickable { openClock(ctx) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        FitText(f1.format(now), 34f, t.text, Modifier.fillMaxWidth(), FontWeight.Bold)
                        FitText(f2.format(now).uppercase(), 12f, t.dim, Modifier.fillMaxWidth())
                    }
                    if (x != null) {
                        val d = Weather.describe(x.code)
                        Spacer(Modifier.width(6.dp))
                        Text(d.second, fontSize = 22.sp, color = t.accent)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            x.temp.toString() + "°", fontSize = 30.sp, color = t.text,
                            fontFamily = Mono, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false
                        )
                    }
                }
                if (x == null) {
                    Spacer(Modifier.height(6.dp))
                    FitText("Clima: esperando ubicación e internet…", 11f, t.dim, Modifier.fillMaxWidth(), null, FontFamily.Default)
                } else {
                    Spacer(Modifier.height(4.dp))
                    if (hh >= 105.dp) {
                        FitText(Weather.describe(x.code).first, 13f, t.text, Modifier.fillMaxWidth(), null, FontFamily.Default)
                    }
                    if (hh >= 130.dp) {
                        FitText("MÁX " + x.hi + "°  MÍN " + x.lo + "°", 11f, t.dim, Modifier.fillMaxWidth())
                    }
                    if (hh >= 150.dp) {
                        FitText("HUM " + x.hum + "%  VIENTO " + x.wind + " KM/H", 11f, t.dim, Modifier.fillMaxWidth())
                    }
                }
            }
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
fun AppGrid(apps: List<AppItem>, modifier: Modifier, onTap: (AppItem) -> Unit) {
    val t = LocalTheme.current
    val shape = themeShape(t, small = true)
    LazyVerticalGrid(
        GridCells.Adaptive(104.dp), modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(apps) { a ->
            Column(
                Modifier.fillMaxWidth().clip(shape).background(t.card.copy(alpha = t.cardAlpha))
                    .border(1.dp, t.accent.copy(alpha = 0.4f), shape)
                    .clickable { onTap(a) }
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

@Composable
fun AppsScreen(onClose: () -> Unit) {
    val t = LocalTheme.current
    val ctx = LocalContext.current
    val idx by Store.themeIdx.collectAsState()
    BackHandler(onBack = onClose)
    var apps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    LaunchedEffect(Unit) { apps = withContext(Dispatchers.Default) { launchable(ctx) } }
    Box(Modifier.fillMaxSize().background(t.bg)) {
        Backdrop(idx)
        Column(
            Modifier.fillMaxSize().padding(16.dp),
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
            AppGrid(apps, Modifier.fillMaxSize()) { a -> launchPkg(ctx, a.pkg); onClose() }
        }
    }
}

// Elegir qué abre cada botón de la barra inferior
@Composable
fun PickSlotScreen(slotIdx: Int, onClose: () -> Unit) {
    val t = LocalTheme.current
    val ctx = LocalContext.current
    BackHandler(onBack = onClose)
    var apps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    LaunchedEffect(Unit) { apps = withContext(Dispatchers.Default) { launchable(ctx) } }
    Column(
        Modifier.fillMaxSize().background(t.bg).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text(
                "BOTÓN " + (slotIdx + 1), fontSize = 22.sp, color = t.text, fontFamily = Mono,
                fontWeight = FontWeight.Bold, letterSpacing = 3.sp
            )
            NeonButton("VOLVER", Modifier.width(140.dp), onClose)
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Chip("Radio (auto)", false) { Store.setDock(slotIdx, "auto:radio"); onClose() }
            Chip("Bluetooth (auto)", false) { Store.setDock(slotIdx, "auto:bluetooth"); onClose() }
            Chip("YouTube (auto)", false) { Store.setDock(slotIdx, "auto:youtube"); onClose() }
            Chip("Aplicaciones", false) { Store.setDock(slotIdx, "apps"); onClose() }
            Chip("Ajustes de Dash", false) { Store.setDock(slotIdx, "dash"); onClose() }
            Chip("Ajustes Bluetooth", false) { Store.setDock(slotIdx, "btset"); onClose() }
            Chip("Vacío", false) { Store.setDock(slotIdx, "none"); onClose() }
        }
        Text("…o elige cualquier app instalada:", fontSize = 12.sp, color = t.dim, fontFamily = Mono)
        AppGrid(apps, Modifier.fillMaxSize()) { a -> Store.setDock(slotIdx, "app:" + a.pkg); onClose() }
    }
}

// ============================================================ Ajustes
@Composable
fun LabeledSlider(
    label: String, valueText: String, value: Float,
    range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit
) {
    val t = LocalTheme.current
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            Tag(label)
            Text(valueText, fontSize = 12.sp, color = t.text, fontFamily = Mono)
        }
        Slider(
            value = value, onValueChange = onChange, valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = t.accent, activeTrackColor = t.accent,
                inactiveTrackColor = t.dim.copy(alpha = 0.3f)
            )
        )
    }
}

val palette = listOf(
    0xFFFCEE0AL, 0xFFFF7A00L, 0xFFFF2A6DL, 0xFFE4002BL, 0xFFB057FFL,
    0xFF33A8FFL, 0xFF00F0FFL, 0xFF8CFF2EL, 0xFFC6FF00L, 0xFFFFFFFFL,
    0xFFD0D0D0L, 0xFF8A8F9CL, 0xFF000000L
)

@Composable
fun SwatchRow(label: String, current: Long, onPick: (Long) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        Tag(label)
        Chip("Tema", current == 0L) { onPick(0L) }
        for (c in palette) {
            val sel = current == c
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(Color(c.toInt()))
                    .border(if (sel) 3.dp else 1.dp, if (sel) Color.White else Color.Gray, CircleShape)
                    .clickable { onPick(c) }
            )
        }
    }
}

@Composable
fun SettingsScreen(initialTab: Int, onClose: () -> Unit) {
    val t = LocalTheme.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    BackHandler(onBack = onClose)

    val themeIdx by Store.themeIdx.collectAsState()
    val mode by Store.mode.collectAsState()
    val mph by Store.mph.collectAsState()
    val voice by Store.voice.collectAsState()
    val layout by Store.layout.collectAsState()
    val scale by Store.uiScale.collectAsState()
    val tScale by Store.textScale.collectAsState()
    val gSize by Store.gaugeSize.collectAsState()
    val nSize by Store.numSize.collectAsState()
    val cL by Store.colL.collectAsState()
    val cM by Store.colM.collectAsState()
    val cR by Store.colR.collectAsState()
    val alphaPct by Store.cardAlpha.collectAsState()
    val ck by Store.cornerKind.collectAsState()
    val cd by Store.cornerDp.collectAsState()
    val bd by Store.borderDp.collectAsState()
    val aOv by Store.accentOv.collectAsState()
    val a2Ov by Store.accent2Ov.collectAsState()
    val tOv by Store.textOv.collectAsState()
    val blur by Store.blur.collectAsState()
    val dim by Store.dim.collectAsState()
    val dock by Store.dock.collectAsState()
    val bgRev by Store.bgRev.collectAsState()

    var tab by remember { mutableStateOf(initialTab) }
    var pickIdx by remember { mutableStateOf(-1) }
    var msg by remember { mutableStateOf("") }
    var model by remember { mutableStateOf(Store.model.value) }
    var calib by remember { mutableStateOf(Store.calib.value.toString()) }
    var total by remember { mutableStateOf(String.format(Locale.US, "%.1f", Store.totalM.value / 1000)) }
    var name by remember { mutableStateOf(Store.userName.value) }
    val maint0 = remember { Store.maint.value }
    val lastTxt = remember {
        maint0.map { if (it.lastKm < 0) "" else String.format(Locale.US, "%.0f", it.lastKm) }.toMutableStateList()
    }
    val everyTxt = remember { maint0.map { it.everyKm.toString() }.toMutableStateList() }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) { Backgrounds.save(ctx, themeIdx, uri) }
                if (ok) {
                    Store.bgRev.value = Store.bgRev.value + 1
                    msg = "Fondo guardado para el tema " + Themes[themeIdx.coerceIn(0, Themes.size - 1)].name
                } else {
                    msg = "No se pudo leer esa imagen."
                }
            }
        }
    }

    if (pickIdx >= 0) {
        PickSlotScreen(pickIdx) { pickIdx = -1 }
    } else {
        Column(Modifier.fillMaxSize().background(t.bg).padding(horizontal = 20.dp, vertical = 14.dp)) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "AJUSTES", fontSize = 22.sp, color = t.text, fontFamily = Mono,
                    fontWeight = FontWeight.Bold, letterSpacing = 3.sp
                )
                Spacer(Modifier.width(8.dp))
                Chip("General", tab == 0) { tab = 0 }
                Chip("Estilo", tab == 1) { tab = 1 }
                Chip("Tamaños", tab == 2) { tab = 2 }
                Chip("Barra", tab == 3) { tab = 3 }
                Chip("Servicio", tab == 4) { tab = 4 }
            }
            Spacer(Modifier.height(10.dp))
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (tab == 0) {
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
                        Spacer(Modifier.width(12.dp))
                        Tag("UNIDAD")
                        Chip("km/h", !mph) { Store.setMph(false) }
                        Chip("mph", mph) { Store.setMph(true) }
                        Spacer(Modifier.width(12.dp))
                        Tag("SALUDO POR VOZ")
                        Chip("Sí", voice) { Store.setVoice(true) }
                        Chip("No", !voice) { Store.setVoice(false) }
                    }
                }
                if (tab == 1) {
                    LabeledSlider(
                        "TRANSPARENCIA DE LAS TARJETAS", (100 - alphaPct).toString() + "%",
                        (100 - alphaPct).toFloat(), 0f..100f
                    ) { Store.setCardAlpha(100 - it.roundToInt()) }
                    Tag("FONDO DEL TEMA " + Themes[themeIdx.coerceIn(0, Themes.size - 1)].name.uppercase())
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NeonButton("ELEGIR IMAGEN", Modifier.width(190.dp)) {
                            try {
                                pick.launch(arrayOf("image/*"))
                            } catch (e: Exception) {
                                msg = "Tu radio no tiene selector de archivos. Copia la imagen a la carpeta indicada abajo."
                            }
                        }
                        NeonButton("QUITAR", Modifier.width(120.dp)) {
                            Backgrounds.remove(ctx, themeIdx)
                            Store.bgRev.value = Store.bgRev.value + 1
                            msg = "Fondo quitado (se usa el diseño del tema)."
                        }
                    }
                    if (msg.isNotEmpty()) Text(msg, fontSize = 12.sp, color = t.accent2)
                    val ext = remember(bgRev) { ctx.getExternalFilesDir(null)?.path ?: "Android/data/com.dash.radio/files" }
                    Text(
                        "Sin selector de archivos: copia la imagen a " + ext + "/fondo_" + themeIdx + ".jpg",
                        fontSize = 10.sp, color = t.dim, fontFamily = Mono
                    )
                    LabeledSlider("DESENFOQUE", blur.toString() + " / 10", blur.toFloat(), 0f..10f) {
                        Store.setBlur(it.roundToInt())
                    }
                    LabeledSlider("OSCURECER FONDO", dim.toString() + "%", dim.toFloat(), 0f..90f) {
                        Store.setDim(it.roundToInt())
                    }
                    Tag("TARJETAS")
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically
                    ) {
                        Tag("ESQUINAS")
                        Chip("Del tema", ck < 0) { Store.setCornerKind(-1) }
                        Chip("Rectas", ck == 0) { Store.setCornerKind(0) }
                        Chip("Cortadas", ck == 1) { Store.setCornerKind(1) }
                        Chip("Redondas", ck == 2) { Store.setCornerKind(2) }
                    }
                    LabeledSlider("TAMAÑO DE ESQUINA", cd.toString() + " dp", cd.toFloat(), 0f..32f) {
                        if (ck < 0) Store.setCornerKind(2)
                        Store.setCornerDp(it.roundToInt())
                    }
                    LabeledSlider(
                        "GROSOR DEL BORDE", if (bd < 0) "tema" else bd.toString() + " dp",
                        (if (bd < 0) 1 else bd).toFloat(), 0f..4f
                    ) { Store.setBorder(it.roundToInt()) }
                    Tag("COLORES")
                    SwatchRow("PRINCIPAL", aOv) { Store.setAccentOv(it) }
                    SwatchRow("SECUNDARIO", a2Ov) { Store.setAccent2Ov(it) }
                    SwatchRow("TEXTO", tOv) { Store.setTextOv(it) }
                    NeonButton("RESTABLECER ESTILO", Modifier.width(260.dp)) { Store.resetStyle() }
                }
                if (tab == 2) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically
                    ) {
                        Tag("DISEÑO")
                        Chip("Reproductor al centro", layout == 0) { Store.setLayout(0) }
                        Chip("Clásico", layout == 1) { Store.setLayout(1) }
                        Chip("Mapa al centro", layout == 2) { Store.setLayout(2) }
                    }
                    LabeledSlider("TAMAÑO DE LA INTERFAZ", scale.toString() + "%", scale.toFloat(), 70f..140f) {
                        Store.setScale(it.roundToInt())
                    }
                    LabeledSlider("TAMAÑO DEL TEXTO", tScale.toString() + "%", tScale.toFloat(), 70f..150f) {
                        Store.setTextScale(it.roundToInt())
                    }
                    LabeledSlider("VELOCÍMETRO (ANILLO)", gSize.toString() + "%", gSize.toFloat(), 40f..100f) {
                        Store.setGaugeSize(it.roundToInt())
                    }
                    LabeledSlider("NÚMERO DE VELOCIDAD", nSize.toString() + "%", nSize.toFloat(), 50f..150f) {
                        Store.setNumSize(it.roundToInt())
                    }
                    LabeledSlider("COLUMNA IZQUIERDA", cL.toString() + "%", cL.toFloat(), 50f..180f) {
                        Store.setColL(it.roundToInt())
                    }
                    LabeledSlider("COLUMNA CENTRAL", cM.toString() + "%", cM.toFloat(), 50f..180f) {
                        Store.setColM(it.roundToInt())
                    }
                    LabeledSlider("COLUMNA DERECHA", cR.toString() + "%", cR.toFloat(), 50f..180f) {
                        Store.setColR(it.roundToInt())
                    }
                    NeonButton("RESTABLECER TAMAÑOS", Modifier.width(280.dp)) { Store.resetSizes() }
                }
                if (tab == 3) {
                    Text("Elige qué abre cada botón de la barra inferior.", fontSize = 12.sp, color = t.dim)
                    for (i in dock.indices) {
                        val label = remember(dock[i]) { resolveSlot(ctx, dock[i]).label }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Tag("BOTÓN " + (i + 1))
                                Text(if (label.isEmpty()) "(vacío)" else label, fontSize = 16.sp, color = t.text)
                            }
                            NeonButton("CAMBIAR", Modifier.width(150.dp)) { pickIdx = i }
                        }
                    }
                }
                if (tab == 4) {
                    val odo = parseNum(total) ?: (Store.totalM.value / 1000.0)
                    Text(
                        "Odómetro actual: " + fmtKm(odo) + " km. Dash avisa cuando falten 500 km o menos.",
                        fontSize = 12.sp, color = t.dim
                    )
                    Text(
                        "Puedes escribir 107000, 107.000 o 107,000: es lo mismo.",
                        fontSize = 11.sp, color = t.dim
                    )
                    for (i in maint0.indices) {
                        val m = maint0[i]
                        Tag((maintNames[m.key] ?: m.key).uppercase())
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                lastTxt[i], { lastTxt[i] = it },
                                label = { Text(if (m.key == "aceite") "Km del último cambio de aceite" else "Km del último servicio") },
                                singleLine = true, modifier = Modifier.weight(1.4f)
                            )
                            OutlinedTextField(
                                everyTxt[i], { everyTxt[i] = it },
                                label = { Text("Cada (km)") }, singleLine = true, modifier = Modifier.weight(1f)
                            )
                            NeonButton("HECHO HOY", Modifier.width(140.dp)) {
                                Store.maintDone(m.key)
                                lastTxt[i] = String.format(Locale.US, "%.0f", Store.totalM.value / 1000)
                            }
                        }
                        val last = parseNum(lastTxt[i])
                        val every = parseNum(everyTxt[i])?.toInt() ?: m.everyKm
                        if (last == null) {
                            Text("Sin seguimiento: escribe el km del último servicio.", fontSize = 12.sp, color = t.dim)
                        } else {
                            val nextKm = last + every
                            val rem = nextKm - odo
                            Text(
                                "Próximo a los " + fmtKm(nextKm) + " km · " +
                                    (if (rem <= 0.0) "VENCIDO hace " + fmtKm(-rem) + " km" else "faltan " + fmtKm(rem) + " km"),
                                fontSize = 12.sp, color = if (rem <= 0.0) t.hot else t.accent2
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NeonButton("GUARDAR", Modifier.width(150.dp)) {
                    Store.saveSettings(
                        model, (calib.trim().replace(',', '.').toDoubleOrNull() ?: 1.0).coerceIn(0.5, 2.0),
                        parseNum(total)
                    )
                    Store.setName(name)
                    for (i in maint0.indices) {
                        Store.setMaint(
                            maint0[i].key, parseNum(lastTxt[i]) ?: -1.0,
                            parseNum(everyTxt[i])?.toInt() ?: maint0[i].everyKm
                        )
                    }
                    onClose()
                }
                NeonButton("REINICIAR VIAJE", Modifier.width(200.dp)) { Store.resetTrip() }
                NeonButton("CERRAR", Modifier.width(140.dp), onClose)
            }
        }
    }
}
