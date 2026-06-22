package com.TopMob.bookmt.core.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Abstraction over Coroutine dispatchers so that heavy work (parsing, DB, TTS inference) can be
 * pushed off the main thread, and so dispatchers can be swapped for deterministic ones in tests.
 *
 * - [main]      UI / Compose state updates.
 * - [io]        Disk, network, Room, file parsing.
 * - [default]   CPU-bound work (text segmentation, pagination math).
 */
interface DispatcherProvider {
    val main: CoroutineDispatcher
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
}

/** Production implementation backed by [Dispatchers]. */
class DefaultDispatcherProvider : DispatcherProvider {
    override val main: CoroutineDispatcher = Dispatchers.Main.immediate
    override val io: CoroutineDispatcher = Dispatchers.IO
    override val default: CoroutineDispatcher = Dispatchers.Default
}
