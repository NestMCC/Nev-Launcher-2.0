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

package com.movtery.zalithlauncher.ui.screens.content.elements

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * Nev Launcher: barisan tab kategori (Unduh, Pengaturan) yang adaptif terhadap lebar layar.
 *
 * Semua tab selalu terlihat: bila tidak muat dalam satu baris, tab turun ke baris berikutnya
 * (tidak lagi terpotong di tepi layar). Pada lebar sempit ukuran tab dipadatkan.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NevCategoryTabs(
    items: List<CategoryItem>,
    isSelected: (CategoryItem) -> Boolean,
    onClick: (CategoryItem) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val compact = maxWidth < 720.dp
        val horizontalPadding = if (compact) 10.dp else 14.dp
        val verticalPadding = if (compact) 5.dp else 8.dp
        val gap = if (compact) 6.dp else 8.dp
        val iconSize = if (compact) 20.dp else 24.dp
        val textStyle = if (compact) MaterialTheme.typography.labelMedium
        else MaterialTheme.typography.labelLarge

        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalArrangement = Arrangement.spacedBy(gap)
        ) {
            items.forEach { item ->
                val selected = isSelected(item)
                val scheme = MaterialTheme.colorScheme
                val bg = if (selected) scheme.primary else scheme.surfaceBright
                val fg = if (selected) scheme.onPrimary else scheme.onSurface

                Row(
                    modifier = Modifier
                        // Pembatas kelompok diganti jarak ekstra agar tetap rapi saat tab membungkus
                        .padding(start = if (item.division) 6.dp else 0.dp)
                        .clip(RoundedCornerShape(50))
                        .background(bg)
                        .clickable { onClick(item) }
                        .padding(horizontal = horizontalPadding, vertical = verticalPadding),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CompositionLocalProvider(LocalContentColor provides fg) {
                        // Ikon bawaan berukuran 24dp; Box membatasinya saat mode padat
                        Box(modifier = Modifier.size(iconSize)) {
                            item.icon()
                        }
                        Text(
                            text = stringResource(item.textRes),
                            maxLines = 1,
                            color = fg,
                            style = textStyle
                        )
                    }
                }
            }
        }
    }
}
