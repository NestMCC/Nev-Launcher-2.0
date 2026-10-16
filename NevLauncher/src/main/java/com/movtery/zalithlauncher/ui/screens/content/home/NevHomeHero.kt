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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.gif.GifDecoder
import coil3.request.ImageRequest
import com.movtery.zalithlauncher.setting.AllSettings
import com.movtery.zalithlauncher.ui.components.VideoPlayer
import kotlinx.coroutines.launch
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
 *
 * Adaptif: tinggi mengikuti isi (minimum menyesuaikan tinggi layar), logo hanya tampil bila lebar cukup,
 * dan kontrol (Main Sekarang, versi, akun) membungkus ke baris berikutnya di layar sempit.
 */
@OptIn(ExperimentalLayoutApi::class)
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

    val context = androidx.compose.ui.platform.LocalContext.current
    val bannerVersion = NevBannerStore.version.intValue
    val hasMedia = remember(bannerVersion) { NevBannerStore.exists(context) }

    // Tinggi minimum banner menyesuaikan tinggi layar; tinggi sebenarnya mengikuti isi (bisa tumbuh saat kontrol membungkus)
    val shortScreen = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp < 360

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (shortScreen) 112.dp else 152.dp)
            .clip(shape)
            .then(
                if (hasMedia) {
                    // Banner kustom tampil penuh di belakang seluruh layar utama
                    Modifier
                } else {
                    Modifier
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
                }
            )
    ) {
        // Tingkat ukuran berdasarkan lebar banner
        val narrow = maxWidth < 440.dp
        val showLogo = !hasMedia && maxWidth >= 520.dp
        val logoSize = if (maxWidth >= 720.dp && !shortScreen) 84.dp else 64.dp
        val textStart = if (narrow) 14.dp else 22.dp
        // ruang di kanan: logo (bila tampil) atau tombol edit banner
        val textEnd = if (showLogo) logoSize + 32.dp else 52.dp

        if (showLogo) {
            Image(
                painter = painterResource(R.drawable.img_launcher),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp)
                    .size(logoSize)
                    .alpha(0.92f)
            )
        }

        // Tombol ganti / hapus banner kustom
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        var menuOpen by remember { mutableStateOf(false) }
        val picker = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) scope.launch { NevBannerStore.import(context, uri) }
        }
        Box(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
            androidx.compose.material3.IconButton(
                onClick = { menuOpen = true },
                modifier = Modifier.size(34.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color.Black.copy(alpha = 0.4f))
            ) {
                Icon(
                    modifier = Modifier.size(18.dp),
                    painter = painterResource(R.drawable.ic_edit_filled),
                    tint = Color.White,
                    contentDescription = stringResource(R.string.nev_banner_edit)
                )
            }
            androidx.compose.material3.DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(stringResource(R.string.nev_banner_choose)) },
                    onClick = {
                        menuOpen = false
                        picker.launch(arrayOf("image/*", "video/*"))
                    }
                )
                if (hasMedia) {
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(stringResource(R.string.nev_banner_reset)) },
                        onClick = {
                            menuOpen = false
                            scope.launch { NevBannerStore.reset(context) }
                        }
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(
                    start = textStart,
                    end = textEnd,
                    top = if (shortScreen) 10.dp else 14.dp,
                    bottom = if (shortScreen) 10.dp else 14.dp
                ),
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
                style = (if (shortScreen || narrow) MaterialTheme.typography.titleLarge
                else MaterialTheme.typography.headlineSmall).copy(fontWeight = FontWeight.ExtraBold),
                maxLines = 1
            )
            // Deskripsi disembunyikan di layar sangat pendek agar tombol tetap terlihat; di layar sempit boleh 2 baris
            if (!shortScreen) {
                Text(
                    text = "Launcher Minecraft Java untuk pengalaman bermain yang maksimal.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = if (narrow) 2 else 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(if (shortScreen) 2.dp else 6.dp))
            // FlowRow: semua kontrol tetap tampil, otomatis turun ke baris berikutnya bila lebar tidak cukup
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Tombol Main Sekarang = tombol luncurkan (menggantikan tombol Luncurkan di panel kanan)
                ScalingActionButton(
                    modifier = Modifier.align(Alignment.CenterVertically),
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
                        .align(Alignment.CenterVertically)
                        .widthIn(max = if (narrow) 170.dp else 210.dp)
                        .clip(MaterialTheme.shapes.large)
                        .background(Color.Black.copy(alpha = 0.28f))
                ) {
                    versionSelector()
                }

                // Menu akun (Tambahkan Akun / akun aktif) di samping pemilih versi
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .widthIn(max = if (narrow) 140.dp else 170.dp)
                ) {
                    accountChip()
                }
            }
        }
    }
}


/**
 * Nev Launcher: banner kustom (gambar / GIF / video) memenuhi seluruh area layar utama sampai bawah.
 * Tidak menampilkan apa pun jika pengguna belum memilih banner.
 */
@Composable
fun NevBannerMedia(modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val bannerVersion = NevBannerStore.version.intValue
    val hasMedia = remember(bannerVersion) { NevBannerStore.exists(context) }
    if (!hasMedia) return

    val file = remember(bannerVersion) { NevBannerStore.file(context) }
    Box(modifier = modifier) {
        if (AllSettings.bannerIsVideo.state) {
            VideoPlayer(
                videoUri = Uri.fromFile(file),
                modifier = Modifier.matchParentSize(),
                refreshTrigger = bannerVersion,
                volume = 0f
            )
        } else {
            val loader = remember(bannerVersion) {
                ImageLoader.Builder(context).components { add(GifDecoder.Factory()) }.build()
            }
            AsyncImage(
                model = ImageRequest.Builder(context).data(file)
                    .memoryCacheKey("banner-$bannerVersion").diskCacheKey("banner-$bannerVersion").build(),
                imageLoader = loader,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop
            )
        }
        // Lapisan gelap: kiri untuk teks banner, bawah agar kartu tetap terbaca
        Box(
            modifier = Modifier.matchParentSize().background(
                Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.10f)))
            )
        )
        Box(
            modifier = Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    0.0f to Color.Transparent,
                    0.35f to Color.Black.copy(alpha = 0.20f),
                    1.0f to Color.Black.copy(alpha = 0.72f)
                )
            )
        )
    }
}
