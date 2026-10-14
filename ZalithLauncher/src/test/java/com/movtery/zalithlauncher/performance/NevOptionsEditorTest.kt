package com.movtery.zalithlauncher.performance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Date

class NevOptionsEditorTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val balanced = NevPerformancePreset.BALANCED.settings

    @Test
    fun updatesExistingKeysAndKeepsEverythingElse() {
        val original = "version:4189\nrenderDistance:12\nresourcePacks:[\"vanilla\",\"file/x.zip\"]\nmaxFps:120\n"
        val result = NevOptionsEditor.apply(original, mapOf("renderDistance" to "10", "maxFps" to "45"))
        assertEquals(
            "version:4189\nrenderDistance:10\nresourcePacks:[\"vanilla\",\"file/x.zip\"]\nmaxFps:45\n",
            result.text
        )
        assertEquals(listOf("renderDistance", "maxFps"), result.updated)
    }

    @Test
    fun keepsQuotedStyleOfExistingValues() {
        val result = NevOptionsEditor.apply("renderClouds:\"true\"\n", mapOf("renderClouds" to "fast"))
        assertEquals("renderClouds:\"fast\"\n", result.text)
    }

    @Test
    fun keepsCrlfLineEndings() {
        val result = NevOptionsEditor.apply("renderDistance:12\r\nmaxFps:60\r\n", mapOf("renderDistance" to "8"))
        assertEquals("renderDistance:8\r\nmaxFps:60\r\n", result.text)
    }

    @Test
    fun doesNotAddTrailingNewlineThatWasNotThere() {
        val result = NevOptionsEditor.apply("renderDistance:12", mapOf("renderDistance" to "8"))
        assertEquals("renderDistance:8", result.text)
    }

    @Test
    fun addsMissingCoreKeysButSkipsOptionalOnes() {
        val result = NevOptionsEditor.apply(
            "version:1\n",
            mapOf("simulationDistance" to "6", "particles" to "1")
        )
        assertEquals("version:1\nsimulationDistance:6\n", result.text)
        assertEquals(listOf("simulationDistance"), result.added)
        assertEquals(listOf("particles"), result.skipped)
    }

    @Test
    fun reportsUnchangedWhenAlreadyMatching() {
        val result = NevOptionsEditor.apply("maxFps:45\n", mapOf("maxFps" to "45"))
        assertFalse(result.changedAnything)
        assertEquals(listOf("maxFps"), result.unchanged)
    }

    @Test
    fun valueContainingColonStaysIntact() {
        val original = "lastServer:play.example.com:25565\nmaxFps:60\n"
        val result = NevOptionsEditor.apply(original, mapOf("maxFps" to "30"))
        assertEquals("lastServer:play.example.com:25565\nmaxFps:30\n", result.text)
    }

    @Test
    fun emptyFileGetsCoreKeys() {
        val result = NevOptionsEditor.apply("", NevOptionsEditor.toUpdates(balanced))
        assertEquals("renderDistance:10\nsimulationDistance:6\nmaxFps:45\n", result.text)
    }

    @Test
    fun toUpdatesClampsOutOfRangeValues() {
        val updates = NevOptionsEditor.toUpdates(
            balanced.copy(renderDistance = 99, simulationDistance = 1, maxFps = 5, entityDistancePercent = 500)
        )
        assertEquals("20", updates["renderDistance"])
        assertEquals("4", updates["simulationDistance"])
        assertEquals("10", updates["maxFps"])
        assertEquals("1.00", updates["entityDistanceScaling"])
    }

    @Test
    fun presetMatchingDetectsCustomSettings() {
        assertEquals(NevPerformancePreset.CLEAR, NevPerformancePreset.matching(NevPerformancePreset.CLEAR.settings))
        assertEquals(null, NevPerformancePreset.matching(balanced.copy(renderDistance = 11)))
    }

    @Test
    fun baselineProfilesOnlyForKnownVersions() {
        assertTrue(NevPerformanceProfiles.hasBaseline("1.21.11"))
        assertTrue(NevPerformanceProfiles.hasBaseline("26.2"))
        assertFalse(NevPerformanceProfiles.hasBaseline("1.20.1"))
        assertFalse(NevPerformanceProfiles.hasBaseline(null))
    }

    @Test
    fun backupCreateKeepsOnlyNewestFive() {
        val options = File(tmp.root, "options.txt").apply { writeText("a") }
        repeat(7) { index ->
            NevOptionsBackups.create(options, Date(1_700_000_000_000L + index * 1000L))
        }
        assertEquals(NevOptionsBackups.KEEP_COUNT, NevOptionsBackups.list(options).size)
    }

    @Test
    fun restoreLatestBringsBackOldContentAndKeepsCurrentAsPreRestore() {
        val options = File(tmp.root, "options.txt").apply { writeText("old") }
        NevOptionsBackups.create(options, Date(1_700_000_000_000L))
        options.writeText("new")

        val result = NevOptionsBackups.restoreLatest(options)

        assertTrue(result is NevOptionsBackups.RestoreResult.Restored)
        assertEquals("old", options.readText())
        assertEquals("new", File(tmp.root, NevOptionsBackups.PRE_RESTORE_NAME).readText())
    }

    @Test
    fun restoreWithoutBackupReportsNoBackup() {
        val options = File(tmp.root, "options.txt").apply { writeText("x") }
        assertEquals(NevOptionsBackups.RestoreResult.NoBackup, NevOptionsBackups.restoreLatest(options))
        assertEquals("x", options.readText())
    }
}
