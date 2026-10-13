package com.movtery.zalithlauncher.ui.screens.content.server

import com.movtery.zalithlauncher.game.version.multiplayer.pingServer
import com.movtery.zalithlauncher.game.version.multiplayer.resolve
import com.movtery.zalithlauncher.path.GLOBAL_CLIENT
import com.movtery.zalithlauncher.path.GLOBAL_JSON
import com.movtery.zalithlauncher.utils.logging.Logger
import com.movtery.zalithlauncher.utils.network.ServerAddress
import com.tencent.mmkv.MMKV
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import java.util.concurrent.ConcurrentHashMap

/**
 * Satu server di halaman Jelajah (Server Discovery).
 * Daftar ini dibaca dari servers.json di repo Nev Launcher, dengan cadangan bawaan jika offline.
 */
@Serializable
data class HubServer(
    val name: String,
    val address: String,
    val description: String = "",
    val banner: String? = null,
    val categories: List<String> = emptyList(),
    val edition: String = "Java",
    val version: String? = null,
    val bedrockAddress: String? = null,
    val bedrockPort: Int = 19132,
    val accountSupport: String = "Tidak diketahui",
    val featured: Boolean = false
)

@Serializable
private data class HubServerList(
    val servers: List<HubServer> = emptyList()
)

/** Kategori filter di halaman Jelajah; null berarti semua */
val HubCategories: List<String> = listOf(
    "Survival", "SMP", "OneBlock", "SkyBlock", "PvP", "Faction",
    "Creative", "Vanilla", "Parkour", "Minigames", "Indonesia"
)

/** Status ping satu server */
sealed interface HubPing {
    data object Loading : HubPing
    data object Offline : HubPing
    data class Online(
        val online: Int,
        val max: Int,
        val pingMs: Long,
        val versionName: String,
        val icon: ByteArray?
    ) : HubPing
}

object ServerHubManager {
    private const val TAG = "ServerHub"

    /** Sumber daftar server. Edit servers.json di repo untuk menambah/mengubah server. */
    const val REMOTE_URL =
        "https://raw.githubusercontent.com/ridssatrian-lgtm/nev-launcher-2.0/main/servers.json"

    private const val KEY_CACHE = "servers_json"
    private const val PING_COOLDOWN_MS = 20_000L

    private val mmkv: MMKV by lazy { MMKV.mmkvWithID("nev_server_hub") }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pingPermits = Semaphore(4)
    private val lastPingAt = ConcurrentHashMap<String, Long>()

    private val bundled = listOf(
        HubServer(
            name = "Hypixel",
            address = "mc.hypixel.net",
            description = "Salah satu server minigames terbesar di dunia dengan banyak mode permainan.",
            categories = listOf("Minigames", "PvP", "SkyBlock"),
            featured = true
        ),
        HubServer(
            name = "CubeCraft",
            address = "play.cubecraft.net",
            description = "Server minigames dan PvP dengan komunitas besar.",
            categories = listOf("Minigames", "PvP")
        ),
        HubServer(
            name = "The Hive",
            address = "play.hivemc.com",
            description = "Server minigames klasik: BedWars, SkyWars, dan lainnya.",
            categories = listOf("Minigames", "PvP", "Parkour")
        )
    )

    private val _servers = MutableStateFlow(loadCached() ?: bundled)

    /** Daftar server untuk halaman Jelajah */
    val servers: StateFlow<List<HubServer>> = _servers.asStateFlow()

    private val _pings = MutableStateFlow<Map<String, HubPing>>(emptyMap())

    /** Status ping, kunci = alamat server */
    val pings: StateFlow<Map<String, HubPing>> = _pings.asStateFlow()

    private val _loading = MutableStateFlow(false)

    /** Sedang mengambil daftar server dari internet */
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    /** Ambil daftar terbaru dari servers.json; jika gagal, daftar yang ada tetap dipakai */
    fun refreshList() {
        if (_loading.value) return
        _loading.value = true
        scope.launch {
            try {
                val text = runCatching {
                    withTimeout(10_000) { GLOBAL_CLIENT.get(REMOTE_URL).bodyAsText() }
                }.onFailure {
                    Logger.warning(TAG, "Unable to fetch server list", it)
                }.getOrNull()

                val list = text?.let { parse(it) }
                if (list != null && list.isNotEmpty()) {
                    mmkv.encode(KEY_CACHE, text)
                    _servers.value = list
                }
            } finally {
                _loading.value = false
            }
        }
    }

    /**
     * Ping satu server (Server List Ping).
     * @param force abaikan jeda antar-ping (untuk tombol refresh)
     */
    fun ping(address: String, force: Boolean = false) {
        val now = System.currentTimeMillis()
        val last = lastPingAt[address]
        if (!force && last != null && now - last < PING_COOLDOWN_MS) return
        lastPingAt[address] = now

        //data lama tetap tampil selagi ping ulang berjalan
        _pings.update { map ->
            if (map[address] is HubPing.Online) map else map + (address to HubPing.Loading)
        }

        scope.launch {
            pingPermits.withPermit {
                val result = runCatching {
                    pingServer(ServerAddress.parse(address).resolve(), timeoutMillis = 8_000)
                }.onFailure {
                    Logger.warning(TAG, "Unable to ping server: $address", it)
                }.getOrNull()

                val state = if (result == null) {
                    HubPing.Offline
                } else {
                    HubPing.Online(
                        online = result.status.players.online,
                        max = result.status.players.max,
                        pingMs = result.pingMs,
                        versionName = result.status.version.name,
                        icon = result.status.favicon?.icon
                    )
                }
                _pings.update { it + (address to state) }
            }
        }
    }

    private fun loadCached(): List<HubServer>? {
        return runCatching {
            mmkv.decodeString(KEY_CACHE)?.let { parse(it) }?.takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

    private fun parse(text: String): List<HubServer>? {
        return runCatching {
            GLOBAL_JSON.decodeFromString<HubServerList>(text).servers
                .filter { it.name.isNotBlank() && it.address.isNotBlank() }
        }.onFailure {
            Logger.warning(TAG, "Invalid server list json", it)
        }.getOrNull()
    }
}
