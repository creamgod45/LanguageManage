package cg.creamgod45

import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UsageExclusionSupportTest {
    private val root = Files.createTempDirectory("language-manager-exclusions")

    @AfterTest
    fun cleanup() {
        root.toFile().deleteRecursively()
    }

    @Test
    fun `creates precise relative exclusions below scan root`() {
        val cache = root.resolve("src/generated/cache").createDirectories()
        val reports = root.resolve("src/generated/reports").createDirectories()

        assertEquals(
            listOf("src/generated/cache", "src/generated/reports"),
            UsageExclusionSupport.relativeDirectories(root, listOf(cache.toString(), reports.toString(), cache.toString())),
        )
    }

    @Test
    fun `rejects scan root and folders outside it`() {
        val outside = Files.createTempDirectory("language-manager-exclusions-outside")
        try {
            assertFailsWith<IllegalArgumentException> {
                UsageExclusionSupport.relativeDirectories(root, listOf(root.toString()))
            }
            assertFailsWith<IllegalArgumentException> {
                UsageExclusionSupport.relativeDirectories(root, listOf(outside.toString()))
            }
        } finally {
            outside.toFile().deleteRecursively()
        }
    }
}
