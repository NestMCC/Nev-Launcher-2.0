package com.movtery.zalithlauncher.game.launch

import android.content.Context
import com.movtery.zalithlauncher.utils.device.Architecture
import com.movtery.zalithlauncher.utils.platform.bytesToMB
import com.movtery.zalithlauncher.utils.platform.getTotalMemory
import kotlin.math.max
import kotlin.math.min

/**
 * Nev Launcher: optimasi otomatis Minecraft untuk perangkat dengan spesifikasi rendah.
 * Menyesuaikan pengaturan video (options.txt) dan argumen JVM sesuai kelas perangkat.
 */
object GameOptimizer {
    enum class Tier { LOW, MEDIUM, HIGH }

    /** Kelas perangkat berdasarkan total RAM, jumlah inti CPU, dan arsitektur */
    fun tier(context: Context): Tier {
        val ramMb = getTotalMemory(context).bytesToMB()
        val cores = Runtime.getRuntime().availableProcessors()
        return when {
            Architecture.is32BitsDevice || ramMb <= 3300 || cores <= 4 -> Tier.LOW
            ramMb >= 7500 && cores >= 8 -> Tier.HIGH
            else -> Tier.MEDIUM
        }
    }
}

private fun MCOptions.capInt(key: String, cap: Int) {
    val current = get(key)?.toIntOrNull()
    set(key, (if (current == null) cap else min(current, cap)).toString())
}

/** Terapkan pengaturan ringan ke options.txt. Pengaturan hanya diturunkan, tidak pernah dinaikkan. */
fun MCOptions.applyOptimization(tier: GameOptimizer.Tier) {
    if (tier == GameOptimizer.Tier.HIGH) return
    val low = tier == GameOptimizer.Tier.LOW

    capInt("renderDistance", if (low) 4 else 8)
    capInt("simulationDistance", if (low) 5 else 8)
    capInt("maxFps", if (low) 45 else 60)

    // particles: 0 = semua, 1 = berkurang, 2 = minimal
    val particles = get("particles")?.toIntOrNull() ?: 0
    set("particles", max(particles, if (low) 2 else 1).toString())

    set("entityShadows", "false")

    if (low) {
        capInt("mipmapLevels", 0)
        capInt("biomeBlendRadius", 0)
        val scaling = get("entityDistanceScaling")?.toFloatOrNull() ?: 1f
        set("entityDistanceScaling", min(scaling, 0.5f).toString())
        // Grafis cepat (kunci berbeda antar versi Minecraft; hanya diubah jika kunci ada)
        if (containsKey("fancyGraphics")) set("fancyGraphics", "false")
        if (containsKey("graphicsMode")) set("graphicsMode", "0")
        if (containsKey("ao")) {
            val isBoolean = get("ao") == "true" || get("ao") == "false"
            set("ao", if (isBoolean) "false" else "0")
        }
    }
}
