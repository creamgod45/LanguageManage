package cg.creamgod45.localization

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalizationModelsTest {
    @Test
    fun `old schemes receive default usage scan settings`() {
        val scheme =
            Json.decodeFromString<LanguageSchemeDto>(
                """{"id":"old","name":"Legacy","files":["en.json"],"updatedAtEpochMs":1}""",
            )

        assertEquals("", scheme.usageScanSettings.basePath)
        assertTrue(scheme.localeNotes.isEmpty())
        assertEquals(DEFAULT_USAGE_REGEX_PATTERNS, scheme.usageScanSettings.regexPatterns)
        assertEquals(DEFAULT_USAGE_EXCLUDED_DIRECTORIES, scheme.usageScanSettings.excludedDirectories)
        assertTrue("vendor" in scheme.usageScanSettings.excludedDirectories)
        assertTrue(".github" in scheme.usageScanSettings.excludedDirectories)
        assertTrue("storage" in scheme.usageScanSettings.excludedDirectories)
        assertTrue(".idea" in scheme.usageScanSettings.excludedDirectories)
        assertTrue(".gradle" in scheme.usageScanSettings.excludedDirectories)
        assertTrue("database" in scheme.usageScanSettings.excludedDirectories)
        assertEquals(
            "中文.key1",
            Regex(DEFAULT_USAGE_REGEX_PATTERNS.single())
                .find("translate('中文.key1')")
                ?.groups
                ?.get("key")
                ?.value,
        )
        assertEquals(
            "Not powered on or not detected",
            Regex(DEFAULT_USAGE_REGEX_PATTERNS.single())
                .find("translate(\"Not powered on or not detected\")")
                ?.groups
                ?.get("key")
                ?.value,
        )
    }

    @Test
    fun `scheme locale notes survive serialization while old transfers default empty`() {
        val scheme = LanguageSchemeDto("scheme", "Demo", listOf("en.json"), 1, localeNotes = mapOf("es-MX" to "formal"))
        val restored = Json.decodeFromString<LanguageSchemeDto>(Json.encodeToString(LanguageSchemeDto.serializer(), scheme))
        val oldPortable = Json.decodeFromString<PortableLanguageSchemeDto>("""{"name":"Old","files":["en.json"]}""")

        assertEquals(mapOf("es-MX" to "formal"), restored.localeNotes)
        assertTrue(oldPortable.localeNotes.isEmpty())
    }
}
