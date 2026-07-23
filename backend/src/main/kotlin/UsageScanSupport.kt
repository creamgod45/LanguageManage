package cg.creamgod45

import cg.creamgod45.localization.HARD_MAX_ENTRIES_PER_FILE
import cg.creamgod45.localization.HARD_MAX_ENTRIES_PER_SCHEME
import cg.creamgod45.localization.HARD_MAX_LANGUAGE_FILE_KB
import cg.creamgod45.localization.HARD_MAX_LANGUAGE_SCHEME_MB
import cg.creamgod45.localization.LanguageEntryDto
import cg.creamgod45.localization.MAX_USAGE_EXCLUSIONS
import cg.creamgod45.localization.UsageScanSettingsDto
import cg.creamgod45.localization.UsageLocationDto
import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.CancellationException
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
    private const val MAX_EXCLUSION_LENGTH = 200
    private const val MAX_USAGE_LOCATION_RECORDS = 250_000
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
        require(exclusions.size <= MAX_USAGE_EXCLUSIONS) { backendMessage("usage.exclusion.count", MAX_USAGE_EXCLUSIONS) }
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
        cancellationCheck: () -> Unit = {},
    ): Map<String, Int> = scan(root, entries, languageFiles, settings, cancellationCheck).counts

    fun scan(
        root: Path,
        entries: List<LanguageEntryDto>,
        languageFiles: List<String>,
        settings: UsageScanSettingsDto,
        cancellationCheck: () -> Unit = {},
        fileProcessed: (Path, Int) -> Unit = { _, _ -> },
    ): UsageScanResult {
        cancellationCheck()
        val counts = entries.associate { it.id to 0 }.toMutableMap()
        val locationCounts = linkedMapOf<UsageLocationKey, Int>()
        var locationsTruncated = false
        val needleOwners = mutableMapOf<String, MutableSet<String>>()
        entries.forEach { entry ->
            cancellationCheck()
            setOf(entry.key, if (entry.namespace.isBlank()) entry.key else "${entry.namespace}.${entry.key}").forEach { needle ->
                needleOwners.getOrPut(needle) { linkedSetOf() } += entry.id
            }
        }
        val patterns = settings.regexPatterns.map(::Regex)
        var visitedFiles = 0
        try {
            walkCandidateFiles(root, languageFiles, settings, cancellationCheck) { file ->
                visitedFiles++
                try {
                    val content = readTextCancellable(file, cancellationCheck)
                    val occurrences = extractCandidateOccurrences(content, patterns, cancellationCheck)
                    val modifiedAt = Files.getLastModifiedTime(file).toMillis()
                    occurrences.forEach { occurrence ->
                        cancellationCheck()
                        needleOwners[occurrence.candidate].orEmpty().forEach { id ->
                            counts[id] = counts.getValue(id) + 1
                            val key = UsageLocationKey(id, file.toString(), occurrence.range.first, modifiedAt)
                            if (key in locationCounts || locationCounts.size < MAX_USAGE_LOCATION_RECORDS) {
                                locationCounts[key] = (locationCounts[key] ?: 0) + 1
                            } else {
                                locationsTruncated = true
                            }
                        }
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    log.debug("Unable to scan source file '$file'", error)
                } finally {
                    fileProcessed(file, visitedFiles)
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            log.debug("Usage scan stopped early", error)
        }
        log.info("Usage scan checked $visitedFiles source files for ${entries.size} localization entries")
        return UsageScanResult(
            counts,
            locationCounts.map { (location, count) ->
                UsageLocationDto(
                    location.entryId,
                    location.filePath,
                    location.offset,
                    location.sourceModifiedAtEpochMs,
                    occurrenceCount = count,
                )
            },
            locationsTruncated,
        )
    }

    fun sourceFileCount(
        root: Path,
        languageFiles: List<String>,
        settings: UsageScanSettingsDto,
        cancellationCheck: () -> Unit = {},
    ): Int {
        var count = 0
        try {
            walkCandidateFiles(root, languageFiles, settings, cancellationCheck) { count++ }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            log.debug("Usage scan planning stopped early", error)
        }
        return count
    }

    private fun walkCandidateFiles(
        root: Path,
        languageFiles: List<String>,
        settings: UsageScanSettingsDto,
        cancellationCheck: () -> Unit,
        visit: (Path) -> Unit,
    ) {
        val scanRoot = root.toRealPath()
        val ignoredDirectories = settings.excludedDirectories.map { it.replace('\\', '/').trim('/').lowercase() }.toSet()
        val normalizedLanguageFiles =
            languageFiles.mapTo(hashSetOf()) {
                runCatching { SafeLanguageFileAccess.validate(it).toString() }.getOrDefault(it)
            }
        Files.walkFileTree(
            scanRoot,
            object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(
                    dir: Path,
                    attrs: BasicFileAttributes,
                ): FileVisitResult {
                    cancellationCheck()
                    if (dir == scanRoot) return FileVisitResult.CONTINUE
                    val relative = scanRoot.relativize(dir).joinToString("/") { it.toString() }.lowercase()
                    val excluded =
                        ignoredDirectories.any { item ->
                            if ('/' in item) relative == item || relative.startsWith("$item/") else dir.fileName.toString().lowercase() == item
                        }
                    return if (excluded) FileVisitResult.SKIP_SUBTREE else FileVisitResult.CONTINUE
                }

                override fun visitFile(
                    file: Path,
                    attrs: BasicFileAttributes,
                ): FileVisitResult {
                    cancellationCheck()
                    if (attrs.isRegularFile && file.toString() !in normalizedLanguageFiles) visit(file)
                    return FileVisitResult.CONTINUE
                }
            },
        )
    }

    private fun extractCandidateOccurrences(
        line: String,
        patterns: List<Regex>,
        cancellationCheck: () -> Unit,
    ): Set<CandidateOccurrence> =
        buildSet {
            patterns.forEach { pattern ->
                cancellationCheck()
                pattern.findAll(line).forEach { match ->
                    cancellationCheck()
                    val named = runCatching { match.groups["key"] }.getOrNull()
                    val capturedGroup = named ?: (1 until match.groups.size).firstNotNullOfOrNull { match.groups[it] }
                    val captured = capturedGroup?.value ?: match.value
                    if (captured.length in 1..256) {
                        add(CandidateOccurrence(captured, capturedGroup?.range ?: match.range))
                    }
                }
            }
        }

    private fun readTextCancellable(
        file: Path,
        cancellationCheck: () -> Unit,
    ): String =
        Files.newInputStream(file).reader(StandardCharsets.UTF_8).buffered().use { reader ->
            val buffer = CharArray(DEFAULT_BUFFER_SIZE)
            buildString {
                while (true) {
                    cancellationCheck()
                    val count = reader.read(buffer)
                    if (count < 0) break
                    append(buffer, 0, count)
                }
            }
        }

    private data class CandidateOccurrence(
        val candidate: String,
        val range: IntRange,
    )

    private data class UsageLocationKey(
        val entryId: String,
        val filePath: String,
        val offset: Int,
        val sourceModifiedAtEpochMs: Long,
    )
}

internal data class UsageScanResult(
    val counts: Map<String, Int>,
    val locations: List<UsageLocationDto>,
    val locationsTruncated: Boolean,
)
