package com.movtery.zalithlauncher.game.record

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Nev Launcher: status perekaman layar.
 * Layanan perekam dan VMActivity berjalan di proses yang sama (:game), jadi state ini dipakai bersama.
 */
object ScreenRecordState {
    private val _recording = MutableStateFlow(false)
    val recording: StateFlow<Boolean> = _recording

    internal fun set(value: Boolean) {
        _recording.value = value
    }
}
