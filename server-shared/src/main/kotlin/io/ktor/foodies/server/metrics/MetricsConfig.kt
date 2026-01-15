package io.ktor.foodies.server.metrics

import io.ktor.server.application.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry

fun Application.prometheus(micrometer: MicrometerMetricsConfig.() -> Unit = {}): PrometheusMeterRegistry {
    val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    install(MicrometerMetrics) {
        registry = prometheus
        micrometer()
    }
    routing { get("/metrics") { call.respondText(prometheus.scrape()) } }
    monitor.subscribe(ApplicationStopped) { prometheus.close() }
    return prometheus
}
