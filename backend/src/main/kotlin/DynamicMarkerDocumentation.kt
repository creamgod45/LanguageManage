package cg.creamgod45

import cg.creamgod45.localization.DynamicMarkerMatch
import cg.creamgod45.localization.DynamicMarkerSyntax
import com.intellij.model.Pointer
import com.intellij.openapi.util.TextRange
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.DocumentationTargetProvider
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiFile
import cg.creamgod45.LanguageManagerBackendBundle.message as backendMessage

class DynamicMarkerDocumentationTargetProvider : DocumentationTargetProvider {
    override fun documentationTargets(
        file: PsiFile,
        offset: Int,
    ): List<DocumentationTarget> {
        val marker = DynamicMarkerSyntax.findAt(file.text, offset) ?: return emptyList()
        return listOf(DynamicMarkerDocumentationTarget(file, marker))
    }
}

private class DynamicMarkerDocumentationTarget(
    private val file: PsiFile,
    private val marker: DynamicMarkerMatch,
) : DocumentationTarget {
    override fun createPointer(): Pointer<out DocumentationTarget> =
        Pointer.fileRangePointer(file, TextRange(marker.startOffset, marker.endOffsetExclusive)) { restoredFile, restoredRange ->
            DynamicMarkerSyntax.findAt(restoredFile.text, restoredRange.startOffset, restoredRange.endOffset)
                ?.let { DynamicMarkerDocumentationTarget(restoredFile, it) }
        }

    override fun computePresentation(): TargetPresentation =
        TargetPresentation.builder("@languageManager")
            .locationText(file.name)
            .presentation()

    override fun computeDocumentationHint(): String = backendMessage("dynamic.marker.documentation.hint")

    override fun computeDocumentation(): DocumentationResult {
        val groups =
            marker.groups.joinToString("<br>") { group ->
                "<b>${escape(group.name)}</b>: ${group.keys.joinToString(", ") { escape(it) }}"
            }
        return DocumentationResult.documentation(
            "<div class='definition'><pre>@languageManager(method: dynamic, ...)</pre></div>" +
                "<div class='content'><p>${escape(backendMessage("dynamic.marker.documentation.description"))}</p>" +
                "<p>${escape(backendMessage("dynamic.marker.documentation.groups"))}<br>$groups</p>" +
                "<p>${escape(backendMessage("dynamic.marker.documentation.action"))}</p></div>",
        )
    }

    private fun escape(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
