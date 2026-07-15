package cg.creamgod45

import cg.creamgod45.localization.LanguageEntryDto
import cg.creamgod45.localization.UsageScanSettingsDto
import cg.creamgod45.localization.HARD_MAX_ENTRIES_PER_FILE
import cg.creamgod45.localization.HARD_MAX_ENTRIES_PER_SCHEME
import cg.creamgod45.localization.HARD_MAX_LANGUAGE_FILE_KB
import cg.creamgod45.localization.HARD_MAX_LANGUAGE_SCHEME_MB
import com.intellij.openapi.diagnostic.Logger
import java.nio.charset.StandardCharsets
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import cg.creamgod45.LanguageManagerBackendBundle.message as backendMessage

internal object UsageScanSupport {
    private const val MAX_REGEX_PATTERNS = 20
    private const val MAX_REGEX_LENGTH = 512
    private const val MAX_EXCLUSIONS = 100
    private const val MAX_EXCLUSION_LENGTH = 200
    private const val MAX_LINE_LENGTH = 4096
    private const val MAX_FILES = 2000
    private const val MAX_FILE_BYTES = 512_000
    private val sourceExtensions =
        setOf(
            "php",
            "js",
            "ts",
            "tsx",
            "jsx",
            "vue",
            "java",
            "kt",
            "kts",
            "py",
            "rb",
            "go",
            "rs",
            "html",
            "twig",
        )
    private val log = Logger.getInstance(UsageScanSupport::class.java)

    fun normalize(settings: UsageScanSettingsDto): UsageScanSettingsDto {
        val basePath =
            settings.basePath.trim().let { raw ->
                if (raw.isEmpty()) "" else SafeLanguageFileAccess.validateDirectory(raw).toString()
            }
        val patterns =
            settings.regexPatterns
                .map(String::trim)
                .filter(String::isNotEmpty)
                .distinct()
        require(patterns.size in 1..MAX_REGEX_PATTERNS) { backendMessage("usage.regex.count", MAX_REGEX_PATTERNS) }
        patterns.forEach { pattern ->
            require(pattern.length <= MAX_REGEX_LENGTH && pattern.none(Char::isISOControl)) {
                backendMessage("usage.regex.length", MAX_REGEX_LENGTH)
            }
            runCatching { Regex(pattern) }.getOrElse {
                val detail =
                    (it.message ?: it.javaClass.simpleName)
                        .replace(Regex("[\u0000-\u0008\u000B\u000C\u000E-\u001F]"), "?")
                        .take(500)
                throw IllegalArgumentException(backendMessage("usage.regex.invalid", detail))
            }
        }
        val exclusions =
            settings.excludedDirectories
                .map { it.trim().replace('\\', '/').trim('/') }
                .filter(String::isNotEmpty)
                .distinct()
        require(exclusions.size <= MAX_EXCLUSIONS) { backendMessage("usage.exclusion.count", MAX_EXCLUSIONS) }
        exclusions.forEach { exclusion ->
            val segments = exclusion.split('/')
            require(
                exclusion.length <= MAX_EXCLUSION_LENGTH &&
                    exclusion.none(Char::isISOControl) &&
                    "://" !in exclusion && ':' !in exclusion &&
                    segments.none { it.isBlank() || it == "." || it == ".." },
            ) { backendMessage("usage.exclusion.invalid", exclusion) }
        }
        require(settings.maxLanguageFileKb in 1..HARD_MAX_LANGUAGE_FILE_KB) {
            backendMessage("load.limit.file.size", HARD_MAX_LANGUAGE_FILE_KB)
        }
        require(settings.maxLanguageSchemeMb in 1..HARD_MAX_LANGUAGE_SCHEME_MB) {
            backendMessage("load.limit.scheme.size", HARD_MAX_LANGUAGE_SCHEME_MB)
        }
        require(settings.maxEntriesPerFile in 1..HARD_MAX_ENTRIES_PER_FILE) {
            backendMessage("load.limit.file.entries", HARD_MAX_ENTRIES_PER_FILE)
        }
        require(settings.maxEntriesPerScheme in 1..HARD_MAX_ENTRIES_PER_SCHEME) {
            backendMessage("load.limit.scheme.entries", HARD_MAX_ENTRIES_PER_SCHEME)
        }
        require(settings.maxEntriesPerFile <= settings.maxEntriesPerScheme) {
            backendMessage("load.limit.entries.order")
        }
        return settings.copy(basePath = basePath, regexPatterns = patterns, excludedDirectories = exclusions)
    }

