package cg.creamgod45

import java.nio.file.Path
import cg.creamgod45.LanguageManagerBackendBundle.message as backendMessage

internal object UsageExclusionSupport {
    fun relativeDirectories(
        scanRoot: Path,
        rawFolderPaths: List<String>,
    ): List<String> {
        val root = scanRoot.toRealPath()
        return rawFolderPaths
            .map(SafeLanguageFileAccess::validateDirectory)
            .map { folder ->
                require(folder != root && folder.startsWith(root)) {
                    backendMessage("usage.exclusion.folder.outside", folder)
                }
                root.relativize(folder).joinToString("/") { it.toString() }
            }.distinct()
    }
}
