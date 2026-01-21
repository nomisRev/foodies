package io.ktor.foodies.server.auth

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.foodies.server.openid.discover
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import org.slf4j.LoggerFactory

interface ServiceTokenClient {
    suspend fun getToken(): String
    suspend fun getTokenWithScopes(scopes: List<String>): String
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonIgnoreUnknownKeys
internal data class TokenResponse(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("expires_in")
    val expiresIn: Long,
    @SerialName("token_type")
    val tokenType: String
)

class KeycloakServiceTokenClient(
    private val client: HttpClient,
    internal val config: ServiceClientConfig
) : ServiceTokenClient {
    private val logger = LoggerFactory.getLogger(KeycloakServiceTokenClient::class.java)
    private var tokenEndpoint: String? = null

    override suspend fun getToken(): String = getTokenWithScopes(config.defaultScopes)

    override suspend fun getTokenWithScopes(scopes: List<String>): String {
        return getServiceToken(scopes).accessToken
    }

    internal suspend fun getServiceToken(scopes: List<String>): TokenResponse {
        val endpoint = getEndpoint()

        logger.info("Requesting service token for client {} with scopes {}", config.clientId, scopes)

        val response = client.submitForm(
            url = endpoint,
            formParameters = Parameters.build {
                append("grant_type", "client_credentials")
                append("client_id", config.clientId)
                append("client_secret", config.clientSecret)
                if (scopes.isNotEmpty()) {
                    append("scope", scopes.joinToString(" "))
                }
            }
        )

        return if (response.status.isSuccess()) {
            response.body<TokenResponse>()
        } else {
            val status = response.status
            val body = response.bodyAsText()
            throw IllegalStateException("Failed to obtain service token from $endpoint: $status, $body")
        }
    }

    private suspend fun getEndpoint(): String {
        if (tokenEndpoint == null) {
            tokenEndpoint = client.discover(config.issuer).tokenEndpoint
        }
        return tokenEndpoint!!
    }
}
