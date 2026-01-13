package io.ktor.foodies.order

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.foodies.server.openid.discover
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.jwt

suspend fun Application.security(config: Config) {
    val openIdConfig = HttpClient(CIO) {
        install(ContentNegotiation) { json() }
    }.use { it.discover(config.auth.issuer) }
    install(Authentication) {
        jwt {
            verifier(openIdConfig.jwksProvider(), config.auth.issuer)
            validate { credential ->
                if (!credential.payload.audience.contains(config.auth.audience)) {
                    return@validate null
                }
                credential.payload
            }
        }
    }
}
