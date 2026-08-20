package cg.creamgod45

import cg.creamgod45.localization.FileChangePreviewDto
import cg.creamgod45.localization.UsageLocationDto
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import cg.creamgod45.LanguageManagerBackendBundle.message as backendMessage

internal object UsageSourceRenameSupport {
    private const val MAX_EDITABLE_SOURCE_BYTES = 10L * 1024 * 1024

    fun buildPreview(
        root: Path,
        locations: List<UsageLocationDto>,
        allowedEntryIds: Set<String>,
        namespace: String,
        oldKey: String,
        newKey: String,
        cancellationCheck: () -> Unit = {},
    ): List<FileChangePreviewDto> =
        buildPreview(
            root,
            locations,
            allowedEntryIds,
            namespace,
            oldKey,
            if (namespace.isBlank()) newKey else "$namespace.$newKey",
            newKey,
            true,
            cancellationCheck,
        )

    fun buildMergePreview(
        root: Path,
        locations: List<UsageLocationDto>,
        allowedEntryIds: Set<String>,
        sourceNamespace: String,
        sourceKey: String,
        targetUsageReference: String,
        targetKey: String,
        cancellationCheck: () -> Unit = {},
    ): List<FileChangePreviewDto> =
        buildPreview(
            root,
            locations,
            allowedEntryIds,
            sourceNamespace,
            sourceKey,
            targetUsageReference,
            targetKey,
            false,
            cancellationCheck,
        )

    private fun buildPreview(
        root: Path,
        locations: List<UsageLocationDto>,
        allowedEntryIds: Set<String>,
        sourceNamespace: String,
        sourceKey: String,
        targetUsageReference: String,
        targetKey: String,
        preserveSourceStyle: Boolean,
        cancellationCheck: () -> Unit,
    ): List<FileChangePreviewDto> {
        val scanRoot = root.toRealPath()
        val locationsByFile = linkedMapOf<String, MutableMap<Int, UsageLocationDto>>()
        locations.forEach { location ->
            cancellationCheck()
            if (location.entryId in allowedEntryIds) {
                locationsByFile
                    .getOrPut(location.filePath) { linkedMapOf() }
                    .putIfAbsent(location.offset, location)
            }
        }
        return locationsByFile.mapNotNull { (rawPath, positions) ->
            cancellationCheck()
            val path = Path.of(rawPath).toAbsolutePath().normalize()
            require(Files.isRegularFile(path)) { backendMessage("usage.location.not.found") }
            val realPath = path.toRealPath()
            require(realPath.startsWith(scanRoot)) { backendMessage("usage.rename.source.outside.root") }
            require(Files.size(realPath) <= MAX_EDITABLE_SOURCE_BYTES) {
                backendMessage("usage.rename.source.too.large", MAX_EDITABLE_SOURCE_BYTES / 1024)
            }
            val modifiedAt = Files.getLastModifiedTime(realPath).toMillis()
            require(positions.values.all { it.sourceModifiedAtEpochMs == modifiedAt }) {
                backendMessage("usage.location.stale")
            }
            val before = Files.readString(realPath, StandardCharsets.UTF_8)
            val replacements =
                positions.keys
                    .map { offset ->
                        cancellationCheck()
                        replacementAt(
                            before,
                            offset,
                            sourceNamespace,
                            sourceKey,
                            targetUsageReference,
                            targetKey,
                            preserveSourceStyle,
                        )
                            ?: error(backendMessage("usage.rename.capture.mismatch", realPath.fileName))
                    }.sortedWith(
                        compareBy<Replacement>(Replacement::start)
                            .thenByDescending { it.endExclusive - it.start },
                    )
            val nonOverlapping = mutableListOf<Replacement>()
            replacements.forEach { replacement ->
                val previous = nonOverlapping.lastOrNull()
                if (previous == null || replacement.start >= previous.endExclusive) nonOverlapping += replacement
            }
            val estimatedLength =
                before.length.toLong() +
                    nonOverlapping.sumOf { (it.value.length - (it.endExclusive - it.start)).toLong() }
            require(estimatedLength in 0..MAX_EDITABLE_SOURCE_BYTES) {
                backendMessage("usage.rename.source.too.large", MAX_EDITABLE_SOURCE_BYTES / 1024)
            }
            val after =
                buildString(estimatedLength.toInt()) {
                    var cursor = 0
                    nonOverlapping.forEach { replacement ->
                        append(before, cursor, replacement.start)
                        append(replacement.value)
                        cursor = replacement.endExclusive
                    }
                    append(before, cursor, before.length)
                }
            require(after.toByteArray(StandardCharsets.UTF_8).size <= MAX_EDITABLE_SOURCE_BYTES) {
                backendMessage("usage.rename.source.too.large", MAX_EDITABLE_SOURCE_BYTES / 1024)
            }
            if (before == after) {
                null
            } else {
                FileChangePreviewDto(
                    filePath = realPath.toString(),
                    beforeContent = before,
                    afterContent = after,
                    beforeSha256 = sha256(before),
                    editable = true,
                )
            }
        }
    }

