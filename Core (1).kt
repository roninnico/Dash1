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
    val cardOv = MutableStateFlow(0L)         // color de fondo de tarjetas elegido (0 = el del tema)
    val textOv = MutableStateFlow(0L)         // color de texto elegido (0 = el del tema)
    val greetShown = MutableStateFlow(0L)     // último saludo ya mostrado en pantalla
    val dock = MutableStateFlow(listOf("auto:radio", "auto:bluetooth", "apps", "auto:youtube", "dash"))
    val maint = MutableStateFlow(defaultMaint())
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

    // "Hecho hoy": marca el servicio con el kilometraje actual
    fun maintDone(key: String) {
        val now = Math.round(totalM.value / 100.0) / 10.0
        maint.value = maint.value.map { if (it.key == key) Maint(key, now, it.everyKm) else it }
        saveMaint()
    }

    // Kilómetros que faltan (negativo = vencido). null si no se ha configurado.
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

    // Avisos para el saludo: lo que vence o está a 500 km o menos
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

// ---------------------------------------------------------------- Fondos personalizados (uno por tema)
object Backgrounds {
    private fun file(c: Context, idx: Int) = File(c.filesDir, "bg_" + idx + ".jpg")

    // También se acepta copiar la imagen a mano: Android/data/com.dash.radio/files/fondo_N.jpg
    private fun extFile(c: Context, idx: Int): File? {
        val d = c.getExternalFilesDir(null) ?: return null
        for (n in listOf("fondo_" + idx + ".jpg", "fondo_" + idx + ".png", "fondo_" + idx + ".jpeg")) {
            val f = File(d, n)
            if (f.exists()) return f
        }
        return null
    }

    fun has(c: Context, idx: Int): Boolean = file(c, idx).exists() || extFile(c, idx) != null

    // Guarda la imagen elegida, reducida a máx. 1400 px, para el tema idx
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

    fun remove(c: Context, idx: Int) {
        file(c, idx).delete()
        val d = c.getExternalFilesDir(null)
        if (d != null) {
            for (n in listOf("fondo_" + idx + ".jpg", "fondo_" + idx + ".png", "fondo_" + idx + ".jpeg")) {
                try { File(d, n).delete() } catch (e: Exception) { }
            }
        }
    }

    // Carga el fondo y lo difumina reduciéndolo (se dibuja ampliado con filtro: queda borroso)
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

// ---------------------------------------------------------------- Música (cualquier reproductor)
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

    // Se llama desde la pantalla (hilo principal) hasta que el acceso a notificaciones esté activo.
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
        return l.firstOrNull { it.ctl.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: l.firstOrNull { it.ctl.playbackState?.state == PlaybackState.STATE_PAUSED }
            ?: l.firstOrNull()
    }

    fun pick(pkg: String) {
        picked.value = pkg
        lastKey = ""
        refresh()
    }

    private fun refresh() {
        val s = chosen()
        if (s == null) {
            now.value = Now()
            lastKey = ""
            lastArt = null
            return
        }
        val md = s.ctl.metadata
        val st = s.ctl.playbackState
        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
        val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: ""
        val key = s.pkg + "|" + title + "|" + artist
        if (key != lastKey || lastArt == null) {
            lastKey = key
            val bmp = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: md?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            lastArt = if (bmp != null && bmp.width > 320 && bmp.width > 0)
                Bitmap.createScaledBitmap(bmp, 320, 320 * bmp.height / bmp.width, true)
            else bmp
        }
        now.value = Now(
            pkg = s.pkg,
            label = s.label,
            title = title,
            artist = artist,
            art = lastArt,
            playing = st?.state == PlaybackState.STATE_PLAYING,
            durMs = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
            posMs = st?.position ?: 0L,
            posAt = st?.lastPositionUpdateTime ?: 0L,
            speed = st?.playbackSpeed ?: 1f
        )
    }

    private fun tc() = chosen()?.ctl?.transportControls

