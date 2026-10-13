package com.movtery.zalithlauncher.ui.screens.content.server

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.ui.base.BaseScreen
import com.movtery.zalithlauncher.ui.components.rememberDialogMaxHeight
import com.movtery.zalithlauncher.ui.screens.NormalNavKey
import com.movtery.zalithlauncher.ui.screens.content.home.server.ServerCardManager
import com.movtery.zalithlauncher.ui.theme.cardColor
import com.movtery.zalithlauncher.ui.theme.onCardColor
import com.movtery.zalithlauncher.utils.network.ServerAddress
import com.movtery.zalithlauncher.viewmodel.ScreenBackStackViewModel

private val OnlineColor = Color(0xFF4CAF50)
private val OfflineColor = Color(0xFFE53935)
private val LoadingColor = Color(0xFF9E9E9E)

/** Data yang ditampilkan di kartu dan halaman detail */
private data class ServerEntry(
    val name: String,
    val address: String,
    val description: String,
    val banner: String?,
    val categories: List<String>,
    val edition: String,
    val version: String?,
    val icon: ByteArray?,
    val saved: Boolean,
    val bedrock: String? = null,
    val account: String? = null,
    val accountNote: String? = null,
    val location: String? = null
)

/**
 * Nev Server Hub: Jelajah server, Server Saya, detail server, dan filter kategori.
 * @param onPlayServer luncurkan game langsung terhubung ke alamat server
 */
@Composable
fun ServerHubScreen(
    backScreenViewModel: ScreenBackStackViewModel,
    onPlayServer: (address: String) -> Unit
) {
    BaseScreen(
        screenKey = NormalNavKey.ServerHub,
        currentKey = backScreenViewModel.mainScreen.currentKey
    ) { isVisible ->
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            ServerHubContent(onPlayServer = onPlayServer)
        }
    }
}

