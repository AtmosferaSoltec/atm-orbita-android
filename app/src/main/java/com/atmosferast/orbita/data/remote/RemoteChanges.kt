package com.atmosferast.orbita.data.remote

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Bumped by every remote repository after a write it made. What shows pieces of several of them
 * at once, like Inicio, reads again when it changes: REST does not tell when something changed.
 */
@Singleton
class RemoteChanges @Inject constructor() {
    private val version = MutableStateFlow(0)

    val changes: StateFlow<Int> = version.asStateFlow()

    fun bump() = version.update { it + 1 }
}
