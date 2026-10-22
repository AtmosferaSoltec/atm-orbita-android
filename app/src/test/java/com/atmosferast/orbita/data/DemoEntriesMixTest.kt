package com.atmosferast.orbita.data

import com.atmosferast.orbita.data.demo.DemoAccountMirror
import com.atmosferast.orbita.data.demo.DemoDateProvider
import com.atmosferast.orbita.data.demo.DemoEntriesMix
import com.atmosferast.orbita.data.demo.DemoEntriesRepository
import com.atmosferast.orbita.data.demo.DemoStore
import com.atmosferast.orbita.domain.model.Account
import com.atmosferast.orbita.domain.model.AccountDraft
import com.atmosferast.orbita.domain.model.AccountType
import com.atmosferast.orbita.domain.model.Category
import com.atmosferast.orbita.domain.model.DatePeriod
import com.atmosferast.orbita.domain.model.Entry
import com.atmosferast.orbita.domain.model.Movement
import com.atmosferast.orbita.domain.model.MovementDraft
import com.atmosferast.orbita.domain.model.MovementKind
import com.atmosferast.orbita.domain.model.TransferDraft
import com.atmosferast.orbita.domain.repository.AccountsRepository
import com.atmosferast.orbita.domain.repository.EntriesRepository
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

// While movements and transfers come from the API and card payments are still demo data (plans
// 06 to 10 of atm-orbita-api), the expense of a payment shows next to the real entries.

private class ApiAccounts(list: List<Account>) : AccountsRepository {
    val list = MutableStateFlow(list)
    override fun observeAccounts(): Flow<List<Account>> = list
    override suspend fun create(draft: AccountDraft) = Unit
    override suspend fun update(id: String, draft: AccountDraft) = Unit
    override suspend fun setIncludeInSavings(id: String, include: Boolean) = Unit
    override suspend fun archive(id: String) = Unit
}

/** Holds the entries "of the API". */
private class ApiEntries(val entries: MutableStateFlow<List<Entry>>) : EntriesRepository {
    val calls = mutableListOf<String>()
    override fun observeEntries(period: DatePeriod): Flow<List<Entry>> = entries
    override fun observeRecentEntries(limit: Int): Flow<List<Entry>> = entries
    override suspend fun createMovement(draft: MovementDraft) {
        calls += "create ${draft.description}"
    }
    override suspend fun updateMovement(id: String, draft: MovementDraft) {
        calls += "update $id"
    }
    override suspend fun deleteMovement(id: String) {
        calls += "delete $id"
    }
    override suspend fun createTransfer(draft: TransferDraft) {
        calls += "transfer ${draft.note}"
    }
    override suspend fun updateTransfer(id: String, draft: TransferDraft) = Unit
    override suspend fun deleteTransfer(id: String) = Unit
}

class DemoEntriesMixTest {

    private val cash = Account("a-cash", "Efectivo", AccountType.CASH, "PEN", BigDecimal("100.00"))
    private val bank = Account("a-bank", "Banco", AccountType.DEBIT, "PEN", BigDecimal("500.00"))
    private val food = Category("c-food", "Alimentación", MovementKind.EXPENSE, "#0E7490")
    private val october = DatePeriod(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))

    @Test
    fun `a demo expense from an account of the API shows next to the real entries, without the sample`() = runTest {
        val store = DemoStore()
        val accounts = DemoAccountMirror(ApiAccounts(listOf(cash, bank)), store)
        accounts.observeAccounts().first()
        val api = ApiEntries(MutableStateFlow(listOf(Movement("m1", "Almuerzo", food, cash, LocalDate.of(2026, 10, 1), BigDecimal("18.50")))))
        val demo = DemoEntriesRepository(store, DemoDateProvider())
        val mix = DemoEntriesMix(api, demo, accounts, store)

        // What paying a card purchase does today: an expense in the demo store.
        demo.createMovement(MovementDraft(MovementKind.EXPENSE, BigDecimal("240.00"), "a-bank", "transport", "Pasajes"))
        val list = mix.observeEntries(october).first().map { (it as Movement).description }

        // The sample has its own October movements and transfer; none of them shows.
        assertEquals(listOf("Pasajes", "Almuerzo"), list)

        val payment = store.current.movements.first { it.description == "Pasajes" }.id
        mix.deleteMovement(payment)
        mix.deleteMovement("m1")
        mix.createTransfer(TransferDraft("a-bank", "a-cash", BigDecimal("50"), BigDecimal("50"), null, LocalDate.of(2026, 10, 2), "Retiro"))
        assertEquals(listOf("delete m1", "transfer Retiro"), api.calls)
        assertEquals(listOf("Almuerzo"), mix.observeEntries(october).first().map { (it as Movement).description })
    }
}
