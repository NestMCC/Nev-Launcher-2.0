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

/** Hasil pencocokan satu baris log terhadap satu aturan */
private class Match(
    val args: List<String> = emptyList(),
    val detail: String? = null
)

/** Pencocok sebuah aturan. [next] adalah baris sesudahnya (untuk pesan yang terpecah di dua baris) */
private typealias Matcher = (line: String, next: String?) -> Match?

private class Rule(
    val cause: CrashCause,
    /** true bila aturan ini terus dijalankan setelah cocok pertama untuk mengumpulkan rincian (mis. banyak mod) */
    val collectsDetails: Boolean = false,
    val matcher: Matcher
)

/** Penampung sementara temuan untuk satu penyebab selama pemindaian */
private class Accumulator {
    var args: List<String> = emptyList()
    var evidence: String? = null
    val details = LinkedHashSet<String>()

    fun add(match: Match, line: String) {
        if (evidence == null) {
            evidence = line.trim().replace(Regex("\\s+"), " ").take(CrashAnalyzer.MAX_EVIDENCE_LENGTH)
            args = match.args
        }
        match.detail?.let { detail ->
            if (details.size < CrashAnalyzer.MAX_DETAILS) details.add(detail)
        }
    }
}

/**
 * Penganalisis log crash Minecraft.
 *
 * Bekerja murni di atas teks: memindai log baris demi baris dengan sekumpulan aturan,
 * lalu menggabungkan dan mengurutkan temuan. Tidak bergantung pada Android sehingga
 * bisa diuji sebagai unit test biasa.
 */
object CrashAnalyzer {
    internal const val MAX_EVIDENCE_LENGTH = 300
    internal const val MAX_DETAILS = 8

    /** Baris yang lebih panjang dari ini dipotong sebelum dicocokkan, agar regex tetap cepat */
    private const val MAX_LINE_LENGTH = 4000

    /**
     * @param logText isi log game (latest_game.log atau sejenisnya)
     * @param crashReportText isi file crash-report, bila berhasil ditemukan
     * @param exit cara proses game berakhir, bila diketahui
     */
    fun analyze(
        logText: String,
        crashReportText: String? = null,
        exit: ExitInfo? = null
    ): CrashAnalysisResult {
        val found = LinkedHashMap<CrashCause, Accumulator>()
        scan(logText, found)
        if (crashReportText != null) scan(crashReportText, found)

        addExitBasedFindings(found, exit)

        val findings = found.entries
            .asSequence()
            .filter { (cause, _) -> !isSuppressed(cause, found.keys) }
            .sortedWith(compareBy({ it.key.severity }, { RULE_ORDER[it.key] ?: Int.MAX_VALUE }))
            .map { (cause, acc) ->
                CrashFinding(
                    cause = cause,
                    args = acc.args,
                    details = acc.details.toList(),
                    evidence = acc.evidence
                )
            }
            .toList()

        //crash report lebih lengkap daripada log; log dipakai bila report tidak ada atau tidak memuatnya
        val suspectedMods = (parseSuspectedMods(crashReportText) + parseSuspectedMods(logText))
            .distinct()
            .take(MAX_DETAILS)
        return CrashAnalysisResult(
            findings = findings,
            suspectedMods = suspectedMods,
            reportDescription = crashReportText?.let(::parseDescription) ?: parseDescription(logText)
        )
    }

    private fun scan(text: String, out: MutableMap<CrashCause, Accumulator>) {
        val lines = text.lines()
        for (i in lines.indices) {
            val raw = lines[i]
            if (raw.isBlank()) continue
            val line = if (raw.length > MAX_LINE_LENGTH) raw.take(MAX_LINE_LENGTH) else raw
            val next = lines.getOrNull(i + 1)?.take(MAX_LINE_LENGTH)

            for (rule in RULES) {
                val acc = out[rule.cause]
                if (acc != null) {
                    //sudah ketemu: lewati kecuali aturan ini memang masih mengumpulkan rincian
                    if (!rule.collectsDetails || acc.details.size >= MAX_DETAILS) continue
                }
                val match = rule.matcher(line, next) ?: continue
                out.getOrPut(rule.cause) { Accumulator() }.add(match, line)
            }
        }
    }

