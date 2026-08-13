package cg.creamgod45

import cg.creamgod45.localization.HardcodedAnalysisProgressDto
import cg.creamgod45.localization.HardcodedAnalysisResultDto
import cg.creamgod45.localization.HardcodedAnalysisStage
import cg.creamgod45.localization.HardcodedAnalysisStatisticsDto
import cg.creamgod45.localization.HardcodedTextCandidateDto
import cg.creamgod45.localization.HardcodedTextConfidence
import cg.creamgod45.localization.LanguageEntryDto
import cg.creamgod45.localization.UsageScanSettingsDto
import kotlinx.coroutines.CancellationException
import java.nio.charset.StandardCharsets
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/** Incremental, language-agnostic detector for likely user-facing quoted literals. */
internal class HardcodedTextAnalysisSupport {
    companion object {
        private const val MAX_SOURCE_FILE_BYTES = 5L * 1024 * 1024
        private const val MAX_LINE_LENGTH = 32_768
        private const val MAX_CANDIDATES = 100_000
        private const val MAX_DISCOVERED_FILES = 200_000
        private val QUOTED_LITERAL = Regex("(?s)([\\\"'`])((?:\\\\.|(?!\\1).){2,500})\\1")
    }

    private data class FileFingerprint(val modifiedAt: Long, val size: Long)
    private data class CachedFile(val fingerprint: FileFingerprint, val items: List<HardcodedTextCandidateDto>, val skipped: Boolean)

    private var cacheScope = ""
    private val fileCache = mutableMapOf<String, CachedFile>()
    private var lastResult: HardcodedAnalysisResultDto? = null

