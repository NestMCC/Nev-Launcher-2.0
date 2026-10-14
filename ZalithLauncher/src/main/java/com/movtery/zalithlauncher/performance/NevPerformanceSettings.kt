/*
 * NEV Performance — launcher-side performance profiles.
 * Derived from the NevLauncher/ZalithLauncher codebase; see repository license.
 */
package com.movtery.zalithlauncher.performance

/** Mode grafis vanilla (`graphicsMode` di options.txt). */
enum class NevGraphicsMode(val optionValue: String, val label: String) {
    FAST("0", "Fast"),
    FANCY("1", "Fancy")
}

/** Mode awan vanilla (`renderClouds` di options.txt). */
enum class NevCloudMode(val optionValue: String, val label: String) {
    OFF("false", "Off"),
    FAST("fast", "Fast"),
    FANCY("true", "Fancy")
}

/** Mode partikel vanilla (`particles` di options.txt). */
enum class NevParticleMode(val optionValue: String, val label: String) {
    ALL("0", "All"),
    DECREASED("1", "Decreased"),
    MINIMAL("2", "Minimal")
}

/**
 * Kumpulan pengaturan yang dikelola NEV Performance.
 * Semua nilai hanyalah titik awal pengujian, bukan janji FPS pada perangkat tertentu.
 */
data class NevPerformanceSettings(
    val renderDistance: Int,
    val simulationDistance: Int,
    val maxFps: Int,
    val graphics: NevGraphicsMode,
    val clouds: NevCloudMode,
    val particles: NevParticleMode,
    val entityDistancePercent: Int
) {
    /** Pastikan nilai berada di rentang yang masuk akal sebelum ditulis ke file game. */
    fun sanitized() = copy(
        renderDistance = renderDistance.coerceIn(RENDER_DISTANCE_RANGE),
        simulationDistance = simulationDistance.coerceIn(SIMULATION_DISTANCE_RANGE),
        maxFps = maxFps.coerceIn(FPS_RANGE),
        entityDistancePercent = entityDistancePercent.coerceIn(ENTITY_DISTANCE_RANGE)
    )

    companion object {
        val RENDER_DISTANCE_RANGE = 4..20
        val SIMULATION_DISTANCE_RANGE = 4..12
        val FPS_RANGE = 10..260
        val ENTITY_DISTANCE_RANGE = 50..100

        val FPS_CHOICES = listOf(30, 45, 60)
        val ENTITY_DISTANCE_CHOICES = listOf(50, 75, 100)
    }
}

/** Profil siap pakai. Angka dasar mengikuti baseline di docs/NEV_PERFORMANCE_ENGINE.md. */
enum class NevPerformancePreset(
    val label: String,
    val description: String,
    val settings: NevPerformanceSettings
) {
    BALANCED(
        "Balanced",
        "Grafis tetap bagus dengan beban yang seimbang.",
        NevPerformanceSettings(
            renderDistance = 10,
            simulationDistance = 6,
            maxFps = 45,
            graphics = NevGraphicsMode.FANCY,
            clouds = NevCloudMode.FAST,
            particles = NevParticleMode.DECREASED,
            entityDistancePercent = 75
        )
    ),
    CLEAR(
        "Clear Graphics",
        "Utamakan tampilan vanilla yang jernih; kurangi jarak jika FPS turun.",
        NevPerformanceSettings(
            renderDistance = 12,
            simulationDistance = 6,
            maxFps = 45,
            graphics = NevGraphicsMode.FANCY,
            clouds = NevCloudMode.FANCY,
            particles = NevParticleMode.ALL,
            entityDistancePercent = 100
        )
    ),
    PERFORMANCE(
        "Performance",
        "Kurangi beban pemrosesan untuk membantu menjaga FPS.",
        NevPerformanceSettings(
            renderDistance = 6,
            simulationDistance = 4,
            maxFps = 30,
            graphics = NevGraphicsMode.FAST,
            clouds = NevCloudMode.OFF,
            particles = NevParticleMode.MINIMAL,
            entityDistancePercent = 50
        )
    );

    companion object {
        /** Preset yang persis sama dengan [settings], atau null bila pengguna sudah mengubahnya (kustom). */
        fun matching(settings: NevPerformanceSettings): NevPerformancePreset? =
            entries.firstOrNull { it.settings == settings }
    }
}

/** Versi Minecraft yang punya profil dasar di folder `packs/`. Belum ada yang lolos uji perangkat. */
object NevPerformanceProfiles {
    val BASELINE_VERSIONS = setOf("1.21.11", "26.2")

    fun hasBaseline(minecraftVersion: String?): Boolean =
        minecraftVersion != null && minecraftVersion in BASELINE_VERSIONS
}
