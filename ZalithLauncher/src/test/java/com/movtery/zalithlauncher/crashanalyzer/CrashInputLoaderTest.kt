/*
 * Zalith Launcher 2
 * Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
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
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package com.movtery.zalithlauncher.crashanalyzer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CrashInputLoaderTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val now = 1_800_000_000_000L

    private fun file(parent: File, name: String, text: String, modified: Long): File {
        parent.mkdirs()
        return File(parent, name).apply {
            writeText(text)
            setLastModified(modified)
        }
    }

    @Test
    fun missingLogGivesEmptyText() {
        val input = CrashInputLoader.load(File(tmp.root, "nope.log"), null)
        assertEquals("", input.logText)
        assertNull(input.crashReportText)
    }

    @Test
    fun readsWholeSmallLog() {
        val log = file(tmp.root, "latest_game.log", "line1\nline2\n", now)
        assertEquals("line1\nline2\n", CrashInputLoader.load(log, null).logText)
    }

    @Test
    fun largeLogKeepsTheEndAndDropsThePartialFirstLine() {
        val text = "HEAD-LINE\n" + "x".repeat(3_000_000) + "\nTAIL-LINE\n"
        val log = file(tmp.root, "big.log", text, now)
        val read = CrashInputLoader.load(log, null).logText
        assertTrue(read.endsWith("TAIL-LINE\n"))
        assertFalse(read.contains("HEAD-LINE"))
        assertTrue(read.length <= CrashInputLoader.MAX_LOG_BYTES)
    }

    @Test
    fun picksNewestRecentCrashReport() {
        val gameDir = tmp.newFolder("game")
        val log = file(tmp.root, "latest_game.log", "x", now)
        val reports = File(gameDir, "crash-reports")
        file(reports, "crash-2026-01-01_old.txt", "OLD", now - 60_000)
        file(reports, "crash-2026-01-02_new.txt", "NEW", now - 1_000)
        file(reports, "readme.txt", "IGNORED", now)

        assertEquals("NEW", CrashInputLoader.load(log, gameDir).crashReportText)
    }

    @Test
    fun ignoresCrashReportFromAnOlderSession() {
        val gameDir = tmp.newFolder("game")
        val log = file(tmp.root, "latest_game.log", "x", now)
        file(
            File(gameDir, "crash-reports"), "crash-ancient.txt", "ANCIENT",
            now - CrashInputLoader.REPORT_WINDOW_MS - 60_000
        )
        assertNull(CrashInputLoader.load(log, gameDir).crashReportText)
    }

    @Test
    fun combinesCrashReportAndHsErr() {
        val gameDir = tmp.newFolder("game")
        val log = file(tmp.root, "latest_game.log", "x", now)
        file(File(gameDir, "crash-reports"), "crash-a.txt", "REPORT", now)
        file(gameDir, "hs_err_pid1234.log", "HSERR", now)

        val text = CrashInputLoader.load(log, gameDir).crashReportText.orEmpty()
        assertTrue("REPORT" in text)
        assertTrue("HSERR" in text)
    }

    @Test
    fun missingGameDirIsFine() {
        val log = file(tmp.root, "latest_game.log", "x", now)
        assertNull(CrashInputLoader.load(log, File(tmp.root, "does-not-exist")).crashReportText)
        assertNull(CrashInputLoader.load(log, null).crashReportText)
    }

    @Test
    fun loadedInputFeedsTheAnalyzer() {
        val gameDir = tmp.newFolder("game")
        val log = file(tmp.root, "latest_game.log", "ok so far", now)
        file(
            File(gameDir, "crash-reports"), "crash-x.txt",
            "Description: Exception in server tick loop\njava.lang.OutOfMemoryError: Java heap space\nSuspected Mods: Create (create)",
            now
        )
        val input = CrashInputLoader.load(log, gameDir)
        val result = CrashAnalyzer.analyze(input.logText, input.crashReportText)
        assertEquals(CrashCause.OUT_OF_MEMORY, result.findings.single().cause)
        assertEquals(listOf("Create (create)"), result.suspectedMods)
    }
}
