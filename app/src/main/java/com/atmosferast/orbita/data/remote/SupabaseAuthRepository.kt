package com.atmosferast.orbita.data.remote

import com.atmosferast.orbita.domain.model.Credentials
import com.atmosferast.orbita.domain.model.SessionState
import com.atmosferast.orbita.domain.repository.AuthRepository
import com.atmosferast.orbita.domain.repository.DataError
import com.atmosferast.orbita.domain.repository.DataException
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Email + password against Supabase Auth. The library stores the session and refreshes the
 * token by itself; the app only watches [session].
 */
class SupabaseAuthRepository @Inject constructor(private val client: SupabaseClient) : AuthRepository {

    override val session: Flow<SessionState> = client.auth.sessionStatus
        .map { status ->
            when (status) {
                SessionStatus.Initializing -> SessionState.Loading
                is SessionStatus.Authenticated -> signedIn(status.session.user?.email)
                is SessionStatus.NotAuthenticated -> SessionState.SignedOut
                // The token could not be renewed (e.g. offline) but the session is still there.
                is SessionStatus.RefreshFailure ->
                    client.auth.currentSessionOrNull()?.let { signedIn(it.user?.email) }
                        ?: SessionState.SignedOut
            }
        }
        .distinctUntilChanged()

    private fun signedIn(email: String?) = SessionState.SignedIn(email.orEmpty())

    override suspend fun signIn(credentials: Credentials) = translating {
        client.auth.signInWith(Email) {
            email = credentials.email.trim()
            password = credentials.password
        }
    }

    override suspend fun signUp(credentials: Credentials) = translating {
        client.auth.signUpWith(Email) {
            email = credentials.email.trim()
            password = credentials.password
        }
        // With "Confirm email" on, the project creates the user but opens no session yet.
        if (client.auth.currentSessionOrNull() == null) {
            throw DataException(DataError.EMAIL_NOT_CONFIRMED)
        }
    }

    override suspend fun signOut() = translating { client.auth.signOut() }
}

/** Runs [block] turning the errors of supabase-kt into [DataException]. */
internal suspend fun translating(block: suspend () -> Unit) {
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: DataException) {
        throw e
    } catch (e: AuthRestException) {
        throw DataException(
            when (e.errorCode) {
                AuthErrorCode.InvalidCredentials, AuthErrorCode.UserNotFound ->
                    DataError.INVALID_CREDENTIALS
                AuthErrorCode.UserAlreadyExists, AuthErrorCode.EmailExists ->
                    DataError.EMAIL_ALREADY_REGISTERED
                AuthErrorCode.EmailNotConfirmed -> DataError.EMAIL_NOT_CONFIRMED
                else -> DataError.UNKNOWN
            },
            e,
        )
    } catch (e: RestException) {
        throw DataException(DataError.UNKNOWN, e)
    } catch (e: HttpRequestException) {
        throw DataException(DataError.NETWORK, e)
    } catch (e: IOException) {
        throw DataException(DataError.NETWORK, e)
    }
}
