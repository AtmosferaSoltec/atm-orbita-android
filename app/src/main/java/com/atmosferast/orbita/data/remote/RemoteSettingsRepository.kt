package com.atmosferast.orbita.data.remote

import com.atmosferast.orbita.domain.model.FxPair
import com.atmosferast.orbita.domain.model.UserSettings
import com.atmosferast.orbita.domain.repository.DataError
import com.atmosferast.orbita.domain.repository.DataException
import com.atmosferast.orbita.domain.repository.SettingsRepository
import io.ktor.client.call.body
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.HttpMethod
import java.math.RoundingMode
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onStart

/**
 * The user's currencies and exchange rate (atm-orbita-api, plan 02). REST does not tell when
 * something changes, so the last read is kept here, replaced by every answer of a write, and
 * dropped when the session changes.
 */
@Singleton
class RemoteSettingsRepository @Inject constructor(
    private val api: ApiSession,
    private val changes: RemoteChanges = RemoteChanges(),
) : SettingsRepository {

    private val cache = MutableStateFlow<UserSettings?>(null)

    init {
        api.onSessionChange { cache.value = null }
    }

    override fun observeSettings(): Flow<UserSettings> =
        cache.onStart { if (cache.value == null) load() }.filterNotNull()
            .reloadingOn(changes) { load() }

    /** A pair is only saved together with a rate above zero; the API checks it again. */
    override suspend fun setFx(fx: FxPair) {
        val rate = fx.rate?.takeIf { it.signum() > 0 } ?: throw DataException(DataError.FX_NOT_CONFIGURED)
        // The API keeps 6 decimals, like the inverse the app computes when the pair is swapped.
        val request = SetFxRequest(fx.main, fx.secondary, rate.setScale(6, RoundingMode.HALF_UP))
        send(HttpMethod.Put, "settings/fx", request)
    }

    override suspend fun setDisplayCurrency(currency: String) {
        send(HttpMethod.Patch, "settings", UpdateSettingsRequest(displayCurrency = currency))
    }

    private suspend fun load() {
        val response = api.authorized { url("settings") }.requireSuccess()
        cache.value = response.body<DataEnvelope<SettingsDto>>().data.toUserSettings()
    }

    private suspend inline fun <reified T : Any> send(method: HttpMethod, path: String, payload: T) {
        val response = api.authorized {
            this.method = method
            url(path)
            setBody(payload)
        }.requireSuccess()
        cache.value = response.body<DataEnvelope<SettingsDto>>().data.toUserSettings()
        changes.bump()
    }
}
