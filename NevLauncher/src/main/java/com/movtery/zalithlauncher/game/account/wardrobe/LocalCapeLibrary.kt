/*
 * NEV Launcher
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */

package com.movtery.zalithlauncher.game.account.wardrobe

import android.graphics.BitmapFactory
import com.movtery.zalithlauncher.game.account.Account
import com.movtery.zalithlauncher.path.PathManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.io.FileUtils
import org.jackhuang.hmcl.util.DigestUtils
import java.io.File

/**
 * Satu cape yang sudah diinstal ke pustaka cape lokal.
 * @param name nama tampilan (nama file tanpa .png)
 * @param file file PNG di pustaka
 */
data class LocalCape(
    val name: String,
    val file: File
)

/**
 * Penanda "tanpa cape" untuk daftar pilihan
 */
val NoLocalCape = LocalCape("", File(""))

/**
 * Pustaka cape lokal untuk akun offline.
 * Cape yang dipilih disalin ke file cape akun ([Account.getCapeFile]),
 * lalu disajikan ke game oleh server Yggdrasil offline.
 */
object LocalCapeLibrary {
    private const val MAX_NAME_LENGTH = 40

    private fun dir(): File = File(PathManager.DIR_GAME, "local_capes").also { it.mkdirs() }

    /** Daftar cape yang sudah diinstal, urut berdasarkan nama */
    fun list(): List<LocalCape> {
        val files = dir().listFiles { f -> f.isFile && f.extension.equals("png", ignoreCase = true) }
            ?: return emptyList()
        return files
            .sortedBy { it.name.lowercase() }
            .map { LocalCape(it.nameWithoutExtension, it) }
    }

    /**
     * Cape Minecraft berukuran 64x32 (atau kelipatannya), atau 22x17 (format lama, atau kelipatannya)
     */
    suspend fun validateCapeFile(file: File): Boolean = withContext(Dispatchers.IO) {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        val w = options.outWidth
        val h = options.outHeight
        if (w <= 0 || h <= 0) return@withContext false
        val modern = w == h * 2 && w % 64 == 0
        val legacy = w * 17 == h * 22 && w % 22 == 0
        modern || legacy
    }

    private fun sanitizeName(raw: String): String {
        val base = raw.substringBeforeLast('.')
            .replace(Regex("[^\\p{L}\\p{N} _.\\-]"), "_")
            .trim()
            .take(MAX_NAME_LENGTH)
        return base.ifEmpty { "cape" }
    }

    /**
     * Instal cape dari [source] ke pustaka. Nama dibuat unik jika sudah dipakai.
     * @param displayName nama asal, biasanya nama file yang dipilih pengguna
     */
    suspend fun install(source: File, displayName: String): LocalCape = withContext(Dispatchers.IO) {
        val base = sanitizeName(displayName)
        val dir = dir()
        var name = base
        var index = 2
        while (File(dir, "$name.png").exists()) {
            name = "$base ($index)"
            index++
        }
        val target = File(dir, "$name.png")
        FileUtils.copyFile(source, target)
        LocalCape(name, target)
    }

    /** Hapus cape dari pustaka */
    suspend fun delete(cape: LocalCape) = withContext(Dispatchers.IO) {
        FileUtils.deleteQuietly(cape.file)
    }

    private fun File.sha256(): String = DigestUtils.digestToString("SHA-256", readBytes())

    /** Cape pustaka yang sedang dipakai akun (dicocokkan lewat hash isi file), atau null */
    suspend fun activeFor(account: Account): LocalCape? = withContext(Dispatchers.IO) {
        val current = account.getCapeFile().takeIf { it.exists() } ?: return@withContext null
        val hash = runCatching { current.sha256() }.getOrNull() ?: return@withContext null
        list().firstOrNull { runCatching { it.file.sha256() }.getOrNull() == hash }
    }

    /**
     * Pakai [cape] untuk [account]. Jika null atau [NoLocalCape], cape akun dihapus.
     */
    suspend fun applyTo(account: Account, cape: LocalCape?) = withContext(Dispatchers.IO) {
        val target = account.getCapeFile()
        if (cape == null || cape == NoLocalCape || !cape.file.exists()) {
            FileUtils.deleteQuietly(target)
        } else {
            target.parentFile?.mkdirs()
            FileUtils.copyFile(cape.file, target)
        }
    }
}
