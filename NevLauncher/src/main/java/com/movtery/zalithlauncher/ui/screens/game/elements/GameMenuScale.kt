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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.movtery.zalithlauncher.setting.AllSettings

/**
 * Ukuran panel Menu Game (persen).
 *
 * Ukuran yang dipakai tampilan ([applied]) dipisahkan dari nilai slider: ukuran baru
 * baru diterapkan saat slider dilepas, supaya slider (yang berada di dalam menu itu
 * sendiri) tidak "lari" dari jari ketika menu membesar atau mengecil saat digeser.
 */
object GameMenuScale {
    /** Ukuran yang sedang diterapkan ke Menu Game (persen) */
    var applied by mutableIntStateOf(AllSettings.gameMenuScale.getValue())

    /** Simpan dan terapkan ukuran baru */
    fun commit(value: Int) {
        AllSettings.gameMenuScale.save(value)
        applied = AllSettings.gameMenuScale.getValue()
    }
}

/**
 * Menerapkan ukuran Menu Game: dp dan sp di dalam [content] ikut mengecil/membesar
 * secara merata. Ukuran layar (px) tidak berubah, jadi panel yang lebih kecil
 * memuat lebih banyak isi sekaligus.
 */
@Composable
fun ProvideGameMenuScale(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val scale = GameMenuScale.applied / 100f
    val scaledDensity = remember(density, scale) {
        Density(density = density.density * scale, fontScale = density.fontScale)
    }
    CompositionLocalProvider(LocalDensity provides scaledDensity, content = content)
}
