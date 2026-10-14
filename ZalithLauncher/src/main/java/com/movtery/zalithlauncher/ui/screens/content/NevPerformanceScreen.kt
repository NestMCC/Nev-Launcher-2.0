/*
 * NEV Performance — launcher-side performance profiles.
 * Derived from the NevLauncher/ZalithLauncher codebase; see repository license.
 */
package com.movtery.zalithlauncher.ui.screens.content

import android.content.Context
import android.content.SharedPreferences
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.movtery.zalithlauncher.game.version.installed.VersionsManager
import com.movtery.zalithlauncher.performance.NevCloudMode
import com.movtery.zalithlauncher.performance.NevGraphicsMode
import com.movtery.zalithlauncher.performance.NevOptionsBackups
import com.movtery.zalithlauncher.performance.NevOptionsEditor
import com.movtery.zalithlauncher.performance.NevParticleMode
import com.movtery.zalithlauncher.performance.NevPerformancePreset
import com.movtery.zalithlauncher.performance.NevPerformanceProfiles
import com.movtery.zalithlauncher.performance.NevPerformanceSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

private const val PREFS_NAME = "nev_performance"

@Composable
fun NevPerformanceScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    // currentVersion adalah StateFlow: wajib dikoleksi agar layar ikut diperbarui saat versi diganti.
    val selectedVersion by VersionsManager.currentVersion.collectAsState()
    val versionName = selectedVersion?.getVersionName()
    val gameDir = selectedVersion?.getGameDir()
    val optionsFile = gameDir?.let { File(it, "options.txt") }
    val minecraftVersion = selectedVersion?.getVersionInfo()?.minecraftVersion

    // Pengaturan disimpan per versi game; berganti versi memuat pengaturan versi itu.
    var settings by remember(versionName) { mutableStateOf(preferences.loadSettings(versionName)) }
    var status by remember(versionName) {
        mutableStateOf("Pilih profil, lalu simpan atau terapkan ke options.txt.")
    }
    var busy by remember { mutableStateOf(false) }
    var backupCount by remember(versionName) { mutableIntStateOf(0) }

    LaunchedEffect(optionsFile) {
        backupCount = optionsFile?.let { withContext(Dispatchers.IO) { NevOptionsBackups.list(it).size } } ?: 0
    }

    val matchingPreset = NevPerformancePreset.matching(settings)

    fun saveProfile(message: String? = null) {
        preferences.saveSettings(versionName, settings)
        status = message
            ?: "Profil tersimpan di Nev Launcher untuk versi ini. Untuk menerapkan ke game, tekan Terapkan ke Minecraft."
    }

    fun applyToMinecraft() {
        val target = optionsFile
        if (selectedVersion == null || target == null) {
            status = "Pilih versi Minecraft yang terpasang di layar utama terlebih dahulu."
            return
        }
        val snapshot = settings.sanitized()
        scope.launch {
            busy = true
            val message = withContext(Dispatchers.IO) { applySettingsToFile(target, snapshot) }
            if (message.ok) {
                preferences.saveSettings(versionName, snapshot)
                backupCount = withContext(Dispatchers.IO) { NevOptionsBackups.list(target).size }
            }
            status = message.text
            busy = false
        }
    }

    fun restoreBackup() {
        val target = optionsFile ?: run {
            status = "Pilih versi Minecraft yang terpasang di layar utama terlebih dahulu."
            return
        }
        scope.launch {
            busy = true
            status = withContext(Dispatchers.IO) {
                try {
                    when (val result = NevOptionsBackups.restoreLatest(target)) {
                        is NevOptionsBackups.RestoreResult.Restored ->
                            "options.txt dikembalikan dari ${result.from.name}. Isi sebelum dipulihkan disimpan sebagai ${NevOptionsBackups.PRE_RESTORE_NAME}. Buka ulang Minecraft agar terbaca."
                        NevOptionsBackups.RestoreResult.NoBackup ->
                            "Belum ada cadangan options.txt buatan Nev untuk versi ini."
                    }
                } catch (error: Exception) {
                    "Gagal memulihkan cadangan: ${error.message ?: "kesalahan file"}. Periksa izin penyimpanan dan coba lagi."
                }
            }
            backupCount = withContext(Dispatchers.IO) { NevOptionsBackups.list(target).size }
            busy = false
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
                Text(versionName ?: "Belum ada versi dipilih", style = MaterialTheme.typography.bodyLarge)
                Text(
                    gameDir?.absolutePath ?: "Pilih versi Minecraft di beranda. Terapkan hanya saat game tertutup.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (selectedVersion != null) {
                    Text(
                        if (NevPerformanceProfiles.hasBaseline(minecraftVersion)) {
                            "Minecraft $minecraftVersion punya profil dasar NEV Performance (belum divalidasi di perangkat)."
                        } else {
                            "Versi ini belum punya profil dasar NEV. Pengaturan umum di bawah tetap bisa diterapkan; hasilnya belum diuji."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Text("Pilih profil", style = MaterialTheme.typography.titleLarge)
        NevPerformancePreset.entries.forEach { preset ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (preset == matchingPreset) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerLow
                ),
                onClick = {
                    settings = preset.settings
                    status = "Profil ${preset.label} dipilih. Tekan Simpan Profil untuk menyimpan."
                }
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(preset.label, style = MaterialTheme.typography.titleMedium)
                    Text(preset.description, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (matchingPreset == null) {
            Text(
                "Pengaturan kustom: nilai sudah diubah dari profil bawaan.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Render distance: ${settings.renderDistance} chunks", style = MaterialTheme.typography.titleMedium)
                Slider(
                    value = settings.renderDistance.toFloat(),
                    onValueChange = { settings = settings.copy(renderDistance = it.roundToInt()) },
                    valueRange = 4f..20f,
                    steps = 15
                )
                Text("Simulation distance: ${settings.simulationDistance} chunks", style = MaterialTheme.typography.titleMedium)
                Slider(
                    value = settings.simulationDistance.toFloat(),
                    onValueChange = { settings = settings.copy(simulationDistance = it.roundToInt()) },
                    valueRange = 4f..12f,
                    steps = 7
                )
                Text("Batas FPS: ${settings.maxFps}", style = MaterialTheme.typography.titleMedium)
                ChoiceRow(
                    choices = NevPerformanceSettings.FPS_CHOICES,
                    selected = settings.maxFps,
                    label = { "$it FPS" },
                    onSelect = { settings = settings.copy(maxFps = it) }
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Grafis", style = MaterialTheme.typography.titleMedium)
                ChoiceRow(
                    choices = NevGraphicsMode.entries,
                    selected = settings.graphics,
                    label = { it.label },
                    onSelect = { settings = settings.copy(graphics = it) }
                )
                Text("Awan", style = MaterialTheme.typography.titleMedium)
                ChoiceRow(
                    choices = NevCloudMode.entries,
                    selected = settings.clouds,
                    label = { it.label },
                    onSelect = { settings = settings.copy(clouds = it) }
                )
                Text("Partikel", style = MaterialTheme.typography.titleMedium)
                ChoiceRow(
                    choices = NevParticleMode.entries,
                    selected = settings.particles,
                    label = { it.label },
                    onSelect = { settings = settings.copy(particles = it) }
                )
                Text("Jarak entitas: ${settings.entityDistancePercent}%", style = MaterialTheme.typography.titleMedium)
                ChoiceRow(
                    choices = NevPerformanceSettings.ENTITY_DISTANCE_CHOICES,
                    selected = settings.entityDistancePercent,
                    label = { "$it%" },
                    onSelect = { settings = settings.copy(entityDistancePercent = it) }
                )
                Text(
                    "Shader tidak diubah (tetap sesuai pilihanmu). Opsi yang tidak ada di options.txt versi ini dilewati dan dilaporkan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Button(onClick = { saveProfile() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text("Simpan Profil")
        }
        Button(onClick = { applyToMinecraft() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text("Terapkan ke Minecraft")
        }
        OutlinedButton(
            onClick = { restoreBackup() },
            enabled = !busy && backupCount > 0,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (backupCount > 0) "Pulihkan Cadangan Terakhir ($backupCount tersimpan)" else "Pulihkan Cadangan (belum ada)")
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Status", style = MaterialTheme.typography.titleMedium)
                Text(status, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Catatan: Minecraft harus ditutup saat menerapkan. Nev membuat cadangan options.txt sebelum menulis (maksimal ${NevOptionsBackups.KEEP_COUNT} terbaru disimpan) dan tidak menyentuh dunia. Mod Sodium/Entity Culling/Lithium belum dipasang otomatis dan perlu validasi khusus renderer Android.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.padding(bottom = 8.dp))
    }
}

@Composable
private fun <T> ChoiceRow(
    choices: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEach { choice ->
            if (choice == selected) {
                Button(onClick = { onSelect(choice) }) { Text(label(choice)) }
            } else {
                OutlinedButton(onClick = { onSelect(choice) }) { Text(label(choice)) }
            }
        }
    }
}

private class ApplyMessage(val ok: Boolean, val text: String)

/** Dijalankan di thread IO: baca options.txt, buat cadangan, lalu tulis perubahan. */
private fun applySettingsToFile(target: File, settings: NevPerformanceSettings): ApplyMessage {
    if (!target.isFile) {
        return ApplyMessage(
            false,
            "options.txt belum ada untuk versi ini. Jalankan Minecraft sekali, kembali ke launcher, lalu coba lagi."
        )
    }
    return try {
        val original = target.readText(Charsets.UTF_8)
        val result = NevOptionsEditor.apply(original, NevOptionsEditor.toUpdates(settings))
        if (!result.changedAnything) {
            return ApplyMessage(true, "options.txt sudah sesuai dengan profil ini. Tidak ada yang diubah.")
        }
        val backup = NevOptionsBackups.create(target)
        target.writeText(result.text, Charsets.UTF_8)
        val changedCount = result.updated.size + result.added.size
        val skippedNote = if (result.skipped.isEmpty()) "" else
            " Dilewati karena tidak ada di options.txt versi ini: ${result.skipped.joinToString(", ")}."
        ApplyMessage(
            true,
            "$changedCount opsi diterapkan. Cadangan dibuat: ${backup.name}.$skippedNote Tutup lalu buka ulang Minecraft agar perubahan terbaca."
        )
    } catch (error: Exception) {
        ApplyMessage(false, "Gagal menerapkan pengaturan: ${error.message ?: "kesalahan file"}. Periksa izin penyimpanan dan coba lagi.")
    }
}

private fun versionKey(versionName: String?, field: String) = "v:${versionName.orEmpty()}:$field"

private inline fun <reified E : Enum<E>> SharedPreferences.enumOr(key: String, default: E): E {
    val name = getString(key, null) ?: return default
    return enumValues<E>().firstOrNull { it.name == name } ?: default
}

/** Muat pengaturan per versi; bila belum ada, pakai profil lama (global) lalu preset Balanced. */
private fun SharedPreferences.loadSettings(versionName: String?): NevPerformanceSettings {
    val base = NevPerformancePreset.BALANCED.settings
    return NevPerformanceSettings(
        renderDistance = getInt(versionKey(versionName, "render_distance"), getInt("render_distance", base.renderDistance)),
        simulationDistance = getInt(versionKey(versionName, "simulation_distance"), getInt("simulation_distance", base.simulationDistance)),
        maxFps = getInt(versionKey(versionName, "fps_limit"), getInt("fps_limit", base.maxFps)),
        graphics = enumOr(versionKey(versionName, "graphics"), base.graphics),
        clouds = enumOr(versionKey(versionName, "clouds"), base.clouds),
        particles = enumOr(versionKey(versionName, "particles"), base.particles),
        entityDistancePercent = getInt(versionKey(versionName, "entity_distance"), base.entityDistancePercent)
    ).sanitized()
}

private fun SharedPreferences.saveSettings(versionName: String?, settings: NevPerformanceSettings) {
    val safe = settings.sanitized()
    edit()
        .putInt(versionKey(versionName, "render_distance"), safe.renderDistance)
        .putInt(versionKey(versionName, "simulation_distance"), safe.simulationDistance)
        .putInt(versionKey(versionName, "fps_limit"), safe.maxFps)
        .putString(versionKey(versionName, "graphics"), safe.graphics.name)
        .putString(versionKey(versionName, "clouds"), safe.clouds.name)
        .putString(versionKey(versionName, "particles"), safe.particles.name)
        .putInt(versionKey(versionName, "entity_distance"), safe.entityDistancePercent)
        .apply()
}
