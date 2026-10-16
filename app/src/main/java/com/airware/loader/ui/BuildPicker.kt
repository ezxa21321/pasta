package com.airware.loader.ui

import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airware.loader.RootShell

const val BUILD_DIR = "/data/local/tmp"

@Composable
fun FilesTab(
    selectedBuild: String?,
    onBuildSelected: (String) -> Unit,
    onLog: (String) -> Unit,
) {
    var builds by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val cs = MaterialTheme.colorScheme

    fun refresh() {
        loading = true
        val found = RootShell.listFiles(BUILD_DIR, "airware")
        builds = found
        loading = false
        if (found.isEmpty()) onLog("no files containing 'airware' found in $BUILD_DIR")
        else onLog("found ${found.size} build(s) in $BUILD_DIR")
    }

    LaunchedEffect(Unit) { refresh() }

    Box(modifier = Modifier.fillMaxSize()) {
        if (builds.isEmpty()) {
            EmptyFilesState(loading = loading, onImport = { refresh() })
        } else {
            BuildsList(
                builds = builds,
                selected = selectedBuild,
                onSelect = { file ->
                    onBuildSelected("$BUILD_DIR/$file")
                    onLog("selected build: $file")
                },
                onRefresh = { refresh() }
            )
        }
    }
}

@Composable
private fun EmptyFilesState(loading: Boolean, onImport: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(tween(300)) + scaleIn(initialScale = 0.85f, animationSpec = tween(300))
        ) {
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .background(cs.surfaceVariant, RoundedCornerShape(50.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Folder,
                    contentDescription = null,
                    tint = cs.onSurfaceVariant,
                    modifier = Modifier.size(56.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = if (loading) "Сканирование..." else "Пусто",
            color = cs.onBackground,
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Импортируйте конфигурации — они появятся здесь.",
            color = cs.onSurfaceVariant,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 40.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(28.dp))
        Button(
            onClick = onImport,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = cs.primary, contentColor = cs.onPrimary),
            modifier = Modifier.height(48.dp)
        ) {
            Text("Импорт конфигурации", fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun BuildsList(
    builds: List<String>,
    selected: String?,
    onSelect: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Конфигурации", color = cs.onBackground, fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            IconButton(onClick = onRefresh) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = cs.onSurfaceVariant)
            }
        }
        builds.forEach { file ->
            val isSelected = selected?.substringAfterLast('/') == file
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) cs.primary.copy(alpha = 0.16f) else cs.surface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .clickableRow { onSelect(file) }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Description, contentDescription = null, tint = cs.primary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(file, color = cs.onSurface, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun Modifier.clickableRow(onClick: () -> Unit): Modifier {
    return this.then(
        Modifier.background(androidx.compose.ui.graphics.Color.Transparent)
            .then(
                androidx.compose.foundation.clickable(
                    indication = null,
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    onClick = onClick
                )
            )
    )
}
