package com.movtery.zalithlauncher.ui.screens.content.server

import com.movtery.zalithlauncher.path.GLOBAL_CLIENT
import com.movtery.zalithlauncher.path.GLOBAL_JSON
import com.movtery.zalithlauncher.utils.logging.Logger
import com.tencent.mmkv.MMKV
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable

/** Port Java bawaan */
private const val JAVA_DEFAULT_PORT = 25565

/**
 * Satu server dari direktori MinecraftServer.ID (MSID).
 * Dipakai untuk daftar (/servers) dan detail (/servers/{slug}); field yang tidak ada di salah satunya bernilai bawaan.
 */
@Serializable
data class MsidServer(
    val slug: String,
    val name: String,
    val description: String = "",
    val address: String,
    val port: Int? = null,
    val bedrockAddress: String? = null,
    val bedrockPort: Int? = null,
    /** JAVA, BEDROCK, atau CROSS_PLAY */
    val edition: String = "JAVA",
    /** ONLINE atau OFFLINE */
    val status: String = "OFFLINE",
    val currentPlayers: Int = 0,
    val maxPlayers: Int = 0,
    val version: String? = null,
    val versionFamily: String? = null,
    val uptimePercent30d: Double = 0.0,
    val iconUrl: String? = null,
    val bannerUrl: String? = null,
    val monthlyVotes: Int = 0,
    val rank: Int? = null,
    val isVerified: Boolean = false,
    val tags: List<String> = emptyList(),
    val hostCountry: String? = null,
    val contacts: List<MsidContact> = emptyList()
) {
    val isOnline: Boolean get() = status.equals("ONLINE", ignoreCase = true)
    val isBedrockOnly: Boolean get() = edition.equals("BEDROCK", ignoreCase = true)
    val isCrossPlay: Boolean get() = edition.equals("CROSS_PLAY", ignoreCase = true)

    /** Alamat Java ("host" atau "host:port"); null jika server hanya Bedrock */
    fun javaTarget(): String? {
        if (isBedrockOnly) return null
        val p = port
        return if (p == null || p == JAVA_DEFAULT_PORT) address else "$address:$p"
    }

    /** Alamat Bedrock "host:port"; null jika server tidak punya Bedrock */
    fun bedrockTarget(): String? {
        if (!isBedrockOnly && !isCrossPlay) return null
        val host = bedrockAddress?.trim()?.takeIf { it.isNotEmpty() } ?: address
        val bedrockPortValue = when {
            bedrockPort != null -> bedrockPort
            isBedrockOnly -> port
            else -> null
        } ?: BEDROCK_DEFAULT_PORT
        return "$host:$bedrockPortValue"
    }

    fun editionLabel(): String = when {
        isBedrockOnly -> "Bedrock"
        isCrossPlay -> "Java & Bedrock"
        else -> "Java"
    }

    /** Halaman server di situs MSID (tempat vote dan atribusi) */
    fun pageUrl(): String = "${MsidDirectory.SITE_URL}/server/$slug"
}

@Serializable
data class MsidContact(
    val type: String = "",
    val value: String = ""
)

@Serializable
data class MsidOverview(
    val totalServers: Int = 0,
    val onlineServers: Int = 0,
    val totalPlayersOnline: Int = 0
)

@Serializable
private data class MsidEnvelope<T>(
    val success: Boolean = false,
    val data: T? = null
)

@Serializable
private data class MsidListData(
    val servers: List<MsidServer> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val totalPages: Int = 0
)

@Serializable
private data class MsidDetailData(
    val server: MsidServer? = null
)

@Serializable
private data class MsidTagsData(
    val tags: List<String> = emptyList()
)

/** Filter dan urutan daftar server Indonesia */
data class MsidQuery(
    val text: String = "",
    /** JAVA, BEDROCK, CROSS_PLAY, atau null untuk semua */
    val edition: String? = null,
    val tag: String? = null,
    /** score, votes, players, atau newest */
    val sort: String = "score"
) {
    val isDefault: Boolean get() = text.isBlank() && edition == null && tag == null && sort == "score"
}

