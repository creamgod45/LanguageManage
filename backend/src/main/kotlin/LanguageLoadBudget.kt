package cg.creamgod45.localization

import java.nio.file.Files
import java.nio.file.Path
import cg.creamgod45.LanguageManagerBackendBundle.message as backendMessage

/** Tracks bounded language-file input before parsers or caches may retain it. */
internal class LanguageLoadBudget(
    private val settings: UsageScanSettingsDto,
) {
    private var acceptedBytes = 0L
    private var acceptedEntries = 0

    fun acceptFile(path: Path): Long {
        val size = Files.size(path)
        val maxFileBytes = settings.maxLanguageFileKb.toLong() * 1024
        val maxSchemeBytes = settings.maxLanguageSchemeMb.toLong() * 1024 * 1024
        require(size <= maxFileBytes) { backendMessage("load.file.too.large", path, settings.maxLanguageFileKb) }
        require(acceptedBytes <= maxSchemeBytes - size) {
            backendMessage("load.scheme.too.large", settings.maxLanguageSchemeMb)
        }
        acceptedBytes += size
        return size
    }

    fun acceptEntries(
        path: Path,
        count: Int,
    ) {
        require(count <= settings.maxEntriesPerFile) {
            backendMessage("load.file.too.many.entries", path, settings.maxEntriesPerFile)
        }
        require(acceptedEntries <= settings.maxEntriesPerScheme - count) {
            backendMessage("load.scheme.too.many.entries", settings.maxEntriesPerScheme)
        }
        acceptedEntries += count
    }
}
