package cg.creamgod45

import cg.creamgod45.localization.DynamicSourceGroupDto
import cg.creamgod45.localization.DynamicSourceRuleDto
import cg.creamgod45.localization.DynamicMarkerSyntax
import cg.creamgod45.localization.MAX_DYNAMIC_SOURCE_GROUPS_PER_RULE
import cg.creamgod45.localization.MAX_DYNAMIC_SOURCE_KEYS_PER_GROUP
import cg.creamgod45.localization.MAX_DYNAMIC_SOURCE_RULES
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.CancellationException

internal data class DynamicKeyOccurrence(
    val key: String,
    val offset: Int,
)

internal object DynamicSourceSupport {
    private const val MAX_CONVERSION_SOURCE_BYTES = 10L * 1024 * 1024

    fun markerOccurrences(content: String): List<DynamicKeyOccurrence> =
        DynamicMarkerSyntax.findAll(content).flatMap { marker ->
            marker.groups.flatMap { group -> group.keys.map { key -> DynamicKeyOccurrence(key, marker.startOffset) } }
        }

    fun normalizeRules(
        rules: List<DynamicSourceRuleDto>,
        root: Path,
    ): List<DynamicSourceRuleDto> {
        require(rules.size <= MAX_DYNAMIC_SOURCE_RULES)
        val normalizedRoot = root.toRealPath()
        return rules.map { rule ->
            require(rule.id.length in 1..100 && rule.id.none(Char::isISOControl))
            require(rule.method == "dynamic")
            require(rule.line in 1..10_000_000 && rule.column in 1..10_000_000)
            require(rule.groups.size in 1..MAX_DYNAMIC_SOURCE_GROUPS_PER_RULE)
            val file = safeSourceFile(rule.filePath, normalizedRoot)
            val groups =
                rule.groups.map { group ->
                    val name = group.name.trim()
                    require(name.matches(Regex("[A-Za-z][A-Za-z0-9_-]{0,63}")))
                    val keys = group.keys.map(String::trim).map(TranslationInputValidation::key).distinct()
                    require(keys.size in 1..MAX_DYNAMIC_SOURCE_KEYS_PER_GROUP)
                    DynamicSourceGroupDto(name, keys)
                }
            rule.copy(filePath = file.toString(), groups = groups)
        }.distinctBy { it.id }
    }

    fun ruleOccurrences(
        rules: List<DynamicSourceRuleDto>,
        root: Path,
        cancellationCheck: () -> Unit = {},
    ): List<Triple<Path, DynamicKeyOccurrence, Long>> {
        require(rules.size <= MAX_DYNAMIC_SOURCE_RULES)
        return rules.flatMap { rawRule ->
            cancellationCheck()
            try {
                val rule = normalizeRules(listOf(rawRule), root).single()
                val path = Path.of(rule.filePath)
                val content = Files.readString(path, StandardCharsets.UTF_8)
                val offset = lineColumnOffset(content, rule.line, rule.column)
                val modifiedAt = Files.getLastModifiedTime(path).toMillis()
                rule.groups.flatMap { group -> group.keys.map { key -> Triple(path, DynamicKeyOccurrence(key, offset), modifiedAt) } }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    fun safeSourceFile(
        raw: String,
        root: Path,
    ): Path {
        require(raw.isNotBlank() && raw.length <= 4_096 && raw.none(Char::isISOControl))
        val lower = raw.lowercase()
        require("://" !in lower && !lower.startsWith("ldap:") && !lower.startsWith("file:"))
        require(!lower.startsWith("\\\\.\\") && "globalroot" !in lower)
        val path = Path.of(raw).toAbsolutePath().normalize()
        require(Files.isRegularFile(path))
        val realPath = path.toRealPath()
        require(realPath.startsWith(root))
        return realPath
    }

    fun safeConversionSourceFile(
        raw: String,
        root: Path,
    ): Path = safeSourceFile(raw, root).also { require(Files.size(it) <= MAX_CONVERSION_SOURCE_BYTES) }

    fun lineColumnOffset(
        content: String,
        line: Int,
        column: Int,
    ): Int {
        var offset = 0
        repeat(line - 1) {
            val next = content.indexOf('\n', offset)
            require(next >= 0)
            offset = next + 1
        }
        val lineEnd = content.indexOf('\n', offset).let { if (it < 0) content.length else it }
        require(offset + column - 1 <= lineEnd)
        return offset + column - 1
    }

    fun lineStartOffset(
        content: String,
        line: Int,
    ): Int = lineColumnOffset(content, line, 1)

    fun markerCommentLine(
        filePath: String,
        groups: List<DynamicSourceGroupDto>,
        indentation: String = "",
    ): String {
        val marker = DynamicMarkerSyntax.render(groups)
        val lower = filePath.lowercase()
        val comment =
            when {
                lower.endsWith(".blade.php") -> "{{-- $marker --}}"
                lower.endsWith(".html") || lower.endsWith(".xml") -> "<!-- $marker -->"
                lower.endsWith(".py") || lower.endsWith(".yaml") || lower.endsWith(".yml") || lower.endsWith(".sh") -> "# $marker"
                else -> "// $marker"
            }
        return "$indentation$comment\n"
    }

    fun removeMarker(
        content: String,
        markerStart: Int,
        markerEndExclusive: Int,
    ): String {
        require(markerStart in 0 until content.length && markerEndExclusive in (markerStart + 1)..content.length)
        val lineStart = content.lastIndexOf('\n', markerStart - 1).let { if (it < 0) 0 else it + 1 }
        val lineBreak = content.indexOf('\n', markerEndExclusive)
        val lineEnd = if (lineBreak < 0) content.length else lineBreak
        val standalone = markerOccupiesWholeCommentLine(content, markerStart, markerEndExclusive)
        return if (standalone) {
            content.removeRange(lineStart, if (lineBreak < 0) lineEnd else lineBreak + 1)
        } else {
            content.removeRange(markerStart, markerEndExclusive)
        }
    }

    fun markerOccupiesWholeCommentLine(
        content: String,
        markerStart: Int,
        markerEndExclusive: Int,
    ): Boolean {
        require(markerStart in 0 until content.length && markerEndExclusive in (markerStart + 1)..content.length)
        val markerText = content.substring(markerStart, markerEndExclusive)
        val lineStart = content.lastIndexOf('\n', markerStart - 1).let { if (it < 0) 0 else it + 1 }
        val lineEnd = content.indexOf('\n', markerEndExclusive).let { if (it < 0) content.length else it }
        val trimmedLine = content.substring(lineStart, lineEnd).trim()
        return trimmedLine == "// $markerText" ||
                trimmedLine == "# $markerText" ||
                trimmedLine == "/* $markerText */" ||
                trimmedLine == "* $markerText" ||
                trimmedLine == "<!-- $markerText -->" ||
                trimmedLine == "{{-- $markerText --}}"
    }
}
