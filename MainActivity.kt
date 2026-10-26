package com.dash.radio

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

val Accent = Color(0xFF4DD0E1)
val CardBg = Color(0xFF121212)

class MainActivity : ComponentActivity() {
    private val perm = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { startTrip() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                var apps by remember { mutableStateOf(false) }
                if (apps) AppsScreen { apps = false } else Dash { apps = true }
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

@Composable
fun Dash(onApps: () -> Unit) {
    val ctx = LocalContext.current
    val speed by Store.speed.collectAsState()
    val total by Store.totalM.collectAsState()
    val trip by Store.tripM.collectAsState()
    val model by Store.model.collectAsState()
    var time by remember { mutableStateOf("") }
    var info by remember { mutableStateOf<Media.Info?>(null) }
    var access by remember { mutableStateOf(true) }
    var showSet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val f = SimpleDateFormat("HH:mm", Locale.getDefault())
        while (true) {
            time = f.format(Date())
            access = Media.hasAccess(ctx)
            info = Media.info(ctx)
            delay(1000)
        }
    }

    Row(
        Modifier.fillMaxSize().background(Color.Black).padding(28.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        Column(
            Modifier.weight(1f).fillMaxHeight(),
            Arrangement.Center, Alignment.CenterHorizontally
        ) {
            Text(
                speed.roundToInt().toString(),
                fontSize = 170.sp, fontWeight = FontWeight.Light, color = Color.White
            )
            Text("km/h", fontSize = 26.sp, color = Accent)
        }
        Column(Modifier.weight(1f).fillMaxHeight(), Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column {
                    Text(model, fontSize = 28.sp, color = Color.White, fontWeight = FontWeight.Medium)
                    Text(time, fontSize = 20.sp, color = Color.Gray)
                }
                Row {
                    TextButton(onClick = onApps) { Text("Apps", color = Accent, fontSize = 20.sp) }
                    TextButton(onClick = { showSet = true }) { Text("⚙", color = Accent, fontSize = 24.sp) }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Stat("TOTAL", total, Modifier.weight(1f))
                Stat("VIAJE", trip, Modifier.weight(1f))
            }
            MediaCard(info, access)
        }
    }
    if (showSet) SettingsDialog { showSet = false }
}

@Composable
fun Stat(label: String, meters: Double, modifier: Modifier) {
    Card(
        modifier, shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(label, fontSize = 14.sp, color = Color.Gray, letterSpacing = 2.sp)
            Text(
                String.format(Locale.US, "%.1f", meters / 1000),
                fontSize = 40.sp, color = Color.White, fontWeight = FontWeight.Light
            )
            Text("km", fontSize = 16.sp, color = Accent)
        }
    }
}

@Composable
fun MediaCard(info: Media.Info?, access: Boolean) {
    val ctx = LocalContext.current
    Card(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(Modifier.padding(18.dp)) {
            if (!access) {
                Text("Para controlar la música, da acceso a notificaciones", color = Color.Gray, fontSize = 16.sp)
                TextButton(onClick = {
                    ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }) { Text("Dar acceso", color = Accent) }
            } else {
                Text(info?.title ?: "Sin reproducción", fontSize = 22.sp, color = Color.White, maxLines = 1)
                Text(info?.artist ?: "", fontSize = 16.sp, color = Color.Gray, maxLines = 1)
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
                    TextButton(onClick = { Media.prev(ctx) }) { Text("⏮", fontSize = 34.sp, color = Color.White) }
                    TextButton(onClick = { Media.toggle(ctx) }) {
                        Text(if (info?.playing == true) "⏸" else "▶", fontSize = 34.sp, color = Accent)
                    }
                    TextButton(onClick = { Media.next(ctx) }) { Text("⏭", fontSize = 34.sp, color = Color.White) }
                }
            }
        }
    }
}

@Composable
fun SettingsDialog(onClose: () -> Unit) {
    var model by remember { mutableStateOf(Store.model.value) }
    var calib by remember { mutableStateOf(Store.calib.value.toString()) }
    var total by remember { mutableStateOf(String.format(Locale.US, "%.1f", Store.totalM.value / 1000)) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Ajustes") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(model, { model = it }, label = { Text("Modelo del auto") }, singleLine = true)
                OutlinedTextField(total, { total = it }, label = { Text("Kilometraje total (km)") }, singleLine = true)
                OutlinedTextField(calib, { calib = it }, label = { Text("Calibración (1.00 = sin ajuste)") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                Store.saveSettings(model, calib.toDoubleOrNull() ?: 1.0, total.toDoubleOrNull())
                onClose()
            }) { Text("Guardar") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { Store.resetTrip() }) { Text("Reiniciar viaje") }
                TextButton(onClick = onClose) { Text("Cancelar") }
            }
        }
    )
}

fun launchable(c: Context): List<Pair<String, String>> {
    val pm = c.packageManager
    val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(i, 0)
        .map { it.loadLabel(pm).toString() to it.activityInfo.packageName }
        .filter { it.second != c.packageName }
        .sortedBy { it.first.lowercase() }
}

@Composable
fun AppsScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    BackHandler(onBack = onClose)
    val apps = remember { launchable(ctx) }
    LazyVerticalGrid(
        GridCells.Adaptive(150.dp),
        Modifier.fillMaxSize().background(Color.Black).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(apps) { (name, pkg) ->
            Card(
                Modifier.fillMaxWidth().clickable {
                    ctx.packageManager.getLaunchIntentForPackage(pkg)?.let { ctx.startActivity(it) }
                    onClose()
                },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg)
            ) {
                Text(name, Modifier.padding(18.dp), color = Color.White, maxLines = 1)
            }
        }
    }
}
