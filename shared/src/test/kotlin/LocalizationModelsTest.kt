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
}
