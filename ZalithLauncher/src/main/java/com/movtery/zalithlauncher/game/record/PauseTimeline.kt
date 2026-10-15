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

/**
 * 录制时间线：把编码器给出的时间戳换算成文件内的时间戳，并剔除暂停期间的内容
 *
 * 所有时间单位均为微秒，且与采样时间戳处于同一个时钟（单调时钟）。
 * 暂停区间内的样本会被丢弃，暂停之后的样本整体前移，使文件中不留下空白。
 *
 * @param originUs 录制起点（对应文件内的 0）
 */
class PauseTimeline(private val originUs: Long) {
    private val closed = ArrayList<LongArray>()
    private var openStart = -1L

    val isPaused: Boolean
        @Synchronized get() = openStart >= 0

    @Synchronized
    fun pause(nowUs: Long) {
        if (openStart < 0) openStart = nowUs
    }

    @Synchronized
    fun resume(nowUs: Long) {
        if (openStart >= 0) {
            closed.add(longArrayOf(openStart, maxOf(openStart, nowUs)))
            openStart = -1L
        }
    }

    /**
     * @return 文件内的时间戳；样本位于暂停区间内时返回 -1，表示应当丢弃
     */
    @Synchronized
    fun map(ptsUs: Long): Long {
        if (openStart in 0..ptsUs) return -1L
        var offset = originUs
        for (interval in closed) {
            if (ptsUs >= interval[0] && ptsUs < interval[1]) return -1L
            if (ptsUs >= interval[1]) offset += interval[1] - interval[0]
        }
        return (ptsUs - offset).coerceAtLeast(0L)
    }
}
