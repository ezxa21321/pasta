package com.airware.loader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airware.loader.ui.*
import com.airware.loader.ui.theme.AccentPink
import com.airware.loader.ui.theme.AccentPinkDim
import com.airware.loader.ui.theme.AirwareLoaderTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TARGET_PACKAGE = "com.axlebolt.standoff2"

enum class LoaderStatus { IDLE, CHECKING, INJECTING, SUCCESS, ERROR }

class MainActivity : ComponentActivity() {

    init {
        System.loadLibrary("airware_injector")
    }

    // JNI: запускает ptrace-инжект нативной библиотеки чита в процесс игры.
    external fun nativeInject(targetPackage: String, libPath: String): Boolean

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AirwareLoaderTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LoaderScreen(onInject = { pkg, libPath -> nativeInject(pkg, libPath) })
                }
            }
        }
    }
}

@Composable
fun LoaderScreen(onInject: (String, String) -> Boolean) {
    var status by remember { mutableStateOf(LoaderStatus.IDLE) }
    var hasRoot by remember { mutableStateOf<Boolean?>(null) }
    var gameRunning by remember { mutableStateOf<Boolean?>(null) }
    var selectedBuild by remember { mutableStateOf<String?>(null) }
    val log = rememberLogState()
    val scope = rememberCoroutineScope()

    // Проверка root + состояния игры при старте, и периодический опрос состояния игры.
    LaunchedEffect(Unit) {
        log.info("checking root access...")
        val root = withContext(Dispatchers.IO) { RootShell.hasRoot() }
        hasRoot = root
        if (root) log.success("root access granted") else log.error("root access denied — su not available")

        while (true) {
            val pids = withContext(Dispatchers.IO) { RootShell.pidOf(TARGET_PACKAGE) }
            val running = pids.isNotEmpty()
            if (gameRunning != running) {
                if (running) log.success("Standoff 2 detected, pid=${pids.first()}")
                else log.warn("Standoff 2 is not running")
            }
            gameRunning = running
            delay(2000)
        }
    }

    val canInject = hasRoot == true && gameRunning == true && selectedBuild != null &&
            status != LoaderStatus.CHECKING && status != LoaderStatus.INJECTING

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .padding(top = 48.dp, bottom = 16.dp),
        ) {
            HeaderRow()
            Spacer(modifier = Modifier.height(20.dp))

            RootStatusChip(hasRoot)
            Spacer(modifier = Modifier.height(12.dp))

            TargetCard(gameRunning = gameRunning)
            Spacer(modifier = Modifier.height(12.dp))

            BuildPickerCard(
                selectedBuild = selectedBuild?.substringAfterLast('/'),
                onBuildSelected = { selectedBuild = it },
                onLog = { log.info(it) }
            )
            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    val build = selectedBuild ?: return@Button
                    scope.launch {
                        status = LoaderStatus.CHECKING
                        log.info("re-checking root and process before injection...")
                        delay(300)

                        if (hasRoot != true) {
                            log.error("abort: no root access")
                            status = LoaderStatus.ERROR
                            return@launch
                        }
                        val pids = withContext(Dispatchers.IO) { RootShell.pidOf(TARGET_PACKAGE) }
                        if (pids.isEmpty()) {
                            log.error("abort: Standoff 2 process not found")
                            status = LoaderStatus.ERROR
                            return@launch
                        }

                        status = LoaderStatus.INJECTING
                        log.info("injecting '$build' into pid=${pids.first()}...")
                        val ok = withContext(Dispatchers.IO) { onInject(TARGET_PACKAGE, build) }
                        status = if (ok) {
                            log.success("injection complete")
                            LoaderStatus.SUCCESS
                        } else {
                            log.error("injection failed — see logcat for native errors")
                            LoaderStatus.ERROR
                        }
                    }
                },
                enabled = canInject,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentPink,
                    contentColor = Color(0xFF1A1A1E)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = when (status) {
                        LoaderStatus.CHECKING -> "Checking..."
                        LoaderStatus.INJECTING -> "Injecting..."
                        LoaderStatus.SUCCESS -> "Injected"
                        else -> "Inject"
                    },
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            LogWindow(state = log, modifier = Modifier.weight(1f).fillMaxWidth())

            Spacer(modifier = Modifier.height(12.dp))
            BannerStrip()
        }
    }
}

@Composable
fun HeaderRow() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = "airware", color = Color(0xFFF0F0F5), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.width(10.dp))
        DeveloperBadge()
    }
    Text(text = "Loader", color = Color(0xFF9E9EA8), fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
}

@Composable
fun DeveloperBadge() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(AccentPinkDim, RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Icon(Icons.Filled.Verified, contentDescription = null, tint = AccentPink, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = "Developer", color = AccentPink, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun RootStatusChip(hasRoot: Boolean?) {
    val (label, color) = when (hasRoot) {
        null -> "Checking root..." to Color(0xFF9E9EA8)
        true -> "Root access granted" to Color(0xFF66BB6A)
        false -> "Root access denied" to Color(0xFFEF5350)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth()
    ) {
        Box(modifier = Modifier.size(8.dp).background(color, RoundedCornerShape(4.dp)))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = label, color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun TargetCard(gameRunning: Boolean?) {
    val (label, color) = when (gameRunning) {
        null -> "Checking..." to Color(0xFF9E9EA8)
        true -> "Running" to Color(0xFF66BB6A)
        false -> "Not running" to Color(0xFFEF5350)
    }
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF121216)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(44.dp).background(Color(0xFF1A1A20), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("S2", color = AccentPink, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text("Standoff 2", color = Color(0xFFF0F0F5), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(text = label, color = color, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun BannerStrip() {
    val channels = listOf("t.me/timesplinter", "t.me/weeqz", "t.me/theftfelt")
    Column(modifier = Modifier.fillMaxWidth()) {
        Divider(color = Color(0xFF1A1A20), thickness = 1.dp)
        Spacer(modifier = Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            channels.forEach { handle ->
                Text(text = handle, color = Color(0xFF55555E), fontSize = 11.sp)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}