    /**
     * Temuan yang hanya bisa disimpulkan dari cara proses berakhir (bukan dari isi log)
     */
    private fun addExitBasedFindings(found: MutableMap<CrashCause, Accumulator>, exit: ExitInfo?) {
        if (exit == null || !exit.isSignal) return

        when (exit.code) {
            //SIGKILL: biasanya dibunuh sistem Android karena kehabisan RAM
            SIGNAL_KILL -> found.getOrPut(CrashCause.SYSTEM_KILLED) { Accumulator() }.apply {
                if (evidence == null) evidence = "signal ${exit.code}"
            }
            //Sinyal crash native; jangan menimpa temuan native yang lebih spesifik
            in NATIVE_CRASH_SIGNALS -> {
                if (CrashCause.NATIVE_CRASH_GRAPHICS !in found && CrashCause.NATIVE_CRASH_GENERIC !in found) {
                    found.getOrPut(CrashCause.NATIVE_CRASH_GENERIC) { Accumulator() }.apply {
                        if (evidence == null) evidence = "signal ${exit.code}"
                    }
                }
            }
        }
    }

    /**
     * Beberapa temuan umum tidak berguna bila sudah ada temuan yang lebih spesifik.
     */
    private fun isSuppressed(cause: CrashCause, present: Set<CrashCause>): Boolean {
        return when (cause) {
            CrashCause.MOD_LOADING_ERROR -> present.any { it in SPECIFIC_MOD_PROBLEMS }
            CrashCause.NATIVE_CRASH_GENERIC -> CrashCause.NATIVE_CRASH_GRAPHICS in present
            CrashCause.MOD_CLASS_MISMATCH -> present.any { it in VERSION_PROBLEMS }
            else -> false
        }
    }

    private val SPECIFIC_MOD_PROBLEMS = setOf(
        CrashCause.MOD_MISSING_DEPENDENCY,
        CrashCause.MOD_VERSION_MISMATCH,
        CrashCause.WRONG_MINECRAFT_VERSION,
        CrashCause.WRONG_LOADER_VERSION,
        CrashCause.MOD_CONFLICT,
        CrashCause.MOD_DUPLICATE,
        CrashCause.INVALID_MOD_FILE
    )

    private val VERSION_PROBLEMS = setOf(
        CrashCause.MOD_MISSING_DEPENDENCY,
        CrashCause.MOD_VERSION_MISMATCH,
        CrashCause.WRONG_MINECRAFT_VERSION,
        CrashCause.WRONG_LOADER_VERSION
    )

    private const val SIGNAL_KILL = 9
    //SIGILL, SIGABRT, SIGBUS, SIGFPE, SIGSEGV
    private val NATIVE_CRASH_SIGNALS = setOf(4, 6, 7, 8, 11)

    //region crash report

    private val SUSPECTED_MODS_REGEX = Regex("""^\s*Suspected Mods?:\s*(.+)$""", RegexOption.MULTILINE)
    private val MOD_WITH_ID_REGEX = Regex("""[^,()]+?\s*\([^)]+\)""")
    private val DESCRIPTION_REGEX = Regex("""^\s*Description:\s*(.+)$""", RegexOption.MULTILINE)

    /**
     * Mengambil daftar mod tersangka dari baris "Suspected Mods: A (a), B (b)"
     */
    private fun parseSuspectedMods(text: String?): List<String> {
        if (text.isNullOrEmpty()) return emptyList()
        val value = SUSPECTED_MODS_REGEX.find(text)?.groupValues?.get(1)?.trim() ?: return emptyList()
        if (value.equals("NONE", ignoreCase = true) || value.isEmpty()) return emptyList()

        val withIds = MOD_WITH_ID_REGEX.findAll(value).map { it.value.trim().trimStart(',').trim() }.toList()
        val mods = withIds.ifEmpty { value.split(',').map { it.trim() } }
        return mods.filter { it.isNotEmpty() }.distinct().take(MAX_DETAILS)
    }

