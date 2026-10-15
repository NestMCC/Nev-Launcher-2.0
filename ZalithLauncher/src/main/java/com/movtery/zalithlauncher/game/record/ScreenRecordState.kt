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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package com.movtery.zalithlauncher.game.record

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 录屏状态，由 [ScreenRecordService] 维护，游戏内菜单观察
 *
 * 录屏服务与游戏界面同处 `:game` 进程，因此可以直接共享这份内存状态
 */
object ScreenRecordState {
    /**
     * @param recording 是否正在录制（暂停期间仍为 true）
     * @param startedAt 录制开始时间，基于 [android.os.SystemClock.elapsedRealtime]
     * @param paused 是否处于暂停
     * @param pausedAt 本次暂停开始的时间，仅在 [paused] 时有意义
     * @param pausedTotalMs 之前所有暂停累计的时长
     */
    data class State(
        val recording: Boolean = false,
        val startedAt: Long = 0L,
        val paused: Boolean = false,
        val pausedAt: Long = 0L,
        val pausedTotalMs: Long = 0L
    ) {
        /**
         * 已录制的有效时长（不含暂停）
         * @param now 当前的 [android.os.SystemClock.elapsedRealtime]
         */
        fun elapsedMs(now: Long): Long {
            if (!recording) return 0L
            val end = if (paused) pausedAt else now
            return (end - startedAt - pausedTotalMs).coerceAtLeast(0L)
        }
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    val isRecording: Boolean get() = _state.value.recording

    internal fun markStarted(startedAt: Long) {
        _state.value = State(recording = true, startedAt = startedAt)
    }

    internal fun markPaused(now: Long) {
        val s = _state.value
        if (!s.recording || s.paused) return
        _state.value = s.copy(paused = true, pausedAt = now)
    }

    internal fun markResumed(now: Long) {
        val s = _state.value
        if (!s.recording || !s.paused) return
        _state.value = s.copy(paused = false, pausedTotalMs = s.pausedTotalMs + (now - s.pausedAt), pausedAt = 0L)
    }

    internal fun markStopped() {
        _state.value = State()
    }
}
