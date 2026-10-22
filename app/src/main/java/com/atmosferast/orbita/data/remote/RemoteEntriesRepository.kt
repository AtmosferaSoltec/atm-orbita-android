package com.atmosferast.orbita.data.remote

import com.atmosferast.orbita.domain.model.DatePeriod
import com.atmosferast.orbita.domain.model.Entry
import com.atmosferast.orbita.domain.model.MovementDraft
import com.atmosferast.orbita.domain.model.TransferDraft
import com.atmosferast.orbita.domain.repository.DataError
import com.atmosferast.orbita.domain.repository.DataException
import com.atmosferast.orbita.domain.repository.EntriesRepository
import io.ktor.client.call.body
import io.ktor.client.request.parameter
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.HttpMethod
import java.math.RoundingMode
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update

/**
 * Incomes and expenses (atm-orbita-api, plan 05). A list is read again, all its pages, after
 * every change made here and when the session changes; the account balances too, since every
 * movement moves one.
 *
 * Transfers are still demo data until plan 06: their methods fail here, and the app wires them
 * to the demo store (data/demo/DemoEntriesMix).
 */
@Singleton
class RemoteEntriesRepository @Inject constructor(
    private val api: ApiSession,
    private val accounts: RemoteAccountsRepository,
) : EntriesRepository {

    /** Bumped after every write, so open lists read again. */
    private val version = MutableStateFlow(0)

    init {
        api.onSessionChange { version.update { it + 1 } }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeEntries(period: DatePeriod): Flow<List<Entry>> =
        version.flatMapLatest { flow { emit(readAll(period.from, period.to)) } }

    /** The newest ones of the last 10 years (the longest period the API lists at once). */
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeRecentEntries(limit: Int): Flow<List<Entry>> =
        version.flatMapLatest {
            flow {
                // Tomorrow, so a movement saved today in a zone ahead of the phone's still shows.
                val to = LocalDate.now().plusDays(1)
                emit(readPage(to.minusYears(10).plusDays(1), to, page = 1, pageSize = limit).data.map { it.toMovement() })
            }
        }

    override suspend fun createMovement(draft: MovementDraft) {
        send(HttpMethod.Post, "transactions", draft.toRequest(id = UUID.randomUUID().toString()))
    }

    override suspend fun updateMovement(id: String, draft: MovementDraft) {
        send(HttpMethod.Patch, "transactions/$id", draft.toRequest(id = null))
    }

    override suspend fun deleteMovement(id: String) {
        api.authorized {
            method = HttpMethod.Delete
            url("transactions/$id")
        }.requireSuccess()
        changed()
    }

    override suspend fun createTransfer(draft: TransferDraft) = notYet()
    override suspend fun updateTransfer(id: String, draft: TransferDraft) = notYet()
    override suspend fun deleteTransfer(id: String) = notYet()

    private fun notYet(): Nothing = throw DataException(DataError.UNKNOWN)

    private suspend fun readAll(from: LocalDate, to: LocalDate): List<Entry> {
        val out = mutableListOf<Entry>()
        var page = 1
        do {
            val result = readPage(from, to, page, PAGE_SIZE)
            out += result.data.map { it.toMovement() }
            page++
        } while (out.size < result.meta.total && result.data.isNotEmpty())
        return out
    }

    private suspend fun readPage(from: LocalDate, to: LocalDate, page: Int, pageSize: Int): EntryPageDto =
        api.authorized {
            url("entries")
            parameter("from", from.toString())
            parameter("to", to.toString())
            parameter("page", page)
            parameter("page_size", pageSize)
        }.requireSuccess().body()

    private suspend fun send(method: HttpMethod, path: String, payload: TransactionRequest) {
        api.authorized {
            this.method = method
            url(path)
            setBody(payload)
        }.requireSuccess()
        changed()
    }

    private suspend fun changed() {
        version.update { it + 1 }
        accounts.reload()
    }

    private fun MovementDraft.toRequest(id: String?) = TransactionRequest(
        id = id,
        accountId = accountId ?: throw DataException(DataError.NOT_FOUND),
        categoryId = categoryId ?: throw DataException(DataError.NOT_FOUND),
        amount = (amount ?: throw DataException(DataError.UNKNOWN)).setScale(2, RoundingMode.HALF_UP),
        description = description,
        occurredOn = date?.toString(),
    )

    private companion object {
        /** The most the API gives per page. */
        const val PAGE_SIZE = 100
    }
}
