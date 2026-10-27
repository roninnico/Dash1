package com.dash.radio

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.AlarmClock
import android.provider.Settings
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
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
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

// ============================================================ Temas
class Theme(
    val bg: Color, val card: Color, val accent: Color, val accent2: Color, val hot: Color,
    val text: Color, val dim: Color, val map: FloatArray
)

// Cyberpunk 2077: amarillo ácido + cian + magenta sobre negro
val Cyber = Theme(
    bg = Color(0xFF04050A), card = Color(0xFF0D0F17),
    accent = Color(0xFFFCEE0A), accent2 = Color(0xFF00F0FF), hot = Color(0xFFFF2A6D),
    text = Color(0xFFF2F2F2), dim = Color(0xFF8A8F9C),
    map = floatArrayOf(
        -0.45f, 0f, 0f, 0f, 114.75f,
        0f, -0.8f, 0f, 0f, 204f,
        0f, 0f, -1f, 0f, 255f,
        0f, 0f, 0f, 1f, 0f
    )
)

// Neon Genesis Evangelion: naranja NERV + verde neón + rojo alerta sobre púrpura oscuro
val Nerv = Theme(
    bg = Color(0xFF050308), card = Color(0xFF110A1A),
    accent = Color(0xFFFF7A00), accent2 = Color(0xFF8CFF2E), hot = Color(0xFFFF2D2D),
    text = Color(0xFFF4EEFF), dim = Color(0xFF8D7FA3),
    map = floatArrayOf(
        -0.7f, 0f, 0f, 0f, 178.5f,
        0f, -0.35f, 0f, 0f, 89.25f,
        0f, 0f, -0.9f, 0f, 229.5f,
        0f, 0f, 0f, 1f, 0f
    )
)

val LocalTheme = compositionLocalOf { Cyber }
val Mono = FontFamily.Monospace

