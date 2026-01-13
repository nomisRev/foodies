package io.ktor.foodies.server.metrics

import io.ktor.server.application.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.core.instrument.binder.jvm.*
import io.micrometer.core.instrument.binder.system.*
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry

data class MetricsConfig(
    val enabled: Boolean = true,
    val serviceName: String,
    val includeJvmMetrics: Boolean = true,
    val includeSystemMetrics: Boolean = true
)

fun Application.installMetrics(config: MetricsConfig): PrometheusMeterRegistry {
    val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    // Install Ktor metrics plugin
    install(MicrometerMetrics) {
        this.registry = registry

        // Tag all metrics with service name
        meterBinders = buildList {
            if (config.includeJvmMetrics) {
                add(JvmMemoryMetrics())
                add(JvmGcMetrics())
                add(JvmThreadMetrics())
                add(ClassLoaderMetrics())
            }
            if (config.includeSystemMetrics) {
                add(ProcessorMetrics())
                add(UptimeMetrics())
                add(FileDescriptorMetrics())
            }
        }

        // Add common tags
        distributionStatisticConfig = DistributionStatisticConfig.builder()
            .percentiles(0.5, 0.95, 0.99)
            .build()
            .merge(distributionStatisticConfig)
    }

    // Expose metrics endpoint
    routing {
        get("/metrics") {
            call.respondText(registry.scrape())
        }
    }

    return registry
}
