package com.atmosferast.orbita.data

import com.atmosferast.orbita.data.demo.DemoAccountMirror
import com.atmosferast.orbita.data.demo.DemoDateProvider
import com.atmosferast.orbita.data.demo.DemoEntriesRepository
import com.atmosferast.orbita.data.demo.DemoStore
import com.atmosferast.orbita.domain.model.Account
import com.atmosferast.orbita.domain.model.AccountDraft
import com.atmosferast.orbita.domain.model.AccountType
import com.atmosferast.orbita.domain.model.Movement
import com.atmosferast.orbita.domain.model.MovementDraft
import com.atmosferast.orbita.domain.model.MovementKind
import com.atmosferast.orbita.domain.repository.AccountsRepository
import java.math.BigDecimal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

// While accounts come from the API and movements are still demo data (plans 04 to 05 of
// atm-orbita-api), a movement can be saved in an account of the API.

private class FixedAccounts(list: List<Account>) : AccountsRepository {
    val list = MutableStateFlow(list)
    override fun observeAccounts(): Flow<List<Account>> = list
    override suspend fun create(draft: AccountDraft) = Unit
    override suspend fun update(id: String, draft: AccountDraft) = Unit
    override suspend fun setIncludeInSavings(id: String, include: Boolean) = Unit
    override suspend fun archive(id: String) = Unit
}

class DemoAccountMirrorTest {

    @Test
    fun `a demo movement in an account of the API moves its balance`() = runTest {
        val store = DemoStore()
        val api = Account("0199a0e2-0000-7000-8000-0000000000aa", "Efectivo", AccountType.CASH, "PEN", BigDecimal("100.00"))
        val mirror = DemoAccountMirror(FixedAccounts(listOf(api)), store)
        val entries = DemoEntriesRepository(store, DemoDateProvider())

        assertEquals(listOf(api), mirror.observeAccounts().first())
        entries.createMovement(MovementDraft(MovementKind.EXPENSE, BigDecimal("30.00"), api.id, "food", "Almuerzo"))

        assertEquals(BigDecimal("70.00"), mirror.observeAccounts().first().single().balance)
        val newest = entries.observeRecentEntries(1).first().single() as Movement
        assertEquals(api.id, newest.account.id)
    }
}
