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

/**
 * Tingkat keyakinan analisis terhadap sebuah temuan.
 * Urutan enum dipakai untuk mengurutkan hasil: yang paling yakin tampil paling atas.
 */
enum class CrashSeverity {
    /** Penyebab hampir pasti (pesan error sangat spesifik) */
    DEFINITE,

    /** Penyebab kemungkinan besar */
    PROBABLE,

    /** Petunjuk tambahan, belum tentu penyebab utama */
    HINT
}

/**
 * Daftar penyebab crash yang dikenali analyzer.
 * Teks yang ditampilkan ke pengguna dipetakan di lapisan UI (lihat `CrashCauseText`),
 * sehingga engine ini tidak bergantung pada Android.
 */
enum class CrashCause(val severity: CrashSeverity) {
    //Memori
    OUT_OF_MEMORY(CrashSeverity.DEFINITE),
    OUT_OF_MEMORY_METASPACE(CrashSeverity.DEFINITE),
    OUT_OF_MEMORY_NATIVE(CrashSeverity.DEFINITE),
    GPU_OUT_OF_MEMORY(CrashSeverity.PROBABLE),
    SYSTEM_KILLED(CrashSeverity.PROBABLE),

    //Java / JVM
    JAVA_TOO_OLD(CrashSeverity.DEFINITE),
    JAVA_TOO_NEW(CrashSeverity.PROBABLE),
    JVM_INIT_FAILED(CrashSeverity.DEFINITE),
    INVALID_JVM_ARGUMENT(CrashSeverity.DEFINITE),
    NATIVE_CRASH_GRAPHICS(CrashSeverity.PROBABLE),
    NATIVE_CRASH_GENERIC(CrashSeverity.PROBABLE),
    NATIVE_LIBRARY_ERROR(CrashSeverity.PROBABLE),

    //Mod & loader
    MOD_MISSING_DEPENDENCY(CrashSeverity.DEFINITE),
    MOD_VERSION_MISMATCH(CrashSeverity.DEFINITE),
    WRONG_MINECRAFT_VERSION(CrashSeverity.DEFINITE),
    WRONG_LOADER_VERSION(CrashSeverity.DEFINITE),
    MOD_DUPLICATE(CrashSeverity.DEFINITE),
    MOD_CONFLICT(CrashSeverity.DEFINITE),
    INVALID_MOD_FILE(CrashSeverity.PROBABLE),
    MOD_MIXIN_FAILURE(CrashSeverity.PROBABLE),
    MOD_ENTRYPOINT_ERROR(CrashSeverity.PROBABLE),
    MOD_CLASS_MISMATCH(CrashSeverity.PROBABLE),
    MOD_LOADING_ERROR(CrashSeverity.HINT),

    //Grafis
    GRAPHICS_INIT_FAILED(CrashSeverity.PROBABLE),
    SHADER_ERROR(CrashSeverity.PROBABLE),

    //File & penyimpanan
    CORRUPT_FILE(CrashSeverity.PROBABLE),
    MAIN_CLASS_MISSING(CrashSeverity.DEFINITE),
    STORAGE_FULL(CrashSeverity.DEFINITE),
    STORAGE_PERMISSION(CrashSeverity.PROBABLE),
    WORLD_DATA_ERROR(CrashSeverity.PROBABLE)
}

/**
 * Satu temuan hasil analisis.
 *
 * @param args argumen untuk teks deskripsi/saran (mis. versi Java yang dibutuhkan)
 * @param details baris rincian tambahan yang bersifat netral bahasa (nama mod, nama kelas, dll.)
 * @param evidence baris log asli yang menjadi dasar temuan ini
 */
data class CrashFinding(
    val cause: CrashCause,
    val args: List<String> = emptyList(),
    val details: List<String> = emptyList(),
    val evidence: String? = null
)

/**
 * Informasi bagaimana proses game berakhir, bila diketahui.
 *
 * @param code kode keluar, atau nomor sinyal bila [isSignal] bernilai true
 */
data class ExitInfo(
    val code: Int,
    val isSignal: Boolean
)

/**
 * Hasil analisis sebuah log crash.
 *
 * @param findings temuan, sudah diurutkan dari yang paling yakin
 * @param suspectedMods mod yang dicurigai menurut crash report (bagian "Suspected Mods")
 * @param reportDescription deskripsi singkat dari crash report (baris "Description:"), bila ada
 */
data class CrashAnalysisResult(
    val findings: List<CrashFinding>,
    val suspectedMods: List<String> = emptyList(),
    val reportDescription: String? = null
) {
    /** Apakah analyzer menemukan sesuatu yang bisa ditampilkan */
    val hasAnything: Boolean
        get() = findings.isNotEmpty() || suspectedMods.isNotEmpty()
}
