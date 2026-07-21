package cg.creamgod45

import cg.creamgod45.localization.EntrySearch
import cg.creamgod45.localization.IssueSeverity
import cg.creamgod45.localization.LanguageEntryDto
import cg.creamgod45.localization.UsageScanSettingsDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LanguageFileSupportTest {
    private val temp = Files.createTempDirectory("language-manager-test")

    @AfterTest
    fun cleanup() {
        temp.toFile().deleteRecursively()
    }

    @Test
    fun `parses nested json and reports malformed json safely`() {
        val valid = temp.resolve("en.json").apply { writeText("""{"auth":{"failed":"Invalid"},"ok":true}""") }
        val parsed = LanguageFileCodec.parse(valid, "scheme")
        assertEquals("Invalid", parsed.values["auth.failed"])
        assertEquals("true", parsed.values["ok"])
        val invalid = temp.resolve("bad.json").apply { writeText("{") }
        assertEquals(
            "PARSE_ERROR",
            LanguageFileCodec
                .parse(invalid, "scheme")
                .issues
                .single()
                .code,
        )
    }

    @Test
    fun `expands json arrays into ordinary editable translation rows`() {
        val file =
            temp.resolve("en.json").apply {
                writeText(
                    """
                    {
                      "welcome_features": ["Fast", {"title": "Safe", "enabled": true}, null],
                      "nested": {"steps": [1, 2, 3]}
                    }
                    """.trimIndent(),
                )
            }
        val parsed = LanguageFileCodec.parse(file, "scheme")
        assertEquals("Fast", parsed.values["welcome_features.0"])
        assertEquals("Safe", parsed.values["welcome_features.1.title"])
        assertEquals("true", parsed.values["welcome_features.1.enabled"])
        assertEquals("null", parsed.values["welcome_features.2"])
        assertEquals("2", parsed.values["nested.steps.1"])
        assertTrue(listOf("welcome_features") in parsed.jsonArrayPaths)
        assertTrue(listOf("nested", "steps") in parsed.jsonArrayPaths)

        parsed.values["nested.steps.1"] = "updated"
        LanguageFileCodec.write(parsed)
        val reread = LanguageFileCodec.parse(file, "scheme")
        assertEquals("updated", reread.values["nested.steps.1"])
        assertEquals("null", reread.values["welcome_features.2"])
        assertEquals(
            3,
            Json
                .parseToJsonElement(file.toFile().readText())
                .jsonObject
                .getValue("welcome_features")
                .jsonArray.size,
        )
        assertTrue(reread.issues.isEmpty())
    }

    @Test
    fun `round trips multiple ten-level arrays containing one hundred objects each`() {
        fun deepObject(
            example: Int,
            item: Int,
        ): String {
            var value = "{\"title\":\"Example $example item $item\"}"
            repeat(8) { level -> value = "{\"level${8 - level}\":$value}" }
            return value
        }
        val source =
            (1..3).joinToString(prefix = "{", postfix = "}") { example ->
                "\"example$example\":" + (0 until 100).joinToString(prefix = "[", postfix = "]") { deepObject(example, it) }
            }
        val file = temp.resolve("en.json").apply { writeText(source) }

        val parsed = LanguageFileCodec.parse(file, "scheme")

        assertTrue(parsed.issues.isEmpty())
        assertEquals(300, parsed.values.size)
        assertEquals("Example 2 item 99", parsed.values["example2.99.level1.level2.level3.level4.level5.level6.level7.level8.title"])
        parsed.values["example3.42.level1.level2.level3.level4.level5.level6.level7.level8.title"] = "Changed"
        LanguageFileCodec.write(parsed)
        val reread = LanguageFileCodec.parse(file, "scheme")
        assertTrue(reread.issues.isEmpty())
        assertEquals(300, reread.values.size)
        assertEquals("Changed", reread.values["example3.42.level1.level2.level3.level4.level5.level6.level7.level8.title"])
    }

    @Test
    fun `large single file with many objects missing in another locale stays bounded and analyzable`() {
        val objectCount = 10_000
        val en =
            temp.resolve("en.json").apply {
                writeText((0 until objectCount).joinToString(prefix = "{\"records\":[", postfix = "]}") { "{\"label\":\"Value $it\"}" })
            }
        val zh = temp.resolve("zh_TW.json").apply { writeText("""{"records":[{"label":"值 0"}]}""") }
        val documents = listOf(LanguageFileCodec.parse(en, "scheme"), LanguageFileCodec.parse(zh, "scheme"))
        val entries =
            documents.flatMap { document ->
                document.values.map { (key, value) ->
                    LanguageEntryDto("${document.locale}:$key", "scheme", document.path.toString(), document.locale, "", key, value)
                }
            }

        val issues = LocalizationAnalysis.analyze("scheme", entries)
        val joinedRows = EntrySearch.join(entries)

        assertEquals(objectCount + 1, entries.size)
        assertEquals(objectCount, joinedRows.size)
        assertTrue(documents.all { it.issues.isEmpty() })
        assertTrue(issues.any { it.code == "MISSING_TRANSLATION" })
    }

    @Test
    fun `preserves sentence keys containing periods as literal json keys`() {
        val shortKey = "Import a CSV file using the create button at the top right"
        val longKey = "$shortKey. Old data is cleared before each import."
        val file =
            temp.resolve("en.json").apply {
                writeText(
                    """
                    {
                      "$shortKey": "Short instruction",
                      "$longKey": "Full instruction",
                      "dialog": {"title": "Import"}
                    }
                    """.trimIndent(),
                )
            }
        val parsed = LanguageFileCodec.parse(file, "scheme")
        assertEquals(listOf(longKey), parsed.keyPaths[longKey])
        assertEquals(listOf("dialog", "title"), parsed.keyPaths["dialog.title"])
        parsed.values[longKey] = "Updated"
        LanguageFileCodec.write(parsed)

        val root = Json.parseToJsonElement(file.toFile().readText()).jsonObject
        assertEquals("Short instruction", root.getValue(shortKey).jsonPrimitive.content)
        assertEquals("Updated", root.getValue(longKey).jsonPrimitive.content)
        assertEquals(
            "Import",
            root
                .getValue("dialog")
                .jsonObject
                .getValue("title")
                .jsonPrimitive.content,
        )
    }

    @Test
    fun `parses yaml nesting comments and quoted colons`() {
        val file =
            temp.resolve("zh_TW.yaml").apply {
                writeText(
                    """
                    auth:
                      failed: "登入: 失敗" # comment
                      retry: '再試一次'
                    """.trimIndent(),
                )
            }
        val parsed = LanguageFileCodec.parse(file, "scheme")
        assertEquals("登入: 失敗", parsed.values["auth.failed"])
        assertEquals("再試一次", parsed.values["auth.retry"])
    }

    @Test
    fun `parses Laravel php arrays without executing code`() {
        val localeDir = temp.resolve("en").createDirectories()
        val file =
            localeDir.resolve("messages.php").apply {
                writeText(
                    """
                    <?php
                    // translations
                    return ['auth' => ['failed' => 'Invalid'], 'count' => 2];
                    """.trimIndent(),
                )
            }
        val parsed = LanguageFileCodec.parse(file, "scheme")
        assertEquals("en", parsed.locale)
        assertEquals("messages", parsed.namespace)
        assertEquals("Invalid", parsed.values["auth.failed"])
        assertEquals("2", parsed.values["count"])
    }

    @Test
    fun `recognizes locale directories above nested category folders`() {
        val locales = listOf("en", "zh_CN", "zh_HK", "zh_TW")
        val componentNames = listOf("button", "copyable", "modal", "pagination")
        locales.forEach { locale ->
            componentNames.forEach { component ->
                temp.resolve("$locale/components").createDirectories().resolve("$component.php").writeText(
                    if (component == "pagination") {
                        """<?php return ['fields' => ['records_per_page' => ['label' => '$locale per page']], 'actions' => ['next' => ['label' => '$locale next']]];"""
                    } else {
                        """<?php return ['messages' => ['label' => '$locale $component']];"""
                    },
                )
            }
        }
        val source = temp.resolve("en/components/pagination.php")
        val translated = temp.resolve("zh_TW/components/pagination.php")

        val sourceDocument = LanguageFileCodec.parse(source, "scheme")
        val translatedDocument = LanguageFileCodec.parse(translated, "scheme")
        val discovery = LanguageFolderDiscovery.discover(temp.toString())

        assertTrue(sourceDocument.issues.isEmpty())
        assertEquals("en", sourceDocument.locale)
        assertEquals("zh_TW", translatedDocument.locale)
        assertEquals("components.pagination", sourceDocument.namespace)
        assertEquals("en per page", sourceDocument.values["fields.records_per_page.label"])
        assertEquals(16, discovery.files.size)
        assertTrue(discovery.files.all { it.recognized })
        assertEquals(locales.toSet(), discovery.files.map { it.locale }.toSet())
        assertEquals(componentNames.map { "components.$it" }.toSet(), discovery.files.map { it.namespace }.toSet())
        val target = LanguageLocaleVersionSupport.buildTargets(listOf(sourceDocument, translatedDocument), "en", "es").single()
        assertEquals(temp.resolve("es/components/pagination.php").toAbsolutePath().normalize(), target.path)
    }

    @Test
    fun `recognizes every file in the provided nested PHP fixture`() {
        val fixture =
            generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
                .map { it.resolve("example-for-php-filament") }
                .first(Files::isDirectory)

        val discovery = LanguageFolderDiscovery.discover(fixture.toString())

        assertEquals(16, discovery.files.size)
        assertTrue(discovery.files.all { it.recognized })
        assertEquals(setOf("en", "zh_CN", "zh_HK", "zh_TW"), discovery.files.map { it.locale }.toSet())
        assertEquals(
            setOf("components.button", "components.copyable", "components.modal", "components.pagination"),
            discovery.files.map { it.namespace }.toSet(),
        )
        assertEquals(9, discovery.files.single { it.locale == "en" && it.namespace == "components.pagination" }.entryCount)
        assertEquals(7, discovery.files.single { it.locale == "zh_CN" && it.namespace == "components.pagination" }.entryCount)
    }

    @Test
    fun `parses Laravel language files with strict types declaration before return`() {
        val localeDir = temp.resolve("zh_TW").createDirectories()
        val file =
            localeDir.resolve("auth.php").apply {
                writeText(
                    """
                    <?php

                    declare(strict_types=1);

                    return [
                        'failed' => '這些憑證與我們的記錄不符。',
                        'password' => '提供的密碼錯誤。',
                    ];
                    """.trimIndent(),
                )
            }

        val parsed = LanguageFileCodec.parse(file, "scheme")

        assertTrue(parsed.issues.isEmpty())
        assertEquals("zh_TW", parsed.locale)
        assertEquals("auth", parsed.namespace)
        assertEquals(2, parsed.values.size)
    }

    @Test
    fun `rejects unsupported PHP declare directives`() {
        val file =
            temp.resolve("unsafe.php").apply {
                writeText("<?php declare(ticks=1); return ['key' => 'value'];")
            }

        val issue = LanguageFileCodec.parse(file, "scheme").issues.single()

        assertEquals("PARSE_ERROR", issue.code)
        assertTrue(issue.message.contains("declare(strict_types=1)"))
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
        val cases =
            mapOf(
                "en.json" to "{\"old\":\"value\"}",
                "en.yml" to "old: value\n",
                "messages.php" to "<?php return ['old' => 'value'];",
                "LanguageManagerBundle.properties" to "old=value\n",
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
    fun `parses Java properties syntax and locale suffix`() {
        val file =
            temp.resolve("LanguageManagerBundle_zh_TW.properties").apply {
                writeText(
                    "# JetBrains resource bundle\n" +
                        "greeting=哈囉\n" +
                        "escaped\\ key:escaped\\ value\n" +
                        "unicode=\\u4F60\\u597D\n" +
                        "continued=first\\\n  second\n" +
                        "equation=a=b:c\n",
                )
            }

        val parsed = LanguageFileCodec.parse(file, "scheme")

        assertTrue(parsed.issues.isEmpty())
        assertEquals("zh_TW", parsed.locale)
        assertEquals("LanguageManagerBundle", parsed.namespace)
        assertEquals("哈囉", parsed.values["greeting"])
        assertEquals("escaped value", parsed.values["escaped key"])
        assertEquals("你好", parsed.values["unicode"])
        assertEquals("firstsecond", parsed.values["continued"])
        assertEquals("a=b:c", parsed.values["equation"])

        parsed.values["path"] = "C:\\Users\\Language Manager"
        LanguageFileCodec.write(parsed)
        val reread = LanguageFileCodec.parse(file, "scheme")
        assertEquals("C:\\Users\\Language Manager", reread.values["path"])
    }

    @Test
    fun `reports malformed properties without throwing`() {
        val duplicate =
            temp.resolve("Duplicate.properties").apply {
                writeText("same=first\nsame=second\n")
            }
        val invalidUnicode =
            temp.resolve("Invalid.properties").apply {
                writeText("bad=\\u12XZ\n")
            }

        assertEquals(
            "PARSE_ERROR",
            LanguageFileCodec
                .parse(duplicate, "scheme")
                .issues
                .single()
                .code,
        )
        assertEquals(
            "PARSE_ERROR",
            LanguageFileCodec
                .parse(invalidUnicode, "scheme")
                .issues
                .single()
                .code,
        )
    }

    @Test
    fun `renders change preview without writing the source file`() {
        val original = "{\"empty\":\"\",\"kept\":\"value\"}"
        val file = temp.resolve("preview.json").apply { writeText(original) }
        val document = LanguageFileCodec.parse(file, "scheme")
        document.values["empty"] = "empty"

        val proposed = LanguageFileCodec.render(document)

        assertEquals(original, file.toFile().readText(), "預覽不可修改來源檔")
        assertEquals(
            "empty",
            Json
                .parseToJsonElement(proposed)
                .jsonObject
                .getValue("empty")
                .jsonPrimitive.content,
        )
    }

    @Test
    fun `safe access blocks service URIs device paths and unsupported files`() {
        assertFailsWith<IllegalArgumentException> { SafeLanguageFileAccess.validate("ldap://attacker/test.json") }
        assertFailsWith<IllegalArgumentException> { SafeLanguageFileAccess.validate("\\\\.\\pipe\\test.json") }
        val text = temp.resolve("test.txt").apply { writeText("x") }
        assertFailsWith<IllegalArgumentException> { SafeLanguageFileAccess.validate(text.toString()) }
    }

    @Test
    fun `creates a blank Spanish Laravel locale from every English namespace`() {
        val en = temp.resolve("lang/en").createDirectories()
        val auth = en.resolve("auth.php").apply { writeText("<?php return ['failed' => 'Invalid credentials'];") }
        val validation = en.resolve("validation.php").apply { writeText("<?php return ['required' => 'Required'];") }
        val sources = listOf(auth, validation).map { LanguageFileCodec.parse(it, "scheme") }

        val targets = LanguageLocaleVersionSupport.buildTargets(sources, "en", "es")

        assertEquals(setOf("auth.php", "validation.php"), targets.map { it.path.fileName.toString() }.toSet())
        assertTrue(
            targets.all {
                it.path.parent.fileName
                    .toString() == "es"
            },
        )
        targets.forEach { target ->
            target.path.parent.createDirectories()
            target.path.writeText(target.content)
            val parsed = LanguageFileCodec.parse(target.path, "scheme")
            assertEquals("es", parsed.locale)
            assertTrue(parsed.values.values.all(String::isEmpty))
        }
    }

    @Test
    fun `creates a blank Spanish properties bundle from the base bundle`() {
        val source =
            temp.resolve("LanguageManagerFrontendBundle.properties").apply {
                writeText("button.save=Save\ntab.translations=Translations\n")
            }
        val sourceDocument = LanguageFileCodec.parse(source, "scheme")

        val target =
            LanguageLocaleVersionSupport
                .buildTargets(
                    listOf(sourceDocument),
                    "en",
                    "es",
                ).single()

        assertEquals("LanguageManagerFrontendBundle_es.properties", target.path.fileName.toString())
        target.path.writeText(target.content)
        val parsed = LanguageFileCodec.parse(target.path, "scheme")
        assertEquals("es", parsed.locale)
        assertEquals("LanguageManagerFrontendBundle", parsed.namespace)
        assertTrue(parsed.values.values.all(String::isEmpty))
    }

    @Test
    fun `properties locale detection supports BCP 47 script and numeric region tags`() {
        val traditional = temp.resolve("Messages_zh-Hant.properties").apply { writeText("hello=哈囉\n") }
        val latinAmerica = temp.resolve("Messages_es-419.properties").apply { writeText("hello=Hola\n") }

        val traditionalDocument = LanguageFileCodec.parse(traditional, "scheme")
        val latinAmericaDocument = LanguageFileCodec.parse(latinAmerica, "scheme")

        assertEquals("zh-Hant", traditionalDocument.locale)
        assertEquals("Messages", traditionalDocument.namespace)
        assertEquals("es-419", latinAmericaDocument.locale)
        assertEquals("Messages", latinAmericaDocument.namespace)
    }

    @Test
    fun `new JSON locale keeps expanded array structure while clearing every translation`() {
        val source =
            temp.resolve("en.json").apply {
                writeText("""{"title":"Welcome","features":["Fast","Safe"]}""")
            }
        val target =
            LanguageLocaleVersionSupport
                .buildTargets(
                    listOf(LanguageFileCodec.parse(source, "scheme")),
                    "en",
                    "es",
                ).single()

        target.path.writeText(target.content)
        val parsed = LanguageFileCodec.parse(target.path, "scheme")
        assertEquals("", parsed.values["title"])
        assertEquals("", parsed.values["features.0"])
        assertEquals("", parsed.values["features.1"])
        assertTrue(listOf("features") in parsed.jsonArrayPaths)
    }

    @Test
    fun `folder discovery parses supported files and skips generated directories`() {
        temp.resolve("en.json").writeText("""{"hello":"Hello","bye":"Bye"}""")
        temp.resolve("LanguageManagerBundle_ja.properties").writeText("hello=こんにちは\n")
        temp.resolve("bad.yaml").writeText("root:\n\tkey: invalid indentation")
        temp.resolve("notes.txt").writeText("not a language file")
        temp
            .resolve("zh_TW")
            .createDirectories()
            .resolve("messages.php")
            .writeText("<?php return ['hello' => '哈囉'];")
        temp
            .resolve("vendor")
            .createDirectories()
            .resolve("ignored.json")
            .writeText("""{"ignored":"value"}""")

        val result = LanguageFolderDiscovery.discover(temp.toString())

        assertFalse(result.truncated)
        assertEquals(4, result.files.size)
        assertTrue(result.files.single { it.filePath.endsWith("en.json") }.let { it.recognized && it.entryCount == 2 && it.locale == "en" })
        assertTrue(
            result.files.single { it.filePath.endsWith("LanguageManagerBundle_ja.properties") }.let {
                it.recognized && it.entryCount == 1 && it.locale == "ja" && it.namespace == "LanguageManagerBundle"
            },
        )
        assertTrue(
            result.files.single { it.filePath.endsWith("messages.php") }.let {
                it.recognized && it.locale == "zh_TW" &&
                    it.namespace == "messages"
            },
        )
        assertFalse(result.files.single { it.filePath.endsWith("bad.yaml") }.recognized)
        assertTrue(result.files.none { it.filePath.endsWith("notes.txt") || it.filePath.contains("vendor") })
    }

    @Test
    fun `folder discovery combines locale directories and removes overlapping files`() {
        val lang = temp.resolve("lang").createDirectories()
        val en = lang.resolve("en").createDirectories()
        val zhCn = lang.resolve("zh_CN").createDirectories()
        val zhTw = lang.resolve("zh_TW").createDirectories()
        en.resolve("auth.php").writeText("<?php return ['failed' => 'Invalid credentials'];")
        en.resolve("validation.php").writeText("<?php return ['required' => 'Required'];")
        zhCn.resolve("auth.php").writeText("<?php return ['failed' => '账号或密码错误'];")
        zhCn.resolve("validation.php").writeText("<?php return ['required' => '必填'];")
        zhTw.resolve("auth.php").writeText("<?php return ['failed' => '帳號或密碼錯誤'];")
        zhTw.resolve("validation.php").writeText("<?php return ['required' => '必填'];")

        val result = LanguageFolderDiscovery.discover(listOf(lang.toString(), en.toString(), zhCn.toString(), zhTw.toString()))

        assertFalse(result.truncated)
        assertEquals(4, result.folderPaths.size)
        assertEquals(6, result.files.size)
        assertEquals(setOf("en", "zh_CN", "zh_TW"), result.files.map { it.locale }.toSet())
        assertEquals(setOf("auth", "validation"), result.files.map { it.namespace }.toSet())
        assertTrue(result.files.all { it.recognized && it.entryCount == 1 })
    }

    @Test
    fun `parser stops while building entries instead of retaining an oversized map`() {
        val file = temp.resolve("many.json").apply { writeText("""{"one":"1","two":"2","three":"3"}""") }

        val parsed = LanguageFileCodec.parse(file, "scheme", maxEntries = 2)

        assertTrue(parsed.values.isEmpty())
        assertTrue(
            parsed.issues
                .single()
                .message
                .contains("2"),
        )
    }

    @Test
    fun `parser rejects extreme structured nesting before JSON tree allocation`() {
        val nested = "{" + (1..129).joinToString("") { "\"level$it\":{" } + "\"value\":\"x\"" + "}".repeat(130)
        val file = temp.resolve("deep.json").apply { writeText(nested) }

        val parsed = LanguageFileCodec.parse(file, "scheme")

        assertTrue(parsed.values.isEmpty())
        assertTrue(
            parsed.issues
                .single()
                .message
                .contains("128"),
        )
    }

    @Test
    fun `folder discovery uses the configured new-scheme file budget`() {
        temp.resolve("large.json").writeText("""{"key":"value"}""" + " ".repeat(2_048))

        val result =
            LanguageFolderDiscovery.discover(
                temp.toString(),
                UsageScanSettingsDto(maxLanguageFileKb = 1),
            )

        assertFalse(result.files.single().recognized)
        assertTrue(
            result.files
                .single()
                .errorMessage
                .orEmpty()
                .contains("1"),
        )
    }
}
