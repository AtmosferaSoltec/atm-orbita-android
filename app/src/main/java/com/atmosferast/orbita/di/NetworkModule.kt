package com.atmosferast.orbita.di

import com.atmosferast.orbita.data.remote.KeystoreTokenStore
import com.atmosferast.orbita.data.remote.TokenStore
import com.atmosferast.orbita.data.remote.createApiClient
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import javax.inject.Singleton

/** The HTTP client of the Orbita API and where its tokens are kept. Only built with the API. */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideHttpClient(config: AppConfig): HttpClient = createApiClient(config.apiBaseUrl, OkHttp.create())
}

@Module
@InstallIn(SingletonComponent::class)
abstract class TokenStoreModule {

    @Binds
    abstract fun bindTokenStore(store: KeystoreTokenStore): TokenStore
}
