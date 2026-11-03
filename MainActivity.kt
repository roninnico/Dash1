package com.dash.radio

import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable

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
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.tan

// Vacío a propósito: permite leer y controlar la música de otras apps.
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
val maintShort = mapOf(
    "aceite" to "ACEITE", "aire" to "F.AIRE", "neum" to "NEUMÁT.",
    "frenos" to "FRENOS", "bateria" to "BATERÍA"
)
val maintTips = mapOf(
    "aceite" to "toca cambiar el aceite",
    "aire" to "revisa el filtro de aire",
    "neum" to "toca rotar los neumáticos",
    "frenos" to "revisa los frenos",
    "bateria" to "revisa la batería"
)

// ---------------------------------------------------------------- Estado y datos guardados
object Store {
    private lateinit var ctx: Context
    private val sp get() = ctx.getSharedPreferences("dash", Context.MODE_PRIVATE)

    val speed = MutableStateFlow(0f)
    val totalM = MutableStateFlow(0.0)
    val tripM = MutableStateFlow(0.0)
    val model = MutableStateFlow("Mi auto")
    val calib = MutableStateFlow(1.0)
    val gpsAcc = MutableStateFlow(-1f)
    val lastFix = MutableStateFlow(0L)
    val lat = MutableStateFlow(Double.NaN)
    val lon = MutableStateFlow(Double.NaN)
    val bearing = MutableStateFlow(0f)
    val mph = MutableStateFlow(false)
    val themeIdx = MutableStateFlow(0)
    val mode = MutableStateFlow(0)            // 0 = oscuro, 1 = AMOLED, 2 = claro
    val userName = MutableStateFlow("Nico")
    val voice = MutableStateFlow(true)
    val greetText = MutableStateFlow("")
    val greetTick = MutableStateFlow(0L)
    val greetAt = MutableStateFlow(0L)
    val blur = MutableStateFlow(6)            // 0 = nítido ... 10 = muy difuminado
    val dim = MutableStateFlow(55)            // % que se oscurece la imagen de fondo
    val layout = MutableStateFlow(0)          // 0 = reproductor al centro, 1 = clásico, 2 = mapa al centro
    val uiScale = MutableStateFlow(100)       // % de tamaño de toda la interfaz
    val cardAlpha = MutableStateFlow(86)      // % de opacidad de las tarjetas
    val cornerKind = MutableStateFlow(-1)     // -1 = del tema, 0 = rectas, 1 = cortadas, 2 = redondas
    val cornerDp = MutableStateFlow(16)
    val borderDp = MutableStateFlow(-1)       // -1 = del tema
    val accentOv = MutableStateFlow(0L)       // 0 = color del tema
    val accent2Ov = MutableStateFlow(0L)
    val textScale = MutableStateFlow(100)     // % solo del texto
    val gaugeSize = MutableStateFlow(100)     // % del anillo del velocímetro
    val numSize = MutableStateFlow(100)       // % del número de velocidad
    val colL = MutableStateFlow(100)          // % de ancho de cada columna
    val colM = MutableStateFlow(100)
    val colR = MutableStateFlow(100)
    val mapZoom = MutableStateFlow(16)        // 12 (lejos) a 18 (muy cerca)
    val dockPos = MutableStateFlow(0)         // 0 = abajo, 1 = izquierda, 2 = derecha
    val cardOv = MutableStateFlow(0L)         // color de fondo de tarjetas elegido (0 = el del tema)
    val textOv = MutableStateFlow(0L)         // color de texto elegido (0 = el del tema)
    val greetShown = MutableStateFlow(0L)     // último saludo ya mostrado en pantalla
    val dock = MutableStateFlow(listOf("auto:radio", "auto:bluetooth", "apps", "auto:youtube", "dash"))
    val maint = MutableStateFlow(defaultMaint())
    val carRev = MutableStateFlow(0)          // sube cuando cambia la foto del auto
    val bgRev = MutableStateFlow(0)           // sube cada vez que cambia un fondo

