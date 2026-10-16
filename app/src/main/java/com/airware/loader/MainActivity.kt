package com.airware.loader

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
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
import com.airware.loader.ui.theme.AccentPink
import com.airware.loader.ui.theme.AccentPinkDim
import com.airware.loader.ui.theme.AirwareLoaderTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TARGET_PACKAGE = "com.axlebolt.standoff2"

enum class LoaderStatus { IDLE, CHECKING, INJECTING, SUCCESS, ERROR }

class MainActivity : ComponentActivity() {

    init {
        System.loadLibrary("airware_injector")
    }

    // JNI: запускает ptrace-инжект нативной библиотеки чита в процесс игры.
    // Возвращает true при успехе. Реализация — app/src/main/jni/injector.cpp
    external fun nativeInject(targetPackage: String, libPath: String): Boolean

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AirwareLoaderTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LoaderScreen(
                        isTargetInstalled = isPackageInstalled(TARGET_PACKAGE),
                        onInject = { onInjectClick() }
                    )
                }
            }
        }
    }

    private fun isPackageInstalled(pkg: String): Boolean = try {
        packageManager.getPackageInfo(pkg, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    private fun onInjectClick(): Boolean {
        val libPath = "${filesDir.absolutePath}/airware.sh" // переименованная .so, см. notes в конце
        return nativeInject(TARGET_PACKAGE, libPath)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoaderScreen(isTargetInstalled: Boolean, onInject: () -> Boolean) {
    var status by remember { mutableStateOf(LoaderStatus.IDLE) }
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .padding(top = 48.dp, bottom = 16.dp),
        ) {
            HeaderRow()

            Spacer(modifier = Modifier.height(24.dp))

            TargetCard(
                isInstalled = isTargetInstalled,
                status = status,
                onLaunch = {
                    if (status == LoaderStatus.IDLE || status == LoaderStatus.ERROR) {
                        scope.launch {
                            status = LoaderStatus.CHECKING
                            delay(500)
                            status = LoaderStatus.INJECTING
                            delay(400)
                            val ok = onInject()
                            status = if (ok) LoaderStatus.SUCCESS else LoaderStatus.ERROR
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.weight(1f))

            BannerStrip()
        }
    }
}

@Composable
fun HeaderRow() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "airware",
            color = Color(0xFFF0F0F5),
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.width(10.dp))
        DeveloperBadge()
    }
    Text(
        text = "Loader",
        color = Color(0xFF9E9EA8),
        fontSize = 13.sp,
        modifier = Modifier.padding(top = 2.dp)
    )
}

@Composable
fun DeveloperBadge() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(AccentPinkDim, RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Verified,
            contentDescription = null,
            tint = AccentPink,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "Developer",
            color = AccentPink,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun TargetCard(isInstalled: Boolean, status: LoaderStatus, onLaunch: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF121216)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color(0xFF1A1A20), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("S2", color = AccentPink, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("Standoff 2", color = Color(0xFFF0F0F5), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(
                        text = if (isInstalled) "Package found" else "Not installed",
                        color = if (isInstalled) Color(0xFF66BB6A) else Color(0xFFEF5350),
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            StatusRow(status)
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onLaunch,
                enabled = isInstalled && status != LoaderStatus.CHECKING && status != LoaderStatus.INJECTING,
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
        }
    }
}

@Composable
fun StatusRow(status: LoaderStatus) {
    val (label, color) = when (status) {
        LoaderStatus.IDLE -> "Ready" to Color(0xFF9E9EA8)
        LoaderStatus.CHECKING -> "Checking process..." to Color(0xFFFFB74D)
        LoaderStatus.INJECTING -> "Mapping library..." to Color(0xFFFFB74D)
        LoaderStatus.SUCCESS -> "Injection successful" to Color(0xFF66BB6A)
        LoaderStatus.ERROR -> "Injection failed — check root access" to Color(0xFFEF5350)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = label, color = color, fontSize = 12.sp)
    }
}

// Плашки каналов — просто текст, без кликабельных ссылок и без Linkify.
@Composable
fun BannerStrip() {
    val channels = listOf("t.me/timesplinter", "t.me/weeqz", "t.me/theftfelt")
    Column(modifier = Modifier.fillMaxWidth()) {
        Divider(color = Color(0xFF1A1A20), thickness = 1.dp)
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            channels.forEach { handle ->
                Text(
                    text = handle,
                    color = Color(0xFF55555E),
                    fontSize = 11.sp
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}
