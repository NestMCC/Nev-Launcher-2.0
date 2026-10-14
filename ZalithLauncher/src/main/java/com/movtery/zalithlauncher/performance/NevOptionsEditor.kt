/*
 * NEV Performance — launcher-side performance profiles.
 * Derived from the NevLauncher/ZalithLauncher codebase; see repository license.
 */
package com.movtery.zalithlauncher.performance

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Mengubah teks `options.txt` tanpa merusak isi lain: urutan baris, gaya akhir baris
 * (LF/CRLF) dan tanda kutip pada nilai yang sudah ada dipertahankan.
 */
object NevOptionsEditor {

    /** Kunci inti yang aman ditambahkan bila belum ada. Kunci lain hanya diubah bila sudah ada. */
    val CORE_KEYS = setOf("renderDistance", "simulationDistance", "maxFps")

    data class Result(
        val text: String,
        /** Kunci yang nilainya berubah. */
        val updated: List<String>,
        /** Kunci yang sudah bernilai sama. */
        val unchanged: List<String>,
        /** Kunci inti yang ditambahkan karena belum ada. */
        val added: List<String>,
        /** Kunci yang tidak ada di file dan tidak ditambahkan (mungkin berbeda pada versi game ini). */
        val skipped: List<String>
    ) {
        val changedAnything get() = updated.isNotEmpty() || added.isNotEmpty()
    }

    /** Nilai mentah (tanpa tanda kutip) per kunci options.txt untuk [settings]. */
    fun toUpdates(settings: NevPerformanceSettings): Map<String, String> {
        val safe = settings.sanitized()
        return linkedMapOf(
            "renderDistance" to safe.renderDistance.toString(),
            "simulationDistance" to safe.simulationDistance.toString(),
            "maxFps" to safe.maxFps.toString(),
            "graphicsMode" to safe.graphics.optionValue,
            "renderClouds" to safe.clouds.optionValue,
            "particles" to safe.particles.optionValue,
            "entityDistanceScaling" to String.format(Locale.US, "%.2f", safe.entityDistancePercent / 100f)
        )
    }

    fun apply(original: String, updates: Map<String, String>, addIfMissing: Set<String> = CORE_KEYS): Result {
        val newline = if (original.contains("\r\n")) "\r\n" else "\n"
        val endsWithNewline = original.endsWith("\n")
        val lines = if (original.isEmpty()) mutableListOf<String>() else original.split(LINE_BREAK).toMutableList()
        if (endsWithNewline && lines.lastOrNull() == "") lines.removeAt(lines.lastIndex)

        val updated = linkedSetOf<String>()
        val unchanged = linkedSetOf<String>()
        for (index in lines.indices) {
            val line = lines[index]
            val separator = line.indexOf(':')
            if (separator <= 0) continue
            val key = line.substring(0, separator)
            val value = updates[key] ?: continue
            val existing = line.substring(separator + 1)
            val quoted = existing.length >= 2 && existing.startsWith('"') && existing.endsWith('"')
            val newValue = if (quoted) "\"$value\"" else value
            if (existing == newValue) {
                if (key !in updated) unchanged += key
            } else {
                lines[index] = "$key:$newValue"
                unchanged -= key
                updated += key
            }
        }

        val added = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        for ((key, value) in updates) {
            if (key in updated || key in unchanged) continue
            if (key in addIfMissing) {
                lines += "$key:$value"
                added += key
            } else {
                skipped += key
            }
        }

        val text = lines.joinToString(newline) + if (endsWithNewline || (original.isEmpty() && lines.isNotEmpty())) newline else ""
        return Result(text, updated.toList(), unchanged.toList(), added, skipped)
    }

    private val LINE_BREAK = Regex("\r?\n")
}

/**
 * Cadangan options.txt buatan Nev. Dunia dan file lain tidak pernah disentuh.
 */
object NevOptionsBackups {
    const val BACKUP_PREFIX = "options.txt.nev-backup-"
    const val PRE_RESTORE_NAME = "options.txt.nev-prerestore"
    const val KEEP_COUNT = 5

    sealed interface RestoreResult {
        data class Restored(val from: File) : RestoreResult
        data object NoBackup : RestoreResult
    }

    /** Daftar cadangan, terbaru lebih dulu. Format nama berlebar tetap sehingga urutan nama = urutan waktu. */
    fun list(options: File): List<File> {
        val dir = options.parentFile ?: return emptyList()
        return dir.listFiles { file -> file.isFile && file.name.startsWith(BACKUP_PREFIX) }
            ?.sortedByDescending { it.name }
            .orEmpty()
    }

    /** Salin [options] ke cadangan baru lalu buang cadangan lama di luar [KEEP_COUNT] terbaru. */
    fun create(options: File, now: Date = Date()): File {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(now)
        val backup = File(options.parentFile, BACKUP_PREFIX + stamp)
        options.copyTo(backup, overwrite = false)
        list(options).drop(KEEP_COUNT).forEach { it.delete() }
        return backup
    }

    /** Kembalikan cadangan terbaru. Isi options.txt saat ini disimpan dulu sebagai [PRE_RESTORE_NAME]. */
    fun restoreLatest(options: File): RestoreResult {
        val latest = list(options).firstOrNull() ?: return RestoreResult.NoBackup
        if (options.isFile) {
            options.copyTo(File(options.parentFile, PRE_RESTORE_NAME), overwrite = true)
        }
        latest.copyTo(options, overwrite = true)
        return RestoreResult.Restored(latest)
    }
}
