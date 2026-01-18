package io.ktor.foodies.basket

import io.ktor.client.HttpClient
import io.ktor.client.engine.apache5.Apache5
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.foodies.server.openid.discover
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.jwt

// TODO: this needs to go to shared code infrastructure
//  audiences need to properly setup in keycloak first
suspend fun Application.security(config: Config) {
    val openIdConfig = HttpClient(Apache5) {
        install(ContentNegotiation) { json() }
    }.use { it.discover(config.auth.issuer) }
    install(Authentication) {
        jwt {
            verifier(openIdConfig.jwks(), config.auth.issuer)
            validate { credential ->
// TODO: Setup proper audience in keycloak
//                 Validate audience if configured
//                if (config.auth.audience.isNotBlank()) {
                if (!credential.payload.audience.contains(config.auth.audience)) {
                    return@validate null
                }
//                }
                credential.payload
            }
        }
    }
}