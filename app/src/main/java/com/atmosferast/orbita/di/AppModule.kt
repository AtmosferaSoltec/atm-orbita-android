package com.atmosferast.orbita.di

import com.atmosferast.orbita.BuildConfig
import com.atmosferast.orbita.data.demo.DemoAccountsRepository
import com.atmosferast.orbita.data.demo.DemoAuthRepository
import com.atmosferast.orbita.data.demo.DemoCategoriesRepository
import com.atmosferast.orbita.data.demo.DemoCreditRepository
import com.atmosferast.orbita.data.demo.DemoDateProvider
import com.atmosferast.orbita.data.demo.DemoEntriesRepository
import com.atmosferast.orbita.data.demo.DemoReportsRepository
import com.atmosferast.orbita.data.demo.DemoSettingsRepository
import com.atmosferast.orbita.data.remote.RemoteAuthRepository
import com.atmosferast.orbita.domain.repository.AccountsRepository
import com.atmosferast.orbita.domain.repository.AuthRepository
import com.atmosferast.orbita.domain.repository.CategoriesRepository
import com.atmosferast.orbita.domain.repository.CreditRepository
import com.atmosferast.orbita.domain.repository.DateProvider
import com.atmosferast.orbita.domain.repository.EntriesRepository
import com.atmosferast.orbita.domain.repository.ReportsRepository
import com.atmosferast.orbita.domain.repository.SettingsRepository
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
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

    /** The demo data is dated: while it is in use, so is "today". */
    @Provides
    fun provideDateProvider(demo: DemoDateProvider): DateProvider = demo

    @Provides
    fun provideAccountsRepository(demo: DemoAccountsRepository): AccountsRepository = demo

    @Provides
    fun provideCategoriesRepository(demo: DemoCategoriesRepository): CategoriesRepository = demo

    @Provides
    fun provideEntriesRepository(demo: DemoEntriesRepository): EntriesRepository = demo

    @Provides
    fun provideReportsRepository(demo: DemoReportsRepository): ReportsRepository = demo

    @Provides
    fun provideCreditRepository(demo: DemoCreditRepository): CreditRepository = demo

    @Provides
    fun provideSettingsRepository(demo: DemoSettingsRepository): SettingsRepository = demo
}
