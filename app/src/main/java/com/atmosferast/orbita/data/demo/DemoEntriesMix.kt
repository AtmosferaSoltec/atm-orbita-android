package com.atmosferast.orbita.data.demo

import com.atmosferast.orbita.domain.model.DatePeriod
import com.atmosferast.orbita.domain.model.Entry
import com.atmosferast.orbita.domain.model.Movement
import com.atmosferast.orbita.domain.model.MovementDraft
import com.atmosferast.orbita.domain.model.Transfer
import com.atmosferast.orbita.domain.model.TransferDraft
import com.atmosferast.orbita.domain.repository.AccountsRepository
import com.atmosferast.orbita.domain.repository.EntriesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Temporary, until transfers (plan 06) and card payments (plan 10) move to the API: movements
 * come from [remote], and transfers stay in [demo]. The lists mix in the demo entries that touch
 * only the user's accounts of the API ([accounts]): transfers made in this session and the
 * expense of a card payment. The sample entries point to the demo's own accounts and are left out.
 */
class DemoEntriesMix(
    private val remote: EntriesRepository,
    private val demo: DemoEntriesRepository,
    private val accounts: AccountsRepository,
    private val store: DemoStore,
) : EntriesRepository {

    override fun observeEntries(period: DatePeriod): Flow<List<Entry>> =
        combine(remote.observeEntries(period), sessionEntries()) { real, local ->
            (real + local.filter { it.date in period }).sortedByDescending { it.date }
        }

    override fun observeRecentEntries(limit: Int): Flow<List<Entry>> =
        combine(remote.observeRecentEntries(limit), sessionEntries()) { real, local ->
            (real + local).sortedByDescending { it.date }.take(limit)
        }

    override suspend fun createMovement(draft: MovementDraft) = remote.createMovement(draft)

    override suspend fun updateMovement(id: String, draft: MovementDraft) =
        if (isDemoMovement(id)) demo.updateMovement(id, draft) else remote.updateMovement(id, draft)

    override suspend fun deleteMovement(id: String) =
        if (isDemoMovement(id)) demo.deleteMovement(id) else remote.deleteMovement(id)

    override suspend fun createTransfer(draft: TransferDraft) = demo.createTransfer(draft)
    override suspend fun updateTransfer(id: String, draft: TransferDraft) = demo.updateTransfer(id, draft)
    override suspend fun deleteTransfer(id: String) = demo.deleteTransfer(id)

    private fun isDemoMovement(id: String) = store.current.movements.any { it.id == id }

    private fun sessionEntries(): Flow<List<Entry>> =
        combine(accounts.observeAccounts(), store.observe { it.entries }) { own, entries ->
            val ids = own.map { it.id }.toSet()
            entries.filter { entry ->
                when (entry) {
                    is Movement -> entry.account.id in ids
                    is Transfer -> entry.from.id in ids && entry.to.id in ids
                }
            }
        }
}
