package com.movtery.zalithlauncher.ui.screens.content.server

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.ui.components.rememberDialogMaxHeight
import com.movtery.zalithlauncher.ui.screens.content.home.server.ServerCardManager
import com.movtery.zalithlauncher.ui.theme.cardColor
import com.movtery.zalithlauncher.ui.theme.onCardColor
import com.movtery.zalithlauncher.utils.network.openLink
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.util.Locale

private val IdLocale: Locale = Locale.forLanguageTag("id-ID")

private val SortOptions = listOf(
    "score" to "Teratas",
    "votes" to "Vote",
    "players" to "Pemain",
    "newest" to "Terbaru"
)

private val EditionOptions = listOf(
    null to "Semua edisi",
    "JAVA" to "Java",
    "CROSS_PLAY" to "Java & Bedrock",
    "BEDROCK" to "Bedrock"
)

private fun formatNumber(value: Int): String = NumberFormat.getIntegerInstance(IdLocale).format(value)

private fun formatPercent(value: Double): String = "%.1f".format(IdLocale, value)

/** Deskripsi dari MSID sering berisi markdown; tampilkan sebagai teks biasa */
private fun plainDescription(text: String): String = text
    .replace(Regex("(?m)^\\s*#+\\s*"), "")
    .replace("**", "")
    .replace("`", "")
    .trim()
    .take(600)

/**
 * Tab "Server Indonesia" di Nev Server Hub.
 * Data dari direktori MinecraftServer.ID lewat API publiknya.
 */
