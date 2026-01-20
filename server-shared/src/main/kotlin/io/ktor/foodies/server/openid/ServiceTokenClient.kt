package io.ktor.foodies.server.openid

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.apache5.Apache5
import io.ktor.client.plugins.BrowserUserAgent
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.plugin
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.createRouteScopedPlugin
import io.micrometer.core.instrument.MockClock.clock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import org.slf4j.LoggerFactory
import java.lang.AutoCloseable
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

context(app: Application)
suspend fun HttpClient.withServiceAuth(serviceClientConfig: ServiceClientConfig): HttpClient {
    val httpClient = HttpClient(Apache5) { install(ContentNegotiation) { json() } }
    app.monitor.subscribe(ApplicationStopped) { httpClient.close() }

    val openIdConfig = httpClient.discover(serviceClientConfig.issuer)
    val serviceTokenClient = KeycloakServiceTokenClient(httpClient, serviceClientConfig, openIdConfig)
    return config {
        install(Auth) {
            bearer {
                loadTokens { serviceTokenClient.loadTokens() }
                refreshTokens { serviceTokenClient.refreshToken(oldTokens) }
            }
        }
    }
}

@Serializable
data class ServiceClientConfig(
    val issuer: String,
    val clientId: String,
    val clientSecret: String,
    val defaultScopes: List<String>,
    val refreshBufferSeconds: Duration = 30.seconds
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonIgnoreUnknownKeys
private data class TokenResponse(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("expires_in")
    val expiresIn: Int,
    @SerialName("token_type")
    val tokenType: String,
    val scope: String? = null
)

private data class CachedToken(val accessToken: String, val expiresAt: Instant, val scopes: List<String>)

private class KeycloakServiceTokenClient(
    private val httpClient: HttpClient,
    private val config: ServiceClientConfig,
    private val openIdConfig: OpenIdConfiguration,
    private val clock: Clock = Clock.System
) : AutoCloseable {
    private val logger = LoggerFactory.getLogger(KeycloakServiceTokenClient::class.java)
    private val mutex = Mutex()
    private var cachedToken: CachedToken? = null

    suspend fun loadTokens(): BearerTokens = mutex.withLock {
        val cached = cachedToken
        if (cached != null && !isExpired(cached)) {
            logger.debug("Using cached token for client {}", config.clientId)
            return@withLock BearerTokens(cached.accessToken, cached.accessToken)
        }
        logger.info("Fetching new token for client {}", config.clientId)
        fetchToken()
    }

    suspend fun refreshToken(oldTokens: BearerTokens?): BearerTokens = mutex.withLock {
        logger.info("Refreshing token for client {}", config.clientId)
        fetchToken()
    }

    private suspend fun fetchToken(): BearerTokens {
        val credentials = Base64.encode("${config.clientId}:${config.clientSecret}".toByteArray())

        val response = httpClient.submitForm(
            url = openIdConfig.tokenEndpoint,
            formParameters = Parameters.build {
                append("grant_type", "client_credentials")
                if (config.defaultScopes.isNotEmpty()) {
                    append("scope", config.defaultScopes.joinToString(" "))
                }
            }
        ) {
            header(HttpHeaders.Authorization, "Basic $credentials")
        }

        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            logger.error("Token request failed: {} - {}", response.status, errorBody)
            throw ServiceTokenException("Failed to obtain token: ${response.status} - $errorBody")
        }

        val tokenResponse = response.body<TokenResponse>()
        val expiresAt = clock.now() + tokenResponse.expiresIn.seconds
        val scopes = tokenResponse.scope?.split(" ") ?: config.defaultScopes

        cachedToken = CachedToken(accessToken = tokenResponse.accessToken, expiresAt = expiresAt, scopes = scopes)

        logger.info("Token obtained for client {}, expires at {}", config.clientId, expiresAt)
        return BearerTokens(tokenResponse.accessToken, tokenResponse.accessToken)
    }

    private fun isExpired(token: CachedToken): Boolean {
        val now = clock.now()
        val bufferTime = token.expiresAt - config.refreshBufferSeconds
        return now >= bufferTime
    }

    override fun close() = httpClient.close()

    companion object {
        suspend fun create(
            config: ServiceClientConfig,
            clock: Clock = Clock.System
        ): KeycloakServiceTokenClient {
            val httpClient = HttpClient(Apache5) { install(ContentNegotiation) { json() } }
            val openIdConfig = httpClient.discover(config.issuer)
            return KeycloakServiceTokenClient(httpClient, config, openIdConfig, clock)
        }
    }
}

class ServiceTokenException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
