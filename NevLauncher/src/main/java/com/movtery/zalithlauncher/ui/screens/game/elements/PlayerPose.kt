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

package com.movtery.zalithlauncher.ui.screens.game.elements

import com.movtery.zalithlauncher.R
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Posisi dan arah pandang pemain di dalam game.
 *
 * @param yaw sudut yaw ala Minecraft: 0 = selatan (+Z), 90 = barat (-X),
 * 180 = utara (-Z), -90 / 270 = timur (+X)
 */
data class PlayerPose(
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float
) {
    /** Yaw yang sudah dinormalkan ke rentang 0 sampai 360 */
    val normalizedYaw: Float
        get() {
            val v = yaw % 360f
            return if (v < 0f) v + 360f else v
        }

    /** Arah mata angin (8 arah) dari yaw saat ini */
    val compass: Compass
        get() = Compass.entries[((normalizedYaw / 45f).roundToInt()) % Compass.entries.size]
}

/**
 * 8 arah mata angin, urutannya mengikuti yaw Minecraft (mulai dari selatan, searah jarum jam
 * jika dilihat dari atas ke arah barat).
 */
enum class Compass(val nameRes: Int) {
    SOUTH(R.string.game_hud_compass_s),
    SOUTH_WEST(R.string.game_hud_compass_sw),
    WEST(R.string.game_hud_compass_w),
    NORTH_WEST(R.string.game_hud_compass_nw),
    NORTH(R.string.game_hud_compass_n),
    NORTH_EAST(R.string.game_hud_compass_ne),
    EAST(R.string.game_hud_compass_e),
    SOUTH_EAST(R.string.game_hud_compass_se)
}

/** Format koordinat: bilangan bulat (dibulatkan ke bawah, sama seperti blok yang dipijak) */
fun Double.toBlockText(): String = floor(this).toLong().toString()
