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

package com.movtery.zalithlauncher.ui.screens.main.control_editor.edit_style

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.nonInteractiveScrollbar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.movtery.layer_controller.layout.RendererStyleBox
import com.movtery.layer_controller.observable.ObservableButtonStyle
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.setting.enums.isLauncherInDarkTheme
import com.movtery.zalithlauncher.ui.components.MarqueeText
import com.movtery.zalithlauncher.ui.components.fadeEdge
import com.movtery.zalithlauncher.ui.components.rememberDialogMaxHeight
import com.movtery.zalithlauncher.ui.screens.main.control_editor.InfoLayoutItem
import com.movtery.zalithlauncher.ui.screens.main.control_editor.InfoLayoutTextItem
import com.movtery.zalithlauncher.ui.theme.cardColor
import com.movtery.zalithlauncher.ui.theme.onCardColor
import com.movtery.zalithlauncher.utils.string.isNotEmptyOrBlank

@Composable
fun StyleListDialog(
    styles: List<ObservableButtonStyle>,
    onEditStyle: (ObservableButtonStyle) -> Unit,
    onCreate: () -> Unit,
    onAddPreset: (ButtonStylePreset) -> Unit,
    onClone: (ObservableButtonStyle) -> Unit,
    onDelete: (ObservableButtonStyle) -> Unit,
    onClose: () -> Unit
) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnClickOutside = false,
            dismissOnBackPress = false,
            usePlatformDefaultWidth = false
        )
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .heightIn(max = rememberDialogMaxHeight())
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .padding(all = 3.dp)
                    .heightIn(max = (maxHeight - 6.dp).coerceAtMost(rememberDialogMaxHeight()))
                    .wrapContentHeight(),
                shadowElevation = 3.dp,
                color = cardColor(false),
                contentColor = onCardColor(),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Column(
                    modifier = Modifier.padding(all = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    MarqueeText(
                        text = stringResource(R.string.control_editor_edit_style_config),
                        style = MaterialTheme.typography.titleMedium
                    )

                    val itemModifier = Modifier.padding(horizontal = 2.dp)
                    var showPresets by remember { mutableStateOf(false) }

                    if (styles.isNotEmpty()) {
                        val scrollState = rememberLazyListState()
                        LazyColumn(
                            modifier = Modifier
                                .fadeEdge(state = scrollState)
                                .weight(1f, fill = false)
                                .fillMaxWidth()
                                .nonInteractiveScrollbar(
                                    state = scrollState.scrollIndicatorState!!,
                                    orientation = Orientation.Vertical,
                                )
                                .animateContentSize(),
                            state = scrollState,
                            contentPadding = PaddingValues(vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(styles) { style ->
                                StyleItem(
                                    modifier = itemModifier,
                                    style = style,
                                    onClick = { onEditStyle(style) },
                                    onClone = { onClone(style) },
                                    onDelete = { onDelete(style) }
                                )
                            }
                        }
                    } else {
                        InfoLayoutTextItem(
                            modifier = Modifier.padding(vertical = 12.dp),
                            title = stringResource(R.string.control_editor_edit_style_config_empty),
                            onClick = onCreate
                        )
                    }

                    //Gaya siap pakai: ketuk salah satu untuk menambahkannya ke daftar gaya
                    if (showPresets) {
                        PresetPicker(
                            modifier = Modifier.padding(bottom = 12.dp),
                            onPick = { preset ->
                                onAddPreset(preset)
                                showPresets = false
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        FilledTonalButton(
                            modifier = Modifier.weight(1f, fill = false),
                            onClick = { showPresets = !showPresets }
                        ) {
                            MarqueeText(text = stringResource(R.string.control_editor_style_presets))
                        }
                        Spacer(Modifier.width(16.dp))
                        FilledTonalButton(
                            modifier = Modifier.weight(1f, fill = false),
                            onClick = onCreate
                        ) {
                            MarqueeText(text = stringResource(R.string.control_manage_create_new))
                        }
                        Spacer(Modifier.width(16.dp))
                        Button(
                            modifier = Modifier.weight(1f, fill = false),
                            onClick = onClose
                        ) {
                            MarqueeText(text = stringResource(R.string.generic_close))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StyleItem(
    style: ObservableButtonStyle,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onClone: () -> Unit,
    onDelete: () -> Unit
) {
    InfoLayoutItem(
        modifier = modifier,
        onClick = onClick
    ) {
        RendererStyleBox(
            modifier = Modifier.size(50.dp),
            style = style,
            text = "abc",
            isPressed = false,
            isDark = isLauncherInDarkTheme()
        )
        Spacer(modifier = Modifier.width(8.dp))
        MarqueeText(
            modifier = Modifier
                .padding(vertical = 8.dp)
                .weight(1f),
            text = style.name.takeIf { it.isNotEmptyOrBlank() } ?: stringResource(R.string.generic_unspecified),
            style = MaterialTheme.typography.bodyMedium
        )
        IconButton(
            onClick = onClone
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_copy_all_outlined),
                contentDescription = stringResource(R.string.generic_copy)
            )
        }
        IconButton(
            onClick = onDelete
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_delete_outlined),
                contentDescription = stringResource(R.string.generic_delete)
            )
        }
        Icon(
            modifier = Modifier
                .size(28.dp),
            painter = painterResource(R.drawable.ic_arrow_right_rounded),
            contentDescription = null
        )
    }
}

/**
 * Deretan pratinjau gaya siap pakai
 */
@Composable
private fun PresetPicker(
    onPick: (ButtonStylePreset) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isLauncherInDarkTheme()
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MarqueeText(
            text = stringResource(R.string.control_editor_style_presets_hint),
            style = MaterialTheme.typography.labelMedium
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ButtonStylePresets.forEach { preset ->
                val previewStyle = remember(preset) { ObservableButtonStyle(preset.create()) }
                Column(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.medium)
                        .clickable { onPick(preset) }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .width(72.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    RendererStyleBox(
                        modifier = Modifier.size(50.dp),
                        style = previewStyle,
                        text = "abc",
                        isPressed = false,
                        isDark = isDark
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    MarqueeText(
                        modifier = Modifier.fillMaxWidth(),
                        text = preset.name,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
