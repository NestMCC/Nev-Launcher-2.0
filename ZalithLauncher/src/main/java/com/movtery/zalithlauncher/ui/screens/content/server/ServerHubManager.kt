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
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * Satu server di halaman Jelajah (Server Discovery).
 * Daftar ini dibaca dari servers.json di repo Nev Launcher, dengan cadangan bawaan jika offline.
 */
@Serializable
data class HubServer(
    val name: String,
    /** Alamat Java (host atau host:port) */
    val address: String,
    /** Alamat Bedrock (host atau host:port, port bawaan 19132); null jika tidak ada */
    val bedrockAddress: String? = null,
    val description: String = "",
    val banner: String? = null,
    val categories: List<String> = emptyList(),
    /** Contoh: "Java", "Bedrock", "Java & Bedrock" */
    val edition: String = "Java",
    val version: String? = null,
    /** "both" (premium & non-premium), "premium", "nonpremium", atau null jika belum diketahui */
    val account: String? = null,
    val accountNote: String? = null,
    val location: String? = null,
    val featured: Boolean = false
)

@Serializable
private data class HubServerList(
    val servers: List<HubServer> = emptyList()
)

/** Port Bedrock bawaan */
const val BEDROCK_DEFAULT_PORT = 19132

/** Pisahkan "host" atau "host:port" Bedrock */
fun parseBedrockAddress(address: String): Pair<String, Int> {
    val trimmed = address.trim()
    val idx = trimmed.lastIndexOf(':')
    if (idx > 0) {
        val port = trimmed.substring(idx + 1).toIntOrNull()
        if (port != null && port in 1..65535) return trimmed.substring(0, idx) to port
    }
    return trimmed to BEDROCK_DEFAULT_PORT
}

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
            name = "AlwiNation",
            address = "alwination.id",
            bedrockAddress = "alwination.id:19132",
            description = "Server Indonesia dengan banyak mode: Vanilla, Tycoon, Skyblock, Arena PvP, dan lainnya.",
            categories = listOf("Survival", "Vanilla", "Tycoon", "SkyBlock", "PvP"),
            edition = "Java & Bedrock",
            location = "Indonesia",
            featured = true
        ),
        HubServer(
            name = "Minesive",
            address = "minesive.com",
            bedrockAddress = "be.minesive.com:19132",
            description = "Server fokus ekonomi, PvP, dan survival, juga untuk latihan dan minigame.",
            categories = listOf("Economy", "PvP", "Lifesteal", "Survival"),
            edition = "Java & Bedrock",
            location = "Singapura"
        ),
        HubServer(
            name = "RelxMC",
            address = "play.relxmc.com",
            bedrockAddress = "play.relxmc.com:19132",
            description = "Server crossplay Java dan Bedrock dari Indonesia.",
            categories = listOf("Survival", "Economy", "PvP", "Ranks", "Crossplay"),
            edition = "Java & Bedrock",
            location = "Indonesia"
        ),
        HubServer(
            name = "KaizenMC",
            address = "play.kaizenmc.id",
            bedrockAddress = "play.kaizenmc.id:19132",
            description = "Server Indonesia sejak 2022 dengan fokus gameplay unik, anti-cheat, dan crossplay Java-Bedrock.",
            categories = listOf("Survival", "SkyBlock", "Tycoon", "Creative", "Crossplay"),
            edition = "Java & Bedrock",
            version = "1.21.6+",
            account = "both",
            accountNote = "Premium masuk otomatis. Non-premium daftar dengan /register lalu /login. Akun alt tidak diizinkan.",
            location = "Indonesia"
        )
    )

    private val _servers = MutableStateFlow(loadCached() ?: bundled)

    /** Daftar server untuk halaman Jelajah */
    val servers: StateFlow<List<HubServer>> = _servers.asStateFlow()

    private val _pings = MutableStateFlow<Map<String, HubPing>>(emptyMap())

    /** Status ping, kunci = alamat server */
    val pings: StateFlow<Map<String, HubPing>> = _pings.asStateFlow()

    private val _bedrockPings = MutableStateFlow<Map<String, HubPing>>(emptyMap())

    /** Status ping Bedrock, kunci = alamat Bedrock sesuai data server */
    val bedrockPings: StateFlow<Map<String, HubPing>> = _bedrockPings.asStateFlow()

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

    /**
     * Ping Bedrock (RakNet Unconnected Ping lewat UDP).
     * @param address host atau host:port
     */
    fun pingBedrock(address: String, force: Boolean = false) {
        val key = "bedrock:$address"
        val now = System.currentTimeMillis()
        val last = lastPingAt[key]
        if (!force && last != null && now - last < PING_COOLDOWN_MS) return
        lastPingAt[key] = now

        _bedrockPings.update { map ->
            if (map[address] is HubPing.Online) map else map + (address to HubPing.Loading)
        }

        scope.launch {
            pingPermits.withPermit {
                val result = runCatching {
                    val (host, port) = parseBedrockAddress(address)
                    pingBedrockRaw(host, port)
                }.onFailure {
                    Logger.warning(TAG, "Unable to ping bedrock server: $address", it)
                }.getOrNull()

                _bedrockPings.update { it + (address to (result ?: HubPing.Offline)) }
            }
        }
    }

    private suspend fun pingBedrockRaw(host: String, port: Int, timeoutMs: Int = 5_000): HubPing.Online? =
        withContext(Dispatchers.IO) {
            DatagramSocket().use { socket ->
                socket.soTimeout = timeoutMs
                val magic = byteArrayOf(
                    0x00, 0xff.toByte(), 0xff.toByte(), 0x00,
                    0xfe.toByte(), 0xfe.toByte(), 0xfe.toByte(), 0xfe.toByte(),
                    0xfd.toByte(), 0xfd.toByte(), 0xfd.toByte(), 0xfd.toByte(),
                    0x12, 0x34, 0x56, 0x78
                )
                val start = System.currentTimeMillis()
                val request = ByteBuffer.allocate(1 + 8 + 16 + 8)
                    .put(0x01.toByte())
                    .putLong(start)
                    .put(magic)
                    .putLong(Random.nextLong())
                    .array()

                val target = InetAddress.getByName(host)
                socket.send(DatagramPacket(request, request.size, target, port))

                val buffer = ByteArray(2048)
                val packet = DatagramPacket(buffer, buffer.size)
                socket.receive(packet)
                val ping = System.currentTimeMillis() - start

                val data = ByteBuffer.wrap(packet.data, 0, packet.length)
                //0x1C = Unconnected Pong
                if (data.get().toInt() != 0x1C) return@use null
                data.position(1 + 8 + 8 + 16)
                val length = data.short.toInt() and 0xFFFF
                val bytes = ByteArray(minOf(length, data.remaining()))
                data.get(bytes)

                //MCPE;MOTD;protokol;versi;online;maks;...
                val parts = String(bytes, Charsets.UTF_8).split(";")
                if (parts.size < 6) return@use null
                HubPing.Online(
                    online = parts[4].toIntOrNull() ?: 0,
                    max = parts[5].toIntOrNull() ?: 0,
                    pingMs = ping,
                    versionName = parts[3],
                    icon = null
                )
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
