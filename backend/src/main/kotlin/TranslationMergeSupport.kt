package cg.creamgod45

import java.nio.file.Path
import cg.creamgod45.LanguageManagerBackendBundle.message as backendMessage

internal object TranslationMergeSupport {
    fun apply(
        documents: List<ParsedLanguageFile>,
        sourceNamespace: String,
        sourceKey: String,
        targetNamespace: String,
        targetKey: String,
    ): List<ParsedLanguageFile> {
        val sourceDocuments = documents.filter { it.namespace == sourceNamespace && sourceKey in it.values }
        require(sourceDocuments.isNotEmpty()) { backendMessage("entry.key.not.found", sourceKey) }
        val targetTemplate =
            documents.firstOrNull { it.namespace == targetNamespace && targetKey in it.values }
                ?: error(backendMessage("merge.target.not.found"))
        val changed = linkedMapOf<Path, ParsedLanguageFile>()

        sourceDocuments.groupBy(ParsedLanguageFile::locale).forEach { (locale, localeSources) ->
            val targetCandidates = documents.filter { it.locale == locale && it.namespace == targetNamespace }
            require(targetCandidates.count { targetKey in it.values } <= 1) {
                backendMessage("merge.target.locale.ambiguous", locale)
            }
            val targetDocument =
                targetCandidates.singleOrNull { targetKey in it.values }
                    ?: targetCandidates.singleOrNull()
                    ?: error(backendMessage("merge.target.locale.file.missing", locale))
            val sourceValue = localeSources.firstNotNullOfOrNull { it.values[sourceKey]?.takeIf(String::isNotBlank) }
                ?: localeSources.first().values.getValue(sourceKey)
            if (targetDocument.values[targetKey].isNullOrBlank()) {
                targetDocument.values[targetKey] = sourceValue
                val templatePath = targetTemplate.keyPaths[targetKey]
                targetDocument.keyPaths[targetKey] =
                    templatePath ?: if (targetKey.any(Char::isWhitespace)) listOf(targetKey) else targetKey.split('.').filter(String::isNotBlank)
                if (targetKey in targetTemplate.structuredValueKeys || sourceKey in localeSources.first().structuredValueKeys) {
                    targetDocument.structuredValueKeys += targetKey
                }
            }
            changed[targetDocument.path] = targetDocument
            localeSources.forEach { sourceDocument ->
                sourceDocument.values.remove(sourceKey)
                sourceDocument.structuredValueKeys.remove(sourceKey)
                sourceDocument.keyPaths.remove(sourceKey)
                changed[sourceDocument.path] = sourceDocument
            }
        }
        return changed.values.toList()
    }
}