    private fun parseDescription(text: String): String? {
        return DESCRIPTION_REGEX.find(text)?.groupValues?.get(1)?.trim()?.take(MAX_EVIDENCE_LENGTH)
    }

    //endregion

    //region helper pencocok

    /** Cocok bila baris mengandung salah satu kata kunci (peka huruf besar/kecil) */
    private fun keywords(vararg needles: String): Matcher = { line, _ ->
        if (needles.any { line.contains(it) }) Match() else null
    }

    /**
     * Cocok bila [regex] ditemukan di baris.
     * @param requires kata yang wajib ada di baris, sebagai penyaring cepat sebelum regex dijalankan
     */
    private fun pattern(
        regex: Regex,
        requires: String? = null,
        build: (MatchResult) -> Match? = { Match() }
    ): Matcher = { line, _ ->
        if (requires != null && !line.contains(requires)) null else regex.find(line)?.let(build)
    }

    private fun String.shorten(max: Int = 120) = trim().let { if (it.length > max) it.take(max) + "…" else it }

    //endregion

    //region aturan Fabric / Forge

    private val FABRIC_MISSING = Regex("""Mod '([^']+)' \(([^)]+)\).*?requires (.*?),? which is missing""")
    private val FABRIC_DEP_NAME = Regex("""'([^']+)' \(([^)]+)\)\s*$""")
    private val FABRIC_WRONG_VERSION = Regex(
        """Mod '([^']+)' \(([^)]+)\).*?requires (.*?) of (?:mod )?'([^']+)' \(([^)]+)\), but only the wrong version is present: ([^!\s]+)"""
    )
    private val FABRIC_CONFLICT = Regex(
        """Mod '([^']+)' \(([^)]+)\).*?\b(?:conflicts with|is incompatible with|breaks)\b.*?'([^']+)' \(([^)]+)\)"""
    )
    private val FORGE_DEPENDENCY = Regex(
        """Mod ID: '([^']+)', Requested by: '([^']+)', Expected range: '([^']*)', Actual version: '([^']*)'"""
    )
    private val NEOFORGE_REQUIRES = Regex("""\bMod (\w[\w.\-]*) requires (\w[\w.\-]*) (\S+)(?: or above)?""")

    private val LOADER_IDS = setOf(
        "forge", "neoforge", "fabricloader", "fabric-loader", "fabric_loader",
        "quilt_loader", "quilt-loader", "fml", "javafml"
    )

    private fun isMinecraft(id: String) = id.equals("minecraft", ignoreCase = true)
    private fun isLoader(id: String) = id.lowercase() in LOADER_IDS
    private fun isJava(id: String) = id.equals("java", ignoreCase = true)

    /** Pesan Fabric: "…requires <syarat> of mod 'X' (x), but only the wrong version is present: Y!" */
    private fun fabricWrongVersion(accept: (depId: String) -> Boolean): Matcher =
        pattern(FABRIC_WRONG_VERSION, requires = "wrong version") { m ->
            val (mod, _, required, depName, depId, found) = m.destructured
            when {
                !accept(depId) -> null
                isJava(depId) -> Regex("""\d+""").find(required)?.value?.let { Match(args = listOf(it)) }
                else -> Match(detail = "$mod → $depName: $required ≠ $found".shorten(160))
            }
        }

    //endregion

