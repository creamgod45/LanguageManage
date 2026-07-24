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
                        replacementAt(before, offset, namespace, oldKey, newKey)
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
        namespace: String,
        oldKey: String,
        newKey: String,
    ): Replacement? {
        if (offset !in 0..content.length) return null
        val candidates =
            buildList {
                if (namespace.isNotBlank()) add("$namespace.$oldKey" to "$namespace.$newKey")
                add(oldKey to newKey)
            }
        return candidates.firstNotNullOfOrNull { (candidate, replacement) ->
            if (content.regionMatches(offset, candidate, 0, candidate.length)) {
                Replacement(offset, offset + candidate.length, replacement)
            } else {
                null
            }
        }
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
}
