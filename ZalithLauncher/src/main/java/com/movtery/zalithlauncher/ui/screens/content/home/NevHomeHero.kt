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

package com.movtery.zalithlauncher.ui.screens.content.home

import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.movtery.zalithlauncher.BuildKeys
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.ui.components.ScalingActionButton
import com.movtery.zalithlauncher.ui.components.nevCardBorder

/**
 * Nev Launcher: banner sambutan di layar utama (merah-gelap, logo N, tombol Main Sekarang)
 */
@Composable
fun NevHeroBanner(
    onPlay: () -> Unit,
    versionSelector: @Composable () -> Unit,
    accountChip: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val gold = MaterialTheme.colorScheme.secondary
    val shape = MaterialTheme.shapes.extraLarge
    val base = Brush.horizontalGradient(
        colors = listOf(Color(0xFF14050A), Color(0xFF3A0A10), Color(0xFF7A0C14))
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(152.dp)
            .clip(shape)
            .background(base)
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(primary.copy(alpha = 0.45f), Color.Transparent),
                        center = Offset(size.width * 0.85f, size.height * 0.5f),
                        radius = size.height * 1.6f
                    )
                )
            }
            .border(nevCardBorder(), shape)
    ) {
        Image(
            painter = painterResource(R.drawable.img_launcher),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp)
                .size(84.dp)
                .alpha(0.92f)
        )

        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 22.dp, end = 110.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "Selamat Datang di",
                style = MaterialTheme.typography.labelMedium,
                color = primary.copy(alpha = 0.95f)
            )
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = primary)) { append("NEV ") }
                    withStyle(SpanStyle(color = gold)) { append(BuildKeys.LAUNCHER_NAME.removePrefix("Nev ").uppercase()) }
                },
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold)
            )
            Text(
                text = "Launcher Minecraft Java untuk pengalaman bermain yang maksimal.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.85f),
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Tombol Main Sekarang = tombol luncurkan (menggantikan tombol Luncurkan di panel kanan)
                ScalingActionButton(
                    onClick = onPlay,
                    shape = RoundedCornerShape(percent = 50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(primary, MaterialTheme.colorScheme.primaryContainer)
                                )
                            )
                            .padding(horizontal = 16.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            modifier = Modifier.size(18.dp),
                            painter = painterResource(R.drawable.ic_play_arrow_filled),
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Main Sekarang",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                // Pemilih versi (dipindah dari menu Luncurkan)
                Box(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .widthIn(max = 200.dp)
                        .clip(MaterialTheme.shapes.large)
                        .background(Color.Black.copy(alpha = 0.28f))
                ) {
                    versionSelector()
                }

                // Menu akun (Tambahkan Akun / akun aktif) di samping pemilih versi
                Box(modifier = Modifier.widthIn(max = 170.dp)) {
                    accountChip()
                }
            }
        }
    }
}