    private fun key(c: Context, code: Int) {
        val am = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    fun playPause(c: Context) {
        val t = tc()
        if (t == null) key(c, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        else if (now.value.playing) t.pause() else t.play()
    }

    fun next(c: Context) {
        val t = tc()
        if (t == null) key(c, KeyEvent.KEYCODE_MEDIA_NEXT) else t.skipToNext()
    }

    fun prev(c: Context) {
        val t = tc()
        if (t == null) key(c, KeyEvent.KEYCODE_MEDIA_PREVIOUS) else t.skipToPrevious()
    }

    fun stop(c: Context) {
        val t = tc()
        if (t == null) key(c, KeyEvent.KEYCODE_MEDIA_STOP) else t.stop()
    }

    fun seek(ms: Long) {
        tc()?.seekTo(ms)
    }
}

// ---------------------------------------------------------------- Clima (Open-Meteo, sin clave)
object Weather {
    data class W(
        val temp: Int, val feels: Int, val hum: Int, val wind: Int,
        val code: Int, val hi: Int, val lo: Int
    )

    fun fetch(lat: Double, lon: Double): W? {
        return try {
            val u = "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon +
                "&current=temperature_2m,apparent_temperature,relative_humidity_2m,wind_speed_10m,weather_code" +
                "&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1"
            val cn = URL(u).openConnection() as HttpURLConnection
            cn.connectTimeout = 8000
            cn.readTimeout = 8000
            val txt = cn.inputStream.bufferedReader().use { it.readText() }
            val j = JSONObject(txt)
            val c = j.getJSONObject("current")
            val d = j.getJSONObject("daily")
            W(
                c.getDouble("temperature_2m").roundToInt(),
                c.getDouble("apparent_temperature").roundToInt(),
                c.getDouble("relative_humidity_2m").roundToInt(),
                c.getDouble("wind_speed_10m").roundToInt(),
                c.getInt("weather_code"),
                d.getJSONArray("temperature_2m_max").getDouble(0).roundToInt(),
                d.getJSONArray("temperature_2m_min").getDouble(0).roundToInt()
            )
        } catch (e: Exception) {
            null
        }
    }

    fun describe(code: Int): Pair<String, String> = when (code) {
        0 -> Pair("Despejado", "☀")
        1, 2 -> Pair("Poco nublado", "☀")
        3 -> Pair("Nublado", "☁")
        45, 48 -> Pair("Niebla", "≋")
        in 51..67, in 80..82 -> Pair("Lluvia", "☂")
        in 71..77, 85, 86 -> Pair("Nieve", "❄")
        in 95..99 -> Pair("Tormenta", "⚡")
        else -> Pair("Variable", "☁")
    }
}

// ---------------------------------------------------------------- Mapa (teselas de OpenStreetMap)
object MapTiles {
    const val Z = 16
    private const val N = 65536.0 // 2^16

    class Sheet(val bmp: Bitmap, val tx: Int, val ty: Int)

    fun worldX(lon: Double): Double = (lon + 180.0) / 360.0 * N

    fun worldY(lat: Double): Double {
        val r = Math.toRadians(lat)
        return (1.0 - ln(tan(r) + 1.0 / cos(r)) / Math.PI) / 2.0 * N
    }

    // Une 3x3 teselas alrededor de la posición. Se vuelve a llamar solo al cambiar de tesela central.
    fun load(lat: Double, lon: Double): Sheet? {
        val tx = floor(worldX(lon)).toInt()
        val ty = floor(worldY(lat)).toInt()
        val out = Bitmap.createBitmap(768, 768, Bitmap.Config.ARGB_8888)
        val cv = android.graphics.Canvas(out)
        var ok = false
        for (dx in -1..1) {
            for (dy in -1..1) {
                try {
                    val cn = URL("https://tile.openstreetmap.org/" + Z + "/" + (tx + dx) + "/" + (ty + dy) + ".png")
                        .openConnection() as HttpURLConnection
                    cn.setRequestProperty("User-Agent", "DashRadio/1.0 (launcher personal para auto)")
                    cn.connectTimeout = 8000
                    cn.readTimeout = 8000
                    val b = BitmapFactory.decodeStream(cn.inputStream)
                    if (b != null) {
                        cv.drawBitmap(b, ((dx + 1) * 256).toFloat(), ((dy + 1) * 256).toFloat(), null)
                        b.recycle()
                        ok = true
                    }
                } catch (e: Exception) {
                }
            }
        }
        return if (ok) Sheet(out, tx, ty) else null
    }
}

// ---------------------------------------------------------------- GPS en segundo plano
class TripService : Service(), LocationListener {
    private var last: Location? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel("dash", "Dash", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val n = if (Build.VERSION.SDK_INT >= 26)
            Notification.Builder(this, "dash") else Notification.Builder(this)
        val notif = n.setContentTitle("Dash")
            .setContentText("Registrando kilometraje")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .build()
        if (Build.VERSION.SDK_INT >= 29)
            startForeground(1, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        else startForeground(1, notif)

        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this)
        } catch (e: SecurityException) {
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onLocationChanged(l: Location) {
        Store.gpsAcc.value = l.accuracy
        Store.lastFix.value = System.currentTimeMillis()
        Store.lat.value = l.latitude
        Store.lon.value = l.longitude
        val p = last
        var ms = if (l.hasSpeed()) l.speed else -1f
        if (ms < 0f && p != null) {
            val dt = (l.time - p.time) / 1000f
            ms = if (dt > 0f) p.distanceTo(l) / dt else 0f
        }
        if (ms < 0f) ms = 0f
        val kmh = ms * 3.6f
        Store.speed.value = if (kmh < 2f) 0f else kmh
        if (l.hasBearing() && ms > 1.5f) Store.bearing.value = l.bearing
        if (l.accuracy < 30f) {
            if (p != null && ms > 1.5f) {
                val d = p.distanceTo(l)
                if (d < 200f) Store.addDistance(d.toDouble())
            }
            last = l
        }
    }

    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    override fun onDestroy() {
        (getSystemService(Context.LOCATION_SERVICE) as LocationManager).removeUpdates(this)
        super.onDestroy()
    }
}
