package com.atmosferast.orbita.data.remote

import com.atmosferast.orbita.domain.repository.DataException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Bumped by every remote repository after a write it made. What shows pieces of several of them
 * at once, like Inicio, reads again when it changes: REST does not tell when something changed.
 */
@Singleton
class RemoteChanges @Inject constructor() {
    private val version = MutableStateFlow(0)

    val changes: StateFlow<Int> = version.asStateFlow()

    private val refreshRequests =
        MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Asks the repositories that keep a list in memory to read it again. */
    internal val refreshes: SharedFlow<Unit> = refreshRequests.asSharedFlow()

    fun bump() = version.update { it + 1 }

    /**
     * The app came back to the foreground: everything on screen is read again, since it may
     * have changed meanwhile on another device.
     */
    fun refreshAll() {
        refreshRequests.tryEmit(Unit)
        bump()
    }
}

/**
 * This flow, and while it is collected, [reload] each time [RemoteChanges.refreshAll] asks.
 * A reload that fails (e.g. offline) keeps what was shown.
 */
internal fun <T> Flow<T>.reloadingOn(changes: RemoteChanges, reload: suspend () -> Unit): Flow<T> =
    channelFlow {
        launch {
            changes.refreshes.collect {
                try {
                    reload()
                } catch (_: DataException) {
                }
            }
        }
        collect { send(it) }
    }
