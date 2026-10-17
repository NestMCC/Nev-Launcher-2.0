/*
 * NEV Launcher
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
 */

package com.movtery.zalithlauncher.ui.screens.content.elements

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.context.copyLocalFile
import com.movtery.zalithlauncher.game.account.Account
import com.movtery.zalithlauncher.game.account.wardrobe.LocalCape
import com.movtery.zalithlauncher.game.account.wardrobe.LocalCapeLibrary
import com.movtery.zalithlauncher.game.account.wardrobe.NoLocalCape
import com.movtery.zalithlauncher.path.PathManager
import com.movtery.zalithlauncher.ui.components.SimpleListDialog
import com.movtery.zalithlauncher.ui.screens.main.control_editor.InfoLayoutTextItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.io.FileUtils
import java.io.File
import java.util.UUID

/**
 * Status cape lokal untuk dialog Wardrobe akun offline.
 * Pilihan baru baru diterapkan saat tombol Konfirmasi ditekan.
 */
@Stable
class LocalCapeUiState(val account: Account) {
    /** Daftar cape yang sudah diinstal */
    var installed by mutableStateOf<List<LocalCape>>(emptyList())

    /** Cape yang saat ini dipakai akun */
    var active by mutableStateOf<LocalCape?>(null)

    /** Cape yang dipilih tapi belum diterapkan */
    var pending by mutableStateOf<LocalCape?>(null)

    /** Apakah pilihan berbeda dari cape yang sedang dipakai */
    var changed by mutableStateOf(false)

    /** Sedang menginstal cape dari file */
    var installing by mutableStateOf(false)

    /** Cape yang sedang dipilih (untuk pratinjau dan label) */
    val selected: LocalCape? get() = if (changed) pending else active

    suspend fun load() {
        installed = withContext(Dispatchers.IO) { LocalCapeLibrary.list() }
        active = LocalCapeLibrary.activeFor(account)
    }

    fun select(cape: LocalCape?) {
        val target = cape?.takeIf { it != NoLocalCape }
        if (target == active) {
            changed = false
            pending = null
        } else {
            changed = true
            pending = target
        }
    }

    fun onDeleted(cape: LocalCape) {
        installed = installed - cape
        if (pending == cape) {
            changed = false
            pending = null
        }
    }
}

@Composable
fun rememberLocalCapeUiState(account: Account): LocalCapeUiState {
    val state = remember(account) { LocalCapeUiState(account) }
    LaunchedEffect(state) {
        if (account.isLocalAccount()) state.load()
    }
    return state
}

/**
 * Tiga menu cape di Wardrobe untuk akun offline:
 * "Cape" (pilih cape), "Instal cape" (impor file PNG), dan "Cape terinstal" (daftar dan hapus)
 */
