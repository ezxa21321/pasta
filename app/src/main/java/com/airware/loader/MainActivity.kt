package com.airware.loader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airware.loader.ui.*
import com.airware.loader.ui.theme.AirwareLoaderTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TARGET_PACKAGE = "com.axlebolt.standoff2"

enum class LoaderStatus { IDLE, CHECKING, INJECTING, SUCCESS, ERROR }
enum class Tab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    HOME("Главная", Icons.Filled.Home),
    LOG("Лог", Icons.Filled.Terminal),
    FILES("Файлы", Icons.Filled.Folder)
}

class MainActivity : ComponentActivity() {

    init {
        System.loadLibrary("airware_injector")
    }

    external fun nativeInject(targetPackage: String, libPath: String): Boolean

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var isDark by remember { mutableStateOf(true) }
            AirwareLoaderTheme(darkTheme = isDark) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    LoaderScreen(
                        isDark = isDark,
                        onToggleTheme = { isDark = !isDark },
                        onInject = { pkg, libPath -> nativeInject(pkg, libPath) }
                    )
                }
            }
        }
    }
}

@Composable
fun LoaderScreen(isDark: Boolean, onToggleTheme: () -> Unit, onInject: (String, String) -> Boolean) {
    val cs = MaterialTheme.colorScheme
    var status by remember { mutableStateOf(LoaderStatus.IDLE) }
    var hasRoot by remember { mutableStateOf<Boolean?>(null) }
    var gameRunning by remember { mutableStateOf<Boolean?>(null) }
    var selectedBuild by remember { mutableStateOf<String?>(null) }
    var currentTab by remember { mutableStateOf(Tab.HOME) }
    val log = rememberLogState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        log.info("checking root access...")
        val root = withContext(Dispatchers.IO) { RootShell.hasRoot() }
        hasRoot = root
        if (root) log.success("root access granted") else log.error("root access denied — su not available")

        while (true) {
            val pids = withContext(Dispatchers.IO) { RootShell.pidOf(TARGET_PACKAGE) }
            val running = pids.isNotEmpty()
            if (gameRunning != running) {
                if (running) log.success("SO2 detected, pid=${pids.first()}")
                else log.warn("SO2 is not running")
            }
            gameRunning = running
            delay(2000)
        }
    }

    val canInject = hasRoot == true && gameRunning == true && selectedBuild != null &&
            status != LoaderStatus.CHECKING && status != LoaderStatus.INJECTING

    fun doStart() {
        val build = selectedBuild ?: return
        scope.launch {
            status = LoaderStatus.CHECKING
            log.info("re-checking root and process before start...")
            delay(300)

            if (hasRoot != true) {
                log.error("abort: no root access")
                status = LoaderStatus.ERROR
                return@launch
            }
            val pids = withContext(Dispatchers.IO) { RootShell.pidOf(TARGET_PACKAGE) }
            if (pids.isEmpty()) {
                log.error("abort: SO2 process not found")
                status = LoaderStatus.ERROR
                return@launch
            }

            status = LoaderStatus.INJECTING
            log.info("starting '$build' on pid=${pids.first()}...")
            val ok = withContext(Dispatchers.IO) { onInject(TARGET_PACKAGE, build) }
            status = if (ok) {
                log.success("start complete")
                LoaderStatus.SUCCESS
            } else {
                log.error("start failed — see logcat for native errors")
                LoaderStatus.ERROR
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopBar(isDark = isDark, onToggleTheme = onToggleTheme)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 8.dp)
            ) {
                when (currentTab) {
                    Tab.HOME -> HomeTab(
                        hasRoot = hasRoot,
                        gameRunning = gameRunning,
                        selectedBuild = selectedBuild?.substringAfterLast('/'),
                        status = status,
                        canInject = canInject,
                        onStart = { doStart() }
                    )
                    Tab.LOG -> LogWindow(state = log, modifier = Modifier.fillMaxSize())
                    Tab.FILES -> BuildPickerCard(
                        selectedBuild = selectedBuild?.substringAfterLast('/'),
                        onBuildSelected = { selectedBuild = it },
                        onLog = { log.info(it) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            BannerStrip()
            Spacer(modifier = Modifier.height(10.dp))
            BottomPillNav(current = currentTab, onSelect = { currentTab = it })
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun TopBar(isDark: Boolean, onToggleTheme: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Главная", color = cs.onBackground, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
        }
        DeveloperBadge()
        Spacer(modifier = Modifier.width(10.dp))
        IconButton(
            onClick = onToggleTheme,
            modifier = Modifier
                .size(40.dp)
                .background(cs.surfaceVariant, CircleShape)
        ) {
            Icon(
                imageVector = if (isDark) Icons.Filled.DarkMode else Icons.Filled.LightMode,
                contentDescription = "Toggle theme",
                tint = cs.onSurfaceVariant
            )
        }
    }
}

@Composable
fun DeveloperBadge() {
    val cs = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(cs.primary.copy(alpha = 0.16f), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Icon(Icons.Filled.Verified, contentDescription = null, tint = cs.primary, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = "Developer", color = cs.primary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun HomeTab(
    hasRoot: Boolean?,
    gameRunning: Boolean?,
    selectedBuild: String?,
    status: LoaderStatus,
    canInject: Boolean,
    onStart: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Column {
        InfoCard(
            icon = Icons.Filled.Security,
            title = "Root доступ",
            subtitle = when (hasRoot) {
                null -> "Проверка..."
                true -> "Получен"
                false -> "Отказано"
            },
            subtitleColor = when (hasRoot) {
                true -> Color(0xFF4CAF50)
                false -> Color(0xFFE53935)
                null -> cs.onSurfaceVariant
            }
        )
        Spacer(modifier = Modifier.height(12.dp))
        InfoCard(
            icon = Icons.Filled.SportsEsports,
            title = "SO2",
            subtitle = when (gameRunning) {
                null -> "Проверка..."
                true -> "Запущена"
                false -> "Не запущена"
            },
            subtitleColor = when (gameRunning) {
                true -> Color(0xFF4CAF50)
                false -> Color(0xFFE53935)
                null -> cs.onSurfaceVariant
            }
        )
        Spacer(modifier = Modifier.height(12.dp))
        InfoCard(
            icon = Icons.Filled.Description,
            title = "Выбранный билд",
            subtitle = selectedBuild ?: "Не выбран",
            subtitleColor = if (selectedBuild != null) cs.primary else cs.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onStart,
            enabled = canInject,
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = cs.primary,
                contentColor = cs.onPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = when (status) {
                    LoaderStatus.CHECKING -> "Checking..."
                    LoaderStatus.INJECTING -> "Starting..."
                    LoaderStatus.SUCCESS -> "Started"
                    LoaderStatus.ERROR -> "Start"
                    LoaderStatus.IDLE -> "Start"
                },
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
fun InfoCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    subtitleColor: Color,
) {
    val cs = MaterialTheme.colorScheme
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(18.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = cs.onSurfaceVariant, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(title, color = cs.onSurface, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                Text(subtitle, color = subtitleColor, fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun BannerStrip() {
    val cs = MaterialTheme.colorScheme
    val channels = listOf("t.me/timesplinter", "t.me/weeqz", "t.me/theftfelt")
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Divider(color = cs.outline, thickness = 1.dp)
        Spacer(modifier = Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            channels.forEach { handle ->
                Text(text = handle, color = cs.onSurfaceVariant, fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun BottomPillNav(current: Tab, onSelect: (Tab) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .background(cs.surfaceVariant, RoundedCornerShape(50)),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Tab.values().forEach { tab ->
            val selected = tab == current
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(6.dp)
                    .background(if (selected) cs.primary else Color.Transparent, RoundedCornerShape(50))
                    .clickableNoRipple { onSelect(tab) }
                    .padding(horizontal = if (selected) 18.dp else 12.dp, vertical = 10.dp)
            ) {
                Icon(
                    tab.icon,
                    contentDescription = tab.label,
                    tint = if (selected) cs.onPrimary else cs.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                if (selected) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(tab.label, color = cs.onPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

// Небольшой helper, чтобы не тянуть индикацию ripple-эффекта поверх кастомного pill-фона.
@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier {
    return this.then(
        Modifier.clickable(
            indication = null,
            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
            onClick = onClick
        )
    )
}