data class MsidListState(
    val servers: List<MsidServer> = emptyList(),
    val page: Int = 0,
    val totalPages: Int = 0,
    val total: Int = 0,
    val loading: Boolean = false,
    val error: String? = null,
    /** Daftar yang tampil berasal dari cache (belum berhasil memuat dari internet) */
    val fromCache: Boolean = false
) {
    val hasMore: Boolean get() = page in 1 until totalPages
}

/**
 * Daftar Server Indonesia untuk Nev Server Hub.
 * Sumber data: API publik https://minecraftserver.id (gratis, read-only, batas 60 permintaan/menit per IP).
 * Atribusi ke minecraftserver.id wajib ditampilkan di UI.
 */
object MsidDirectory {
    private const val TAG = "MsidDirectory"
    private const val API = "https://api.minecraftserver.id/api/v1/public"
    const val SITE_URL = "https://minecraftserver.id/id"

    private const val PAGE_SIZE = 20
    private const val TIMEOUT_MS = 12_000L
    private const val KEY_LIST_CACHE = "msid_list_v1"
    private const val KEY_TAGS_CACHE = "msid_tags_v1"

    /** Dipakai jika /tags belum bisa diambil */
    private val FALLBACK_TAGS = listOf(
        "Survival", "SMP", "SkyBlock", "PvP", "Economy", "Cracked",
        "Roleplay", "Creative", "BedWars", "Minigames", "Factions", "Lifesteal"
    )

    private val mmkv: MMKV by lazy { MMKV.mmkvWithID("nev_server_hub") }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Naik setiap kali daftar dimuat ulang, supaya hasil permintaan lama diabaikan */
    @Volatile
    private var generation = 0

    private val _query = MutableStateFlow(MsidQuery())
    val query: StateFlow<MsidQuery> = _query.asStateFlow()

    private val _state = MutableStateFlow(MsidListState())
    val state: StateFlow<MsidListState> = _state.asStateFlow()

    private val _tags = MutableStateFlow(loadCachedTags() ?: FALLBACK_TAGS)
    val tags: StateFlow<List<String>> = _tags.asStateFlow()

    private val _overview = MutableStateFlow<MsidOverview?>(null)
    val overview: StateFlow<MsidOverview?> = _overview.asStateFlow()

    private val _details = MutableStateFlow<Map<String, MsidServer>>(emptyMap())

    /** Detail lengkap per slug (berisi alamat Bedrock, kontak, dan lain-lain) */
    val details: StateFlow<Map<String, MsidServer>> = _details.asStateFlow()

    /** Panggil saat tab dibuka: tampilkan cache dulu, lalu muat dari internet bila daftar masih kosong */
    fun ensureLoaded() {
        val current = _state.value
        if (current.loading || current.servers.isNotEmpty() && !current.fromCache) return
        if (current.servers.isEmpty()) {
            loadCachedList()?.let { cached ->
                _state.value = MsidListState(
                    servers = cached.servers,
                    page = cached.page,
                    totalPages = cached.totalPages,
                    total = cached.total,
                    fromCache = true
                )
            }
        }
        reload()
    }

    fun setQuery(query: MsidQuery) {
        if (query == _query.value) return
        _query.value = query
        //hasil filter lama tidak berlaku lagi
        _state.update { it.copy(servers = emptyList(), page = 0, totalPages = 0, total = 0, fromCache = false) }
        reload()
    }

    /** Muat ulang dari halaman 1; daftar yang tampil dipertahankan sampai hasil baru datang */
    fun reload() {
        val gen = ++generation
        fetchPage(page = 1, gen = gen)
    }

    /** Muat halaman berikutnya (dipanggil saat pengguna menggulir ke bawah) */
    fun loadMore() {
        val current = _state.value
        if (current.loading || !current.hasMore) return
        fetchPage(page = current.page + 1, gen = generation)
    }

