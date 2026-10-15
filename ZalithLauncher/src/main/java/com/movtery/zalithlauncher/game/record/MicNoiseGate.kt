package com.movtery.zalithlauncher.game.record

import kotlin.math.max
import kotlin.math.sqrt

/**
 * Nev Launcher: peredam suara sekitar untuk mikrofon (noise gate adaptif).
 *
 * Saat pemain tidak bicara, suara dari luar (orang lain, TV, kipas, dsb) yang tertangkap mic
 * diredam sampai hampir senyap. Ambang menyesuaikan otomatis dengan kebisingan latar.
 * Dipakai bersama NoiseSuppressor/AEC bawaan Android bila tersedia.
 */
class MicNoiseGate(sampleRate: Int) {
    private var noiseFloor = 250f
    private var gain = 0f
    private var holdChunks = 0

    //lama suara tetap terbuka setelah pemain berhenti bicara (±350 ms)
    private val holdChunkCount = max(1, (sampleRate * 0.35f / CHUNK_HINT_FRAMES).toInt())

    /**
     * Proses satu blok sampel mono 16-bit di tempat.
     */
    fun process(samples: ShortArray, length: Int) {
        if (length <= 0) return

        var sum = 0.0
        for (i in 0 until length) {
            val s = samples[i].toDouble()
            sum += s * s
        }
        val rms = sqrt(sum / length).toFloat()

        //perkiraan kebisingan latar: turun cepat, naik pelan
        noiseFloor = if (rms < noiseFloor) {
            noiseFloor * 0.8f + rms * 0.2f
        } else {
            noiseFloor + (rms - noiseFloor) * 0.002f
        }

        val threshold = max(noiseFloor * 3f, MIN_THRESHOLD)
        val open = rms > threshold
        if (open) holdChunks = holdChunkCount else if (holdChunks > 0) holdChunks--

        val target = if (open || holdChunks > 0) 1f else CLOSED_GAIN
        val start = gain
        //buka cepat, tutup halus supaya tidak terdengar "terpotong"
        val end = if (target > start) target else start + (target - start) * 0.35f
        val step = (end - start) / length
        var g = start
        for (i in 0 until length) {
            g += step
            val v = (samples[i] * g).toInt()
            samples[i] = v.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        gain = end
    }

    companion object {
        private const val MIN_THRESHOLD = 450f
        private const val CLOSED_GAIN = 0.02f
        private const val CHUNK_HINT_FRAMES = 1024f
    }
}
