package cg.creamgod45.toolWindow

import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertTrue

class EditorTabSupportTest {
    private fun load(name: String) =
        Properties().apply {
            EditorTabSupportTest::class.java.classLoader
                .getResourceAsStream("messages/$name")!!
                .use(::load)
        }

    @Test
    fun `every editor tab view and action has localized text`() {
        val keys = LanguageManagerView.entries.map { it.titleKey } + listOf("action.open.in.editor.tab", "editor.tab.title")
        listOf("", "_zh_TW", "_zh_CN", "_ja", "_ko", "_es", "_th").forEach { suffix ->
            val bundle = load("LanguageManagerFrontendBundle$suffix.properties")
            keys.forEach { key -> assertTrue(bundle.getProperty(key).isNullOrBlank().not(), "$key missing in '$suffix'") }
        }
    }

    @Test
    fun `editor provider is registered in the frontend module`() {
        val descriptor =
            EditorTabSupportTest::class.java.classLoader
                .getResourceAsStream("LanguageManage.frontend.xml")!!
                .bufferedReader()
                .use { it.readText() }

        assertTrue(
            descriptor.contains("""<fileEditorProvider implementation="${LanguageManagerFileEditorProvider::class.java.name}"/>"""),
        )
    }
}
