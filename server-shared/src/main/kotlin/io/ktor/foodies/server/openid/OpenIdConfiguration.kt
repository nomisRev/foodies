package io.ktor.foodies.server.openid

import com.auth0.jwk.JwkProviderBuilder
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import org.slf4j.LoggerFactory

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
    fun jwksProvider() = JwkProviderBuilder(jwksUri)
        .cached(true)
        .rateLimited(true)
        .build()
}

private val logger = LoggerFactory.getLogger(OpenIdConfiguration::class.java)

suspend fun HttpClient.discover(issuer: String): OpenIdConfiguration {
    logger.info("Discovering OpenId configuration from $issuer")
    val response = get("$issuer/.well-known/openid-configuration")
    return if (response.status.isSuccess()) {
        val openId = response.body<OpenIdConfiguration>()
        logger.info("Discovered OpenId configuration: $openId")
        openId
    } else {
        val status = response.status
        val message = response.bodyAsText()
        throw IllegalStateException("Failed to discover OpenId configuration from: $status, $message")
    }
}
