package com.atmosferast.orbita.di

import android.os.Build
import com.atmosferast.orbita.BuildConfig
import com.atmosferast.orbita.data.remote.AuthEndpoint
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

    /** Google sign-in is built but off for the first store release: no client ID, no button. */
    private const val GOOGLE_SIGN_IN_ENABLED = false

    @Provides
    @Singleton
    fun provideAuthEndpoint() = AuthEndpoint(
        BuildConfig.AUTH_API_BASE_URL,
        if (GOOGLE_SIGN_IN_ENABLED) BuildConfig.GOOGLE_SERVER_CLIENT_ID else "",
    )

    @Provides
    @Singleton
    fun provideHttpClient(config: AppConfig): HttpClient = createApiClient(
        baseUrl = config.apiBaseUrl,
        engine = OkHttp.create(),
        userAgent = "Orbita-Android/${BuildConfig.VERSION_NAME} (Android ${Build.VERSION.RELEASE}; ${Build.MODEL})",
    )
}

@Module
@InstallIn(SingletonComponent::class)
abstract class TokenStoreModule {

    @Binds
    abstract fun bindTokenStore(store: KeystoreTokenStore): TokenStore
}
