package io.ktor.foodies.server.shared

import io.ktor.client.HttpClient
import io.ktor.client.engine.apache5.Apache5
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.foodies.server.auth.AuthContextPlugin
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.instrumentation.ktor.v3_0.KtorClientTelemetry

fun Application.httpClientModule(telemetry: OpenTelemetry): HttpClient {
    val httpClient = HttpClient(Apache5) {
        install(ContentNegotiation) { json() }
        install(KtorClientTelemetry) {
            setOpenTelemetry(telemetry)
        }
        install(AuthContextPlugin)
    }
    monitor.subscribe(ApplicationStopped) { httpClient.close() }
    return httpClient
}
