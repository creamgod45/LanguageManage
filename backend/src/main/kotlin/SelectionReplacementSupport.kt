package cg.creamgod45

import cg.creamgod45.localization.FileChangePreviewDto
import cg.creamgod45.localization.ReplacementTemplateRuleDto
import cg.creamgod45.localization.SelectionReplacementCandidateDto
import cg.creamgod45.localization.SelectionReplacementScanDto
import cg.creamgod45.localization.UsageScanSettingsDto
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import cg.creamgod45.LanguageManagerBackendBundle.message as backendMessage

internal object SelectionReplacementSupport {
    private const val MAX_SOURCE_FILE_BYTES = 5L * 1024 * 1024
    private const val MAX_SCANNED_FILES = 50_000
    private const val MAX_CANDIDATE_FILES = 2_000
    private const val MAX_RULES = 1_000
    private const val PLACEHOLDER = "%key%"

    fun scan(
        root: Path,
        languageFiles: List<String>,
        settings: UsageScanSettingsDto,
        selectedText: String,
        rawRules: List<ReplacementTemplateRuleDto>,
        cancellationCheck: () -> Unit = {},
    ): SelectionReplacementScanDto {
        val text = validateSelectedText(selectedText)
        val rules = validateRules(rawRules)
        val languagePaths = languageFiles.mapNotNullTo(hashSetOf()) { runCatching { Path.of(it).toRealPath() }.getOrNull() }
        val ignored = settings.excludedDirectories.map { it.replace('\\', '/').trim('/').lowercase() }.toSet()
        val candidates = mutableListOf<SelectionReplacementCandidateDto>()
        var scanned = 0
        var truncated = false
        val scanRoot = root.toRealPath()

        Files.walkFileTree(
            scanRoot,
            object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                    cancellationCheck()
                    if (dir == scanRoot) return FileVisitResult.CONTINUE
                    val relative = scanRoot.relativize(dir).joinToString("/") { it.toString() }.lowercase()
                    val excluded = ignored.any { item ->
                        if ('/' in item) relative == item || relative.startsWith("$item/")
                        else dir.fileName.toString().lowercase() == item
                    }
                    return if (excluded) FileVisitResult.SKIP_SUBTREE else FileVisitResult.CONTINUE
                }

                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    cancellationCheck()
                    if (!attrs.isRegularFile || file in languagePaths || matchingRule(file, rules) == null) return FileVisitResult.CONTINUE
                    scanned++
                    if (scanned > MAX_SCANNED_FILES) {
                        truncated = true
                        return FileVisitResult.TERMINATE
                    }
                    val content = readSource(file) ?: return FileVisitResult.CONTINUE
                    val count = literalOccurrenceCount(content, text)
                    if (count > 0) candidates += SelectionReplacementCandidateDto(file.toString(), count)
                    if (candidates.size >= MAX_CANDIDATE_FILES) {
                        truncated = true
                        return FileVisitResult.TERMINATE
                    }
                    return FileVisitResult.CONTINUE
                }
            },
        )
        return SelectionReplacementScanDto(candidates.sortedBy { it.filePath }, truncated)
    }

    fun previewFile(
        root: Path,
        languageFiles: List<String>,
        selectedText: String,
        replacementKey: String,
        rawRules: List<ReplacementTemplateRuleDto>,
        rawFilePath: String,
    ): FileChangePreviewDto {
        val text = validateSelectedText(selectedText)
        val key = TranslationInputValidation.key(replacementKey)
        val rules = validateRules(rawRules)
        val file = validateSourceFile(root, rawFilePath)
        require(languageFiles.none { runCatching { Path.of(it).toRealPath() == file }.getOrDefault(false) })
        val rule = matchingRule(file, rules) ?: error(backendMessage("selection.rule.no.match"))
        val before = readSource(file) ?: error(backendMessage("selection.file.not.text"))
        require(before.contains(text)) { backendMessage("selection.text.missing") }
        val replacement = rule.template.replace(PLACEHOLDER, key)
        val after = before.replace(text, replacement)
        return FileChangePreviewDto(file.toString(), before, after, sha256(before), editable = true)
    }

    fun validateRules(rawRules: List<ReplacementTemplateRuleDto>): List<ReplacementTemplateRuleDto> {
        require(rawRules.isNotEmpty() && rawRules.size <= MAX_RULES) { backendMessage("selection.rule.invalid") }
        return rawRules.map { rule ->
            val template = rule.template.trim()
            val suffix = rule.fileSuffix.trim()
            require(template.length in 1..512 && template.none(Char::isISOControl)) { backendMessage("selection.rule.invalid") }
            require(template.windowed(PLACEHOLDER.length).count { it == PLACEHOLDER } == 1) { backendMessage("selection.rule.invalid") }
            require(suffix.matches(Regex("\\.[A-Za-z0-9._-]{1,63}"))) { backendMessage("selection.rule.invalid") }
            ReplacementTemplateRuleDto(template, suffix)
        }
    }

    private fun validateSelectedText(raw: String): String {
        require(raw.length in 1..10_000 && raw.none { it == '\u0000' || (it.code < 32 && it !in "\n\r\t") }) {
            backendMessage("selection.text.invalid")
        }
        return raw
    }

    private fun matchingRule(path: Path, rules: List<ReplacementTemplateRuleDto>): ReplacementTemplateRuleDto? {
        val name = path.fileName.toString().lowercase()
        return rules.firstOrNull { name.endsWith(it.fileSuffix.lowercase()) }
    }

    private fun validateSourceFile(root: Path, raw: String): Path {
        require(raw.isNotBlank() && raw.length <= 4096 && raw.none(Char::isISOControl)) { backendMessage("selection.file.invalid") }
        val lower = raw.lowercase()
        require(!lower.contains("://") && !lower.startsWith("ldap:") && !lower.startsWith("file:")) { backendMessage("selection.file.invalid") }
        require(!lower.startsWith("\\\\.\\") && !lower.contains("globalroot")) { backendMessage("selection.file.invalid") }
        val scanRoot = root.toRealPath()
        val file = Path.of(raw).toAbsolutePath().normalize().toRealPath()
        require(file.startsWith(scanRoot) && Files.isRegularFile(file) && Files.size(file) <= MAX_SOURCE_FILE_BYTES) {
            backendMessage("selection.file.invalid")
        }
        return file
    }

    private fun readSource(path: Path): String? =
        runCatching {
            if (Files.size(path) > MAX_SOURCE_FILE_BYTES) return null
            val bytes = Files.readAllBytes(path)
            if (bytes.any { it == 0.toByte() }) return null
            StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        }.getOrNull()

    private fun literalOccurrenceCount(content: String, needle: String): Int {
        var count = 0
        var start = 0
        while (true) {
            val index = content.indexOf(needle, start)
            if (index < 0) return count
            count++
            start = index + needle.length
        }
    }

    private fun sha256(content: String): String =
        MessageDigest.getInstance("SHA-256").digest(content.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
