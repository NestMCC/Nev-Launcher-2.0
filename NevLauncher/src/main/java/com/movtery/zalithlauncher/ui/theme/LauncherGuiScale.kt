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

package com.movtery.zalithlauncher.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.movtery.zalithlauncher.setting.AllSettings
import com.movtery.zalithlauncher.utils.festival.Festival
import com.movtery.zalithlauncher.viewmodel.BackgroundViewModel

/**
 * Ukuran tampilan (GUI) launcher dalam persen.
 *
 * Nilai yang dipakai tampilan ([applied]) sengaja dipisahkan dari nilai slider:
 * ukuran baru baru diterapkan saat slider dilepas, supaya slider tidak "lari"
 * dari jari ketika tampilan membesar atau mengecil saat digeser.
 */
object LauncherGuiScale {
    /** Ukuran yang sedang diterapkan ke tampilan launcher (persen) */
    var applied by mutableIntStateOf(AllSettings.launcherGuiScale.getValue())

    /** Simpan dan terapkan ukuran baru */
    fun commit(value: Int) {
        AllSettings.launcherGuiScale.save(value)
        applied = AllSettings.launcherGuiScale.getValue()
    }
}

/**
 * Menerapkan ukuran GUI launcher (dp dan sp ikut membesar/mengecil secara merata).
 */
@Composable
fun ProvideLauncherGuiScale(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val scale = LauncherGuiScale.applied / 100f
    val scaledDensity = remember(density, scale) {
        Density(density = density.density * scale, fontScale = density.fontScale)
    }
    CompositionLocalProvider(LocalDensity provides scaledDensity, content = content)
}

/**
 * [ZalithLauncherTheme] yang ikut menerapkan ukuran GUI launcher.
 * Dipakai oleh layar utama launcher saja (bukan editor kontrol ataupun layar game).
 */
@Composable
fun ScaledLauncherTheme(
    backgroundViewModel: BackgroundViewModel? = null,
    festivals: List<Festival> = emptyList(),
    content: @Composable () -> Unit
) {
    ProvideLauncherGuiScale {
        ZalithLauncherTheme(
            backgroundViewModel = backgroundViewModel,
            festivals = festivals,
            content = content
        )
    }
}
