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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.setting.enums.FpsDisplayMode
import com.movtery.zalithlauncher.ui.components.FloatingBall
import com.movtery.zalithlauncher.ui.screens.content.elements.MemoryPreview
import kotlin.math.roundToInt

private val FpsGood = Color(0xFF4CAF50)
private val FpsMedium = Color(0xFFFFC107)
private val FpsBad = Color(0xFFF44336)

private val AxisX = Color(0xFFFF7B7B)
private val AxisY = Color(0xFF7BD88F)
private val AxisZ = Color(0xFF6FA8FF)
private val CompassColor = Color(0xFFFFC857)

/**
 * Overlay game: menampilkan FPS, koordinat, arah mata angin, dan memori.
 * Seluruh overlay bisa diketuk untuk membuka menu dan digeser ke mana saja.
 *
 * @param scale skala ukuran overlay (1f = normal)
 * @param playerPose posisi dan arah pemain, `null` jika belum tersedia
 */
@Composable
fun DraggableGameBall(
    position: Offset,
    onPositionChanged: (Offset) -> Unit,
    onSavePos: () -> Unit,
    gameFps: Int?,
    fpsDisplayMode: FpsDisplayMode,
    fpsHistory: List<Int>,
    fpsMax: Int,
    fpsMin: Int,
    showMemory: Boolean,
    opened: Boolean,
    scale: Float = 1f,
    showCoordinates: Boolean = false,
    showCompass: Boolean = false,
    playerPose: PlayerPose? = null,
    alpha: Float = 1f,
    onClick: () -> Unit = {}
) {
    val density = LocalDensity.current
    //skala diterapkan lewat Density, sehingga ukuran dp dan sp ikut berubah secara merata
    val scaledDensity = remember(density, scale) {
        Density(density = density.density * scale, fontScale = density.fontScale)
    }

    FloatingBall(
        modifier = Modifier.focusProperties {
            canFocus = false
        },
        position = position,
        onPositionChanged = onPositionChanged,
        onSavePos = onSavePos,
        onClick = onClick,
        alpha = alpha,
        color = Color.Black.copy(alpha = 0.45f),
        shape = RoundedCornerShape(14.dp)
    ) {
        CompositionLocalProvider(LocalDensity provides scaledDensity) {
            GameBallContent(
                gameFps = gameFps,
                fpsDisplayMode = fpsDisplayMode,
                fpsHistory = fpsHistory,
                fpsMax = fpsMax,
                fpsMin = fpsMin,
                showMemory = showMemory,
                showCoordinates = showCoordinates,
                showCompass = showCompass,
                playerPose = playerPose,
                opened = opened,
            )
        }
    }
}

@Composable
private fun GameBallContent(
    gameFps: Int?,
    fpsDisplayMode: FpsDisplayMode,
    fpsHistory: List<Int>,
    fpsMax: Int,
    fpsMin: Int,
    showMemory: Boolean,
    showCoordinates: Boolean,
    showCompass: Boolean,
    playerPose: PlayerPose?,
    opened: Boolean,
) {
    val showFps = gameFps != null
    val showInfoRow = showFps || showCoordinates || showCompass
    val hasContent = showInfoRow || showMemory

    Column(
        modifier = Modifier
            .wrapContentSize()
            .animateContentSize()
            .width(IntrinsicSize.Max)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        //tidak ada informasi apa pun yang ditampilkan: sisakan ikon kecil
        //supaya overlay tetap bisa terlihat dan diketuk untuk membuka menu
        if (!hasContent) {
            Box(
                modifier = Modifier.size(28.dp),
                contentAlignment = Alignment.Center
            ) {
                Crossfade(opened) { state ->
                    Icon(
                        modifier = Modifier.size(24.dp),
                        painter = painterResource(
                            if (state) R.drawable.ic_menu_open else R.drawable.ic_menu
                        ),
                        contentDescription = null
                    )
                }
            }
        }

        //baris informasi: FPS, koordinat, dan arah sejajar dalam satu baris
        CustomAnimatedVisibility(
            visible = showInfoRow
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (showFps) {
                    FpsReadout(
                        fps = gameFps ?: 0,
                        mode = fpsDisplayMode,
                        history = fpsHistory,
                        fpsMax = fpsMax,
                        fpsMin = fpsMin
                    )
                }
                if (showCoordinates) {
                    CoordinatesReadout(playerPose)
                }
                if (showCompass) {
                    CompassReadout(playerPose)
                }
            }
        }

        CustomAnimatedVisibility(
            visible = showInfoRow && showMemory
        ) {
            Spacer(Modifier.height(6.dp))
        }

        //内存显示
        CustomAnimatedVisibility(
            visible = showMemory
        ) {
            MemoryPreview(
                modifier = Modifier
                    .defaultMinSize(minWidth = 168.dp)
                    .fillMaxWidth(),
                mainColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                textStyle = MaterialTheme.typography.labelSmall,
                usedText = { usedMemory, totalMemory ->
                    "${usedMemory.toInt()}MB/${totalMemory.toInt()}MB"
                }
            )
        }
    }
}

/** Warna indikator FPS: hijau lancar, kuning sedang, merah berat */
private fun fpsColor(fps: Int): Color = when {
    fps >= 50 -> FpsGood
    fps >= 30 -> FpsMedium
    else -> FpsBad
}

@Composable
private fun FpsReadout(
    fps: Int,
    mode: FpsDisplayMode,
    history: List<Int>,
    fpsMax: Int,
    fpsMin: Int,
) {
    if (mode == FpsDisplayMode.CHART) {
        //帧率图表
        FpsChart(
            history = history,
            fpsMax = fpsMax,
            fpsMin = fpsMin
        )
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(fpsColor(fps), CircleShape)
            )
            Text(
                text = "FPS",
                style = MaterialTheme.typography.labelSmall,
                color = LocalContentColor.current.copy(alpha = 0.7f)
            )
            Text(
                text = fps.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun CoordinatesReadout(pose: PlayerPose?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AxisValue(label = "X", value = pose?.x?.toBlockText(), color = AxisX)
        AxisValue(label = "Y", value = pose?.y?.toBlockText(), color = AxisY)
        AxisValue(label = "Z", value = pose?.z?.toBlockText(), color = AxisZ)
    }
}

@Composable
private fun AxisValue(
    label: String,
    value: String?,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = value ?: "—",
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun CompassReadout(pose: PlayerPose?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = pose?.let { stringResource(it.compass.nameRes) } ?: "—",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = CompassColor
        )
        if (pose != null) {
            Text(
                text = "${pose.normalizedYaw.roundToInt() % 360}°",
                style = MaterialTheme.typography.labelSmall,
                color = LocalContentColor.current.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun ColumnScope.CustomAnimatedVisibility(
    visible: Boolean,
    content: @Composable (AnimatedVisibilityScope.() -> Unit)
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandIn(expandFrom = Alignment.CenterStart) + fadeIn(),
        exit = shrinkOut(shrinkTowards = Alignment.CenterStart) + fadeOut(),
        content = content
    )
}
