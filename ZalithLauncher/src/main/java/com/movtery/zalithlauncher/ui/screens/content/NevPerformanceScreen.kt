/*
 * NEV Performance — launcher-side performance profiles.
 * Derived from the NevLauncher/ZalithLauncher codebase; see repository license.
 */
package com.movtery.zalithlauncher.ui.screens.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.movtery.zalithlauncher.game.version.installed.Version
import com.movtery.zalithlauncher.game.version.installed.VersionsManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class NevPerformancePreset(val label: String, val description: String) {
    BALANCED("Balanced", "Grafis tetap bagus dengan beban yang seimbang."),
    CLEAR("Clear Graphics", "Utamakan tampilan vanilla yang jernih; kurangi jarak jika FPS turun."),
    PERFORMANCE("Performance", "Kurangi beban pemrosesan untuk membantu menjaga FPS.")
}

@Composable
fun NevPerformanceScreen() {
    val context = LocalContext.current
    val preferences = remember {
        context.getSharedPreferences("nev_performance", android.content.Context.MODE_PRIVATE)
    }
    var presetName by remember {
        mutableStateOf(preferences.getString("preset", NevPerformancePreset.BALANCED.name) ?: NevPerformancePreset.BALANCED.name)
    }
    var renderDistance by remember { mutableIntStateOf(preferences.getInt("render_distance", 10)) }
    var simulationDistance by remember { mutableIntStateOf(preferences.getInt("simulation_distance", 6)) }
    var fpsLimit by remember { mutableIntStateOf(preferences.getInt("fps_limit", 45)) }
    var status by remember { mutableStateOf("Pilih profil, lalu simpan atau terapkan ke options.txt.") }

    val selectedVersion: Version? = VersionsManager.currentVersion.value
    val gameDir = selectedVersion?.getGameDir()
    val optionsFile = gameDir?.let { File(it, "options.txt") }
    val selectedPreset = NevPerformancePreset.entries.firstOrNull { it.name == presetName }
        ?: NevPerformancePreset.BALANCED

    fun choosePreset(preset: NevPerformancePreset) {
        presetName = preset.name
        when (preset) {
            NevPerformancePreset.BALANCED -> {
                renderDistance = 10
                simulationDistance = 6
                fpsLimit = 45
            }
            NevPerformancePreset.CLEAR -> {
                renderDistance = 12
                simulationDistance = 6
                fpsLimit = 45
            }
            NevPerformancePreset.PERFORMANCE -> {
                renderDistance = 6
                simulationDistance = 4
                fpsLimit = 30
            }
        }
        status = "Profil \${preset.label} dipilih. Tekan Simpan Profil untuk menyimpan."
    }

    fun saveProfile() {
        preferences.edit()
            .putString("preset", presetName)
            .putInt("render_distance", renderDistance)
            .putInt("simulation_distance", simulationDistance)
            .putInt("fps_limit", fpsLimit)
            .apply()
        status = "Profil tersimpan di Nev Launcher. Untuk menerapkan perubahan ke game, tekan Terapkan ke Minecraft."
    }

    fun applyToMinecraft() {
        val target = optionsFile
        if (selectedVersion == null || target == null) {
            status = "Pilih versi Minecraft yang terpasang di layar utama terlebih dahulu."
            return
        }
        if (!target.isFile) {
            status = "options.txt belum ada untuk versi ini. Jalankan Minecraft sekali, kembali ke launcher, lalu coba lagi."
            return
        }
        try {
            val original = target.readText(Charsets.UTF_8)
            val lines = original.lines().toMutableList()
            val updates = mapOf(
                "renderDistance" to renderDistance.toString(),
                "simulationDistance" to simulationDistance.toString(),
                "maxFps" to fpsLimit.toString()
            )
            var changed = 0
            for (index in lines.indices) {
                val separator = lines[index].indexOf(':')
                if (separator <= 0) continue
                val key = lines[index].substring(0, separator)
                val value = updates[key] ?: continue
                if (lines[index].substring(separator + 1) != value) {
                    lines[index] = "$key:$value"
                    changed++
                }
            }
            if (changed == 0 && updates.keys.none { key -> lines.any { it.startsWith("$key:") } }) {
                status = "Format options.txt versi ini tidak memiliki kunci pengaturan yang dikenali. Tidak ada file yang diubah."
                return
            }
            val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val backup = File(target.parentFile, "options.txt.nev-backup-$timestamp")
            target.copyTo(backup, overwrite = false)
            target.writeText(lines.joinToString("\n"), Charsets.UTF_8)
            saveProfile()
            status = "Pengaturan yang tersedia diterapkan. Cadangan dibuat: \${backup.name}. Tutup lalu buka ulang Minecraft agar perubahan terbaca."
        } catch (error: Exception) {
            status = "Gagal menerapkan pengaturan: \${error.message ?: "kesalahan file"}. Periksa izin penyimpanan dan coba lagi."
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("NEV Performance", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Profil optimasi untuk Minecraft Java di Android. Profil mengubah beberapa opsi grafis yang tersedia; mod tidak dipasang otomatis.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Versi aktif", style = MaterialTheme.typography.titleMedium)
                Text(selectedVersion?.getVersionName() ?: "Belum ada versi dipilih", style = MaterialTheme.typography.bodyLarge)
                Text(
                    gameDir?.absolutePath ?: "Pilih versi Minecraft di beranda. Terapkan hanya saat game tertutup.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Text("Pilih profil", style = MaterialTheme.typography.titleLarge)
        NevPerformancePreset.entries.forEach { preset ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (preset == selectedPreset) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerLow
                ),
                onClick = { choosePreset(preset) }
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(preset.label, style = MaterialTheme.typography.titleMedium)
                    Text(preset.description, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Render distance: $renderDistance chunks", style = MaterialTheme.typography.titleMedium)
                Slider(
                    value = renderDistance.toFloat(),
                    onValueChange = { renderDistance = it.toInt() },
                    valueRange = 4f..20f,
                    steps = 15
                )
                Text("Simulation distance: $simulationDistance chunks", style = MaterialTheme.typography.titleMedium)
                Slider(
                    value = simulationDistance.toFloat(),
                    onValueChange = { simulationDistance = it.toInt() },
                    valueRange = 4f..12f,
                    steps = 7
                )
                Text("Batas FPS: $fpsLimit", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(30, 45, 60).forEach { fps ->
                        if (fpsLimit == fps) {
                            Button(onClick = { fpsLimit = fps }) { Text("$fps FPS") }
                        } else {
                            OutlinedButton(onClick = { fpsLimit = fps }) { Text("$fps FPS") }
                        }
                    }
                }
            }
        }

        Button(onClick = ::saveProfile, modifier = Modifier.fillMaxWidth()) {
            Text("Simpan Profil")
        }
        Button(onClick = ::applyToMinecraft, modifier = Modifier.fillMaxWidth()) {
            Text("Terapkan ke Minecraft")
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Status", style = MaterialTheme.typography.titleMedium)
                Text(status, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Catatan: Minecraft harus ditutup saat menerapkan. Nev membuat cadangan options.txt sebelum menulis. Modpack Sodium/Entity Culling/Lithium belum dipasang otomatis dan perlu validasi khusus renderer Android.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.padding(bottom = 8.dp))
    }
}
