package cg.creamgod45

import java.nio.file.Path

/**
 * Result of turning a batch of selected files or folders into scheme-relative exclusion entries.
 *
 * @property relativePaths files or folders below the scan root, as deduplicated `/`-separated
 *   paths relative to it.
 * @property skippedPaths raw paths that could not be excluded (the scan root itself, a path
 *   outside the scan root, or a path that failed validation). These are reported back so
 *   the user learns nothing silently vanished.
 */
internal data class ExclusionResolution(
    val relativePaths: List<String>,
    val skippedPaths: List<String>,
)

internal object UsageExclusionSupport {
    /**
     * Classify each selected file or folder independently. The scan root, outside paths, and
     * invalid paths are skipped rather than aborting the whole batch.
     */
    fun resolve(
        scanRoot: Path,
        rawPaths: List<String>,
    ): ExclusionResolution {
        val root = scanRoot.toRealPath()
        val accepted = LinkedHashSet<String>()
        val skipped = LinkedHashSet<String>()
        rawPaths.forEach { raw ->
            val relative =
                runCatching {
                    val path = SafeLanguageFileAccess.validateExistingPath(raw)
                    if (path != root && path.startsWith(root)) {
                        root.relativize(path).joinToString("/") { it.toString() }
                    } else {
                        null
                    }
                }.getOrNull()
            if (relative.isNullOrEmpty()) skipped += raw else accepted += relative
        }
        return ExclusionResolution(accepted.toList(), skipped.toList())
    }
}