    private fun replacementAt(
        content: String,
        offset: Int,
        sourceNamespace: String,
        sourceKey: String,
        targetUsageReference: String,
        targetKey: String,
        preserveSourceStyle: Boolean,
    ): Replacement? {
        if (offset !in 0..content.length) return null
        quotedLiteralAt(content, offset)?.let { literal ->
            if (matchesSourceReference(literal.value, sourceNamespace, sourceKey)) {
                val vendorPrefix = literal.value.substringBefore("::", "").takeIf { "::" in literal.value }
                val replacement =
                    if (preserveSourceStyle) {
                        val body = literal.value.substringAfter("::", literal.value)
                        val namespacePart = body.removeSuffix(sourceKey).removeSuffix(".")
                        listOfNotNull(vendorPrefix?.let { "$it::" }, namespacePart.takeIf(String::isNotBlank)?.let { "$it." }, targetKey)
                            .joinToString("")
                    } else if (vendorPrefix != null && "::" !in targetUsageReference) {
                        "$vendorPrefix::$targetUsageReference"
                    } else {
                        targetUsageReference
                    }
                return Replacement(literal.start, literal.endExclusive, replacement)
            }
        }
        val slashNamespace = sourceNamespace.replace('.', '/')
        val candidates =
            buildList {
                if (sourceNamespace.isNotBlank()) {
                    add("$sourceNamespace.$sourceKey" to targetUsageReference)
                    add("$slashNamespace.$sourceKey" to targetUsageReference)
                }
                add(sourceKey to targetKey)
            }
        return candidates.firstNotNullOfOrNull { (candidate, replacement) ->
            if (content.regionMatches(offset, candidate, 0, candidate.length)) {
                Replacement(offset, offset + candidate.length, replacement)
            } else {
                null
            }
        }
    }

    private fun matchesSourceReference(
        value: String,
        namespace: String,
        key: String,
    ): Boolean {
        if (value == key) return true
        if (namespace.isBlank()) return false
        val withoutVendor = value.substringAfter("::", value)
        return withoutVendor == "$namespace.$key" || withoutVendor == "${namespace.replace('.', '/')}.$key"
    }

    private fun quotedLiteralAt(
        content: String,
        offset: Int,
    ): QuotedLiteral? {
        val searchStart = (offset - 512).coerceAtLeast(0)
        for (start in offset.coerceAtMost(content.lastIndex) downTo searchStart) {
            val quote = content[start]
            if (quote != '\'' && quote != '"') continue
            if (isEscaped(content, start)) continue
            var end = start + 1
            while (end < content.length) {
                if (content[end] == quote && !isEscaped(content, end)) {
                    if (offset in (start + 1)..end) return QuotedLiteral(start + 1, end, content.substring(start + 1, end))
                    break
                }
                if (content[end] == '\n' || content[end] == '\r') break
                end++
            }
        }
        return null
    }

    private fun isEscaped(content: String, index: Int): Boolean {
        var slashCount = 0
        var cursor = index - 1
        while (cursor >= 0 && content[cursor] == '\\') {
            slashCount++
            cursor--
        }
        return slashCount % 2 == 1
    }

    private fun sha256(content: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(content.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private data class Replacement(
        val start: Int,
        val endExclusive: Int,
        val value: String,
    )

    private data class QuotedLiteral(
        val start: Int,
        val endExclusive: Int,
        val value: String,
    )
}