    fun counts(
        root: Path,
        entries: List<LanguageEntryDto>,
        languageFiles: List<String>,
        settings: UsageScanSettingsDto,
    ): Map<String, Int> {
        val scanRoot = root.toRealPath()
        val counts = entries.associate { it.id to 0 }.toMutableMap()
        val needleOwners = mutableMapOf<String, MutableSet<String>>()
        entries.forEach { entry ->
            setOf(entry.key, if (entry.namespace.isBlank()) entry.key else "${entry.namespace}.${entry.key}").forEach { needle ->
                needleOwners.getOrPut(needle) { linkedSetOf() } += entry.id
            }
        }
        val ignoredDirectories = settings.excludedDirectories.map { it.replace('\\', '/').trim('/').lowercase() }.toSet()
        val patterns = settings.regexPatterns.map(::Regex)
        val normalizedLanguageFiles =
            languageFiles.mapTo(hashSetOf()) {
                runCatching { SafeLanguageFileAccess.validate(it).toString() }.getOrDefault(it)
            }
        var visitedFiles = 0
        runCatching {
            Files.walkFileTree(
                scanRoot,
                object : SimpleFileVisitor<Path>() {
                    override fun preVisitDirectory(
                        dir: Path,
                        attrs: BasicFileAttributes,
                    ): FileVisitResult {
                        if (dir == scanRoot) return FileVisitResult.CONTINUE
                        val relative = scanRoot.relativize(dir).joinToString("/") { it.toString() }.lowercase()
                        val excluded =
                            ignoredDirectories.any { item ->
                                if ('/' in item) {
                                    relative == item || relative.startsWith("$item/")
                                } else {
                                    dir.fileName.toString().lowercase() == item
                                }
                            }
                        return if (excluded) FileVisitResult.SKIP_SUBTREE else FileVisitResult.CONTINUE
                    }

                    override fun visitFile(
                        file: Path,
                        attrs: BasicFileAttributes,
                    ): FileVisitResult {
                        if (visitedFiles >= MAX_FILES) return FileVisitResult.TERMINATE
                        val extension =
                            file.fileName
                                .toString()
                                .substringAfterLast('.', "")
                                .lowercase()
                        if (extension !in sourceExtensions || attrs.size() > MAX_FILE_BYTES || file.toString() in normalizedLanguageFiles) {
                            return FileVisitResult.CONTINUE
                        }
                        visitedFiles++
                        runCatching {
                            Files.newBufferedReader(file, StandardCharsets.UTF_8).useLines { lines ->
                                lines.forEach { rawLine ->
                                    val candidates = extractCandidates(rawLine.take(MAX_LINE_LENGTH), patterns)
                                    val matchedIds = candidates.flatMapTo(linkedSetOf()) { needleOwners[it].orEmpty() }
                                    matchedIds.forEach { id -> counts[id] = counts.getValue(id) + 1 }
                                }
                            }
                        }
                        return FileVisitResult.CONTINUE
                    }
                },
            )
        }.onFailure { log.debug("Usage scan stopped early", it) }
        log.info("Usage scan checked $visitedFiles source files for ${entries.size} localization entries")
        return counts
    }

    private fun extractCandidates(
        line: String,
        patterns: List<Regex>,
    ): Set<String> =
        buildSet {
            patterns.forEach { pattern ->
                pattern.findAll(line).forEach { match ->
                    val named = runCatching { match.groups["key"]?.value }.getOrNull()
                    val captured =
                        named ?: (1 until match.groups.size)
                            .firstNotNullOfOrNull { match.groups[it]?.value } ?: match.value
                    if (captured.length in 1..256) add(captured)
                }
            }
        }
}
