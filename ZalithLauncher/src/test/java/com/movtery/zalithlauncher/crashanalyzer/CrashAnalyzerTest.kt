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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashAnalyzerTest {

    private fun causes(log: String, report: String? = null, exit: ExitInfo? = null) =
        CrashAnalyzer.analyze(log, report, exit).findings.map { it.cause }

    private fun finding(cause: CrashCause, log: String, report: String? = null, exit: ExitInfo? = null) =
        CrashAnalyzer.analyze(log, report, exit).findings.firstOrNull { it.cause == cause }

    @Test
    fun emptyLogHasNothing() {
        val result = CrashAnalyzer.analyze("")
        assertFalse(result.hasAnything)
        assertTrue(result.findings.isEmpty())
        assertNull(result.reportDescription)
    }

    @Test
    fun cleanLogHasNothing() {
        val log = """
            [12:00:00] [main/INFO]: Setting user: Steve
            [12:00:05] [Render thread/INFO]: Loaded 1234 recipes
        """.trimIndent()
        assertFalse(CrashAnalyzer.analyze(log).hasAnything)
    }

    //region memori

    @Test
    fun detectsJavaHeapOom() {
        val log = """Exception in thread "Render thread" java.lang.OutOfMemoryError: Java heap space"""
        assertEquals(listOf(CrashCause.OUT_OF_MEMORY), causes(log))
    }

    @Test
    fun detectsNativeThreadOomAsNativeNotHeap() {
        val log = "java.lang.OutOfMemoryError: unable to create native thread: possibly out of memory or process/resource limits reached"
        assertEquals(listOf(CrashCause.OUT_OF_MEMORY_NATIVE), causes(log))
    }

    @Test
    fun detectsMetaspaceOom() {
        assertEquals(
            listOf(CrashCause.OUT_OF_MEMORY_METASPACE),
            causes("java.lang.OutOfMemoryError: Metaspace")
        )
    }

    @Test
    fun detectsGpuOom() {
        assertTrue(CrashCause.GPU_OUT_OF_MEMORY in causes("OpenGL error: 1285 (Out of memory)"))
    }

    @Test
    fun sigkillMeansSystemKilled() {
        val found = finding(CrashCause.SYSTEM_KILLED, "", exit = ExitInfo(9, isSignal = true))
        assertNotNull(found)
    }

    @Test
    fun exitCodeNineWithoutSignalIsNotSystemKilled() {
        assertTrue(causes("", exit = ExitInfo(9, isSignal = false)).isEmpty())
    }

    //endregion

    //region Java / JVM

    @Test
    fun classFileVersion65NeedsJava21() {
        val log = "java.lang.UnsupportedClassVersionError: net/minecraft/Foo has been compiled by a more recent version of the Java Runtime (class file version 65.0), this version of the Java Runtime only recognizes class file versions up to 61.0"
        val found = finding(CrashCause.JAVA_TOO_OLD, log)
        assertNotNull(found)
        assertEquals(listOf("21"), found!!.args)
    }

    @Test
    fun oldStyleMajorMinorNeedsJava8() {
        val found = finding(CrashCause.JAVA_TOO_OLD, "Unsupported major.minor version 52.0")
        assertNotNull(found)
        assertEquals(listOf("8"), found!!.args)
    }

    @Test
    fun fabricJavaRequirementNeedsJava17() {
        val log = "- Mod 'Sodium' (sodium) 0.5.8 requires version 17 or later of 'Java HotSpot(TM) 64-Bit Server VM' (java), but only the wrong version is present: 8!"
        val found = finding(CrashCause.JAVA_TOO_OLD, log)
        assertNotNull(found)
        assertEquals(listOf("17"), found!!.args)
        //tidak boleh dihitung sebagai ketidakcocokan versi mod
        assertFalse(CrashCause.MOD_VERSION_MISMATCH in causes(log))
    }

    @Test
    fun detectsTooNewJava() {
        val log = "java.lang.ClassCastException: class jdk.internal.loader.ClassLoaders\$AppClassLoader cannot be cast to class java.net.URLClassLoader"
        assertTrue(CrashCause.JAVA_TOO_NEW in causes(log))
    }

    @Test
    fun detectsJvmInitFailureWithDetail() {
        val log = "Error occurred during initialization of VM\nCould not reserve enough space for 8388608KB object heap"
        val found = finding(CrashCause.JVM_INIT_FAILED, log)
        assertNotNull(found)
        assertTrue(found!!.details.single().startsWith("Could not reserve"))
    }

    @Test
    fun collectsAllInvalidJvmArguments() {
        val log = """
            Unrecognized VM option 'UseShenandoahGC'
            Unrecognized VM option 'ZGenerational'
        """.trimIndent()
        val found = finding(CrashCause.INVALID_JVM_ARGUMENT, log)
        assertEquals(listOf("UseShenandoahGC", "ZGenerational"), found!!.details)
    }

    @Test
    fun graphicsNativeCrashIsNotGeneric() {
        val log = """
            # A fatal error has been detected by the Java Runtime Environment:
            # Problematic frame:
            # C  [libGLESv2_adreno.so+0x1a2b3c]  glDrawElements+0x10
        """.trimIndent()
        val result = causes(log)
        assertTrue(CrashCause.NATIVE_CRASH_GRAPHICS in result)
        assertFalse(CrashCause.NATIVE_CRASH_GENERIC in result)
    }

    @Test
    fun nonGraphicsNativeCrashIsGeneric() {
        val log = """
            # A fatal error has been detected by the Java Runtime Environment:
            # Problematic frame:
            # C  [libfoo.so+0x1a2b3c]  bar+0x10
        """.trimIndent()
        val result = causes(log)
        assertTrue(CrashCause.NATIVE_CRASH_GENERIC in result)
        assertFalse(CrashCause.NATIVE_CRASH_GRAPHICS in result)
    }

    @Test
    fun sigsegvWithoutLogIsGenericNativeCrash() {
        assertEquals(listOf(CrashCause.NATIVE_CRASH_GENERIC), causes("", exit = ExitInfo(11, isSignal = true)))
    }

    @Test
    fun sigsegvDoesNotOverrideGraphicsCrash() {
        val log = "# Problematic frame:\n# C  [libEGL_mali.so+0x1]  eglSwapBuffers+0x4"
        val result = causes(log, exit = ExitInfo(11, isSignal = true))
        assertTrue(CrashCause.NATIVE_CRASH_GRAPHICS in result)
        assertFalse(CrashCause.NATIVE_CRASH_GENERIC in result)
    }

    //endregion

    //region mod & loader

    @Test
    fun fabricMissingDependency() {
        val log = "- Mod 'Sodium Extra' (sodium-extra) 0.5.4 requires any version of mod 'Sodium' (sodium), which is missing!"
        val found = finding(CrashCause.MOD_MISSING_DEPENDENCY, log)
        assertNotNull(found)
        assertEquals(listOf("Sodium Extra → Sodium (sodium)"), found!!.details)
    }

    @Test
    fun forgeMissingDependency() {
        val log = "Mod ID: 'geckolib', Requested by: 'alexsmobs', Expected range: '[4.4,)', Actual version: '[MISSING]'"
        val found = finding(CrashCause.MOD_MISSING_DEPENDENCY, log)
        assertEquals(listOf("alexsmobs → geckolib"), found!!.details)
    }

    @Test
    fun forgeMissingDependencyIsNotVersionMismatch() {
        val log = "Mod ID: 'geckolib', Requested by: 'alexsmobs', Expected range: '[4.4,)', Actual version: '[MISSING]'"
        assertFalse(CrashCause.MOD_VERSION_MISMATCH in causes(log))
    }

    @Test
    fun forgeVersionMismatch() {
        val log = "Mod ID: 'curios', Requested by: 'artifacts', Expected range: '[5.4,)', Actual version: '5.1.0'"
        val found = finding(CrashCause.MOD_VERSION_MISMATCH, log)
        assertNotNull(found)
        assertEquals(listOf("artifacts → curios: [5.4,) ≠ 5.1.0"), found!!.details)
    }

    @Test
    fun forgeWrongMinecraftVersion() {
        val log = "Mod ID: 'minecraft', Requested by: 'create', Expected range: '[1.20.1]', Actual version: '1.19.2'"
        val result = causes(log)
        assertTrue(CrashCause.WRONG_MINECRAFT_VERSION in result)
        assertFalse(CrashCause.MOD_VERSION_MISMATCH in result)
    }

    @Test
    fun fabricWrongLoaderVersion() {
        val log = "- Mod 'Iris' (iris) 1.7.0 requires version 0.15.10 or later of 'Fabric Loader' (fabricloader), but only the wrong version is present: 0.14.0!"
        assertTrue(CrashCause.WRONG_LOADER_VERSION in causes(log))
    }

    @Test
    fun detectsDuplicateMods() {
        assertTrue(CrashCause.MOD_DUPLICATE in causes("net.fabricmc.loader.impl.discovery.ModResolutionException: Duplicate mods found"))
    }

    @Test
    fun detectsMixinFailureWithModName() {
        val log = "[main/WARN]: Mixin apply for mod sodium failed sodium.mixins.json:core.MixinFoo from mod sodium"
        val found = finding(CrashCause.MOD_MIXIN_FAILURE, log)
        assertEquals(listOf("sodium"), found!!.details)
    }

    @Test
    fun genericModLoadingErrorIsSuppressedByAMoreSpecificOne() {
        val log = """
            net.minecraftforge.fml.ModLoadingException: Mod loading has failed
            Mod ID: 'geckolib', Requested by: 'alexsmobs', Expected range: '[4.4,)', Actual version: '[MISSING]'
        """.trimIndent()
        val result = causes(log)
        assertTrue(CrashCause.MOD_MISSING_DEPENDENCY in result)
        assertFalse(CrashCause.MOD_LOADING_ERROR in result)
    }

    @Test
    fun genericModLoadingErrorAppearsAlone() {
        assertEquals(listOf(CrashCause.MOD_LOADING_ERROR), causes("Mod resolution failed"))
    }

    @Test
    fun noSuchMethodIsClassMismatch() {
        val found = finding(CrashCause.MOD_CLASS_MISMATCH, "Caused by: java.lang.NoSuchMethodError: 'void net.minecraft.Foo.bar()'")
        assertNotNull(found)
    }

    @Test
    fun classMismatchIsSuppressedWhenVersionProblemIsKnown() {
        val log = """
            java.lang.NoSuchMethodError: 'void net.minecraft.Foo.bar()'
            - Mod 'Iris' (iris) 1.7.0 requires version 0.15.10 or later of 'Fabric Loader' (fabricloader), but only the wrong version is present: 0.14.0!
        """.trimIndent()
        assertFalse(CrashCause.MOD_CLASS_MISMATCH in causes(log))
    }

    //endregion

    //region grafis, file, penyimpanan

    @Test
    fun detectsGraphicsInitFailure() {
        assertTrue(CrashCause.GRAPHICS_INIT_FAILED in causes("GLFW error 65543: EGL: Failed to create context"))
    }

    @Test
    fun detectsShaderError() {
        assertTrue(CrashCause.SHADER_ERROR in causes("Failed to compile shader: rendertype_solid.fsh"))
    }

    @Test
    fun detectsCorruptJarWithName() {
        val found = finding(CrashCause.CORRUPT_FILE, "java.util.zip.ZipException: zip END header not found in /storage/mods/cool-mod-1.0.jar")
        assertEquals(listOf("cool-mod-1.0.jar"), found!!.details)
    }

    @Test
    fun detectsStorageFull() {
        assertTrue(CrashCause.STORAGE_FULL in causes("java.io.IOException: No space left on device"))
    }

    @Test
    fun detectsPermissionDenied() {
        val found = finding(CrashCause.STORAGE_PERMISSION, "java.io.FileNotFoundException: /storage/emulated/0/x/options.txt (Permission denied)")
        assertEquals(listOf("/storage/emulated/0/x/options.txt"), found!!.details)
    }

    @Test
    fun detectsMissingMainClass() {
        assertTrue(CrashCause.MAIN_CLASS_MISSING in causes("Error: Could not find or load main class net.minecraft.client.main.Main"))
    }

    //endregion

    //region urutan, crash report, ketahanan

    @Test
    fun resultsAreSortedByConfidence() {
        val log = """
            Mod resolution failed
            java.lang.OutOfMemoryError: Java heap space
            Failed to compile shader: x.fsh
        """.trimIndent()
        val severities = CrashAnalyzer.analyze(log).findings.map { it.cause.severity }
        assertEquals(severities.sorted(), severities)
        assertEquals(CrashCause.OUT_OF_MEMORY, CrashAnalyzer.analyze(log).findings.first().cause)
    }

    @Test
    fun evidenceIsTrimmedAndLimited() {
        val log = "    java.lang.OutOfMemoryError: Java heap space   at   Foo " + "x".repeat(1000)
        val evidence = finding(CrashCause.OUT_OF_MEMORY, log)!!.evidence!!
        assertTrue(evidence.length <= CrashAnalyzer.MAX_EVIDENCE_LENGTH)
        assertTrue(evidence.startsWith("java.lang.OutOfMemoryError: Java heap space at Foo "))
    }

    @Test
    fun findingsAreMergedAcrossLogAndReport() {
        val log = "java.lang.OutOfMemoryError: Java heap space"
        val report = "No space left on device"
        assertEquals(
            setOf(CrashCause.OUT_OF_MEMORY, CrashCause.STORAGE_FULL),
            causes(log, report).toSet()
        )
    }

    @Test
    fun parsesSuspectedModsAndDescription() {
        val report = """
            ---- Minecraft Crash Report ----
            Description: Ticking entity

            -- Head --
            Suspected Mods: Create (create), Flywheel (flywheel)
        """.trimIndent()
        val result = CrashAnalyzer.analyze("", report)
        assertEquals(listOf("Create (create)", "Flywheel (flywheel)"), result.suspectedMods)
        assertEquals("Ticking entity", result.reportDescription)
        assertTrue(result.hasAnything)
    }

    @Test
    fun suspectedModsNoneIsIgnored() {
        assertTrue(CrashAnalyzer.analyze("", "Suspected Mods: NONE").suspectedMods.isEmpty())
    }

    @Test
    fun detailsAreCapped() {
        val log = (1..50).joinToString("\n") { "Unrecognized VM option 'Opt$it'" }
        assertEquals(CrashAnalyzer.MAX_DETAILS, finding(CrashCause.INVALID_JVM_ARGUMENT, log)!!.details.size)
    }

    @Test
    fun veryLongLinesDoNotHang() {
        val log = "a".repeat(5_000_000) + "\njava.lang.OutOfMemoryError: Java heap space"
        val start = System.nanoTime()
        assertTrue(CrashCause.OUT_OF_MEMORY in causes(log))
        assertTrue("took too long", (System.nanoTime() - start) / 1_000_000 < 5_000)
    }

    @Test
    fun everyCauseHasAtLeastOneRuleOrIsExitBased() {
        //menjaga agar CrashCause tidak ditambahkan tanpa aturan yang bisa menghasilkannya
        val exitBased = setOf(CrashCause.SYSTEM_KILLED)
        CrashCause.entries.filter { it !in exitBased }.forEach { cause ->
            assertTrue("$cause has no rule", CrashAnalyzer.RULE_ORDER[cause] != Int.MAX_VALUE)
        }
    }

    //endregion
}
