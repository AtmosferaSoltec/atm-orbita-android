package com.atmosferast.orbita.data.remote

import com.atmosferast.orbita.domain.model.Account
import com.atmosferast.orbita.domain.model.AccountDraft
import com.atmosferast.orbita.domain.repository.AccountsRepository
import io.ktor.client.call.body
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.HttpMethod
import java.math.RoundingMode
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update

/**
 * The user's accounts with the balances the API computes (atm-orbita-api, plan 04). Like the
 * settings, the last list read is kept here, updated with the answer of every write, and dropped
 * when the session changes.
 */
@Singleton
class RemoteAccountsRepository @Inject constructor(
    private val api: ApiSession,
) : AccountsRepository {

    private val cache = MutableStateFlow<List<Account>?>(null)

    init {
        api.onSessionChange { cache.value = null }
    }

    override fun observeAccounts(): Flow<List<Account>> =
        cache.onStart { if (cache.value == null) load() }.filterNotNull()

    override suspend fun create(draft: AccountDraft) {
        val request = CreateAccountRequest(
            id = UUID.randomUUID().toString(),
            name = draft.name,
            type = draft.type.toApi(),
            currency = draft.currency,
            initialBalance = draft.initialBalance.setScale(2, RoundingMode.HALF_UP),
            includeInSavings = draft.includeInSavings,
        )
        val created = send(HttpMethod.Post, "accounts", request)
        cache.update { list -> list?.let { it + created } }
    }

    override suspend fun update(id: String, draft: AccountDraft) {
        patch(id, UpdateAccountRequest(draft.name, draft.type.toApi(), draft.includeInSavings))
    }

    override suspend fun setIncludeInSavings(id: String, include: Boolean) {
        patch(id, UpdateAccountRequest(includeInSavings = include))
    }

    override suspend fun archive(id: String) {
        api.authorized {
            method = HttpMethod.Post
            url("accounts/$id/archive")
        }.requireSuccess()
        cache.update { list -> list?.filterNot { it.id == id } }
    }

    private suspend fun patch(id: String, request: UpdateAccountRequest) {
        val updated = send(HttpMethod.Patch, "accounts/$id", request)
        cache.update { list -> list?.map { if (it.id == id) updated else it } }
    }

    private suspend fun load() {
        val response = api.authorized { url("accounts") }.requireSuccess()
        cache.value = response.body<DataEnvelope<AccountListDto>>().data.accounts.map { it.toAccount() }
    }

    private suspend inline fun <reified T : Any> send(method: HttpMethod, path: String, payload: T): Account {
        val response = api.authorized {
            this.method = method
            url(path)
            setBody(payload)
        }.requireSuccess()
        return response.body<DataEnvelope<AccountDto>>().data.toAccount()
    }
}
