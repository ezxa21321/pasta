package com.airware.loader

import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

/**
 * Простой враппер над su-шеллом. Каждый вызов exec() поднимает отдельный `su`
 * процесс, пишет команду в stdin, читает stdout до EOF. Этого достаточно для
 * разовых проверок (root/pidof/ls) — для долгоживущей сессии стоит держать
 * один процесс su открытым, но для лоадера это избыточно.
 */
object RootShell {

    data class Result(val exitCode: Int, val stdout: String, val stderr: String)

    fun exec(command: String, timeoutMs: Long = 4000): Result {
        return try {
            val process = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(false)
                .start()

            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()

            val finished = process.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                return Result(-1, stdout, "timeout")
            }

            Result(process.exitValue(), stdout.trim(), stderr.trim())
        } catch (e: Exception) {
            Result(-1, "", e.message ?: "su exec failed")
        }
    }

    /** Проверка root: пытаемся выполнить `id` через su и смотрим на uid=0. */
    fun hasRoot(): Boolean {
        val result = exec("id")
        return result.exitCode == 0 && result.stdout.contains("uid=0")
    }

    /** Список PID-ов процесса по имени пакета (пусто, если не запущен). */
    fun pidOf(packageName: String): List<Int> {
        val result = exec("pidof $packageName")
        if (result.exitCode != 0 || result.stdout.isBlank()) return emptyList()
        return result.stdout.split(Regex("\\s+")).mapNotNull { it.toIntOrNull() }
    }

    /** Листинг файлов в директории, отфильтрованных по подстроке в имени (без учёта регистра). */
    fun listFiles(dir: String, nameContains: String): List<String> {
        val result = exec("ls -1 $dir 2>/dev/null")
        if (result.exitCode != 0) return emptyList()
        return result.stdout
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.contains(nameContains, ignoreCase = true) }
            .toList()
    }
}
