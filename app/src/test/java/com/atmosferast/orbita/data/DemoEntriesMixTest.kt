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
import com.atmosferast.orbita.domain.model.Transfer
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

// While movements come from the API and transfers are still demo data (plans 05 to 06 of
// atm-orbita-api), both show in the same list.

private class ApiAccounts(list: List<Account>) : AccountsRepository {
    val list = MutableStateFlow(list)
    override fun observeAccounts(): Flow<List<Account>> = list
    override suspend fun create(draft: AccountDraft) = Unit
    override suspend fun update(id: String, draft: AccountDraft) = Unit
    override suspend fun setIncludeInSavings(id: String, include: Boolean) = Unit
    override suspend fun archive(id: String) = Unit
}

/** Holds the movements "of the API". */
private class ApiEntries(val entries: MutableStateFlow<List<Entry>>) : EntriesRepository {
    val created = mutableListOf<MovementDraft>()
    val deleted = mutableListOf<String>()
    override fun observeEntries(period: DatePeriod): Flow<List<Entry>> = entries
    override fun observeRecentEntries(limit: Int): Flow<List<Entry>> = entries
    override suspend fun createMovement(draft: MovementDraft) {
        created += draft
    }
    override suspend fun updateMovement(id: String, draft: MovementDraft) = Unit
    override suspend fun deleteMovement(id: String) {
        deleted += id
    }
    override suspend fun createTransfer(draft: TransferDraft) = error("not in the API")
    override suspend fun updateTransfer(id: String, draft: TransferDraft) = error("not in the API")
    override suspend fun deleteTransfer(id: String) = error("not in the API")
}

class DemoEntriesMixTest {

    private val cash = Account("a-cash", "Efectivo", AccountType.CASH, "PEN", BigDecimal("100.00"))
    private val bank = Account("a-bank", "Banco", AccountType.DEBIT, "PEN", BigDecimal("500.00"))
    private val food = Category("c-food", "Alimentación", MovementKind.EXPENSE, "#0E7490")
    private val october = DatePeriod(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))

    @Test
    fun `movements of the API and transfers of the session share the list, without the sample`() = runTest {
        val store = DemoStore()
        val accounts = DemoAccountMirror(ApiAccounts(listOf(cash, bank)), store)
        accounts.observeAccounts().first()
        val api = ApiEntries(MutableStateFlow(listOf(Movement("m1", "Almuerzo", food, cash, LocalDate.of(2026, 10, 1), BigDecimal("18.50")))))
        val demo = DemoEntriesRepository(store, DemoDateProvider())
        val mix = DemoEntriesMix(api, demo, accounts, store)

        mix.createTransfer(
            TransferDraft("a-bank", "a-cash", BigDecimal("50.00"), BigDecimal("50.00"), null, LocalDate.of(2026, 10, 2), "Retiro"),
        )
        val list = mix.observeEntries(october).first()

        // The sample has its own October movements and transfer; none of them shows.
        assertEquals(listOf("Retiro", "Almuerzo"), list.map { (it as? Transfer)?.note ?: (it as Movement).description })
        assertEquals(BigDecimal("150.00"), accounts.observeAccounts().first().first { it.id == "a-cash" }.balance)

        mix.createMovement(MovementDraft(MovementKind.EXPENSE, BigDecimal("5.00"), "a-cash", "c-food", "Café"))
        mix.deleteMovement("m1")
        assertEquals(listOf("Café"), api.created.map { it.description })
        assertEquals(listOf("m1"), api.deleted)
    }
}
