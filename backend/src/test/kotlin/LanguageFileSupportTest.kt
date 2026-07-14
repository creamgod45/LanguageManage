package cg.creamgod45

import cg.creamgod45.localization.IssueSeverity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.*
import java.nio.file.Files

class LanguageFileSupportTest {
    private val temp = Files.createTempDirectory("language-manager-test")

    @AfterTest
    fun cleanup() { temp.toFile().deleteRecursively() }

    @Test
    fun `parses nested json and reports malformed json safely`() {
        val valid = temp.resolve("en.json").apply { writeText("""{"auth":{"failed":"Invalid"},"ok":true}""") }
        val parsed = LanguageFileCodec.parse(valid, "scheme")
        assertEquals("Invalid", parsed.values["auth.failed"])
        assertEquals("true", parsed.values["ok"])
        val invalid = temp.resolve("bad.json").apply { writeText("{" ) }
        assertEquals("PARSE_ERROR", LanguageFileCodec.parse(invalid, "scheme").issues.single().code)
    }

    @Test
    fun `preserves json arrays as structured editable values`() {
        val file = temp.resolve("en.json").apply { writeText("""
            {
              "welcome_features": ["Fast", {"title": "Safe", "enabled": true}, null],
              "nested": {"steps": [1, 2, 3]}
            }
        """.trimIndent()) }
        val parsed = LanguageFileCodec.parse(file, "scheme")
        assertTrue("welcome_features" in parsed.structuredValueKeys)
        assertTrue("nested.steps" in parsed.structuredValueKeys)
        assertTrue(parsed.values.getValue("welcome_features").startsWith("["))

        parsed.values["nested.steps"] = "[3, 2, 1]"
        LanguageFileCodec.write(parsed)
        val reread = LanguageFileCodec.parse(file, "scheme")
        assertEquals(Json.parseToJsonElement("[3,2,1]"), Json.parseToJsonElement(reread.values.getValue("nested.steps")))
        assertTrue("welcome_features" in reread.structuredValueKeys)
        assertTrue(reread.issues.isEmpty())
    }

    @Test
    fun `preserves sentence keys containing periods as literal json keys`() {
        val shortKey = "Import a CSV file using the create button at the top right"
        val longKey = "$shortKey. Old data is cleared before each import."
        val file = temp.resolve("en.json").apply { writeText("""
            {
              "$shortKey": "Short instruction",
              "$longKey": "Full instruction",
              "dialog": {"title": "Import"}
            }
        """.trimIndent()) }
        val parsed = LanguageFileCodec.parse(file, "scheme")
        assertEquals(listOf(longKey), parsed.keyPaths[longKey])
        assertEquals(listOf("dialog", "title"), parsed.keyPaths["dialog.title"])
        parsed.values[longKey] = "Updated"
        LanguageFileCodec.write(parsed)

        val root = Json.parseToJsonElement(file.toFile().readText()).jsonObject
        assertEquals("Short instruction", root.getValue(shortKey).jsonPrimitive.content)
        assertEquals("Updated", root.getValue(longKey).jsonPrimitive.content)
        assertEquals("Import", root.getValue("dialog").jsonObject.getValue("title").jsonPrimitive.content)
    }

    @Test
    fun `parses yaml nesting comments and quoted colons`() {
        val file = temp.resolve("zh_TW.yaml").apply { writeText("""
            auth:
              failed: "登入: 失敗" # comment
              retry: '再試一次'
        """.trimIndent()) }
        val parsed = LanguageFileCodec.parse(file, "scheme")
        assertEquals("登入: 失敗", parsed.values["auth.failed"])
        assertEquals("再試一次", parsed.values["auth.retry"])
    }

