package cg.creamgod45

import java.nio.file.Path

/**
 * Result of turning a batch of selected folders into scheme-relative exclusion entries.
 *
 * @property relativeDirectories folders that live below the scan root, as deduplicated
 *   `/`-separated paths relative to it.
 * @property skippedDirectories raw paths that could not be excluded (the scan root itself, a
 *   folder outside the scan root, or a path that failed validation). These are reported back so
 *   the user learns nothing silently vanished.
 */
internal data class ExclusionResolution(
    val relativeDirectories: List<String>,
    val skippedDirectories: List<String>,
)

internal object UsageExclusionSupport {
    /**
     * Classify each selected folder independently. A folder that is the scan root, lives outside
     * the scan root, or fails validation is skipped rather than aborting the whole batch, so the
     * remaining valid folders are still excluded. (Previously a single such folder threw and no
     * folder in the selection was excluded at all.)
     */
    fun resolve(
        scanRoot: Path,
        rawFolderPaths: List<String>,
    ): ExclusionResolution {
        val root = scanRoot.toRealPath()
        val accepted = LinkedHashSet<String>()
        val skipped = LinkedHashSet<String>()
        rawFolderPaths.forEach { raw ->
            val relative =
                runCatching {
                    val folder = SafeLanguageFileAccess.validateDirectory(raw)
                    if (folder != root && folder.startsWith(root)) {
                        root.relativize(folder).joinToString("/") { it.toString() }
                    } else {
                        null
                    }
                }.getOrNull()
            if (relative.isNullOrEmpty()) skipped += raw else accepted += relative
        }
        return ExclusionResolution(accepted.toList(), skipped.toList())
    }
}