    fun init(c: Context) {
        ctx = c.applicationContext
        totalM.value = sp.getString("total", "0")?.toDoubleOrNull() ?: 0.0
        tripM.value = sp.getString("trip", "0")?.toDoubleOrNull() ?: 0.0
        model.value = sp.getString("model", "Mi auto") ?: "Mi auto"
        calib.value = sp.getString("calib", "1.0")?.toDoubleOrNull() ?: 1.0
        mph.value = sp.getBoolean("mph", false)
        themeIdx.value = sp.getInt("theme", 0)
        mode.value = sp.getInt("mode", 0)
        userName.value = sp.getString("name", "Nico") ?: "Nico"
        voice.value = sp.getBoolean("voice", true)
        blur.value = sp.getInt("blur", 6)
        dim.value = sp.getInt("dim", 55)
        layout.value = sp.getInt("layout", 0)
        uiScale.value = sp.getInt("scale", 100)
        cardAlpha.value = sp.getInt("alpha", 86)
        cornerKind.value = sp.getInt("ckind", -1)
        cornerDp.value = sp.getInt("cdp", 16)
        borderDp.value = sp.getInt("bdp", -1)
        accentOv.value = sp.getLong("acc", 0L)
        accent2Ov.value = sp.getLong("acc2", 0L)
        textScale.value = sp.getInt("tscale", 100)
        gaugeSize.value = sp.getInt("gsize", 100)
        numSize.value = sp.getInt("nsize", 100)
        colL.value = sp.getInt("colL", 100)
        colM.value = sp.getInt("colM", 100)
        colR.value = sp.getInt("colR", 100)
        textOv.value = sp.getLong("tov", 0L)
        cardOv.value = sp.getLong("cov", 0L)
        mapZoom.value = sp.getInt("mzoom", 16).coerceIn(12, 18)
        dockPos.value = sp.getInt("dockpos", 0)
        val d = (sp.getString("dock", "") ?: "").split("\n").filter { it.isNotEmpty() }
        if (d.size == 5) dock.value = d
        val parsed = HashMap<String, Maint>()
        for (line in (sp.getString("maint", "") ?: "").split("\n")) {
            val q = line.split("|")
            if (q.size == 3) {
                val l = q[1].toDoubleOrNull()
                val e = q[2].toIntOrNull()
                if (l != null && e != null) parsed[q[0]] = Maint(q[0], l, e)
            }
        }
        maint.value = defaultMaint().map { parsed[it.key] ?: it }
    }

    private fun defaultMaint() = listOf(
        Maint("aceite", -1.0, 10000), Maint("aire", -1.0, 20000),
        Maint("neum", -1.0, 10000), Maint("frenos", -1.0, 20000), Maint("bateria", -1.0, 40000)
    )

    private fun saveMaint() {
        sp.edit().putString("maint", maint.value.joinToString("\n") { it.key + "|" + it.lastKm + "|" + it.everyKm }).apply()
    }

    fun setMaint(key: String, lastKm: Double, everyKm: Int) {
        maint.value = maint.value.map { if (it.key == key) Maint(key, lastKm, if (everyKm > 0) everyKm else it.everyKm) else it }
        saveMaint()
    }

    fun maintDone(key: String) {
        val now = Math.round(totalM.value / 100.0) / 10.0
        maint.value = maint.value.map { if (it.key == key) Maint(key, now, it.everyKm) else it }
        saveMaint()
    }

    fun remaining(m: Maint): Double? =
        if (m.lastKm < 0) null else m.lastKm + m.everyKm - totalM.value / 1000.0

    fun nextDue(): Pair<Maint, Double>? {
        var best: Pair<Maint, Double>? = null
        for (m in maint.value) {
            val r = remaining(m) ?: continue
            val b = best
            if (b == null || r < b.second) best = Pair(m, r)
        }
        return best
    }

    fun dueNotes(): List<String> {
        val out = ArrayList<String>()
        for (m in maint.value) {
            val r = remaining(m) ?: continue
            if (r <= 500.0) out.add(maintTips[m.key] ?: m.key)
        }
        return out
    }

    private fun pi(k: String, v: Int) {
        sp.edit().putInt(k, v).apply()
    }

    fun setLayout(i: Int) { layout.value = i; pi("layout", i) }
    fun setScale(i: Int) { uiScale.value = i; pi("scale", i) }
    fun setCardAlpha(i: Int) { cardAlpha.value = i; pi("alpha", i) }
    fun setCornerKind(i: Int) { cornerKind.value = i; pi("ckind", i) }
    fun setCornerDp(i: Int) { cornerDp.value = i; pi("cdp", i) }
    fun setBorder(i: Int) { borderDp.value = i; pi("bdp", i) }
    fun setDim(i: Int) { dim.value = i; pi("dim", i) }

    fun setAccentOv(v: Long) {
        accentOv.value = v
        sp.edit().putLong("acc", v).apply()
    }

    fun setAccent2Ov(v: Long) {
        accent2Ov.value = v
        sp.edit().putLong("acc2", v).apply()
    }

    fun setTextScale(i: Int) { textScale.value = i; pi("tscale", i) }
    fun setGaugeSize(i: Int) { gaugeSize.value = i; pi("gsize", i) }
    fun setNumSize(i: Int) { numSize.value = i; pi("nsize", i) }
    fun setColL(i: Int) { colL.value = i; pi("colL", i) }
    fun setColM(i: Int) { colM.value = i; pi("colM", i) }
    fun setColR(i: Int) { colR.value = i; pi("colR", i) }

