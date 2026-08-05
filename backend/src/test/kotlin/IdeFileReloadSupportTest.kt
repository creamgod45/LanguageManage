package cg.creamgod45

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.concurrency.AppExecutorUtil
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.concurrent.CompletableFuture
import kotlin.io.path.createTempDirectory
import kotlin.test.assertEquals

class IdeFileReloadSupportTest : BasePlatformTestCase() {
    fun testReloadsAnAlreadyCachedIdeDocumentFromTheFinalDiskContent() {
        val directory = createTempDirectory("language-manager-reload-")
        val path = directory.resolve("messages.json")
        try {
            Files.writeString(path, "{\"message\":\"before\"}", StandardCharsets.UTF_8)
            val virtualFile =
                requireNotNull(
                    LocalFileSystem.getInstance().refreshAndFindFileByPath(path.toString().replace('\\', '/')),
                )
            val document = requireNotNull(FileDocumentManager.getInstance().getDocument(virtualFile))

            Files.writeString(path, "{\"message\":\"after\"}", StandardCharsets.UTF_8)
            val result = IdeFileReloadSupport.reloadFromDisk(listOf(path, path))

            assertEquals("{\"message\":\"after\"}", document.text)
            assertEquals(IdeFileReloadResult(1, 1, 1, 1), result)
        } finally {
            runCatching { Files.deleteIfExists(path) }
            runCatching { Files.deleteIfExists(directory) }
        }
    }

    fun testReloadFromBackgroundUsesAWriteSafeNonModalEdtContext() {
        val directory = createTempDirectory("language-manager-background-reload-")
        val path = directory.resolve("messages.json")
        try {
            Files.writeString(path, "{\"message\":\"before\"}", StandardCharsets.UTF_8)
            val virtualFile =
                requireNotNull(
                    LocalFileSystem.getInstance().refreshAndFindFileByPath(path.toString().replace('\\', '/')),
                )
            val document = requireNotNull(FileDocumentManager.getInstance().getDocument(virtualFile))
            Files.writeString(path, "{\"message\":\"after-background\"}", StandardCharsets.UTF_8)

            val future =
                CompletableFuture.supplyAsync(
                    { IdeFileReloadSupport.reloadFromDisk(listOf(path)) },
                    AppExecutorUtil.getAppExecutorService(),
                )
            val result = PlatformTestUtil.waitForFuture(future, 10_000)

            assertEquals("{\"message\":\"after-background\"}", document.text)
            assertEquals(IdeFileReloadResult(1, 1, 1, 1), result)
        } finally {
            runCatching { Files.deleteIfExists(path) }
            runCatching { Files.deleteIfExists(directory) }
        }
    }
}