    fun analyze(
        schemeId: String,
        root: Path,
        languageFiles: List<String>,
        entries: List<LanguageEntryDto>,
        settings: UsageScanSettingsDto,
        force: Boolean,
        cancellationCheck: () -> Unit = {},
        progress: (HardcodedAnalysisProgressDto) -> Unit = {},
    ): HardcodedAnalysisResultDto {
        cancellationCheck()
        val scanRoot = root.toRealPath()
        val entryValueFingerprint = entries.fold(1) { hash, entry -> 31 * hash + entry.value.hashCode() }
        val scope =
            listOf(
                schemeId,
                scanRoot,
                languageFiles.sorted().hashCode(),
                entryValueFingerprint,
                settings.regexPatterns.joinToString("\u0001"),
                settings.excludedDirectories.joinToString("\u0001"),
            ).joinToString("\u0000")
        if (scope != cacheScope) {
            fileCache.clear()
            lastResult = null
            cacheScope = scope
        }
        if (!force) lastResult?.let { return it }

        val exclusions = UsagePathExclusions(scanRoot, settings.excludedDirectories)
        val managedFiles = languageFiles.mapNotNullTo(hashSetOf()) { runCatching { Path.of(it).toRealPath().toString() }.getOrNull() }
        val candidates = ArrayList<Path>()
        var discovered = 0
        var discoveryTruncated = false
        progress(HardcodedAnalysisProgressDto(schemeId, HardcodedAnalysisStage.DISCOVERING))
        Files.walkFileTree(
            scanRoot,
            object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                    cancellationCheck()
                    return if (dir != scanRoot && exclusions.excludesDirectory(dir)) FileVisitResult.SKIP_SUBTREE else FileVisitResult.CONTINUE
                }

                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    cancellationCheck()
                    discovered++
                    if (discovered % 128 == 0) {
                        progress(HardcodedAnalysisProgressDto(schemeId, HardcodedAnalysisStage.DISCOVERING, discoveredFiles = discovered, currentPath = relative(scanRoot, file)))
                    }
                    if (candidates.size >= MAX_DISCOVERED_FILES) {
                        discoveryTruncated = true
                        return FileVisitResult.TERMINATE
                    }
                    if (attrs.isRegularFile && !exclusions.excludesFile(file) && file.toString() !in managedFiles) candidates.add(file)
                    return FileVisitResult.CONTINUE
                }
            },
        )

        val knownPatterns = settings.regexPatterns.map(::Regex)
        val managedValues = entries.asSequence().map { it.value.trim() }.filter { it.length >= 2 }.toHashSet()
        val items = ArrayList<HardcodedTextCandidateDto>()
        var processed = 0
        var cacheHits = 0
        var skipped = 0
        var matchedFiles = 0
        var truncated = discoveryTruncated
        val livePaths = candidates.mapTo(hashSetOf()) { it.toString() }
        fileCache.keys.retainAll(livePaths)

        candidates.forEach { file ->
            cancellationCheck()
            if (items.size >= MAX_CANDIDATES) {
                truncated = true
                return@forEach
            }
            val fingerprint = runCatching { FileFingerprint(Files.getLastModifiedTime(file).toMillis(), Files.size(file)) }.getOrNull()
            val cached = fingerprint?.let { value -> fileCache[file.toString()]?.takeIf { it.fingerprint == value } }
            val scanned =
                if (cached != null) {
                    cacheHits++
                    cached
                } else {
                    scanFile(file, fingerprint, knownPatterns, managedValues, cancellationCheck).also { result ->
                        if (fingerprint != null) fileCache[file.toString()] = result
                    }
                }
            processed++
            if (scanned.skipped) skipped++
            if (scanned.items.isNotEmpty()) matchedFiles++
            val remaining = MAX_CANDIDATES - items.size
            items += scanned.items.take(remaining)
            if (scanned.items.size > remaining) truncated = true
            progress(
                HardcodedAnalysisProgressDto(
                    schemeId,
                    HardcodedAnalysisStage.SCANNING,
                    discoveredFiles = discovered,
                    processedFiles = processed,
                    totalFiles = candidates.size,
                    cachedFiles = cacheHits,
                    matchedFiles = matchedFiles,
                    currentPath = relative(scanRoot, file),
                ),
            )
        }
        val statistics =
            HardcodedAnalysisStatisticsDto(
                scannedFiles = processed,
                cachedFiles = cacheHits,
                skippedFiles = skipped,
                matchedFiles = matchedFiles,
                candidateLocations = items.size,
                uniqueTexts = items.asSequence().map { it.text }.distinct().count(),
                highConfidence = items.count { it.confidence == HardcodedTextConfidence.HIGH },
                mediumConfidence = items.count { it.confidence == HardcodedTextConfidence.MEDIUM },
                lowConfidence = items.count { it.confidence == HardcodedTextConfidence.LOW },
            )
        return HardcodedAnalysisResultDto(schemeId, items, statistics, truncated, System.currentTimeMillis()).also { lastResult = it }
    }

    private fun scanFile(
        file: Path,
        fingerprint: FileFingerprint?,
        knownPatterns: List<Regex>,
        managedValues: Set<String>,
        cancellationCheck: () -> Unit,
    ): CachedFile {
        if (fingerprint == null || fingerprint.size > MAX_SOURCE_FILE_BYTES) return CachedFile(fingerprint ?: FileFingerprint(0, 0), emptyList(), true)
        val found = mutableListOf<HardcodedTextCandidateDto>()
        return try {
            Files.newBufferedReader(file, StandardCharsets.UTF_8).useLines { lines ->
                lines.forEachIndexed { index, line ->
                    cancellationCheck()
                    if ('\u0000' in line || line.length > MAX_LINE_LENGTH) return CachedFile(fingerprint, emptyList(), true)
                    val knownRanges = knownPatterns.flatMap { pattern -> pattern.findAll(line).map(MatchResult::range).toList() }
                    QUOTED_LITERAL.findAll(line).forEach { match ->
                        cancellationCheck()
                        val value = match.groupValues[2].trim()
                        val range = match.range
                        if (knownRanges.none { it.first <= range.last && range.first <= it.last } && isCandidate(value)) {
                            val commentLine = line.trimStart().let { it.startsWith("//") || it.startsWith("#") || it.startsWith("/*") || it.startsWith("*") }
                            found +=
                                HardcodedTextCandidateDto(
                                    file.toString(),
                                    index + 1,
                                    range.first + 2,
                                    value,
                                    if (commentLine) HardcodedTextConfidence.LOW else confidence(value, managedValues),
                                )
                        }
                    }
                }
            }
            CachedFile(fingerprint, found, false)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            CachedFile(fingerprint, emptyList(), true)
        }
    }

    private fun isCandidate(value: String): Boolean =
        value.length in 2..500 && value.any(Char::isLetter) && !value.contains("://") && !value.startsWith("/")

    private fun confidence(value: String, managedValues: Set<String>): HardcodedTextConfidence =
        when {
            value in managedValues -> HardcodedTextConfidence.HIGH
            value.any { it.isWhitespace() } || value.any { Character.UnicodeScript.of(it.code) in setOf(Character.UnicodeScript.HAN, Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA, Character.UnicodeScript.HANGUL, Character.UnicodeScript.THAI) } -> HardcodedTextConfidence.MEDIUM
            else -> HardcodedTextConfidence.LOW
        }

    private fun relative(root: Path, file: Path): String = runCatching { root.relativize(file).joinToString("/") }.getOrDefault(file.fileName.toString())
}