    fun shouldGreet(now: Long): Boolean {
        val last = sp.getLong("lastgreet", 0L)
        val boot = now - SystemClock.elapsedRealtime()
        return last == 0L || last < boot || now - last > 30 * 60 * 1000L
    }

    fun markGreeted(now: Long) {
        sp.edit().putLong("lastgreet", now).apply()
    }

    fun setMapZoom(z: Int) {
        val v = z.coerceIn(12, 18)
        mapZoom.value = v
        pi("mzoom", v)
    }

    fun setDockPos(i: Int) { dockPos.value = i; pi("dockpos", i) }

    fun setCardOv(v: Long) {
        cardOv.value = v
        sp.edit().putLong("cov", v).apply()
    }

    fun setTextOv(v: Long) {
        textOv.value = v
        sp.edit().putLong("tov", v).apply()
    }

    fun resetStyle() {
        setCardAlpha(86); setCornerKind(-1); setCornerDp(16); setBorder(-1)
        setAccentOv(0L); setAccent2Ov(0L); setTextOv(0L); setCardOv(0L); setBlur(6); setDim(55)
    }

    fun resetSizes() {
        setScale(100); setTextScale(100); setGaugeSize(100); setNumSize(100)
        setColL(100); setColM(100); setColR(100)
    }

    fun setDock(i: Int, spec: String) {
        val l = dock.value.toMutableList()
        if (i in l.indices) l[i] = spec
        dock.value = l
        sp.edit().putString("dock", l.joinToString("\n")).apply()
    }

    fun addDistance(meters: Double) {
        val d = meters * calib.value
        totalM.value += d
        tripM.value += d
        sp.edit().putString("total", totalM.value.toString())
            .putString("trip", tripM.value.toString()).apply()
    }

    fun resetTrip() {
        tripM.value = 0.0
        sp.edit().putString("trip", "0").apply()
    }

    fun saveSettings(name: String, calibration: Double, totalKm: Double?) {
        model.value = name
        calib.value = if (calibration > 0) calibration else 1.0
        if (totalKm != null) totalM.value = totalKm * 1000
        sp.edit().putString("model", model.value)
            .putString("calib", calib.value.toString())
            .putString("total", totalM.value.toString()).apply()
    }

    fun setMph(v: Boolean) {
        mph.value = v
        sp.edit().putBoolean("mph", v).apply()
    }

    fun setTheme(i: Int) {
        themeIdx.value = i
        sp.edit().putInt("theme", i).apply()
    }

    fun setMode(i: Int) {
        mode.value = i
        sp.edit().putInt("mode", i).apply()
    }

    fun setName(n: String) {
        userName.value = n.trim()
        sp.edit().putString("name", n.trim()).apply()
    }

    fun setBlur(i: Int) {
        blur.value = i
        sp.edit().putInt("blur", i).apply()
    }

    fun setVoice(v: Boolean) {
        voice.value = v
        sp.edit().putBoolean("voice", v).apply()
    }
}

// ---------------------------------------------------------------- Fondos personalizados
object Backgrounds {
    private fun file(c: Context, idx: Int) = File(c.filesDir, "bg_" + idx + ".jpg")

    private fun extFile(c: Context, idx: Int): File? {
        val d = c.getExternalFilesDir(null) ?: return null
        for (n in listOf("fondo_" + idx + ".jpg", "fondo_" + idx + ".png", "fondo_" + idx + ".jpeg")) {
            val f = File(d, n)
            if (f.exists()) return f
        }
        return null
    }

    fun has(c: Context, idx: Int): Boolean = file(c, idx).exists() || extFile(c, idx) != null