@Composable
private fun ServerHubContent(
    onPlayServer: (address: String) -> Unit
) {
    val context = LocalContext.current

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var detailAddress by remember { mutableStateOf<String?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<ServerEntry?>(null) }

    val servers by ServerHubManager.servers.collectAsState()
    val pings by ServerHubManager.pings.collectAsState()
    val bedrockPings by ServerHubManager.bedrockPings.collectAsState()
    val loading by ServerHubManager.loading.collectAsState()
    val savedCards by ServerCardManager.cards.collectAsState()

    LaunchedEffect(Unit) {
        ServerHubManager.refreshList()
    }

    val savedAddresses = remember(savedCards) { savedCards.map { it.record.cardId }.toSet() }

    val discoverEntries = remember(servers, savedAddresses, category) {
        servers
            .filter { server ->
                category == null || server.categories.any { it.equals(category, ignoreCase = true) }
            }
            .map { server ->
                ServerEntry(
                    name = server.name,
                    address = server.address,
                    description = server.description,
                    banner = server.banner,
                    categories = server.categories,
                    edition = server.edition,
                    version = server.version,
                    icon = null,
                    saved = server.address in savedAddresses,
                    bedrock = server.bedrockAddress,
                    account = server.account,
                    accountNote = server.accountNote,
                    location = server.location
                )
            }
    }
    val categories = remember(servers) {
        servers.flatMap { it.categories }.distinctBy { it.lowercase() }.sorted()
    }
    val savedEntries = remember(savedCards) {
        savedCards.map { card ->
            ServerEntry(
                name = card.record.name,
                address = card.record.cardId,
                description = "",
                banner = null,
                categories = emptyList(),
                edition = "Java",
                version = null,
                icon = card.record.icon,
                saved = true
            )
        }
    }
    val entries = if (tab == 0) discoverEntries else savedEntries

    //ping semua server yang sedang tampil
    LaunchedEffect(entries.map { it.address to it.bedrock }) {
        entries.forEach { entry ->
            ServerHubManager.ping(entry.address)
            entry.bedrock?.let { ServerHubManager.pingBedrock(it) }
        }
    }

    val detail = detailAddress?.let { address ->
        (discoverEntries + savedEntries).firstOrNull { it.address == address }
            ?: servers.firstOrNull { it.address == address }?.let { server ->
                ServerEntry(
                    server.name, server.address, server.description, server.banner,
                    server.categories, server.edition, server.version, null,
                    server.address in savedAddresses,
                    server.bedrockAddress, server.account, server.accountNote, server.location
                )
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
    ) {
        //Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Nev Server Hub",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Jelajahi server, simpan favorit, lalu main sekali ketuk",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = {
                    ServerHubManager.refreshList()
                    entries.forEach { entry ->
                        ServerHubManager.ping(entry.address, force = true)
                        entry.bedrock?.let { ServerHubManager.pingBedrock(it, force = true) }
                    }
                }
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_refresh),
                    contentDescription = "Segarkan"
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        //Tab: Jelajah | Server Saya
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HubChip(selected = tab == 0, label = "Jelajah", onClick = { tab = 0 })
            HubChip(
                selected = tab == 1,
                label = "Server Saya (${savedCards.size})",
                onClick = { tab = 1 }
            )
        }

        //Kategori (hanya di Jelajah)
        if (tab == 0) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HubChip(selected = category == null, label = "Semua", onClick = { category = null })
                categories.forEach { name ->
                    HubChip(
                        selected = category == name,
                        label = name,
                        onClick = { category = if (category == name) null else name }
                    )
                }
            }
        }

        LazyVerticalGrid(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            columns = GridCells.Adaptive(minSize = 250.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (tab == 1) {
                item(key = "add_server", span = { GridItemSpan(maxLineSpan) }) {
                    AddServerCard(onClick = { showAdd = true })
                }
            }

            if (entries.isEmpty()) {
                item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                    EmptyInfo(
                        text = if (tab == 0) {
                            if (loading) "Memuat daftar server…" else "Belum ada server di kategori ini."
                        } else {
                            "Belum ada server tersimpan. Tambahkan lewat tombol di atas, atau simpan dari Jelajah."
                        }
                    )
                }
            }

            items(entries, key = { "${tab}_${it.address}" }) { entry ->
                ServerCard(
                    entry = entry,
                    ping = pings[entry.address],
                    bedrockPing = entry.bedrock?.let { bedrockPings[it] },
                    onClick = { detailAddress = entry.address }
                )
            }
        }
    }

    //Halaman detail server
    if (detail != null) {
        ServerDetailDialog(
            entry = detail,
            ping = pings[detail.address],
            bedrockPing = detail.bedrock?.let { bedrockPings[it] },
            onDismiss = { detailAddress = null },
            onPlay = {
                detailAddress = null
                onPlayServer(detail.address)
            },
            onSave = if (!detail.saved) {
                {
                    val icon = (pings[detail.address] as? HubPing.Online)?.icon ?: detail.icon
                    val added = ServerCardManager.addCard(detail.name, detail.address, icon)
                    Toast.makeText(
                        context,
                        if (added) "Disimpan ke Server Saya" else "Server sudah ada di Server Saya",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else null,
            onRename = if (detail.saved) {
                { renameTarget = detail }
            } else null,
            onRemove = if (detail.saved && tab == 1) {
                {
                    ServerCardManager.removeCard(detail.address)
                    detailAddress = null
                }
            } else null
        )
    }

    if (showAdd) {
        AddServerDialog(
            onDismiss = { showAdd = false },
            onConfirm = { name, address ->
                val added = ServerCardManager.addCard(name, address)
                Toast.makeText(
                    context,
                    if (added) "Server ditambahkan" else "Server dengan alamat itu sudah ada",
                    Toast.LENGTH_SHORT
                ).show()
                showAdd = false
            }
        )
    }

    renameTarget?.let { target ->
        RenameServerDialog(
            initialName = target.name,
            onDismiss = { renameTarget = null },
            onConfirm = { name ->
                ServerCardManager.setCardName(target.address, name)
                renameTarget = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HubChip(selected: Boolean, label: String, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1) }
    )
}

@Composable
private fun EmptyInfo(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AddServerCard(onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Surface(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = primary.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, primary.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_add),
                contentDescription = null,
                tint = primary
            )
            Text(
                text = "Tambah server (IP dan port)",
                style = MaterialTheme.typography.titleSmall,
                color = primary
            )
        }
    }
}

@Composable
private fun ServerCard(
    entry: ServerEntry,
    ping: HubPing?,
    bedrockPing: HubPing?,
    onClick: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val icon = (ping as? HubPing.Online)?.icon ?: entry.icon

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
            ServerLogo(icon = icon, size = 52.dp)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                StatusLine(ping = ping, label = "Java")
                if (entry.bedrock != null) {
                    StatusLine(ping = bedrockPing, label = "Bedrock")
                }
                val versionText = (ping as? HubPing.Online)?.versionName ?: entry.version
                val meta = listOfNotNull(
                    versionText,
                    entry.categories.take(3).takeIf { it.isNotEmpty() }?.joinToString(" • "),
                    accountShort(entry.account)
                ).joinToString("  |  ")
                if (meta.isNotEmpty()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusLine(ping: HubPing?, label: String? = null) {
    val prefix = if (label != null) "$label  " else ""
    val (color, text) = when (ping) {
        is HubPing.Online -> OnlineColor to "${prefix}Online  ${ping.online}/${ping.max}  •  ${ping.pingMs} ms"
        HubPing.Offline -> OfflineColor to "${prefix}Offline"
        else -> LoadingColor to "${prefix}Memeriksa…"
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ServerLogo(icon: ByteArray?, size: Dp) {
    val primary = MaterialTheme.colorScheme.primary
    val bitmap = remember(icon) {
        icon?.let {
            runCatching { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }.getOrNull()
        }
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(primary.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                modifier = Modifier.fillMaxSize(),
                bitmap = bitmap,
                contentDescription = null,
                filterQuality = FilterQuality.None
            )
        } else {
            Icon(
                modifier = Modifier.size(size / 2),
                painter = painterResource(R.drawable.ic_dns_outlined),
                contentDescription = null,
                tint = primary
            )
        }
    }
}

@Composable
private fun ServerDetailDialog(
    entry: ServerEntry,
    ping: HubPing?,
    bedrockPing: HubPing?,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onSave: (() -> Unit)?,
    onRename: (() -> Unit)?,
    onRemove: (() -> Unit)?
) {
    val context = LocalContext.current
    val primary = MaterialTheme.colorScheme.primary
    val icon = (ping as? HubPing.Online)?.icon ?: entry.icon

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
                    if (entry.banner != null) {
                        AsyncImage(
                            modifier = Modifier.fillMaxSize(),
                            model = entry.banner,
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
                        ServerLogo(icon = icon, size = 56.dp)
                        Text(
                            text = entry.name,
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
                    StatusLine(ping = ping, label = "Java")
                    if (entry.bedrock != null) {
                        StatusLine(ping = bedrockPing, label = "Bedrock")
                    }

                    if (entry.description.isNotBlank()) {
                        Text(text = entry.description, style = MaterialTheme.typography.bodyMedium)
                    }

                    //Alamat server: Java dan Bedrock
                    AddressRow(label = "IP Java", value = entry.address)
                    if (entry.bedrock != null) {
                        val (bedrockHost, bedrockPort) = parseBedrockAddress(entry.bedrock)
                        AddressRow(label = "IP Bedrock", value = bedrockHost)
                        AddressRow(label = "Port Bedrock", value = bedrockPort.toString())
                    }

                    val versionText = (ping as? HubPing.Online)?.versionName ?: entry.version
                    InfoRow("Edisi", entry.edition)
                    val bedrockVersion = (bedrockPing as? HubPing.Online)?.versionName
                    if (versionText != null) InfoRow("Versi Java", versionText)
                    if (bedrockVersion != null) InfoRow("Versi Bedrock", bedrockVersion)
                    if (entry.categories.isNotEmpty()) {
                        InfoRow("Mode", entry.categories.joinToString(", "))
                    }
                    InfoRow("Akun", accountLong(entry.account))
                    if (!entry.accountNote.isNullOrBlank()) {
                        Text(
                            text = entry.accountNote,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!entry.location.isNullOrBlank()) InfoRow("Lokasi", entry.location)

                    Spacer(Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (onSave != null) {
                            OutlinedButton(onClick = onSave) { Text("Simpan") }
                        }
                        if (onRename != null) {
                            OutlinedButton(onClick = onRename) { Text("Ubah nama") }
                        }
                        if (onRemove != null) {
                            OutlinedButton(onClick = onRemove) { Text("Hapus") }
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onDismiss) { Text("Tutup") }
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

private fun accountShort(account: String?): String? = when (account) {
    "both" -> "Premium & Non-premium"
    "premium" -> "Premium"
    "nonpremium" -> "Non-premium"
    else -> null
}

private fun accountLong(account: String?): String = when (account) {
    "both" -> "Premium & Non-premium (cracked)"
    "premium" -> "Premium saja (online-mode)"
    "nonpremium" -> "Non-premium (offline-mode)"
    else -> "Belum diketahui"
}

/** Baris alamat dengan tombol salin */
@Composable
private fun AddressRow(label: String, value: String) {
    val context = LocalContext.current
    val primary = MaterialTheme.colorScheme.primary
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = primary.copy(alpha = 0.12f)
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                modifier = Modifier.width(96.dp),
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                modifier = Modifier.weight(1f),
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            IconButton(
                onClick = {
                    context.getSystemService(ClipboardManager::class.java)
                        ?.setPrimaryClip(ClipData.newPlainText(label, value))
                    Toast.makeText(context, "$label disalin", Toast.LENGTH_SHORT).show()
                }
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_content_copy_filled),
                    contentDescription = "Salin $label",
                    tint = primary
                )
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            modifier = Modifier.width(96.dp),
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun AddServerDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, address: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    val addressValid = remember(address) {
        address.isNotBlank() && runCatching { ServerAddress.parse(address.trim()) }.isSuccess
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tambah server") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nama server") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Alamat (contoh: play.contoh.net:25565)") },
                    singleLine = true,
                    isError = address.isNotBlank() && !addressValid
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = addressValid,
                onClick = {
                    val ip = address.trim()
                    onConfirm(name.ifBlank { ip }, ip)
                }
            ) { Text("Tambah") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

@Composable
private fun RenameServerDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ubah nama server") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nama server") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onConfirm(name) }
            ) { Text("Simpan") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}
