package com.atmosferast.orbita.data.remote

import com.atmosferast.orbita.domain.model.CreditReminder
import com.atmosferast.orbita.domain.model.FxPair
import com.atmosferast.orbita.domain.model.HomeSummary
import com.atmosferast.orbita.domain.repository.HomeRepository
import io.ktor.client.call.body
import io.ktor.client.request.url
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Inicio in a single request (atm-orbita-api, plan 08), read again after any write made through
 * the remote repositories ([RemoteChanges]) and when the session changes.
 */
@Singleton
class RemoteHomeRepository @Inject constructor(
    private val api: ApiSession,
    private val changes: RemoteChanges,
) : HomeRepository {

    init {
        api.onSessionChange { changes.bump() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeHome(): Flow<HomeSummary> =
        changes.changes.flatMapLatest {
            flow {
                emit(api.authorized { url("home") }.requireSuccess().body<DataEnvelope<HomeDto>>().data.toSummary())
            }
        }
}

// Body of GET /home.

@Serializable
internal data class HomeDto(
    val settings: HomeSettingsDto,
    val savings: SavingsDto,
    val month: MonthDto,
    @SerialName("recent_entries") val recentEntries: List<EntryDto>,
    val credit: HomeCreditDto? = null,
) {
    fun toSummary() = HomeSummary(
        fx = FxPair(settings.mainCurrency, settings.secondaryCurrency, settings.rate),
        displayCurrency = settings.displayCurrency,
        savings = savings.totals.associate { it.currency to it.amount },
        includedAccounts = savings.includedCount,
        activeAccounts = savings.activeCount,
        monthIncome = month.incomeTotal,
        monthExpense = month.expenseTotal,
        recentEntries = recentEntries.map { it.toEntry() },
        credit = credit?.let { CreditReminder(it.pendingCount, LocalDate.parse(it.nextDueDate), it.hasUrgent) },
    )
}

@Serializable
internal data class HomeSettingsDto(
    @SerialName("main_currency") val mainCurrency: String,
    @SerialName("secondary_currency") val secondaryCurrency: String,
    @SerialName("display_currency") val displayCurrency: String,
    @Serializable(with = BigDecimalSerializer::class) val rate: BigDecimal? = null,
)

@Serializable
internal data class SavingsDto(
    @SerialName("included_count") val includedCount: Int,
    @SerialName("active_count") val activeCount: Int,
    val totals: List<CurrencyAmountDto>,
)

@Serializable
internal data class CurrencyAmountDto(
    val currency: String,
    @Serializable(with = BigDecimalSerializer::class) val amount: BigDecimal,
)

@Serializable
internal data class MonthDto(
    @SerialName("income_total") @Serializable(with = BigDecimalSerializer::class) val incomeTotal: BigDecimal,
    @SerialName("expense_total") @Serializable(with = BigDecimalSerializer::class) val expenseTotal: BigDecimal,
)

@Serializable
internal data class HomeCreditDto(
    @SerialName("pending_count") val pendingCount: Int,
    @SerialName("next_due_date") val nextDueDate: String,
    @SerialName("has_urgent") val hasUrgent: Boolean,
)