    fun save(c: Context, idx: Int, uri: Uri): Boolean {
        return try {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
            var sample = 1
            while (o.outWidth / sample > 1400 || o.outHeight / sample > 1400) sample *= 2
            val o2 = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o2) }
            if (bmp == null) {
                false
            } else {
                val fmt = if (bmp.hasAlpha()) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                file(c, idx).outputStream().use { bmp.compress(fmt, 85, it) }
                bmp.recycle()
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun carFile(c: Context) = File(c.filesDir, "car.png")

    fun saveCar(c: Context, uri: Uri): Boolean {
        return try {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
            var sample = 1
            while (o.outWidth / sample > 1200 || o.outHeight / sample > 1200) sample *= 2
            val o2 = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o2) }
            if (bmp == null) {
                false
            } else {
                carFile(c).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bmp.recycle()
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    fun loadCar(c: Context): Bitmap? {
        val f = carFile(c)
        if (!f.exists()) return null
        return try { BitmapFactory.decodeFile(f.path) } catch (e: Exception) { null }
    }

    fun removeCar(c: Context) {
        carFile(c).delete()
    }

    fun remove(c: Context, idx: Int) {
        file(c, idx).delete()
        val d = c.getExternalFilesDir(null)
        if (d != null) {
            for (n in listOf("fondo_" + idx + ".jpg", "fondo_" + idx + ".png", "fondo_" + idx + ".jpeg")) {
                try { File(d, n).delete() } catch (e: Exception) { }
            }
        }
    }

    fun load(c: Context, idx: Int, blur: Int): Bitmap? {
        var f: File? = file(c, idx)
        if (f == null || !f.exists()) f = extFile(c, idx)
        if (f == null) return null
        return try {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, o)
            var sample = 1
            while (o.outWidth / sample > 1400 || o.outHeight / sample > 1400) sample *= 2
            val src = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
            if (src == null) {
                null
            } else if (blur <= 0) {
                src
            } else {
                val k = 2 + blur * 3
                val small = Bitmap.createScaledBitmap(src, max(8, src.width / k), max(8, src.height / k), true)
                if (small !== src) src.recycle()
                small
            }
        } catch (e: Exception) {
            null
        }
    }
}

// ---------------------------------------------------------------- Música
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
        } catch (e: Exception) {
        }
    }

    private fun reload(list: List<MediaController>?) {
        for ((c, cb) in registered) {
            try { c.unregisterCallback(cb) } catch (e: Exception) { }
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
            try { k.registerCallback(cb) } catch (e: Exception) { }
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

        val key = s.pkg + "|" + title + "|" + artist
                val key = s.pkg + "|" + title + "|" + artist
        val finalArt = if (key == lastKey) lastArt else {
            lastKey = key
            lastArt = art
            art
        }

        val playing = state?.state == PlaybackState.STATE_PLAYING
        val pos = state?.position ?: 0L
        val posAt = state?.lastPositionUpdateTime ?: SystemClock.elapsedRealtime()
        val speed = state?.playbackSpeed ?: 1f

                now.value = Now(s.pkg, s.label, title, artist, finalArt, playing, dur, pos, posAt, speed)
    }

    fun play() { chosen()?.ctl?.transportControls?.play() }
    fun pause() { chosen()?.ctl?.transportControls?.pause() }
    fun next() { chosen()?.ctl?.transportControls?.skipToNext() }
    fun prev() { chosen()?.ctl?.transportControls?.skipToPrevious() }
    fun seekTo(pos: Long) { chosen()?.ctl?.transportControls?.seekTo(pos) }
}

// ---------------------------------------------------------------- Servicio GPS / Ubicación
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
                Store.lat.value = l.latitude
                Store.lon.value = l.longitude
                Store.bearing.value = l.bearing
                Store.gpsAcc.value = l.accuracy
                Store.lastFix.value = now

                val spd = if (l.hasSpeed()) l.speed * 3.6f else 0f
                Store.speed.value = spd

                if (prev != null) {
                    val dist = l.distanceTo(prev).toDouble()
                    if (dist in 0.5..100.0) {
                        Store.addDistance(dist)
                    }
                }
            }
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        try {
            locMgr?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 800L, 1f, locList!!)
        } catch (e: Exception) {
        }
    }

    override fun onDestroy() {
        try {
            if (locList != null) locMgr?.removeUpdates(locList!!)
        } catch (e: Exception) {
        }
        super.onDestroy()
    }
}

// ============================================================ Pantallas principales
@Composable
fun Dash(onApps: () -> Unit, onSettings: (Int) -> Unit) {
    val t = LocalTheme.current
    val layout by Store.layout.collectAsState()
    val ctx = LocalContext.current

    LaunchedEffect(Unit) {
        MediaHub.ensure(ctx)
    }

    Box(
        Modifier.fillMaxSize().background(t.bg)
    ) {
        Column(
            Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.spaceBetween
        ) {
            Row(
                Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (layout) {
                    1 -> {
                        MapCard(Modifier.weight(1.6f).fillMaxHeight())
                        Column(
                            Modifier.weight(1f).fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            TimeWeatherCard(Modifier.weight(0.8f))
                            MusicCard(Modifier.weight(1f))
                            GaugeCard(Modifier.weight(1.5f)) { onSettings(3) }
                        }
                    }
                    else -> {
                        Column(
                            Modifier.weight(1.7f).fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            MapCard(Modifier.weight(2f))
                            MusicCard(Modifier.weight(1f))
                        }
                        Column(
                            Modifier.weight(0.8f).fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
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
        CtlBtn(0, false, { onSettings(0) })
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