    @Test
    fun `parses Laravel php arrays without executing code`() {
        val localeDir = temp.resolve("en").createDirectories()
        val file = localeDir.resolve("messages.php").apply { writeText("""<?php
            // translations
            return ['auth' => ['failed' => 'Invalid'], 'count' => 2];
        """.trimIndent()) }
        val parsed = LanguageFileCodec.parse(file, "scheme")
        assertEquals("en", parsed.locale)
        assertEquals("messages", parsed.namespace)
        assertEquals("Invalid", parsed.values["auth.failed"])
        assertEquals("2", parsed.values["count"])
    }

    @Test
    fun `rejects executable php expressions`() {
        val file = temp.resolve("evil.php").apply { writeText("<?php return ['x' => shell_exec('whoami')];") }
        val issue = LanguageFileCodec.parse(file, "scheme").issues.single()
        assertEquals(IssueSeverity.ERROR, issue.severity)
        assertEquals("PARSE_ERROR", issue.code)
    }

    @Test
    fun `round trips all supported formats`() {
        val cases = mapOf(
            "en.json" to "{\"old\":\"value\"}",
            "en.yml" to "old: value\n",
            "messages.php" to "<?php return ['old' => 'value'];",
        )
        cases.forEach { (name, initial) ->
            val parent = if (name.endsWith("php")) temp.resolve("en").createDirectories() else temp
            val path = parent.resolve(name).apply { writeText(initial) }
            val document = LanguageFileCodec.parse(path, "scheme")
            document.values["new.key"] = "new value"
            LanguageFileCodec.write(document)
            val reread = LanguageFileCodec.parse(path, "scheme")
            assertEquals("new value", reread.values["new.key"], name)
            assertTrue(reread.issues.isEmpty(), name)
        }
    }

    @Test
    fun `renders change preview without writing the source file`() {
        val original = "{\"empty\":\"\",\"kept\":\"value\"}"
        val file = temp.resolve("preview.json").apply { writeText(original) }
        val document = LanguageFileCodec.parse(file, "scheme")
        document.values["empty"] = "empty"

        val proposed = LanguageFileCodec.render(document)

        assertEquals(original, file.toFile().readText(), "預覽不可修改來源檔")
        assertEquals("empty", Json.parseToJsonElement(proposed).jsonObject.getValue("empty").jsonPrimitive.content)
    }

    @Test
    fun `safe access blocks service URIs device paths and unsupported files`() {
        assertFailsWith<IllegalArgumentException> { SafeLanguageFileAccess.validate("ldap://attacker/test.json") }
        assertFailsWith<IllegalArgumentException> { SafeLanguageFileAccess.validate("\\\\.\\pipe\\test.json") }
        val text = temp.resolve("test.txt").apply { writeText("x") }
        assertFailsWith<IllegalArgumentException> { SafeLanguageFileAccess.validate(text.toString()) }
    }

    @Test
    fun `folder discovery parses supported files and skips generated directories`() {
        temp.resolve("en.json").writeText("""{"hello":"Hello","bye":"Bye"}""")
        temp.resolve("bad.yaml").writeText("root:\n\tkey: invalid indentation")
        temp.resolve("notes.txt").writeText("not a language file")
        temp.resolve("zh_TW").createDirectories().resolve("messages.php")
            .writeText("<?php return ['hello' => '哈囉'];")
        temp.resolve("vendor").createDirectories().resolve("ignored.json")
            .writeText("""{"ignored":"value"}""")

        val result = LanguageFolderDiscovery.discover(temp.toString())

        assertFalse(result.truncated)
        assertEquals(3, result.files.size)
        assertTrue(result.files.single { it.filePath.endsWith("en.json") }.let { it.recognized && it.entryCount == 2 && it.locale == "en" })
        assertTrue(result.files.single { it.filePath.endsWith("messages.php") }.let { it.recognized && it.locale == "zh_TW" && it.namespace == "messages" })
        assertFalse(result.files.single { it.filePath.endsWith("bad.yaml") }.recognized)
        assertTrue(result.files.none { it.filePath.endsWith("notes.txt") || it.filePath.contains("vendor") })
    }
}