    private val CLASS_FILE_VERSION = Regex("""class file version (\d+)\.\d+""")
    private val OLD_MAJOR_MINOR = Regex("""Unsupported major\.minor version (\d+)\.\d+""")
    private val JAR_NAME = Regex("""([^\s/\\]+\.jar)""")
    private val MIXIN_MOD = Regex("""Mixin apply for mod (\S+) failed""")
    private val ENTRYPOINT_FABRIC = Regex(
        """Could not execute entrypoint stage '([^']+)' due to errors, provided by '([^']+)'"""
    )
    private val FORGE_CAUGHT = Regex("""Caught exception from (.+?) \(([\w.\-]+)\)""")
    private val CLASS_ERROR = Regex(
        """java\.lang\.(NoSuchMethodError|NoSuchFieldError|NoClassDefFoundError|AbstractMethodError|IncompatibleClassChangeError): ?(.*)"""
    )
    private val CLASS_NOT_FOUND = Regex(
        """(?:Caused by: |Exception in thread "[^"]*" )java\.lang\.ClassNotFoundException: (.*)"""
    )
    private val PROBLEMATIC_LIB = Regex("""([\w.\-]+\.so[\w.]*)""")
    private val GRAPHICS_LIB = Regex(
        """(?i)libGLES|libEGL|libmali|libvulkan|libgsl|libadreno|libglapi|libOpenglSystemCommon|virgl|zink|gl4es|mobileglues|libOSMesa|angle|libPVR|libIMGegl|libq3d|vulkan"""
    )
    private val UNRECOGNIZED_OPTION = Regex(
        """Unrecognized (?:VM )?option:? '?([^'\s]+)'?|Improperly specified VM option '([^']+)'"""
    )
    private val UNSATISFIED_LINK = Regex(
        """UnsatisfiedLinkError: (.*)|Failed to locate library: (\S+)|Failed to load (?:native )?library (\S+)"""
    )
    private val PERMISSION_FILE = Regex("""FileNotFoundException: (.*?) \((?:Permission denied|Operation not permitted)\)""")

