package com.atmosferast.orbita.data.demo

import com.atmosferast.orbita.domain.model.DatePeriod
import com.atmosferast.orbita.domain.model.Entry
import com.atmosferast.orbita.domain.model.Movement
import com.atmosferast.orbita.domain.model.MovementDraft
import com.atmosferast.orbita.domain.repository.AccountsRepository
import com.atmosferast.orbita.domain.repository.EntriesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Temporary, until card payments move to the API (plan 10): movements and transfers come from
 * [remote], but paying a card purchase still writes its expense in the demo store. The lists mix
 * in those demo expenses when they come from the user's accounts of the API ([accounts]), and
 * editing or deleting one goes to [demo]. The sample entries point to the demo's own accounts
 * and are left out.
 */
class DemoEntriesMix(
    private val remote: EntriesRepository,
    private val demo: DemoEntriesRepository,
    private val accounts: AccountsRepository,
    private val store: DemoStore,
) : EntriesRepository by remote {

    override fun observeEntries(period: DatePeriod): Flow<List<Entry>> =
        combine(remote.observeEntries(period), sessionEntries()) { real, local ->
            (real + local.filter { it.date in period }).sortedByDescending { it.date }
        }

    override fun observeRecentEntries(limit: Int): Flow<List<Entry>> =
        combine(remote.observeRecentEntries(limit), sessionEntries()) { real, local ->
            (real + local).sortedByDescending { it.date }.take(limit)
        }

    override suspend fun updateMovement(id: String, draft: MovementDraft) =
        if (isDemoMovement(id)) demo.updateMovement(id, draft) else remote.updateMovement(id, draft)

    override suspend fun deleteMovement(id: String) =
        if (isDemoMovement(id)) demo.deleteMovement(id) else remote.deleteMovement(id)

    private fun isDemoMovement(id: String) = store.current.movements.any { it.id == id }

    private fun sessionEntries(): Flow<List<Entry>> =
        combine(accounts.observeAccounts(), store.observe { it.entries }) { own, entries ->
            val ids = own.map { it.id }.toSet()
            entries.filter { it is Movement && it.account.id in ids }
        }
}
