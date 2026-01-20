package io.ktor.foodies.server.test

import dasniko.testcontainers.keycloak.KeycloakContainer
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
import io.ktor.foodies.server.openid.discover
import io.ktor.http.parameters
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import kotlin.io.encoding.Base64

@Serializable
@JsonIgnoreUnknownKeys
private data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("token_type") val tokenType: String,
    @SerialName("expires_in") val expiresIn: Int
)

class KeycloakAuthContext(
    val keycloak: KeycloakContainer,
    val httpClient: HttpClient
) {
    val issuer: String get() = "${keycloak.authServerUrl}/realms/foodies-keycloak"

    suspend fun createServiceToken(clientId: String, clientSecret: String, scopes: List<String> = emptyList()): String {
        val openIdConfig = httpClient.discover(issuer)
        val credentials = Base64.encode("$clientId:$clientSecret".toByteArray())

        val response = httpClient.submitForm(
            url = openIdConfig.tokenEndpoint,
            formParameters = parameters {
                append("grant_type", "client_credentials")
                if (scopes.isNotEmpty()) {
                    append("scope", scopes.joinToString(" "))
                }
            }
        ) {
            header("Authorization", "Basic $credentials")
        }

        return response.body<TokenResponse>().accessToken
    }

    suspend fun createUserToken(
        username: String,
        password: String,
        clientId: String = "foodies",
        clientSecret: String = "foodies_client_secret"
    ): String {
        val tokenUrl = "$issuer/protocol/openid-connect/token"
        val response = httpClient.submitForm(
            url = tokenUrl,
            formParameters = parameters {
                append("grant_type", "password")
                append("client_id", clientId)
                append("client_secret", clientSecret)
                append("username", username)
                append("password", password)
                append("scope", "openid profile email")
            }
        )
        return response.body<TokenResponse>().accessToken
    }

    suspend fun createOrderServiceToken(): String =
        createServiceToken("order-service", "order-service-secret", listOf("basket"))

    suspend fun createBasketServiceToken(): String =
        createServiceToken("basket-service", "basket-service-secret", listOf("order"))

    suspend fun createFoodLoverToken(): String = createUserToken("food_lover", "password")

    suspend fun createPizzaFanToken(): String = createUserToken("pizza_fan", "password")
}
