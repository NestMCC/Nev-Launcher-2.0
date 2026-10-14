/*
 * Zalith Launcher 2
 * Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package com.movtery.zalithlauncher.crashanalyzer

import java.io.File
import java.io.RandomAccessFile

/**
 * Bahan mentah untuk [CrashAnalyzer].
 *
 * @param logText isi akhir log game
 * @param crashReportText gabungan crash report Minecraft dan/atau hs_err JVM yang terkait, bila ada
 */
data class CrashInput(
    val logText: String,
    val crashReportText: String?
)

/**
 * Membaca log game dan crash report terbaru dari disk.
 * Tidak bergantung pada Android; hanya memakai [File].
 */
object CrashInputLoader {
    /** Hanya bagian akhir log yang dibaca; penyebab crash hampir selalu ada di akhir */
    internal const val MAX_LOG_BYTES = 2L * 1024 * 1024
    internal const val MAX_REPORT_BYTES = 512L * 1024

    /**
     * Crash report hanya dianggap milik crash ini bila dibuat dalam rentang waktu ini
     * sebelum log terakhir ditulis. Mencegah laporan lama dari sesi sebelumnya ikut terbaca.
     */
    internal const val REPORT_WINDOW_MS = 10 * 60 * 1000L

    /**
     * @param logFile log game yang sedang dianalisis
     * @param gameDir direktori kerja game (tempat `crash-reports/` dan `hs_err_pid*.log`), bila diketahui
     */
    fun load(logFile: File, gameDir: File?): CrashInput {
        val logText = readTail(logFile, MAX_LOG_BYTES).orEmpty()

        //log ditulis sampai game berakhir, jadi waktu modifikasinya adalah patokan "kapan crash terjadi"
        val reference = logFile.takeIf { it.isFile }?.lastModified()?.takeIf { it > 0 } ?: System.currentTimeMillis()

        val reports = buildList {
            if (gameDir != null) {
                newestRecent(File(gameDir, "crash-reports").listFiles(), reference) {
                    it.name.startsWith("crash-") && it.name.endsWith(".txt")
                }?.let { add(it) }
                newestRecent(gameDir.listFiles(), reference) {
                    it.name.startsWith("hs_err_pid") && it.name.endsWith(".log")
                }?.let { add(it) }
            }
        }.mapNotNull { readTail(it, MAX_REPORT_BYTES) }

        return CrashInput(
            logText = logText,
            crashReportText = reports.takeIf { it.isNotEmpty() }?.joinToString("\n")
        )
    }

    private fun newestRecent(files: Array<File>?, reference: Long, accept: (File) -> Boolean): File? {
        return files.orEmpty()
            .asSequence()
            .filter { it.isFile && accept(it) }
            .filter { it.lastModified() >= reference - REPORT_WINDOW_MS }
            .maxByOrNull { it.lastModified() }
    }

    /**
     * Membaca paling banyak [maxBytes] byte terakhir dari file.
     * Bila file dipotong di tengah baris, baris yang terpotong itu dibuang.
     * @return null bila file tidak ada atau tidak bisa dibaca
     */
    internal fun readTail(file: File, maxBytes: Long): String? {
        if (!file.isFile) return null
        return runCatching {
            RandomAccessFile(file, "r").use { raf ->
                val length = raf.length()
                val start = (length - maxBytes).coerceAtLeast(0)
                raf.seek(start)
                val buffer = ByteArray((length - start).toInt())
                raf.readFully(buffer)
                val text = String(buffer, Charsets.UTF_8)
                if (start > 0) text.substringAfter('\n', missingDelimiterValue = text) else text
            }
        }.getOrNull()
    }
}
