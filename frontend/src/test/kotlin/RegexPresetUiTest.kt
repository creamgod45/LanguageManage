package cg.creamgod45

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RegexPresetUiTest {
    @Test
    fun `recommended framework patterns are valid and retain key capture`() {
        assertTrue(
            RegexPresetUi.presets
                .map {
                    it.name
                }.containsAll(
                    listOf(
                        "Laravel",
                        "Symfony",
                        "webman",
                        "Laminas / Zend",
                        "CodeIgniter",
                        "CakePHP",
                        "Yii",
                        "Phalcon",
                        "FuelPHP",
                        "Slim / Pixie / custom",
                        "Spring MessageSource",
                        "ResourceBundle",
                        "IntelliJ Platform Plugin",
                    ),
                ),
        )
        val patterns = RegexPresetUi.presets.flatMap { it.patterns }
        assertEquals(patterns.size, patterns.distinct().size)
        patterns.forEach { pattern ->
            assertTrue(pattern.length <= 512)
            assertTrue("(?<key>" in pattern)
            Regex(pattern)
        }
    }

    @Test
    fun `Rust i18n captures only literal macro keys`() {
        val preset = RegexPresetUi.presets.single {
            it.name == LanguageManagerBundle.message("settings.regex.preset.rust.i18n")
        }
        assertEquals(LanguageManagerBundle.message("settings.regex.preset.rust.group"), preset.group)
        val patterns = preset.patterns.map(::Regex)
        val cases = mapOf(
            """t!("hello")""" to "hello",
            """t!("hello", locale = "zh-CN")""" to "hello",
            """t!("messages.hello", name = "world")""" to "messages.hello",
            """t!("messages.hello", "name" => "world")""" to "messages.hello",
            """rust_i18n::t!("Calendar.week.monday")""" to "Calendar.week.monday",
            """t! { "hello" }""" to "hello",
            """t! [ "hello" ]""" to "hello",
            """t!(r"hello")""" to "hello",
            """rust_i18n::t!(r##"messages.hello"##, name = "world")""" to "messages.hello",
            "t! (\n  \"hello\"\n)" to "hello",
        )
        cases.forEach { (source, expected) ->
            assertEquals(listOf(expected), patterns.flatMap { pattern ->
                pattern.findAll(source).map { it.groups["key"]!!.value }.toList()
            }, source)
        }
        listOf(
            """t!(key)""", """t!(format!("hello {}", name))""",
            """i18n!("locales")""", """not_t!("hello")""",
            """other::t!("hello")""", """t!("hello" + suffix)""",
            """t!(r##"hello"#)""", """t!('hello')""",
            "t!(\"${"x".repeat(257)}\")",
        ).forEach { source -> assertTrue(patterns.none { it.containsMatchIn(source) }, source) }
    }

    @Test
    fun `Laravel key-only recommendation ignores uncertain namespace and group prefixes`() {
        val preset =
            RegexPresetUi.presets.single {
                it.name == LanguageManagerBundle.message("settings.regex.preset.laravel.key.only")
            }
        val pattern = Regex(preset.patterns.single())
        val cases =
            mapOf(
                "__('filament::components/button.messages.uploading_file')" to "messages.uploading_file",
                "__('components/filament.someLangKey1')" to "someLangKey1",
                "trans(\"auth.password.reset\")" to "password.reset",
            )

        cases.forEach { (source, expected) ->
            assertEquals(
                expected,
                pattern
                    .find(source)
                    ?.groups
                    ?.get("key")
                    ?.value,
                source,
            )
        }
    }
}
