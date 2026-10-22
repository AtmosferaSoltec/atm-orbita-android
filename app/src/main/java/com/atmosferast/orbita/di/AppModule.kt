package com.atmosferast.orbita.di

import com.atmosferast.orbita.BuildConfig
import com.atmosferast.orbita.data.demo.DemoAccountsRepository
import com.atmosferast.orbita.data.demo.DemoAuthRepository
import com.atmosferast.orbita.data.demo.DemoCategoriesRepository
import com.atmosferast.orbita.data.demo.DemoCreditRepository
import com.atmosferast.orbita.data.demo.DemoDateProvider
import com.atmosferast.orbita.data.demo.DemoEntriesRepository
import com.atmosferast.orbita.data.demo.DemoHomeRepository
import com.atmosferast.orbita.data.demo.DemoReportsRepository
import com.atmosferast.orbita.data.demo.DemoSettingsRepository
import com.atmosferast.orbita.data.remote.RemoteAccountsRepository
import com.atmosferast.orbita.data.remote.RemoteAuthRepository
import com.atmosferast.orbita.data.remote.RemoteCategoriesRepository
import com.atmosferast.orbita.data.remote.RemoteCreditRepository
import com.atmosferast.orbita.data.remote.RemoteEntriesRepository
import com.atmosferast.orbita.data.remote.RemoteHomeRepository
import com.atmosferast.orbita.data.remote.RemoteReportsRepository
import com.atmosferast.orbita.data.remote.RemoteSettingsRepository
import com.atmosferast.orbita.domain.repository.AccountsRepository
import com.atmosferast.orbita.domain.repository.AuthRepository
import com.atmosferast.orbita.domain.repository.CategoriesRepository
import com.atmosferast.orbita.domain.repository.CreditRepository
import com.atmosferast.orbita.domain.repository.DateProvider
import com.atmosferast.orbita.domain.repository.EntriesRepository
import com.atmosferast.orbita.domain.repository.HomeRepository
import com.atmosferast.orbita.domain.repository.ReportsRepository
import com.atmosferast.orbita.domain.repository.SettingsRepository
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.LocalDate
import javax.inject.Singleton

/**
 * Where the app takes its data from. Without the URL of the Orbita API in local.properties
 * everything is the in-memory demo.
 */
data class AppConfig(val apiBaseUrl: String) {
    val usesApi: Boolean get() = apiBaseUrl.isNotBlank()
}

/**
 * The single place that decides which implementation is behind each repository. To move one to
 * the API: write its implementation in data/remote and return it here when [AppConfig.usesApi].
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppConfig() = AppConfig(BuildConfig.API_BASE_URL)

    // Each repository moves to the API plan by plan (atm-orbita-api/docs/plans); the rest stay
    // on demo data whatever the config. Lazy: the API side is only built when it is used.

    /** Plan 01 of the API: signing in, up and out. */
    @Provides
    @Singleton
    fun provideAuthRepository(
        config: AppConfig,
        demo: Lazy<DemoAuthRepository>,
        remote: Lazy<RemoteAuthRepository>,
    ): AuthRepository = if (config.usesApi) remote.get() else demo.get()

    /**
     * The demo data is dated: while it is in use, so is "today". With the API (movements are real
     * since plan 05) it is the phone's date, which is the zone the profile was created with.
     */
    @Provides
    fun provideDateProvider(config: AppConfig, demo: DemoDateProvider): DateProvider =
        if (config.usesApi) DateProvider { LocalDate.now() } else demo

    /** Plan 04 of the API: accounts with the balances the API computes. */
    @Provides
    @Singleton
    fun provideAccountsRepository(
        config: AppConfig,
        demo: Lazy<DemoAccountsRepository>,
        remote: Lazy<RemoteAccountsRepository>,
    ): AccountsRepository = if (config.usesApi) remote.get() else demo.get()

    /** Plan 03 of the API: categories. */
    @Provides
    @Singleton
    fun provideCategoriesRepository(
        config: AppConfig,
        demo: Lazy<DemoCategoriesRepository>,
        remote: Lazy<RemoteCategoriesRepository>,
    ): CategoriesRepository = if (config.usesApi) remote.get() else demo.get()

    /** Plans 05 and 06 of the API: incomes, expenses and transfers. */
    @Provides
    @Singleton
    fun provideEntriesRepository(
        config: AppConfig,
        demo: Lazy<DemoEntriesRepository>,
        remote: Lazy<RemoteEntriesRepository>,
    ): EntriesRepository = if (config.usesApi) remote.get() else demo.get()

    /** Plan 07 of the API: the report of a period, per currency. */
    @Provides
    @Singleton
    fun provideReportsRepository(
        config: AppConfig,
        demo: Lazy<DemoReportsRepository>,
        remote: Lazy<RemoteReportsRepository>,
    ): ReportsRepository = if (config.usesApi) remote.get() else demo.get()

    /** Plan 09 of the API: cards and pending purchases (paying them is plan 10). */
    @Provides
    @Singleton
    fun provideCreditRepository(
        config: AppConfig,
        demo: Lazy<DemoCreditRepository>,
        remote: Lazy<RemoteCreditRepository>,
    ): CreditRepository = if (config.usesApi) remote.get() else demo.get()

    /** Plan 08 of the API: Inicio in a single request. */
    @Provides
    @Singleton
    fun provideHomeRepository(
        config: AppConfig,
        demo: Lazy<DemoHomeRepository>,
        remote: Lazy<RemoteHomeRepository>,
    ): HomeRepository = if (config.usesApi) remote.get() else demo.get()

    /** Plan 02 of the API: currencies and exchange rate. */
    @Provides
    @Singleton
    fun provideSettingsRepository(
        config: AppConfig,
        demo: Lazy<DemoSettingsRepository>,
        remote: Lazy<RemoteSettingsRepository>,
    ): SettingsRepository = if (config.usesApi) remote.get() else demo.get()
}
