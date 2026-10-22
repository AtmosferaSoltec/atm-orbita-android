package com.atmosferast.orbita.data.demo

import com.atmosferast.orbita.domain.model.Account
import com.atmosferast.orbita.domain.repository.AccountsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest

/**
 * Temporary, until transfers and card payments move to the API (plans 06 and 10 of
 * atm-orbita-api), like [DemoCategoryMirror]: the accounts come from the API but transfers and
 * card payments are still demo data, and the demo store must know every account they point to.
 *
 * Each list read from [remote] is copied into [store] with the API balance as its starting
 * point, and the balances shown add the demo entries of this session on top. The demo's own
 * accounts stay there archived, so the sample entries keep theirs.
 */
class DemoAccountMirror(
    private val remote: AccountsRepository,
    private val store: DemoStore,
) : AccountsRepository by remote {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeAccounts(): Flow<List<Account>> =
        remote.observeAccounts().flatMapLatest { accounts ->
            mirror(accounts)
            store.observe { state -> accounts.map { state.account(it.id) } }
        }

    private fun mirror(accounts: List<Account>) = store.write { state ->
        val byId = accounts.associateBy { it.id }
        val known = state.accounts.map { record ->
            byId[record.id]?.let(::recordOf) ?: record.copy(archived = true)
        }
        val knownIds = state.accounts.map { it.id }.toSet()
        state.copy(accounts = known + accounts.filterNot { it.id in knownIds }.map(::recordOf))
    }

    private fun recordOf(account: Account) = AccountRecord(
        id = account.id,
        name = account.name,
        type = account.type,
        currency = account.currency,
        initialBalance = account.balance,
        includeInSavings = account.includeInSavings,
    )
}
