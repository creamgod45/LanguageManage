package cg.creamgod45

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class TranslationMergeSupportTest {
    @Test
    fun `keeps existing target values and fills blank locales before removing source`() {
        val documents =
            listOf(
                document("en-source.php", "en", "legacy.messages", mapOf("old" to "Old English")),
                document("zh-source.php", "zh_TW", "legacy.messages", mapOf("old" to "舊值")),
                document("en-target.php", "en", "shared.messages", mapOf("new" to "Existing English")),
                document("zh-target.php", "zh_TW", "shared.messages", mapOf("new" to "")),
            )

        TranslationMergeSupport.apply(documents, "legacy.messages", "old", "shared.messages", "new")

        assertEquals("Existing English", documents[2].values["new"])
        assertEquals("舊值", documents[3].values["new"])
        assertFalse(documents[0].values.containsKey("old"))
        assertFalse(documents[1].values.containsKey("old"))
    }

    private fun document(
        name: String,
        locale: String,
        namespace: String,
        values: Map<String, String>,
    ) = ParsedLanguageFile(
        path = Path.of(name),
        locale = locale,
        namespace = namespace,
        values = LinkedHashMap(values),
        keyPaths = values.keys.associateWith { listOf(it) }.toMutableMap(),
    )
}
