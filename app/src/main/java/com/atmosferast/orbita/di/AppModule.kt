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
import com.atmosferast.orbita.data.remote.SupabaseAuthRepository
import com.atmosferast.orbita.domain.repository.AccountsRepository
import com.atmosferast.orbita.domain.repository.AuthRepository
import com.atmosferast.orbita.domain.repository.CategoriesRepository
import com.atmosferast.orbita.domain.repository.CreditRepository
import com.atmosferast.orbita.domain.repository.DateProvider
import com.atmosferast.orbita.domain.repository.EntriesRepository
import com.atmosferast.orbita.domain.repository.ReportsRepository
import com.atmosferast.orbita.domain.repository.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Where the app takes its data from. Without the Supabase keys in local.properties everything
 * is the in-memory demo; with them, the session is real.
 */
data class AppConfig(val supabaseUrl: String, val supabaseAnonKey: String) {
    val usesSupabase: Boolean get() = supabaseUrl.isNotBlank() && supabaseAnonKey.isNotBlank()
}

/**
 * The single place that decides which implementation is behind each repository. To move one to
 * Supabase: write its implementation in data/remote and return it here when
 * [AppConfig.usesSupabase], exactly as [provideAuthRepository] does.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppConfig() = AppConfig(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY)

    /** Only created when something asks for it, that is, when the keys are set. */
    @Provides
    @Singleton
    fun provideSupabaseClient(config: AppConfig): SupabaseClient =
        createSupabaseClient(config.supabaseUrl, config.supabaseAnonKey) {
            install(Auth)
            install(Postgrest)
        }

    @Provides
    @Singleton
    fun provideAuthRepository(
        config: AppConfig,
        demo: Provider<DemoAuthRepository>,
        supabase: Provider<SupabaseAuthRepository>,
    ): AuthRepository = if (config.usesSupabase) supabase.get() else demo.get()

    // Still on demo data, whatever the config: they move to Supabase phase by phase (docs/06).

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
