package cg.creamgod45

import java.nio.file.Path

/** Matches legacy directory exclusions and new file exclusions without changing persisted DTOs. */
internal class UsagePathExclusions(
    private val root: Path,
    exclusions: List<String>,
) {
    private val normalized = exclusions.map(::normalize).filter(String::isNotBlank).toSet()
    private val names = normalized.filterTo(hashSetOf()) { '/' !in it }
    private val paths = normalized.filterTo(hashSetOf()) { '/' in it }

    fun excludesDirectory(directory: Path): Boolean {
        if (directory == root) return false
        val relative = relative(directory)
        return directory.fileName?.toString()?.lowercase() in names ||
            paths.any { relative == it || relative.startsWith("$it/") }
    }

    fun excludesFile(file: Path): Boolean {
        val relative = relative(file)
        return file.fileName?.toString()?.lowercase() in names || relative in paths
    }

    private fun relative(path: Path): String =
        root.relativize(path).joinToString("/") { it.toString() }.lowercase()

    private fun normalize(value: String): String = value.replace('\\', '/').trim('/').lowercase()
}
