package com.airware.loader.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
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
fun BuildPickerCard(
    selectedBuild: String?,
    onBuildSelected: (String) -> Unit,
    onLog: (String) -> Unit,
) {
    var builds by remember { mutableStateOf<List<String>>(emptyList()) }
    var expanded by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme

    fun refresh() {
        loading = true
        val found = RootShell.listFiles(BUILD_DIR, "airware")
        builds = found
        loading = false
        if (found.isEmpty()) {
            onLog("no files containing 'airware' found in $BUILD_DIR")
        } else {
            onLog("found ${found.size} build(s) in $BUILD_DIR")
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Build", color = cs.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(
                        text = selectedBuild ?: "Not selected",
                        color = if (selectedBuild != null) cs.primary else cs.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
                IconButton(onClick = { refresh() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = cs.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { expanded = !expanded },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = cs.onSurface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (loading) "Scanning $BUILD_DIR..." else "Select build")
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    if (builds.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("No files found", color = cs.onSurfaceVariant) },
                            onClick = { expanded = false }
                        )
                    } else {
                        builds.forEach { fileName ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Filled.Description,
                                            contentDescription = null,
                                            tint = cs.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(fileName, color = cs.onSurface)
                                    }
                                },
                                onClick = {
                                    onBuildSelected("$BUILD_DIR/$fileName")
                                    onLog("selected build: $fileName")
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
