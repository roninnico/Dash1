package com.dash.radio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow

// Vacío a propósito: permite controlar la música de otras apps.
class NotifListener : NotificationListenerService()

object Store {
    private lateinit var ctx: Context
    private val sp get() = ctx.getSharedPreferences("dash", Context.MODE_PRIVATE)

    val speed = MutableStateFlow(0f)
    val totalM = MutableStateFlow(0.0)
    val tripM = MutableStateFlow(0.0)
    val model = MutableStateFlow("Mi auto")
    val calib = MutableStateFlow(1.0)

    fun init(c: Context) {
        ctx = c.applicationContext
        totalM.value = sp.getString("total", "0")?.toDoubleOrNull() ?: 0.0
        tripM.value = sp.getString("trip", "0")?.toDoubleOrNull() ?: 0.0
        model.value = sp.getString("model", "Mi auto") ?: "Mi auto"
        calib.value = sp.getString("calib", "1.0")?.toDoubleOrNull() ?: 1.0
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
}

object Media {
    data class Info(val title: String, val artist: String, val playing: Boolean)

    fun hasAccess(c: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(c).contains(c.packageName)

    private fun ctl(c: Context): MediaController? {
        if (!hasAccess(c)) return null
        return try {
            val m = c.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val list = m.getActiveSessions(ComponentName(c, NotifListener::class.java))
            list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                ?: list.firstOrNull()
        } catch (e: Exception) {
            null
        }
    }

    fun info(c: Context): Info? {
        val k = ctl(c) ?: return null
        val md = k.metadata
        return Info(
            md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Sin título",
            md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "",
            k.playbackState?.state == PlaybackState.STATE_PLAYING
        )
    }

    fun prev(c: Context) { ctl(c)?.transportControls?.skipToPrevious() }
    fun next(c: Context) { ctl(c)?.transportControls?.skipToNext() }
    fun toggle(c: Context) {
        val k = ctl(c) ?: return
        if (k.playbackState?.state == PlaybackState.STATE_PLAYING) k.transportControls.pause()
        else k.transportControls.play()
    }
}

// Corre en segundo plano (con la pantalla apagada) midiendo velocidad y distancia por GPS.
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
        val kmh = if (l.hasSpeed()) l.speed * 3.6f else 0f
        Store.speed.value = if (kmh < 2f) 0f else kmh
        if (l.accuracy < 30f) {
            val p = last
            if (p != null && l.speed > 1.5f) {
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
