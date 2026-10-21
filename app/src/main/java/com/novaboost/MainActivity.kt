package com.novaboost

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

private val Neon = Color(0xFF00E5FF)
private val Pink = Color(0xFFFF2E93)
private val Dim = Color(0xFF8A93B2)

// Edit daftar ini kalau ada app yang tidak terdeteksi (huruf kecil, tanpa spasi)
private val KEYS = listOf("freefire", "ggmp", "redcorner", "nebulatouch", "sensix")

data class Game(val label: String, val pkg: String)

class Tweak(
    val title: String,
    val desc: String,
    val on: (List<String>) -> String,
    val off: (List<String>) -> String
)

private fun each(pk: List<String>, f: (String) -> String) = pk.joinToString("; ", transform = f)

private val TWEAKS = listOf(
    Tweak("Whitelist Doze", "Bebas dari Doze & hemat baterai",
        { each(it) { p -> "dumpsys deviceidle whitelist +$p" } },
        { each(it) { p -> "dumpsys deviceidle whitelist -$p" } }),
    Tweak("Izin Background & Overlay", "Tetap hidup di latar + izin overlay",
        { each(it) { p -> "cmd appops set $p RUN_IN_BACKGROUND allow; cmd appops set $p RUN_ANY_IN_BACKGROUND allow; cmd appops set $p SYSTEM_ALERT_WINDOW allow" } },
        { each(it) { p -> "cmd appops set $p RUN_IN_BACKGROUND default; cmd appops set $p RUN_ANY_IN_BACKGROUND default" } }),
    Tweak("Game Mode Performa", "Prioritas performa maksimal (Android 12+)",
        { each(it) { p -> "cmd game mode performance $p" } },
        { each(it) { p -> "cmd game mode standard $p" } }),
    Tweak("Bersihkan RAM", "Matikan proses latar belakang",
        { "am kill-all" }, { "" }),
    Tweak("Mode Fokus (DND)", "Blokir gangguan saat main",
        { "cmd notification set_dnd priority" }, { "cmd notification set_dnd off" }),
    Tweak("Matikan Heads-up", "Tanpa pop-up notifikasi",
        { "settings put global heads_up_notifications_enabled 0" },
        { "settings put global heads_up_notifications_enabled 1" }),
    Tweak("Animasi 0.5x", "Transisi sistem lebih ngebut",
        { "settings put global window_animation_scale 0.5; settings put global transition_animation_scale 0.5; settings put global animator_duration_scale 0.5" },
        { "settings put global window_animation_scale 1.0; settings put global transition_animation_scale 1.0; settings put global animator_duration_scale 1.0" }),
    Tweak("Kunci Hz Maksimal", "Paksa 120Hz (ganti angkanya kalau HP-mu 90Hz)",
        { "settings put system min_refresh_rate 120.0; settings put system peak_refresh_rate 120.0; settings put system user_refresh_rate 120" },
        { "settings delete system min_refresh_rate; settings delete system peak_refresh_rate; settings delete system user_refresh_rate" }),
    Tweak("Touch Responsif", "Percepat respons tahan layar",
        { "settings put secure long_press_timeout 250" },
        { "settings delete secure long_press_timeout" }),
    Tweak("Bersihkan Cache", "Hapus cache semua aplikasi",
        { "pm trim-caches 999G" }, { "" }),
    Tweak("Optimasi Dex", "Kompilasi ulang game (lama, loading lebih cepat)",
        { each(it) { p -> "cmd package compile -m speed-profile -f $p" } },
        { each(it) { p -> "cmd package compile --reset $p" } }),
    Tweak("Matikan Phantom Killer", "Android tidak membunuh proses latar (Android 12+)",
        { "settings put global settings_enable_monitor_phantom_procs false; device_config put activity_manager max_phantom_processes 2147483647" },
        { "settings put global settings_enable_monitor_phantom_procs true; device_config delete activity_manager max_phantom_processes" }),
    Tweak("Downscale Game Terendah", "Render 0.3x: FPS naik, gambar buram (Android 12+)",
        { each(it.filter { p -> p.contains("freefire") }) { p -> "cmd game set --mode performance --downscale 0.3 $p || cmd game downscale 0.3 $p" } },
        { each(it.filter { p -> p.contains("freefire") }) { p -> "cmd game reset $p; cmd game downscale disable $p" } })
)