    /** Aturan, diurutkan dari yang paling spesifik. Urutan ini juga dipakai sebagai tie-breaker hasil. */
    private val RULES: List<Rule> = listOf(
        //region memori
        Rule(
            CrashCause.OUT_OF_MEMORY_NATIVE,
            matcher = pattern(
                Regex(
                    """OutOfMemoryError: (?:unable to create (?:new )?native thread|Direct buffer memory|Cannot allocate memory|Map failed)""" +
                        """|Native memory allocation \((?:mmap|malloc)\) failed|There is insufficient memory for the Java Runtime Environment|Out of Memory Error \("""
                )
            )
        ),
        Rule(
            CrashCause.OUT_OF_MEMORY_METASPACE,
            matcher = pattern(Regex("""OutOfMemoryError: (?:Metaspace|Compressed class space)"""))
        ),
        Rule(
            CrashCause.OUT_OF_MEMORY,
            matcher = pattern(
                Regex("""OutOfMemoryError: (?:Java heap space|GC overhead limit exceeded|Requested array size)|OutOfMemoryError\s*$""")
            )
        ),
        Rule(
            CrashCause.GPU_OUT_OF_MEMORY,
            matcher = pattern(
                Regex("""GL_OUT_OF_MEMORY|\b1285: Out of memory|OpenGL error: ?1285|GL error 1285|Out of video memory|Failed to allocate (?:GPU|video) memory""")
            )
        ),
        //endregion

        //region Java / JVM
        Rule(
            CrashCause.JAVA_TOO_OLD,
            matcher = { line, _ ->
                val classVersion = if (line.contains("class file version")) CLASS_FILE_VERSION.find(line) else null
                val oldStyle = if (classVersion == null && line.contains("major.minor")) OLD_MAJOR_MINOR.find(line) else null
                val major = (classVersion ?: oldStyle)?.groupValues?.get(1)?.toIntOrNull()?.minus(44)
                if (major != null && major >= 1) Match(args = listOf(major.toString())) else null
            }
        ),
        Rule(CrashCause.JAVA_TOO_OLD, matcher = fabricWrongVersion { isJava(it) }),
        Rule(
            CrashCause.JAVA_TOO_NEW,
            matcher = pattern(
                Regex("""ClassLoaders[$]AppClassLoader cannot be cast to class java\.net\.URLClassLoader|InaccessibleObjectException|does not "opens""")
            )
        ),
        Rule(
            CrashCause.JVM_INIT_FAILED,
            matcher = { line, next ->
                when {
                    line.contains("Error occurred during initialization of VM") ->
                        Match(detail = next?.trim()?.takeIf { it.isNotEmpty() }?.shorten())
                    line.contains("Could not reserve enough space") ||
                        line.contains("Invalid maximum heap size") ||
                        line.contains("Invalid initial heap size") ||
                        line.contains("Initial heap size set to a larger value than the maximum heap size") ||
                        line.contains("The specified size exceeds the maximum representable size") -> Match()
                    else -> null
                }
            }
        ),
        Rule(
            CrashCause.INVALID_JVM_ARGUMENT,
            collectsDetails = true,
            matcher = pattern(UNRECOGNIZED_OPTION) { m ->
                Match(detail = (m.groupValues[1].ifEmpty { m.groupValues[2] }).shorten())
            }
        ),
        Rule(
            CrashCause.NATIVE_CRASH_GRAPHICS,
            collectsDetails = true,
            matcher = { line, next ->
                if (!line.contains("Problematic frame")) null else {
                    val lib = next?.let { PROBLEMATIC_LIB.find(it)?.value }
                    if (lib != null && GRAPHICS_LIB.containsMatchIn(lib)) Match(detail = lib) else null
                }
            }
        ),
        Rule(
            CrashCause.NATIVE_CRASH_GENERIC,
            collectsDetails = true,
            matcher = { line, next ->
                when {
                    line.contains("Problematic frame") -> {
                        val lib = next?.let { PROBLEMATIC_LIB.find(it)?.value }
                        if (lib != null && !GRAPHICS_LIB.containsMatchIn(lib)) Match(detail = lib) else null
                    }
                    line.contains("A fatal error has been detected by the Java Runtime Environment") -> Match()
                    else -> null
                }
            }
        ),
        Rule(
            CrashCause.NATIVE_LIBRARY_ERROR,
            collectsDetails = true,
            matcher = pattern(UNSATISFIED_LINK) { m ->
                Match(detail = m.groupValues.drop(1).firstOrNull { it.isNotEmpty() }?.shorten())
            }
        ),
        //endregion

        //region mod & loader
        Rule(
            CrashCause.MOD_MISSING_DEPENDENCY,
            collectsDetails = true,
            matcher = pattern(FABRIC_MISSING, requires = "which is missing") { m ->
                val mod = m.groupValues[1]
                val dep = FABRIC_DEP_NAME.find(m.groupValues[3])?.let { "${it.groupValues[1]} (${it.groupValues[2]})" }
                    ?: m.groupValues[3]
                Match(detail = "$mod → $dep".shorten(160))
            }
        ),
        Rule(
            CrashCause.MOD_MISSING_DEPENDENCY,
            collectsDetails = true,
            matcher = pattern(FORGE_DEPENDENCY, requires = "Actual version") { m ->
                val (modId, requestedBy, _, actual) = m.destructured
                if (actual == "[MISSING]") Match(detail = "$requestedBy → $modId") else null
            }
        ),
        Rule(
            CrashCause.MOD_MISSING_DEPENDENCY,
            collectsDetails = true,
            matcher = { line, next ->
                //NeoForge: "Mod X requires Y 1.0 or above" lalu "Currently, Y is not installed"
                if (!line.contains(" requires ") || next?.contains("is not installed") != true) null else {
                    NEOFORGE_REQUIRES.find(line)?.let { m ->
                        Match(detail = "${m.groupValues[1]} → ${m.groupValues[2]}")
                    }
                }
            }
        ),
        Rule(
            CrashCause.MOD_VERSION_MISMATCH,
            collectsDetails = true,
            matcher = fabricWrongVersion { depId -> !isMinecraft(depId) && !isLoader(depId) && !isJava(depId) }
        ),
        Rule(
            CrashCause.MOD_VERSION_MISMATCH,
            collectsDetails = true,
            matcher = pattern(FORGE_DEPENDENCY, requires = "Actual version") { m ->
                val (modId, requestedBy, expected, actual) = m.destructured
                if (actual == "[MISSING]" || isMinecraft(modId) || isLoader(modId)) null
                else Match(detail = "$requestedBy → $modId: $expected ≠ $actual".shorten(160))
            }
        ),
        Rule(
            CrashCause.WRONG_MINECRAFT_VERSION,
            collectsDetails = true,
            matcher = fabricWrongVersion { isMinecraft(it) }
        ),
        Rule(
            CrashCause.WRONG_MINECRAFT_VERSION,
            collectsDetails = true,
            matcher = pattern(FORGE_DEPENDENCY, requires = "Actual version") { m ->
                val (modId, requestedBy, expected, actual) = m.destructured
                if (isMinecraft(modId) && actual != "[MISSING]") {
                    Match(detail = "$requestedBy → Minecraft: $expected ≠ $actual".shorten(160))
                } else null
            }
        ),
        Rule(
            CrashCause.WRONG_LOADER_VERSION,
            collectsDetails = true,
            matcher = fabricWrongVersion { isLoader(it) }
        ),
        Rule(
            CrashCause.WRONG_LOADER_VERSION,
            collectsDetails = true,
            matcher = pattern(FORGE_DEPENDENCY, requires = "Actual version") { m ->
                val (modId, requestedBy, expected, actual) = m.destructured
                if (isLoader(modId) && actual != "[MISSING]") {
                    Match(detail = "$requestedBy → $modId: $expected ≠ $actual".shorten(160))
                } else null
            }
        ),
        Rule(
            CrashCause.MOD_DUPLICATE,
            matcher = pattern(Regex("""(?i)duplicate[ _-]?mods?"""))
        ),
        Rule(
            CrashCause.MOD_CONFLICT,
            collectsDetails = true,
            matcher = pattern(FABRIC_CONFLICT) { m ->
                Match(detail = "${m.groupValues[1]} ↔ ${m.groupValues[3]}".shorten(160))
            }
        ),
        Rule(
            CrashCause.INVALID_MOD_FILE,
            collectsDetails = true,
            matcher = pattern(
                Regex("""(?i)invalid mod file|InvalidModFileException|missing (?:the )?mods\.toml|no mods\.toml|Failed to find mods\.toml|not a valid mod file""")
            ).withJarDetail()
        ),
        Rule(
            CrashCause.MOD_MIXIN_FAILURE,
            collectsDetails = true,
            matcher = { line, _ ->
                val named = if (line.contains("Mixin apply for mod")) MIXIN_MOD.find(line) else null
                when {
                    named != null -> Match(detail = named.groupValues[1])
                    line.contains("MixinApplyError") ||
                        line.contains("MixinTransformerError") ||
                        line.contains("InvalidInjectionException") ||
                        line.contains("InvalidMixinException") ||
                        line.contains("MixinInitialisationError") ||
                        line.contains("Critical injection failure") ||
                        (line.contains("@Mixin target") && line.contains("was not found")) -> Match()
                    else -> null
                }
            }
        ),
        Rule(
            CrashCause.MOD_ENTRYPOINT_ERROR,
            collectsDetails = true,
            matcher = { line, _ ->
                val fabric = if (line.contains("Could not execute entrypoint")) ENTRYPOINT_FABRIC.find(line) else null
                val forge = if (fabric == null && line.contains("Caught exception from")) FORGE_CAUGHT.find(line) else null
                when {
                    fabric != null -> Match(detail = fabric.groupValues[2])
                    forge != null -> Match(detail = "${forge.groupValues[1]} (${forge.groupValues[2]})".shorten())
                    else -> null
                }
            }
        ),
        Rule(
            CrashCause.MOD_CLASS_MISMATCH,
            collectsDetails = true,
            matcher = { line, _ ->
                val m = if (line.contains("java.lang.")) CLASS_ERROR.find(line) else null
                val notFound = if (m == null && line.contains("ClassNotFoundException")) CLASS_NOT_FOUND.find(line) else null
                when {
                    m != null -> Match(detail = m.groupValues[2].shorten().ifEmpty { m.groupValues[1] })
                    notFound != null -> Match(detail = notFound.groupValues[1].shorten())
                    else -> null
                }
            }
        ),
        Rule(
            CrashCause.MOD_LOADING_ERROR,
            matcher = keywords(
                "Incompatible mod set!",
                "ModLoadingException",
                "LoadingFailedException",
                "ModResolutionException",
                "Failed to start Fabric",
                "FormattedException",
                "Mod resolution failed",
                "Mod Loading has failed",
                "Mod loading has failed"
            )
        ),
        //endregion

        //region grafis
        Rule(
            CrashCause.GRAPHICS_INIT_FAILED,
            matcher = pattern(
                Regex(
                    """GLFW error (?:65542|65543|65545)\b|Failed to create (?:the )?window|failed to initialize GLFW|Pixel format not accelerated""" +
                        """|The driver does not appear to support OpenGL|Couldn't set pixel format|No OpenGL (?:support|context)""" +
                        """|OpenGL (?:ES )?[\d.]+ (?:is )?(?:required|not supported)|Failed to (?:create|make) (?:the )?(?:EGL|GL) ?context|eglCreateContext|eglMakeCurrent"""
                )
            )
        ),
        Rule(
            CrashCause.SHADER_ERROR,
            matcher = pattern(
                Regex(
                    """(?i)failed to compile shader|error compiling shader|shader compile(?:r| error)|could(?: not|n't) (?:compile|link) (?:the )?(?:shader|program)""" +
                        """|ShaderLinkError|shader program linking failed|GLSL compile|glLinkProgram failed|failed to link program|failed to create shader"""
                )
            )
        ),
        //endregion

        //region file & penyimpanan
        Rule(
            CrashCause.MAIN_CLASS_MISSING,
            matcher = pattern(
                Regex(
                    """Could not find or load main class|Error: Unable to initialize main class|Could not find the main class""" +
                        """|ClassNotFoundException: (?:net\.minecraft\.client\.main\.Main|net\.minecraft\.launchwrapper\.Launch""" +
                        """|net\.fabricmc\.loader\.impl\.launch\.knot\.KnotClient|org\.quiltmc\.loader\.impl\.launch\.knot\.KnotClient""" +
                        """|cpw\.mods\.bootstraplauncher\.BootstrapLauncher)"""
                )
            )
        ),
        Rule(
            CrashCause.CORRUPT_FILE,
            collectsDetails = true,
            matcher = pattern(
                Regex(
                    """ZipException|zip END header not found|invalid LOC header|invalid CEN header|error in opening zip file""" +
                        """|Invalid or corrupt jarfile|Unexpected end of ZLIB input stream|ZipFile invalid"""
                )
            ).withJarDetail()
        ),
        Rule(CrashCause.STORAGE_FULL, matcher = keywords("No space left on device")),
        Rule(
            CrashCause.STORAGE_PERMISSION,
            collectsDetails = true,
            matcher = { line, _ ->
                val file = if (line.contains("Permission denied") || line.contains("Operation not permitted")) {
                    PERMISSION_FILE.find(line)
                } else null
                when {
                    file != null -> Match(detail = file.groupValues[1].shorten())
                    line.contains("AccessDeniedException") ||
                        line.contains("(Permission denied)") ||
                        line.contains("Read-only file system") -> Match()
                    else -> null
                }
            }
        ),
        Rule(
            CrashCause.WORLD_DATA_ERROR,
            matcher = pattern(
                Regex(
                    """Chunk file at .* is in the wrong location|Failed to load datapacks|Exception reading .*\.mca""" +
                        """|Failed to (?:read|load) (?:chunk|region|level)|Failed to (?:read|parse) level\.dat|Error loading world|Couldn't load chunk"""
                )
            )
        )
        //endregion
    )

    /** Urutan aturan, dipakai sebagai tie-breaker saat mengurutkan temuan dengan tingkat keyakinan sama */
    internal val RULE_ORDER: Map<CrashCause, Int> = buildMap {
        RULES.forEachIndexed { index, rule -> putIfAbsent(rule.cause, index) }
        CrashCause.entries.forEach { putIfAbsent(it, Int.MAX_VALUE) }
    }

    /** Menambahkan nama file .jar pada baris sebagai rincian, bila ada */
    private fun Matcher.withJarDetail(): Matcher = { line, next ->
        this(line, next)?.let { match ->
            val jar = JAR_NAME.find(line)?.groupValues?.get(1)
            if (jar != null) Match(args = match.args, detail = jar.shorten()) else match
        }
    }
}
