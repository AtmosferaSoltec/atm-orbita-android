package com.atmosferast.orbita.data.remote

import com.atmosferast.orbita.domain.model.CategoryTotal
import com.atmosferast.orbita.domain.model.CurrencyReport
import com.atmosferast.orbita.domain.model.DatePeriod
import com.atmosferast.orbita.domain.repository.ReportsRepository
import io.ktor.client.call.body
import io.ktor.client.request.parameter
import io.ktor.client.request.url
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable

/**
 * The report of a period (atm-orbita-api, plan 07), read again whenever a movement changes. The
 * API also sends each percentage; the screen keeps working it out with percentOf, which gives
 * the same number.
 */
@Singleton
class RemoteReportsRepository @Inject constructor(
    private val api: ApiSession,
    private val entries: RemoteEntriesRepository,
) : ReportsRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeReport(period: DatePeriod): Flow<List<CurrencyReport>> =
        entries.changes.flatMapLatest { flow { emit(read(period)) } }

    private suspend fun read(period: DatePeriod): List<CurrencyReport> =
        api.authorized {
            url("reports/summary")
            parameter("from", period.from.toString())
            parameter("to", period.to.toString())
        }.requireSuccess().body<DataEnvelope<SummaryDto>>().data.currencies.map { it.toReport() }
}

// Body of GET /reports/summary.

@Serializable
internal data class SummaryDto(val currencies: List<CurrencyReportDto>)

@Serializable
internal data class CurrencyReportDto(
    val currency: String,
    val incomes: List<ReportLineDto>,
    val expenses: List<ReportLineDto>,
) {
    fun toReport() = CurrencyReport(
        currency = currency,
        incomes = incomes.map { it.toTotal() },
        expenses = expenses.map { it.toTotal() },
    )
}

@Serializable
internal data class ReportLineDto(
    val category: CategoryDto,
    @Serializable(with = BigDecimalSerializer::class) val total: BigDecimal,
) {
    fun toTotal() = CategoryTotal(category.toCategory(), total)
}