// ============================================================ Actividad
class MainActivity : ComponentActivity() {
    private val perm = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { startTrip() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        setContent {
            val idx by Store.themeIdx.collectAsState()
            val theme = if (idx == 1) Nerv else Cyber
            CompositionLocalProvider(LocalTheme provides theme) {
                MaterialTheme(
                    colorScheme = darkColorScheme(
                        primary = theme.accent, onPrimary = Color.Black,
                        secondary = theme.accent2, background = theme.bg,
                        surface = theme.card, onSurface = theme.text
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
    }

    override fun onResume() {
        super.onResume()
        startTrip()
    }

    private fun startTrip() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) ContextCompat.startForegroundService(this, Intent(this, TripService::class.java))
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
    val shape = CutCornerShape(topStart = 16.dp, bottomEnd = 16.dp)
    Column(
        modifier.clip(shape).background(t.card, shape)
            .border(1.dp, t.accent.copy(alpha = 0.55f), shape).padding(pad)
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
    val shape = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp)
    Box(
        modifier.height(46.dp).clip(shape).background(t.card, shape)
            .border(1.dp, t.accent, shape).clickable(onClick = onClick),
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
    val shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp)
    Box(
        Modifier.clip(shape)
            .background(if (selected) t.accent else Color.Transparent, shape)
            .border(1.dp, t.accent, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text.uppercase(), fontSize = 12.sp, fontFamily = Mono, maxLines = 1,
            color = if (selected) Color.Black else t.accent
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
    val s = if (big) 54.dp else 44.dp
    Box(
        Modifier.size(s).clip(CircleShape)
            .border(if (big) 2.dp else 1.dp, if (big) t.accent2 else t.accent.copy(alpha = 0.6f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Glyph(kind, if (big) t.accent2 else t.text, Modifier.size(s * 0.55f))
    }
}

// ============================================================ Pantalla principal
@Composable
fun Dash(onApps: () -> Unit, onSettings: () -> Unit) {
    val t = LocalTheme.current
    val total by Store.totalM.collectAsState()
    val trip by Store.tripM.collectAsState()
    Row(
        Modifier.fillMaxSize().background(t.bg).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GaugeCard(Modifier.weight(1f).fillMaxHeight())
        Column(
            Modifier.weight(1.25f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MapCard(Modifier.weight(1f).fillMaxWidth())
            MusicCard(Modifier.weight(1.2f).fillMaxWidth())
        }
        Column(
            Modifier.weight(0.9f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TimeCard(Modifier.fillMaxWidth())
            WeatherCard(Modifier.fillMaxWidth().weight(1f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("TOTAL", total, Modifier.weight(1f))
                Stat("VIAJE", trip, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NeonButton("APPS", Modifier.weight(1f), onApps)
                NeonButton("⚙", Modifier.width(56.dp), onSettings)
            }
        }
    }
}

@Composable
fun Stat(label: String, meters: Double, modifier: Modifier) {
    val t = LocalTheme.current
    NeonCard(modifier, pad = 10.dp) {
        Tag(label)
        Text(
            String.format(Locale.US, "%.1f", meters / 1000), fontSize = 20.sp, color = t.text,
            fontFamily = Mono, fontWeight = FontWeight.Bold, maxLines = 1
        )
        Text("KM", fontSize = 11.sp, color = t.accent2, fontFamily = Mono)
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
                    Offset(c.x + cs * r2, c.y + sn * r2), strokeWidth = 2f
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
    val filter = remember(t) { ColorFilter.colorMatrix(ColorMatrix(t.map)) }
    val shape = CutCornerShape(topStart = 16.dp, bottomEnd = 16.dp)
    Box(
        modifier
            .clickable { if (navApps.isNotEmpty()) launchPkg(ctx, navApps[0].first) }
            .clip(shape).background(t.card, shape)
            .border(1.dp, t.accent.copy(alpha = 0.55f), shape)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val s = sheet
            if (img != null && s != null && !lat.isNaN()) {
                val px = ((MapTiles.worldX(lon) - (s.tx - 1)) * 256.0).toFloat()
                val py = ((MapTiles.worldY(lat) - (s.ty - 1)) * 256.0).toFloat()
                drawImage(img, topLeft = Offset(c.x - px, c.y - py), colorFilter = filter)
            } else {
                var x = 0f
                while (x < size.width) {
                    drawLine(t.dim.copy(alpha = 0.18f), Offset(x, 0f), Offset(x, size.height), 1f)
                    x += 40f
                }
                var y = 0f
                while (y < size.height) {
                    drawLine(t.dim.copy(alpha = 0.18f), Offset(0f, y), Offset(size.width, y), 1f)
                    y += 40f
                }
            }
            if (!lat.isNaN()) {
                val p = Path().apply {
                    moveTo(c.x, c.y - 24f); lineTo(c.x + 15f, c.y + 18f)
                    lineTo(c.x, c.y + 9f); lineTo(c.x - 15f, c.y + 18f); close()
                }
                rotate(degrees = brg, pivot = c) {
                    drawPath(p, t.accent)
                    drawPath(p, Color.Black, style = Stroke(width = 2f))
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
    var drag by remember { mutableStateOf<Float?>(null) }
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
        if (!access) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Activa Dash en «Acceso a notificaciones» para controlar la música.",
                fontSize = 13.sp, color = t.dim
            )
            Spacer(Modifier.height(8.dp))
            NeonButton("ACTIVAR ACCESO", Modifier.fillMaxWidth()) {
                try { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } catch (e: Exception) { }
            }
        } else {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(62.dp).clip(CircleShape).background(t.bg)
                        .border(2.dp, t.accent, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (artImg != null) {
                        Image(artImg, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        Text("♪", fontSize = 26.sp, color = t.accent)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f).clickable { if (now.pkg.isNotEmpty()) launchPkg(ctx, now.pkg) }) {
                    Text(
                        if (now.title.isBlank()) "Sin reproducción" else now.title,
                        fontSize = 18.sp, color = t.text, fontWeight = FontWeight.Bold, maxLines = 1
                    )
                    Text(now.artist, fontSize = 14.sp, color = t.accent2, maxLines = 1)
                }
            }
            Slider(
                value = drag ?: frac,
                onValueChange = { drag = it },
                onValueChangeFinished = {
                    val d = drag
                    if (d != null && dur > 0L) MediaHub.seek((d * dur).toLong())
                    drag = null
                },
                enabled = dur > 0L,
                colors = SliderDefaults.colors(
                    thumbColor = t.accent, activeTrackColor = t.accent,
                    inactiveTrackColor = t.dim.copy(alpha = 0.3f)
                )
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(fmt(pos), fontSize = 11.sp, color = t.dim, fontFamily = Mono)
                Text(fmt(dur), fontSize = 11.sp, color = t.dim, fontFamily = Mono)
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

// ------------------------------------------------------------ Hora y clima
@Composable
fun TimeCard(modifier: Modifier) {
    val t = LocalTheme.current
    val ctx = LocalContext.current
    var now by remember { mutableStateOf(Date()) }
    val f1 = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val f2 = remember { SimpleDateFormat("EEE d MMM", Locale.getDefault()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(1000)
        }
    }
    NeonCard(modifier.clickable { openClock(ctx) }, tag = "HORA", pad = 12.dp) {
        Text(
            f1.format(now), fontSize = 44.sp, color = t.accent2, fontFamily = Mono,
            fontWeight = FontWeight.Bold
        )
        Text(f2.format(now).uppercase(), fontSize = 13.sp, color = t.dim, fontFamily = Mono)
    }
}

@Composable
fun WeatherCard(modifier: Modifier) {
    val t = LocalTheme.current
    val ctx = LocalContext.current
    var w by remember { mutableStateOf<Weather.W?>(null) }
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
    NeonCard(modifier, tag = "CLIMA", pad = 12.dp) {
        val x = w
        if (x == null) {
            Text("Esperando ubicación e internet…", fontSize = 13.sp, color = t.dim)
        } else {
            val d = Weather.describe(x.code)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(d.second, fontSize = 32.sp, color = t.accent)
                Spacer(Modifier.width(8.dp))
                Text(
                    x.temp.toString() + "°", fontSize = 38.sp, color = t.accent2,
                    fontFamily = Mono, fontWeight = FontWeight.Bold
                )
            }
            Text(d.first, fontSize = 14.sp, color = t.text)
            Spacer(Modifier.height(4.dp))
            Text("MÁX " + x.hi + "°  MÍN " + x.lo + "°", fontSize = 12.sp, color = t.dim, fontFamily = Mono)
            Text("SENS. " + x.feels + "°  HUM " + x.hum + "%", fontSize = 12.sp, color = t.dim, fontFamily = Mono)
            Text("VIENTO " + x.wind + " KM/H", fontSize = 12.sp, color = t.dim, fontFamily = Mono)
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
    val shape = CutCornerShape(10.dp)
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
    val mph by Store.mph.collectAsState()
    var model by remember { mutableStateOf(Store.model.value) }
    var calib by remember { mutableStateOf(Store.calib.value.toString()) }
    var total by remember { mutableStateOf(String.format(Locale.US, "%.1f", Store.totalM.value / 1000)) }
    Column(
        Modifier.fillMaxSize().background(t.bg).padding(24.dp),
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tag("TEMA")
            Chip("Cyberpunk", themeIdx == 0) { Store.setTheme(0) }
            Chip("Evangelion", themeIdx == 1) { Store.setTheme(1) }
            Spacer(Modifier.width(20.dp))
            Tag("UNIDAD")
            Chip("km/h", !mph) { Store.setMph(false) }
            Chip("mph", mph) { Store.setMph(true) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NeonButton("GUARDAR", Modifier.width(150.dp)) {
                Store.saveSettings(model, calib.toDoubleOrNull() ?: 1.0, total.toDoubleOrNull())
                onClose()
            }
            NeonButton("REINICIAR VIAJE", Modifier.width(210.dp)) { Store.resetTrip() }
            NeonButton("CANCELAR", Modifier.width(150.dp), onClose)
        }
    }
}
