package io.ktor.foodies.server.security

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.http.parameters
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("TokenRefresher")

@Serializable
data class RefreshTokenResponse(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("id_token")
    val idToken: String,
    @SerialName("expires_in")
    val expiresIn: Long,
    @SerialName("refresh_token")
    val refreshToken: String? = null,
    @SerialName("token_type")
    val tokenType: String = "Bearer"
)

class TokenRefresher(
    private val httpClient: HttpClient,
    private val tokenEndpoint: String,
    private val clientId: String,
    private val clientSecret: String
) {
    suspend fun refresh(refreshToken: String): RefreshTokenResponse {
        logger.debug("Refreshing access token")
        return httpClient.submitForm(
            url = tokenEndpoint,
            formParameters = parameters {
                append("grant_type", "refresh_token")
                append("refresh_token", refreshToken)
                append("client_id", clientId)
                append("client_secret", clientSecret)
            }
        ).body()
    }
}

fun UserSession.shouldRefresh(bufferSeconds: Long = 60): Boolean {
    val expiresAt = expiresAt ?: return false
    return Clock.System.now() >= expiresAt - bufferSeconds.seconds
}

fun UserSession.isExpired(): Boolean {
    val expiresAt = expiresAt ?: return false // If no expiration info, assume valid (JWT validation handles it)
    return Clock.System.now() >= expiresAt
}

fun UserSession.withRefreshedTokens(response: RefreshTokenResponse): UserSession {
    val now = Clock.System.now()
    return copy(
        idToken = response.idToken,
        accessToken = response.accessToken,
        expiresIn = response.expiresIn,
        expiresAt = now + response.expiresIn.seconds,
        refreshToken = response.refreshToken ?: this.refreshToken
    )
}