private fun findGames(c: Context): List<Game> = try {
    val pm = c.packageManager
    pm.getInstalledApplications(0)
        .filter { pm.getLaunchIntentForPackage(it.packageName) != null && it.packageName != c.packageName }
        .map { Game(pm.getApplicationLabel(it).toString(), it.packageName) }
        .filter { g ->
            val n = g.label.filter { it.isLetterOrDigit() }.lowercase()
            KEYS.any { n.contains(it) }
        }
        .sortedBy { it.label }
} catch (e: Throwable) {
    emptyList()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val f = java.io.File(filesDir, "crash.txt")
        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            try { f.writeText(android.util.Log.getStackTraceString(e)) } catch (t: Throwable) {}
            android.os.Process.killProcess(android.os.Process.myPid())
        }
        if (f.exists()) {
            val trace = f.readText()
            f.delete()
            setContent {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    Surface(Modifier.fillMaxSize()) {
                        Column(Modifier.systemBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
                            Text("⚠ APP CRASH: screenshot layar ini, kirim ke Claude", color = Pink, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(12.dp))
                            Text(trace.take(2500), fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                        }
                    }
                }
            }
            return
        }
        enableEdgeToEdge()
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { App() } }
    }
}

@Composable
fun App() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val games = remember { findGames(ctx) }
    val on = remember { mutableStateListOf(0, 1, 2, 3, 5, 6, 7, 11, 12) }
    val log = remember { mutableStateListOf("Siap tempur 🔥") }
    var state by remember { mutableIntStateOf(0) } // 0 mati, 1 belum izin, 2 aktif
    var phase by remember { mutableIntStateOf(0) } // 0 idle, 1 proses, 2 selesai

    fun refresh() {
        state = try {
            if (!Shizuku.pingBinder()) 0
            else if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) 2 else 1
        } catch (e: Throwable) { 0 }
    }
    fun say(s: String) {
        log.add(0, s)
        if (log.size > 30) log.removeAt(log.lastIndex)
    }
    fun play(p: String) {
        try {
            ctx.packageManager.getLaunchIntentForPackage(p)?.let { ctx.startActivity(it) }
        } catch (e: Throwable) {
            say("⚠ ${e.message}")
        }
    }
    fun go(boost: Boolean) {
        if (state != 2) { say("⚠ Aktifkan Shizuku & beri izin dulu"); return }
        scope.launch {
            phase = 1
            try {
                val pk = games.map { it.pkg }
                TWEAKS.forEachIndexed { i, t ->
                    if (!boost || i in on) {
                        val r = Sh.run(if (boost) t.on(pk) else t.off(pk))
                        say((if (boost) "✔ " else "↺ ") + t.title + (if (r.startsWith("ERR")) "  ⚠" else ""))
                    }
                }
                phase = if (boost) 2 else 0
            } catch (e: Throwable) {
                say("⚠ ${e.message}")
                phase = 0
            }
        }
    }

    DisposableEffect(Unit) {
        val a = Shizuku.OnBinderReceivedListener { refresh() }
        val b = Shizuku.OnBinderDeadListener { refresh() }
        val c = Shizuku.OnRequestPermissionResultListener { _, _ -> refresh() }
        refresh()
        try {
            Shizuku.addBinderReceivedListenerSticky(a)
            Shizuku.addBinderDeadListener(b)
            Shizuku.addRequestPermissionResultListener(c)
        } catch (e: Throwable) {
            say("⚠ Shizuku: ${e.message}")
        }
        onDispose {
            try {
                Shizuku.removeBinderReceivedListener(a)
                Shizuku.removeBinderDeadListener(b)
                Shizuku.removeRequestPermissionResultListener(c)
            } catch (e: Throwable) {}
        }
    }

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF070B16), Color(0xFF1B0F33), Color(0xFF050810)))
        )
    ) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "NOVABOOST",
                        style = TextStyle(
                            brush = Brush.linearGradient(listOf(Neon, Pink)),
                            fontSize = 28.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp
                        )
                    )
                    Text("Game Booster • Shizuku", color = Dim, fontSize = 12.sp)
                }
                val (txt, col) = when (state) {
                    2 -> "Shizuku Aktif" to Color(0xFF00E676)
                    1 -> "Beri Izin" to Color(0xFFFFC400)
                    else -> "Shizuku Mati" to Color(0xFFFF5252)
                }
                Pill(txt, col) {
                    if (state == 0) play("moe.shizuku.privileged.api")
                    else try { Shizuku.requestPermission(100) } catch (e: Throwable) { refresh() }
                }
            }

            BoostButton(phase) { if (phase != 1) go(true) }
            Text(
                when (phase) {
                    1 -> "Sedang mengoptimasi..."
                    2 -> "Siap main! Gas 🔥"
                    else -> "Ketuk untuk BOOST ${on.size} fitur"
                },
                color = Dim
            )

            Glass {
                Text("🎮  GAME & TOOLS TERDETEKSI", color = Neon, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                if (games.isEmpty()) {
                    Text(
                        "Belum ada yang cocok. Edit daftar KEYS di kode.",
                        color = Dim, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)
                    )
                }
                games.forEach { g ->
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(g.label, color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text(g.pkg, color = Dim, fontSize = 11.sp)
                        }
                        TextButton(onClick = { play(g.pkg) }) {
                            Text("MAIN ▶", color = Pink, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Glass {
                Text("⚡  FITUR BOOST", color = Neon, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                TWEAKS.forEachIndexed { i, t ->
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(t.title, color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text(t.desc, color = Dim, fontSize = 12.sp)
                        }
                        Switch(
                            checked = i in on,
                            onCheckedChange = { if (it) on.add(i) else on.remove(i) },
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = Neon, checkedThumbColor = Color(0xFF001018)
                            )
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = { go(false) },
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, Pink)
            ) { Text("↺  RESET KE NORMAL", color = Pink) }

            Glass {
                Text("▌LOG", color = Dim, fontSize = 11.sp)
                log.take(8).forEach {
                    Text(it, color = Color(0xFF7CFFB2), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun Glass(content: @Composable ColumnScope.() -> Unit) {
    val s = RoundedCornerShape(24.dp)
    Column(
        Modifier.fillMaxWidth().clip(s).background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Brush.linearGradient(listOf(Neon.copy(alpha = 0.6f), Pink.copy(alpha = 0.3f))), s)
            .padding(16.dp),
        content = content
    )
}

@Composable
fun Pill(text: String, c: Color, onClick: () -> Unit) {
    Row(
        Modifier.clip(CircleShape).background(c.copy(alpha = 0.15f)).border(1.dp, c, CircleShape)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(8.dp).background(c, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(text, color = c, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun BoostButton(phase: Int, onClick: () -> Unit) {
    val t = rememberInfiniteTransition(label = "t")
    val pulse by t.animateFloat(0.9f, 1.05f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "p")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "s")
    Box(
        Modifier.size(200.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2
            drawCircle(Brush.radialGradient(listOf(Neon.copy(alpha = 0.35f), Color.Transparent), center, r), r * pulse)
            rotate(if (phase == 1) spin else 0f) {
                drawArc(
                    Brush.sweepGradient(listOf(Neon, Pink, Neon), center), 0f, 360f, false,
                    Offset(center.x - r * 0.78f, center.y - r * 0.78f), Size(r * 1.56f, r * 1.56f),
                    style = Stroke(10.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            drawCircle(Brush.linearGradient(listOf(Color(0xFF16233F), Color(0xFF2A1450))), r * 0.68f)
        }
        Text(
            when (phase) { 1 -> "..."; 2 -> "✓"; else -> "BOOST" },
            color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp
        )
    }
}