@Composable
fun LocalCapeSection(
    modifier: Modifier = Modifier,
    state: LocalCapeUiState
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showSelector by remember { mutableStateOf(false) }
    var showManager by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            state.installing = true
            installCapeFromUri(context, uri, state)
            state.installing = false
        }
    }

    val currentName = state.selected?.name ?: stringResource(R.string.cape_name_none)

    //pilih cape dari yang sudah diinstal
    InfoLayoutTextItem(
        modifier = modifier.fillMaxWidth(),
        title = stringResource(R.string.account_local_cape_current, currentName),
        icon = {
            Icon(
                modifier = Modifier.size(22.dp),
                painter = painterResource(R.drawable.ic_styler),
                contentDescription = null
            )
        },
        onClick = { showSelector = true }
    )

    //instal cape baru dari file PNG
    InfoLayoutTextItem(
        modifier = modifier.fillMaxWidth(),
        title = stringResource(R.string.account_local_cape_install),
        icon = {
            if (state.installing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    modifier = Modifier.size(22.dp),
                    painter = painterResource(R.drawable.ic_upload),
                    contentDescription = null
                )
            }
        },
        onClick = { picker.launch(arrayOf("image/png")) },
        enabled = !state.installing
    )

    //daftar cape yang sudah diinstal
    InfoLayoutTextItem(
        modifier = modifier.fillMaxWidth(),
        title = stringResource(R.string.account_local_cape_installed, state.installed.size),
        icon = {
            Icon(
                modifier = Modifier.size(22.dp),
                painter = painterResource(R.drawable.ic_checkroom),
                contentDescription = null
            )
        },
        onClick = { showManager = true }
    )

    if (showSelector) {
        SimpleListDialog(
            title = stringResource(R.string.account_local_cape),
            items = listOf(NoLocalCape) + state.installed,
            onItemSelected = { cape ->
                state.select(cape)
                showSelector = false
            },
            current = state.selected ?: NoLocalCape,
            itemLayout = { cape, isCurrent, onClick ->
                LocalCapeListItem(
                    modifier = Modifier.fillMaxWidth(),
                    selected = isCurrent,
                    cape = cape,
                    onClick = onClick
                )
            },
            onDismissRequest = { selected ->
                if (!selected) showSelector = false
            }
        )
    }

    if (showManager) {
        AlertDialog(
            onDismissRequest = { showManager = false },
            title = { Text(stringResource(R.string.account_local_cape_installed, state.installed.size)) },
            text = {
                if (state.installed.isEmpty()) {
                    Text(stringResource(R.string.account_local_cape_empty))
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(state.installed, key = { it.name }) { cape ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                LocalCapeAvatar(cape = cape, size = 32.dp)
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    modifier = Modifier.weight(1f),
                                    text = cape.name,
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1
                                )
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            LocalCapeLibrary.delete(cape)
                                            state.onDeleted(cape)
                                        }
                                    }
                                ) {
                                    Icon(
                                        modifier = Modifier.size(22.dp),
                                        painter = painterResource(R.drawable.ic_delete_outlined),
                                        contentDescription = stringResource(R.string.generic_delete)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showManager = false }) {
                    Text(stringResource(R.string.generic_close))
                }
            }
        )
    }
}

@Composable
private fun LocalCapeListItem(
    modifier: Modifier = Modifier,
    selected: Boolean,
    cape: LocalCape,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .clip(shape = MaterialTheme.shapes.large)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)

        if (cape != NoLocalCape) {
            LocalCapeAvatar(cape = cape, size = 32.dp)
            Spacer(Modifier.width(12.dp))
        }

        Text(
            text = if (cape == NoLocalCape) stringResource(R.string.cape_name_none) else cape.name,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun LocalCapeAvatar(cape: LocalCape, size: Dp) {
    val density = LocalDensity.current
    val avatar = remember(cape, density) {
        runCatching {
            BitmapFactory.decodeFile(cape.file.absolutePath)?.let { bitmap ->
                getCapeAvatar(bitmap, size, density).asImageBitmap()
            }
        }.getOrNull()
    }
    if (avatar != null) {
        Image(
            modifier = Modifier.size(
                width = with(density) { avatar.width.toDp() },
                height = size
            ),
            bitmap = avatar,
            contentDescription = null
        )
    }
}

private fun Context.displayNameOf(uri: Uri): String? = runCatching {
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
}.getOrNull()

private suspend fun installCapeFromUri(
    context: Context,
    uri: Uri,
    state: LocalCapeUiState
) {
    val cacheFile = File(PathManager.DIR_IMAGE_CACHE, "cape_pick_${UUID.randomUUID()}")
    try {
        val installed = withContext(Dispatchers.IO) {
            context.copyLocalFile(uri, cacheFile)
            if (!LocalCapeLibrary.validateCapeFile(cacheFile)) {
                null
            } else {
                LocalCapeLibrary.install(cacheFile, context.displayNameOf(uri) ?: "cape")
            }
        }
        if (installed == null) {
            Toast.makeText(context, R.string.account_local_cape_invalid, Toast.LENGTH_LONG).show()
        } else {
            state.installed = (state.installed + installed).sortedBy { it.name.lowercase() }
            Toast.makeText(
                context,
                context.getString(R.string.account_local_cape_install_success, installed.name),
                Toast.LENGTH_SHORT
            ).show()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Toast.makeText(context, R.string.account_local_cape_install_failed, Toast.LENGTH_LONG).show()
    } finally {
        FileUtils.deleteQuietly(cacheFile)
    }
}
