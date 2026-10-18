package com.TopMob.bookmt.presentation.reader

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch

/** A newly opened reader must not read Room before the preceding reader's exit checkpoint. */
internal object ReaderExitWrites {
    private val pending = mutableMapOf<Long, Job>()

    fun enqueue(scope: CoroutineScope, bookId: Long, write: suspend () -> Unit) {
        val job = synchronized(pending) {
            val previous = pending[bookId]
            scope.launch(NonCancellable, start = CoroutineStart.LAZY) {
                previous?.join()
                write()
            }.also { pending[bookId] = it }
        }
        job.invokeOnCompletion {
            synchronized(pending) { if (pending[bookId] === job) pending.remove(bookId) }
        }
        job.start()
    }

    suspend fun await(bookId: Long) {
        while (true) {
            val job = synchronized(pending) { pending[bookId] } ?: return
            job.join()
        }
    }
}
