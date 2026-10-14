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
     * @param recording 是否正在录制
     * @param startedAt 录制开始时间，基于 [android.os.SystemClock.elapsedRealtime]
     */
    data class State(
        val recording: Boolean = false,
        val startedAt: Long = 0L
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    val isRecording: Boolean get() = _state.value.recording

    internal fun markStarted(startedAt: Long) {
        _state.value = State(recording = true, startedAt = startedAt)
    }

    internal fun markStopped() {
        _state.value = State()
    }
}