@Composable
internal fun MsidDirectoryTab(
    modifier: Modifier = Modifier,
    savedAddresses: Set<String>,
    onPlayServer: (address: String) -> Unit
) {
    val context = LocalContext.current

    val state by MsidDirectory.state.collectAsState()
    val query by MsidDirectory.query.collectAsState()
    val tags by MsidDirectory.tags.collectAsState()
    val overview by MsidDirectory.overview.collectAsState()
    val details by MsidDirectory.details.collectAsState()

    var searchText by rememberSaveable { mutableStateOf(query.text) }
    var detailSlug by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        MsidDirectory.ensureLoaded()
        MsidDirectory.loadTags()
        MsidDirectory.loadOverview()
    }

    //tunggu pengguna selesai mengetik; API dibatasi 60 permintaan per menit
    LaunchedEffect(searchText) {
        val wanted = searchText.trim().takeIf { it.length >= 2 } ?: ""
        if (wanted == MsidDirectory.query.value.text) return@LaunchedEffect
        delay(600)
        MsidDirectory.setQuery(MsidDirectory.query.value.copy(text = wanted))
    }

    LazyVerticalGrid(
        modifier = modifier,
        columns = GridCells.Adaptive(minSize = 250.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        //Pencarian, urutan, edisi, dan mode
        item(key = "msid_filters", span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = searchText,
                    onValueChange = { searchText = it.take(100) },
                    singleLine = true,
                    placeholder = { Text("Cari nama atau deskripsi server") },
                    leadingIcon = {
                        Icon(painter = painterResource(R.drawable.ic_search), contentDescription = null)
                    },
                    trailingIcon = if (searchText.isNotEmpty()) {
                        {
                            IconButton(onClick = { searchText = "" }) {
                                Text("✕")
                            }
                        }
                    } else null
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SortOptions.forEach { (value, label) ->
                        HubChip(
                            selected = query.sort == value,
                            label = label,
                            onClick = { MsidDirectory.setQuery(query.copy(sort = value)) }
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    EditionOptions.forEach { (value, label) ->
                        HubChip(
                            selected = query.edition == value,
                            label = label,
                            onClick = { MsidDirectory.setQuery(query.copy(edition = value)) }
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HubChip(
                        selected = query.tag == null,
                        label = "Semua mode",
                        onClick = { MsidDirectory.setQuery(query.copy(tag = null)) }
                    )
                    tags.forEach { tag ->
                        HubChip(
                            selected = query.tag.equals(tag, ignoreCase = true),
                            label = tag,
                            onClick = {
                                MsidDirectory.setQuery(
                                    query.copy(tag = if (query.tag.equals(tag, ignoreCase = true)) null else tag)
                                )
                            }
                        )
                    }
                }

                overview?.let {
                    Text(
                        text = "${formatNumber(it.totalServers)} server  •  ${formatNumber(it.onlineServers)} online  •  " +
                                "${formatNumber(it.totalPlayersOnline)} pemain online",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (state.fromCache) {
            item(key = "msid_cache_note", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = "Menampilkan data tersimpan, sedang memperbarui…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        state.error?.let { message ->
            item(key = "msid_error", span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        modifier = Modifier.weight(1f),
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = OfflineColor
                    )
                    TextButton(onClick = { MsidDirectory.reload() }) { Text("Coba lagi") }
                }
            }
        }

        if (state.servers.isEmpty() && state.error == null) {
            item(key = "msid_empty", span = { GridItemSpan(maxLineSpan) }) {
                EmptyInfo(
                    text = if (state.loading) "Memuat daftar server Indonesia…"
                    else "Tidak ada server yang cocok dengan pencarian ini."
                )
            }
        }

        items(state.servers, key = { it.slug }) { server ->
            MsidServerCard(server = server, onClick = { detailSlug = server.slug })
        }

        if (state.hasMore) {
            item(key = "msid_more", span = { GridItemSpan(maxLineSpan) }) {
                if (state.error == null) {
                    //item ini muncul saat pengguna sampai di bawah: muat halaman berikutnya
                    LaunchedEffect(state.page) { MsidDirectory.loadMore() }
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = "Memuat server lainnya…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        OutlinedButton(onClick = { MsidDirectory.loadMore() }) { Text("Muat lebih banyak") }
                    }
                }
            }
        }

        //Atribusi wajib sesuai ketentuan API MSID
        item(key = "msid_attribution", span = { GridItemSpan(maxLineSpan) }) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TextButton(onClick = { (context as? Activity)?.openLink(MsidDirectory.SITE_URL) }) {
                    Text(
                        text = "Data server dari minecraftserver.id",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }

    //Detail server (dilengkapi data /servers/{slug} begitu tiba)
    val selected = detailSlug?.let { slug ->
        details[slug] ?: state.servers.firstOrNull { it.slug == slug }
    }
    if (selected != null) {
        LaunchedEffect(selected.slug) { MsidDirectory.loadDetail(selected.slug) }

        val javaTarget = selected.javaTarget()
        MsidDetailDialog(
            server = selected,
            saved = javaTarget != null && javaTarget in savedAddresses,
            onDismiss = { detailSlug = null },
            onPlay = {
                detailSlug = null
                javaTarget?.let(onPlayServer)
            },
            onSave = if (javaTarget != null && javaTarget !in savedAddresses) {
                {
                    val added = ServerCardManager.addCard(selected.name, javaTarget, null)
                    Toast.makeText(
                        context,
                        if (added) "Disimpan ke Server Saya" else "Server sudah ada di Server Saya",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else null
        )
    }
}

@Composable
private fun MsidServerCard(server: MsidServer, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val statusColor = if (server.isOnline) OnlineColor else OfflineColor
    val statusText = if (server.isOnline) {
        "Online  ${server.currentPlayers}/${server.maxPlayers}  •  ${formatPercent(server.uptimePercent30d)}% uptime"
    } else {
        "Offline"
    }
    val meta = listOfNotNull(
        server.editionLabel(),
        (server.version ?: server.versionFamily)?.takeIf { it.isNotBlank() },
        "Terverifikasi".takeIf { server.isVerified }
    ).joinToString("  |  ")

    Surface(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = cardColor(false),
        contentColor = onCardColor(),
        border = BorderStroke(1.dp, primary.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MsidLogo(url = server.iconUrl, name = server.name, size = 52.dp)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    server.rank?.let {
                        Text(
                            text = "#$it",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = primary
                        )
                    }
                    Text(
                        modifier = Modifier.weight(1f, fill = false),
                        text = server.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (server.tags.isNotEmpty()) {
                    Text(
                        text = server.tags.take(3).joinToString(" • "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (server.monthlyVotes > 0) {
                    Text(
                        text = "${formatNumber(server.monthlyVotes)} vote bulan ini",
                        style = MaterialTheme.typography.labelSmall,
                        color = primary,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun MsidLogo(url: String?, name: String, size: Dp) {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(primary.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                modifier = Modifier.fillMaxSize(),
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = name.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = primary
            )
        }
    }
}

@Composable
private fun MsidDetailDialog(
    server: MsidServer,
    saved: Boolean,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onSave: (() -> Unit)?
) {
    val context = LocalContext.current
    val primary = MaterialTheme.colorScheme.primary
    val pings by ServerHubManager.pings.collectAsState()
    val bedrockPings by ServerHubManager.bedrockPings.collectAsState()

    val javaTarget = server.javaTarget()
    val bedrockTarget = server.bedrockTarget()

    //status langsung dari perangkat, melengkapi data MSID yang diperbarui tiap 5 menit
    LaunchedEffect(javaTarget, bedrockTarget) {
        javaTarget?.let { ServerHubManager.ping(it, force = true) }
        bedrockTarget?.let { ServerHubManager.pingBedrock(it, force = true) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.62f)
                .heightIn(max = rememberDialogMaxHeight()),
            shape = MaterialTheme.shapes.extraLarge,
            color = cardColor(false),
            contentColor = onCardColor(),
            shadowElevation = 3.dp
        ) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                //Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF2A0707), primary.copy(alpha = 0.65f))
                            )
                        )
                ) {
                    if (!server.bannerUrl.isNullOrBlank()) {
                        AsyncImage(
                            modifier = Modifier.fillMaxSize(),
                            model = server.bannerUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop
                        )
                    }
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MsidLogo(url = server.iconUrl, name = server.name, size = 56.dp)
                        Text(
                            text = server.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (javaTarget != null) StatusLine(ping = pings[javaTarget], label = "Java")
                    if (bedrockTarget != null) StatusLine(ping = bedrockPings[bedrockTarget], label = "Bedrock")

                    val description = plainDescription(server.description)
                    if (description.isNotBlank()) {
                        Text(text = description, style = MaterialTheme.typography.bodyMedium)
                    }

                    //Alamat server: Java dan Bedrock
                    if (javaTarget != null) AddressRow(label = "IP Java", value = javaTarget)
                    if (bedrockTarget != null) {
                        val (bedrockHost, bedrockPort) = parseBedrockAddress(bedrockTarget)
                        AddressRow(label = "IP Bedrock", value = bedrockHost)
                        AddressRow(label = "Port Bedrock", value = bedrockPort.toString())
                    }

                    InfoRow("Edisi", server.editionLabel())
                    (server.version ?: server.versionFamily)?.takeIf { it.isNotBlank() }
                        ?.let { InfoRow("Versi", it) }
                    if (server.tags.isNotEmpty()) InfoRow("Mode", server.tags.joinToString(", "))
                    server.rank?.let { InfoRow("Peringkat", "#$it di MSID") }
                    InfoRow("Vote", "${formatNumber(server.monthlyVotes)} bulan ini")
                    InfoRow(
                        "Status MSID",
                        if (server.isOnline) {
                            "Online ${server.currentPlayers}/${server.maxPlayers}  •  " +
                                    "uptime ${formatPercent(server.uptimePercent30d)}% (30 hari)"
                        } else {
                            "Offline  •  uptime ${formatPercent(server.uptimePercent30d)}% (30 hari)"
                        }
                    )
                    server.hostCountry?.takeIf { it.isNotBlank() }?.let { InfoRow("Lokasi host", it) }

                    if (server.isBedrockOnly) {
                        Text(
                            text = "Server khusus Bedrock: tidak bisa dimainkan lewat Nev Launcher (Java), " +
                                    "tetapi alamatnya bisa disalin.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    //Tautan: halaman MSID (vote dan atribusi) serta kontak server
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { (context as? Activity)?.openLink(server.pageUrl()) }
                        ) { Text("Vote di minecraftserver.id") }
                        server.contacts
                            .filter { it.value.startsWith("http", ignoreCase = true) }
                            .forEach { contact ->
                                OutlinedButton(
                                    onClick = { (context as? Activity)?.openLink(contact.value) }
                                ) {
                                    Text(contact.type.lowercase().replaceFirstChar { it.uppercase() }.ifBlank { "Tautan" })
                                }
                            }
                    }

                    Spacer(Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (onSave != null) {
                            OutlinedButton(onClick = onSave) { Text("Simpan") }
                        } else if (saved) {
                            Text(
                                text = "Tersimpan di Server Saya",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onDismiss) { Text("Tutup") }
                        if (javaTarget != null) {
                            Button(onClick = onPlay) {
                                Icon(
                                    modifier = Modifier.size(18.dp),
                                    painter = painterResource(R.drawable.ic_play_arrow_filled),
                                    contentDescription = null
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("MAIN SEKARANG")
                            }
                        }
                    }
                }
            }
        }
    }
}