    fun loadTags() {
        scope.launch {
            val tags = runCatching {
                val data = request<MsidTagsData>("$API/tags")
                data.tags.filter { it.isNotBlank() }
            }.onFailure {
                Logger.warning(TAG, "Unable to fetch tags", it)
            }.getOrNull()
            if (!tags.isNullOrEmpty()) {
                _tags.value = tags
                mmkv.encode(KEY_TAGS_CACHE, tags.joinToString("\n"))
            }
        }
    }

    fun loadOverview() {
        scope.launch {
            runCatching { request<MsidOverview>("$API/stats/overview") }
                .onFailure { Logger.warning(TAG, "Unable to fetch overview", it) }
                .getOrNull()
                ?.let { _overview.value = it }
        }
    }

    /** Ambil detail satu server (sekali saja per slug) */
    fun loadDetail(slug: String) {
        if (slug in _details.value) return
        scope.launch {
            runCatching { request<MsidDetailData>("$API/servers/$slug").server }
                .onFailure { Logger.warning(TAG, "Unable to fetch server detail: $slug", it) }
                .getOrNull()
                ?.let { detail -> _details.update { it + (slug to detail) } }
        }
    }

    private fun fetchPage(page: Int, gen: Int) {
        _state.update { it.copy(loading = true, error = null) }
        val query = _query.value

        scope.launch {
            val result = runCatching {
                val text = withTimeout(TIMEOUT_MS) {
                    GLOBAL_CLIENT.get("$API/servers") {
                        parameter("page", page)
                        parameter("limit", PAGE_SIZE)
                        parameter("sort", query.sort)
                        if (query.text.trim().length >= 2) parameter("q", query.text.trim().take(100))
                        query.edition?.let { parameter("edition", it) }
                        query.tag?.let { parameter("tag", it) }
                    }.bodyAsText()
                }
                text to decode<MsidListData>(text)
            }

            if (gen != generation) return@launch //sudah ada permintaan yang lebih baru

            result.onSuccess { (text, data) ->
                if (page == 1 && query.isDefault) mmkv.encode(KEY_LIST_CACHE, text)
                _state.update { current ->
                    val merged = if (page == 1) data.servers else (current.servers + data.servers).distinctBy { it.slug }
                    MsidListState(
                        servers = merged,
                        page = data.page,
                        totalPages = data.totalPages,
                        total = data.total,
                        loading = false,
                        error = null,
                        fromCache = false
                    )
                }
            }.onFailure { error ->
                Logger.warning(TAG, "Unable to fetch server list (page $page)", error)
                _state.update { it.copy(loading = false, error = errorMessage(error)) }
            }
        }
    }

    private fun errorMessage(error: Throwable): String {
        val status = (error as? ClientRequestException)?.response?.status?.value
        return when (status) {
            429 -> "Terlalu banyak permintaan ke MSID. Tunggu sebentar lalu segarkan."
            null -> "Tidak bisa memuat daftar server. Periksa koneksi internet."
            else -> "MSID tidak bisa dihubungi (kode $status). Coba lagi nanti."
        }
    }

    private suspend inline fun <reified T> request(url: String): T {
        val text = withTimeout(TIMEOUT_MS) { GLOBAL_CLIENT.get(url).bodyAsText() }
        return decode(text)
    }

    private inline fun <reified T> decode(text: String): T {
        val envelope = GLOBAL_JSON.decodeFromString<MsidEnvelope<T>>(text)
        return envelope.data?.takeIf { envelope.success } ?: error("MSID response unsuccessful")
    }

    private fun loadCachedList(): MsidListData? = runCatching {
        mmkv.decodeString(KEY_LIST_CACHE)?.let { decode<MsidListData>(it) }?.takeIf { it.servers.isNotEmpty() }
    }.getOrNull()

    private fun loadCachedTags(): List<String>? = runCatching {
        mmkv.decodeString(KEY_TAGS_CACHE)?.split("\n")?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() }
    }.getOrNull()
}
