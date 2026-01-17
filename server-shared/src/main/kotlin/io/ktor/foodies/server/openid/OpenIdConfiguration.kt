package io.ktor.foodies.server.openid

import com.auth0.jwk.JwkProviderBuilder
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import java.net.URI

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonIgnoreUnknownKeys
data class OpenIdConfiguration(
    val issuer: String,
    @SerialName("authorization_endpoint")
    val authorizationEndpoint: String,
    @SerialName("token_endpoint")
    val tokenEndpoint: String,
    @SerialName("jwks_uri")
    val jwksUri: String,
    @SerialName("end_session_endpoint")
    val endSessionEndpoint: String
) {
    fun jwks() = JwkProviderBuilder(URI(jwksUri).toURL()).build()
}

suspend fun HttpClient.discover(issuer: String): OpenIdConfiguration =
    get("$issuer/.well-known/openid-configuration").body()

