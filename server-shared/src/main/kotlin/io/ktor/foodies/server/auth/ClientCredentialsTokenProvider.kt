package io.ktor.foodies.server.auth

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.http.parameters
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.seconds

@Serializable
data class TokenResponse(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("expires_in")
    val expiresIn: Long,
    @SerialName("token_type")
    val tokenType: String
)

class ClientCredentialsTokenProvider(
    private val httpClient: HttpClient,
    private val tokenEndpoint: String,
    private val clientId: String,
    private val clientSecret: String,
    private val scope: String? = null
) : ServiceTokenProvider {

    private var cachedToken: ServiceToken? = null
    private val mutex = Mutex()

    override suspend fun getToken(): ServiceToken = mutex.withLock {
        cachedToken?.takeUnless { it.isExpired() } ?: fetchNewToken().also { cachedToken = it }
    }

    private suspend fun fetchNewToken(): ServiceToken {
        val response: TokenResponse = httpClient.submitForm(
            url = tokenEndpoint,
            formParameters = parameters {
                append("grant_type", "client_credentials")
                append("client_id", clientId)
                append("client_secret", clientSecret)
                scope?.let { append("scope", it) }
            }
        ).body()

        return ServiceToken(
            accessToken = response.accessToken,
            expiresAt = Clock.System.now() + response.expiresIn.seconds,
            tokenType = response.tokenType
        )
    }
}
