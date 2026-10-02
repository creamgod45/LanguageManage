package cg.creamgod45

import cg.creamgod45.localization.EntryMutationDto
import cg.creamgod45.localization.LanguageEntryDto
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Editing one translation must change only that value in the file: comments, quoting, indentation, escapes and the
 * other entries stay byte-for-byte identical (quick in-place editing and Edit Selected both save through this path).
 */
class FormatPreservingWriterTest {
    private val temp = Files.createTempDirectory("language-manager-preserve")

    @AfterTest
    fun cleanup() {
        temp.toFile().deleteRecursively()
    }

    private fun file(
        name: String,
        content: String,
    ): Path {
        val parent = if (name.endsWith(".php")) temp.resolve("en").createDirectories() else temp
        return parent.resolve(name).apply { writeText(content) }
    }

    /** Applies one value edit through the same path as the save RPC and returns the text that would be written. */
    private fun editOne(
        path: Path,
        key: String,
        value: String,
    ): String {
        val original = Files.readString(path)
        val document = LanguageFileCodec.parse(path, "scheme")
        val entry =
            LanguageEntryDto("id", "scheme", path.toString(), document.locale, document.namespace, key, document.values.getValue(key))
        EntryMutationSupport.apply(
            listOf(document),
            listOf(entry),
            listOf(EntryMutationDto("id", path.toString(), document.locale, document.namespace, key, value)),
            String::trim,
        )
        return LanguageFileCodec.renderPreservingFormat(document, original)
    }

    @Test
    fun `php edit keeps comments double quotes declare and the other entries`() {
        val original =
            """
            <?php

            declare(strict_types=1);

            // Authentication messages
            return [
                "failed"   => "These credentials do not match our records.",
                'password' => 'The provided password is incorrect.', // keep me
                'nested'   => [
                    'title' => 'Title',
                    'body'  => 'Line one' . ' and two',
                ],
            ];
            """.trimIndent() + "\n"
        val path = file("auth.php", original)

        val written = editOne(path, "nested.body", "Changed body")

        assertEquals(original.replace("'Line one' . ' and two'", "'Changed body'"), written)
    }

    @Test
    fun `json edit keeps indentation key order and other values`() {
        val original = "{\n    \"b\": \"Second\",\n    \"a\": {\n        \"title\": \"Title\",\n        \"list\": [\"x\", \"y\"]\n    },\n    \"count\": 3\n}\n"
        val path = file("en.json", original)

        val written = editOne(path, "a.title", "New \"title\"")

        assertEquals(original.replace("\"Title\"", "\"New \\\"title\\\"\""), written)
    }

    @Test
    fun `json array element edit replaces only that element`() {
        val original = "{\n  \"list\": [\"x\", \"y\"]\n}\n"
        val path = file("arr.json", original)

        assertEquals(original.replace("\"y\"", "\"z\""), editOne(path, "list.1", "z"))
    }

    @Test
    fun `yaml edit keeps comments and quoting of other entries`() {
        val original = "# Greetings\nmessages:\n  hello: 'Hello'   # informal\n  bye: Goodbye\n"
        val path = file("en.yml", original)

        val written = editOne(path, "messages.hello", "Hi")

        assertEquals("# Greetings\nmessages:\n  hello: \"Hi\"   # informal\n  bye: Goodbye\n", written)
    }

    @Test
    fun `properties edit keeps comments unicode escapes and continuation lines`() {
        val original =
            "# Bundle header\n\n" +
                "greeting = \\u4f60\\u597d\n" +
                "long.text=first part \\\n    second part\n" +
                "! another comment\n" +
                "button.save : Save\n"
        val path = file("Messages.properties", original)

        val editedSave = editOne(path, "button.save", "Store")
        assertEquals(original.replace("button.save : Save", "button.save : Store"), editedSave)

        val editedLong = editOne(path, "long.text", "single line")
        assertEquals(original.replace("first part \\\n    second part", "single line"), editedLong)
    }

    @Test
    fun `crlf line endings stay crlf`() {
        val original = "a=One\r\nb=Two\r\n"
        val path = file("Crlf.properties", original)

        assertEquals("a=One\r\nb=Changed\r\n", editOne(path, "b", "Changed"))
    }

    @Test
    fun `unchanged values leave the file text identical`() {
        val original = "<?php\n// comment\nreturn [\"title\" => \"Title\"];\n"
        val path = file("same.php", original)

        assertEquals(original, editOne(path, "title", "Title"))
    }

    @Test
    fun `structural changes fall back to a full render`() {
        val original = "a=One\n"
        val path = file("Added.properties", original)
        val document = LanguageFileCodec.parse(path, "scheme")
        document.values["b"] = "Two"

        assertNull(FormatPreservingWriter.patch(original, document))
        assertEquals(LanguageFileCodec.render(document), LanguageFileCodec.renderPreservingFormat(document, original))
    }
}
