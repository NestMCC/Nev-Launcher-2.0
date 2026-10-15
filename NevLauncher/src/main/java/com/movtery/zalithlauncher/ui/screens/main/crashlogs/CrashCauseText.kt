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

package com.movtery.zalithlauncher.ui.screens.main.crashlogs

import androidx.annotation.StringRes
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.crashanalyzer.CrashCause
import com.movtery.zalithlauncher.crashanalyzer.CrashSeverity

/**
 * Teks yang ditampilkan untuk sebuah [CrashCause].
 * [fix] boleh memakai `%1$s` untuk argumen pertama temuan.
 */
class CrashCauseText(
    @param:StringRes val title: Int,
    @param:StringRes val fix: Int
)

/**
 * `when` dibuat lengkap (tanpa `else`) agar menambah [CrashCause] baru
 * tanpa menambah teksnya akan gagal dikompilasi.
 */
fun CrashCause.text(): CrashCauseText = when (this) {
    CrashCause.OUT_OF_MEMORY -> CrashCauseText(R.string.crash_cause_out_of_memory_title, R.string.crash_cause_out_of_memory_fix)
    CrashCause.OUT_OF_MEMORY_METASPACE -> CrashCauseText(R.string.crash_cause_out_of_memory_metaspace_title, R.string.crash_cause_out_of_memory_metaspace_fix)
    CrashCause.OUT_OF_MEMORY_NATIVE -> CrashCauseText(R.string.crash_cause_out_of_memory_native_title, R.string.crash_cause_out_of_memory_native_fix)
    CrashCause.GPU_OUT_OF_MEMORY -> CrashCauseText(R.string.crash_cause_gpu_out_of_memory_title, R.string.crash_cause_gpu_out_of_memory_fix)
    CrashCause.SYSTEM_KILLED -> CrashCauseText(R.string.crash_cause_system_killed_title, R.string.crash_cause_system_killed_fix)
    CrashCause.JAVA_TOO_OLD -> CrashCauseText(R.string.crash_cause_java_too_old_title, R.string.crash_cause_java_too_old_fix)
    CrashCause.JAVA_TOO_NEW -> CrashCauseText(R.string.crash_cause_java_too_new_title, R.string.crash_cause_java_too_new_fix)
    CrashCause.JVM_INIT_FAILED -> CrashCauseText(R.string.crash_cause_jvm_init_failed_title, R.string.crash_cause_jvm_init_failed_fix)
    CrashCause.INVALID_JVM_ARGUMENT -> CrashCauseText(R.string.crash_cause_invalid_jvm_argument_title, R.string.crash_cause_invalid_jvm_argument_fix)
    CrashCause.NATIVE_CRASH_GRAPHICS -> CrashCauseText(R.string.crash_cause_native_crash_graphics_title, R.string.crash_cause_native_crash_graphics_fix)
    CrashCause.NATIVE_CRASH_GENERIC -> CrashCauseText(R.string.crash_cause_native_crash_generic_title, R.string.crash_cause_native_crash_generic_fix)
    CrashCause.NATIVE_LIBRARY_ERROR -> CrashCauseText(R.string.crash_cause_native_library_error_title, R.string.crash_cause_native_library_error_fix)
    CrashCause.MOD_MISSING_DEPENDENCY -> CrashCauseText(R.string.crash_cause_mod_missing_dependency_title, R.string.crash_cause_mod_missing_dependency_fix)
    CrashCause.MOD_VERSION_MISMATCH -> CrashCauseText(R.string.crash_cause_mod_version_mismatch_title, R.string.crash_cause_mod_version_mismatch_fix)
    CrashCause.WRONG_MINECRAFT_VERSION -> CrashCauseText(R.string.crash_cause_wrong_minecraft_version_title, R.string.crash_cause_wrong_minecraft_version_fix)
    CrashCause.WRONG_LOADER_VERSION -> CrashCauseText(R.string.crash_cause_wrong_loader_version_title, R.string.crash_cause_wrong_loader_version_fix)
    CrashCause.MOD_DUPLICATE -> CrashCauseText(R.string.crash_cause_mod_duplicate_title, R.string.crash_cause_mod_duplicate_fix)
    CrashCause.MOD_CONFLICT -> CrashCauseText(R.string.crash_cause_mod_conflict_title, R.string.crash_cause_mod_conflict_fix)
    CrashCause.INVALID_MOD_FILE -> CrashCauseText(R.string.crash_cause_invalid_mod_file_title, R.string.crash_cause_invalid_mod_file_fix)
    CrashCause.MOD_MIXIN_FAILURE -> CrashCauseText(R.string.crash_cause_mod_mixin_failure_title, R.string.crash_cause_mod_mixin_failure_fix)
    CrashCause.MOD_ENTRYPOINT_ERROR -> CrashCauseText(R.string.crash_cause_mod_entrypoint_error_title, R.string.crash_cause_mod_entrypoint_error_fix)
    CrashCause.MOD_CLASS_MISMATCH -> CrashCauseText(R.string.crash_cause_mod_class_mismatch_title, R.string.crash_cause_mod_class_mismatch_fix)
    CrashCause.MOD_LOADING_ERROR -> CrashCauseText(R.string.crash_cause_mod_loading_error_title, R.string.crash_cause_mod_loading_error_fix)
    CrashCause.GRAPHICS_INIT_FAILED -> CrashCauseText(R.string.crash_cause_graphics_init_failed_title, R.string.crash_cause_graphics_init_failed_fix)
    CrashCause.SHADER_ERROR -> CrashCauseText(R.string.crash_cause_shader_error_title, R.string.crash_cause_shader_error_fix)
    CrashCause.CORRUPT_FILE -> CrashCauseText(R.string.crash_cause_corrupt_file_title, R.string.crash_cause_corrupt_file_fix)
    CrashCause.MAIN_CLASS_MISSING -> CrashCauseText(R.string.crash_cause_main_class_missing_title, R.string.crash_cause_main_class_missing_fix)
    CrashCause.STORAGE_FULL -> CrashCauseText(R.string.crash_cause_storage_full_title, R.string.crash_cause_storage_full_fix)
    CrashCause.STORAGE_PERMISSION -> CrashCauseText(R.string.crash_cause_storage_permission_title, R.string.crash_cause_storage_permission_fix)
    CrashCause.WORLD_DATA_ERROR -> CrashCauseText(R.string.crash_cause_world_data_error_title, R.string.crash_cause_world_data_error_fix)
}

@StringRes
fun CrashSeverity.labelRes(): Int = when (this) {
    CrashSeverity.DEFINITE -> R.string.crash_analysis_confidence_definite
    CrashSeverity.PROBABLE -> R.string.crash_analysis_confidence_probable
    CrashSeverity.HINT -> R.string.crash_analysis_confidence_hint
}
