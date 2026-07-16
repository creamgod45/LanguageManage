package cg.creamgod45

import cg.creamgod45.localization.EntryMutationDto
import cg.creamgod45.localization.LanguageEntryDto
import java.nio.file.Path
import cg.creamgod45.LanguageManagerBackendBundle.message as backendMessage

internal object EntryMutationSupport {
    fun apply(
        documents: List<ParsedLanguageFile>,
        currentEntries: List<LanguageEntryDto>,
        mutations: List<EntryMutationDto>,
        sanitizeValue: (String) -> String,
    ): List<ParsedLanguageFile> {
        require(mutations.isNotEmpty()) { backendMessage("entry.mutations.required") }
        require(
            mutations.distinctBy { Triple(it.filePath, it.namespace, it.key) }.size == mutations.size,
        ) { backendMessage("entry.mutations.duplicate.target") }
        val sourceIds = mutations.mapNotNull { it.id?.takeIf(String::isNotBlank) }
        require(sourceIds.distinct().size == sourceIds.size) { backendMessage("entry.mutations.duplicate.source") }

        val documentsByPath = documents.associateBy { it.path.toAbsolutePath().normalize() }
        val entriesById = currentEntries.associateBy(LanguageEntryDto::id)
        val changed = linkedMapOf<Path, ParsedLanguageFile>()

        mutations.forEach { mutation ->
            val targetPath = Path.of(mutation.filePath).toAbsolutePath().normalize()
            val targetDocument = documentsByPath[targetPath] ?: error(backendMessage("entry.target.outside"))
            var structuredValue = false
            var originalKey: String? = null
            var originalKeyPath: List<String>? = null

            mutation.id?.takeIf(String::isNotBlank)?.let { id ->
                val original = entriesById[id] ?: error(backendMessage("entry.not.found"))
                val originalPath = Path.of(original.filePath).toAbsolutePath().normalize()
                val originalDocument = documentsByPath[originalPath] ?: error(backendMessage("entry.target.outside"))
                originalKey = original.key
                structuredValue = original.key in originalDocument.structuredValueKeys
                originalKeyPath = originalDocument.keyPaths.remove(original.key)
                originalDocument.values.remove(original.key)
                originalDocument.structuredValueKeys.remove(original.key)
                changed[originalPath] = originalDocument
            }

            require(mutation.key !in targetDocument.values) { backendMessage("entry.key.exists", mutation.key) }
            targetDocument.values[mutation.key] = sanitizeValue(mutation.value)
            if (structuredValue) targetDocument.structuredValueKeys += mutation.key
            targetDocument.keyPaths[mutation.key] =
                when {
                    originalKeyPath != null && mutation.key == originalKey -> originalKeyPath
                    originalKeyPath?.size == 1 -> listOf(mutation.key)
                    mutation.key.any(Char::isWhitespace) -> listOf(mutation.key)
                    else -> mutation.key.split('.').filter(String::isNotBlank)
                }
            changed[targetPath] = targetDocument
        }
        return changed.values.toList()
    }
}
