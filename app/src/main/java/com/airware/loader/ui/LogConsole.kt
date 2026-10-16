package com.airware.loader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

enum class LogLevel(val color: Color, val tag: String) {
    INFO(Color(0xFF9E9EA8), "INFO"),
    SUCCESS(Color(0xFF66BB6A), "OK"),
    WARN(Color(0xFFFFB74D), "WARN"),
    ERROR(Color(0xFFEF5350), "CRIT")
}

data class LogEntry(val level: LogLevel, val message: String, val timeMs: Long = System.currentTimeMillis())

class LogState {
    val entries = mutableStateListOf<LogEntry>()

    fun push(level: LogLevel, message: String) {
        entries.add(LogEntry(level, message))
        if (entries.size > 300) entries.removeAt(0)
    }

    fun info(msg: String) = push(LogLevel.INFO, msg)
    fun success(msg: String) = push(LogLevel.SUCCESS, msg)
    fun warn(msg: String) = push(LogLevel.WARN, msg)
    fun error(msg: String) = push(LogLevel.ERROR, msg)
}

@Composable
fun rememberLogState(): LogState = remember { LogState() }

@Composable
fun LogWindow(state: LogState, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.entries.size) {
        if (state.entries.isNotEmpty()) {
            scope.launch { listState.animateScrollToItem(state.entries.size - 1) }
        }
    }

    Column(
        modifier = modifier
            .background(Color(0xFF0E0E12), RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Text(
            text = "LOG",
            color = Color(0xFF55555E),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        if (state.entries.isEmpty()) {
            Text(
                text = "// waiting...",
                color = Color(0xFF3A3A42),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        } else {
            LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
                items(state.entries) { entry ->
                    LogLine(entry)
                }
            }
        }
    }
}

@Composable
private fun LogLine(entry: LogEntry) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            text = "[${entry.level.tag}]",
            color = entry.level.color,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = entry.message,
            color = Color(0xFFCACAD0),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}
