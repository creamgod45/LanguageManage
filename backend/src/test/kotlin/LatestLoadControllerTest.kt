package cg.creamgod45

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class LatestLoadControllerTest {
    @Test
    fun `new request cancels old load and invalidates its generation`() =
        runBlocking {
            val controller = LatestLoadController()
            val firstStarted = CompletableDeferred<Unit>()
            var firstToken = 0L
            var secondToken = 0L

            val first =
                launch {
                    controller.run { token ->
                        firstToken = token
                        firstStarted.complete(Unit)
                        awaitCancellation()
                    }
                }
            firstStarted.await()
            val second =
                launch {
                    controller.run { token ->
                        secondToken = token
                        controller.ensureCurrent(token)
                    }
                }

            joinAll(first, second)

            assertTrue(first.isCancelled)
            assertFalse(second.isCancelled)
            assertNotEquals(firstToken, secondToken)
            assertFalse(controller.isCurrent(firstToken))
            assertTrue(controller.isCurrent(secondToken))
        }
}
