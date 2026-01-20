package io.ktor.foodies.server.openid

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Serializable
private data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in") val expiresIn: Long
)

private data class CachedToken(
    val token: String,
    val expiresAt: Instant
) {
    fun isExpiringSoon(): Boolean =
        Instant.now().plusSeconds(300).isAfter(expiresAt)
}

/**
 * Provides service tokens using OAuth2 client credentials grant.
 * Implementation includes thread-safe caching and automatic refresh for expiring tokens.
 */
class ServiceTokenProvider(
    private val httpClient: HttpClient,
    private val clientId: String,
    private val clientSecret: String,
    private val tokenEndpoint: String
) {
    private val tokenCache = ConcurrentHashMap<String, CachedToken>()

    /**
     * Acquires a token for the specified [targetService].
     * Uses cached token if available and not expiring soon (within 5 minutes).
     */
    suspend fun getToken(targetService: String): TokenResult {
        val cached = tokenCache[targetService]
        if (cached != null && !cached.isExpiringSoon()) {
            return TokenResult.Success(cached.token, cached.expiresAt)
        }

        return fetchAndCacheToken(targetService)
    }

    private suspend fun fetchAndCacheToken(targetService: String): TokenResult {
        return try {
            val response = httpClient.post(tokenEndpoint) {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(FormDataContent(Parameters.build {
                    append("grant_type", "client_credentials")
                    append("client_id", clientId)
                    append("client_secret", clientSecret)
                    append("audience", "service:$targetService")
                }))
            }.body<TokenResponse>()

            val expiresAt = Instant.now().plusSeconds(response.expiresIn)
            tokenCache[targetService] = CachedToken(
                token = response.accessToken,
                expiresAt = expiresAt
            )

            TokenResult.Success(response.accessToken, expiresAt)
        } catch (e: Exception) {
            TokenResult.Failed("Failed to acquire service token", e)
        }
    }
}
