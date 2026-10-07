package com.atmosferast.orbita.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atmosferast.orbita.domain.model.ValidationError
import com.atmosferast.orbita.domain.repository.DataError
import com.atmosferast.orbita.domain.repository.DataException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** Something the user must be told: a form that cannot be saved yet, or a failed operation. */
sealed interface UiMessage {
    data class Invalid(val error: ValidationError) : UiMessage
    data class Failed(val error: DataError) : UiMessage
}

/** One queue of messages for the whole app; the root shows them one by one. */
@Singleton
class MessageBus @Inject constructor() {
    private val channel = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = channel.receiveAsFlow()

    fun post(message: UiMessage) {
        channel.trySend(message)
    }
}

/** What a screen shows while its data arrives, when it fails and once it is there. */
sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data object Failed : Load<Nothing>
    data class Ready<T>(val value: T) : Load<T>
}

fun <T> Flow<T>.asLoad(): Flow<Load<T>> =
    map<T, Load<T>> { Load.Ready(it) }
        .onStart { emit(Load.Loading) }
        .catch { emit(Load.Failed) }

/** Keeps a screen's state alive through a rotation, and stops collecting soon after it leaves. */
val WhileScreenVisible: SharingStarted = SharingStarted.WhileSubscribed(5_000)

/** Base of the ViewModels that save things. */
abstract class ActionViewModel(private val messages: MessageBus) : ViewModel() {
    private var running = false

    /**
     * Runs a write. With an [invalid] draft nothing is saved and the user is told why. [onDone]
     * runs only if [action] succeeds; a failure is reported instead. Taps while a write is
     * still running are ignored.
     */
    protected fun act(
        invalid: ValidationError? = null,
        onDone: () -> Unit = {},
        action: suspend () -> Unit,
    ) {
        if (invalid != null) {
            messages.post(UiMessage.Invalid(invalid))
            return
        }
        if (running) return
        running = true
        viewModelScope.launch {
            try {
                action()
                onDone()
            } catch (e: DataException) {
                messages.post(UiMessage.Failed(e.error))
            } finally {
                running = false
            }
        }
    }
}
