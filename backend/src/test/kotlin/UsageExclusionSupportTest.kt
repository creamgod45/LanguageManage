package cg.creamgod45

import java.nio.file.Path
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class UsageExclusionSupportTest {
    private val tempRoots = mutableListOf<Path>()

    private fun newTempDir(prefix: String): Path {
        val dir = Files.createTempDirectory(prefix)
        tempRoots.add(dir)
        return dir
    }

    @AfterTest
    fun cleanup() {
        tempRoots.forEach { it.toFile().deleteRecursively() }
    }

    /**
     * The exact pre-fix algorithm: `require(...)` inside `.map { }`, so the first folder that is the
     * scan root or lives outside it throws and aborts the whole batch. Kept here to *confirm* the
     * defect the current [UsageExclusionSupport.resolve] fixes.
     */
    private fun legacyRelativeDirectories(
        scanRoot: Path,
        rawFolderPaths: List<String>,
    ): List<String> {
        val root = scanRoot.toRealPath()
        return rawFolderPaths
            .map { Path.of(it).toRealPath() }
            .map { folder ->
                require(folder != root && folder.startsWith(root)) { "outside scan root: $folder" }
                root.relativize(folder).joinToString("/") { it.toString() }
            }.distinct()
    }

    @Test
    fun `creates precise deduplicated relative exclusions below scan root`() {
        val root = newTempDir("lm-exclusions")
        val cache = root.resolve("src/generated/cache").createDirectories()
        val reports = root.resolve("src/generated/reports").createDirectories()

        val resolution =
            UsageExclusionSupport.resolve(root, listOf(cache.toString(), reports.toString(), cache.toString()))

        assertEquals(listOf("src/generated/cache", "src/generated/reports"), resolution.relativeDirectories)
        assertTrue(resolution.skippedDirectories.isEmpty())
    }

    // Case A — scheme base path points at a subfolder (scan root = proj/resources/lang). The user
    // multi-selects an in-root folder to exclude plus a sibling that is OUTSIDE the scan root and is
    // one of the default name-rule exclusions (`vendor`). Pre-fix this aborted the whole batch, so
    // the in-root folder the user actually wanted excluded was lost.
    @Test
    fun `case A keeps the in-root folder when an outside sibling is also selected`() {
        val proj = newTempDir("lm-proj")
        val scanRoot = proj.resolve("resources/lang").createDirectories()
        val generated = scanRoot.resolve("generated").createDirectories()
        val outsideVendor = proj.resolve("vendor").createDirectories()

        // Confirm the problem: the pre-fix algorithm throws and excludes nothing.
        assertFailsWith<IllegalArgumentException> {
            legacyRelativeDirectories(scanRoot, listOf(generated.toString(), outsideVendor.toString()))
        }

        // Fixed behavior: the in-root folder is excluded, the outside sibling is skipped and reported.
        val resolution =
            UsageExclusionSupport.resolve(scanRoot, listOf(generated.toString(), outsideVendor.toString()))

        assertEquals(listOf("generated"), resolution.relativeDirectories)
        assertEquals(listOf(outsideVendor.toString()), resolution.skippedDirectories)
    }

    // Case B — scan root = project root. The user multi-selects a folder to exclude but also (easily,
    // by accident) includes the project root node itself. Pre-fix the root failed `folder != root`,
    // threw, and the folder they wanted excluded was lost.
    @Test
    fun `case B keeps the other folders when the scan root itself is also selected`() {
        val proj = newTempDir("lm-proj-root")
        val src = proj.resolve("src").createDirectories()

        // Confirm the problem: the pre-fix algorithm throws and excludes nothing.
        assertFailsWith<IllegalArgumentException> {
            legacyRelativeDirectories(proj, listOf(proj.toString(), src.toString()))
        }

        // Fixed behavior: src is excluded, the scan root itself is skipped and reported.
        val resolution = UsageExclusionSupport.resolve(proj, listOf(proj.toString(), src.toString()))

        assertEquals(listOf("src"), resolution.relativeDirectories)
        assertEquals(listOf(proj.toString()), resolution.skippedDirectories)
    }

    @Test
    fun `skips the scan root and folders outside it instead of throwing`() {
        val root = newTempDir("lm-exclusions")
        val outside = newTempDir("lm-exclusions-outside")

        val resolution = UsageExclusionSupport.resolve(root, listOf(root.toString(), outside.toString()))

        assertTrue(resolution.relativeDirectories.isEmpty())
        assertEquals(listOf(root.toString(), outside.toString()), resolution.skippedDirectories)
    }
}
