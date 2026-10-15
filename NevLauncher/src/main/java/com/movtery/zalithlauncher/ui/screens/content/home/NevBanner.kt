package com.movtery.zalithlauncher.ui.screens.content.home

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableIntStateOf
import com.movtery.zalithlauncher.setting.AllSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Nev Launcher: penyimpanan banner kustom (gambar / GIF / video) untuk layar utama.
 */
object NevBannerStore {
    /** Naik setiap kali banner berubah, supaya tampilan dimuat ulang */
    val version = mutableIntStateOf(0)

    fun file(context: Context): File = File(File(context.filesDir, "banner"), "banner.file")

    fun exists(context: Context): Boolean = file(context).let { it.exists() && it.length() > 0 }

    /** Menyalin file pilihan pengguna ke penyimpanan privat launcher. Mengembalikan true jika berhasil. */
    suspend fun import(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val mime = context.contentResolver.getType(uri).orEmpty()
            val isVideo = mime.startsWith("video/")
            if (!isVideo && !mime.startsWith("image/")) return@runCatching false
            val target = file(context)
            target.parentFile?.mkdirs()
            val tmp = File(target.parentFile, "banner.tmp")
            context.contentResolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { input.copyTo(it) }
            } ?: return@runCatching false
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) return@runCatching false
            AllSettings.bannerIsVideo.save(isVideo)
            version.intValue++
            true
        }.getOrDefault(false)
    }

    suspend fun reset(context: Context) = withContext(Dispatchers.IO) {
        file(context).delete()
        version.intValue++
    }
}
