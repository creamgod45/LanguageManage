package cg.creamgod45

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Cancels obsolete scheme reads while the service's mutation mutex remains independent. */
internal class LatestLoadController {
    private val mutex = Mutex()

    @Volatile
    private var generation = 0L
    private var activeJob: Job? = null

    suspend fun run(action: suspend (Long) -> Unit) {
        val job = currentCoroutineContext()[Job] ?: error("A coroutine Job is required for scheme loading")
        val token: Long
        val previousJob: Job?
        mutex.withLock {
            previousJob = activeJob
            token = ++generation
            activeJob = job
        }
        if (previousJob !== job) previousJob?.cancel(CancellationException("Superseded by a newer language scheme load"))
        try {
            action(token)
        } finally {
            mutex.withLock {
                if (activeJob === job && generation == token) activeJob = null
            }
        }
    }

    suspend fun ensureCurrent(token: Long?) {
        currentCoroutineContext().ensureActive()
        if (!isCurrent(token)) throw CancellationException("Discarding an obsolete language scheme load")
    }

    fun isCurrent(token: Long?): Boolean = token == null || token == generation
}
