package io.ktor.foodies.server.telemetry

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
import io.opentelemetry.instrumentation.ktor.v3_0.KtorServerTelemetry
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import io.opentelemetry.sdk.trace.export.SpanExporter
import java.util.concurrent.TimeUnit

data class Monitoring(val prometheus: PrometheusMeterRegistry, val opentelemetry: OpenTelemetrySdk)

/**
 * Explicit OpenTelemetry setup with Prometheus and OTLP exporters. To show how to install
 * [KtorServerTelemetry] to automatically monitor processed requested, and errors. And install
 * [io.opentelemetry.extension.kotlin.KotlinContextElement] into the CoroutienContext of your
 * Pipeline, this way it can automatically be inherited by coroutines for tracing.
 *
 * We use nocode opentelemetry for setting up the service name, and version automatically.
 */
fun Application.openTelemetry(otlpEndpoint: String): Monitoring =
    monitoring(MonitoringConfig(otlpEndpoint))

fun Application.monitoring(config: MonitoringConfig): Monitoring {
    val spanExporter: SpanExporter =
        OtlpGrpcSpanExporter.builder().setEndpoint(config.otlpEndpoint).build()

    val tracerProvider =
        SdkTracerProvider.builder()
            .addSpanProcessor(BatchSpanProcessor.builder(spanExporter).build())
            .build()

    val metricExporter = OtlpGrpcMetricExporter.builder().setEndpoint(config.otlpEndpoint).build()

    val meterProvider =
        SdkMeterProvider.builder()
            .registerMetricReader(PeriodicMetricReader.builder(metricExporter).build())
            .build()

    val openTelemetry =
        OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .setMeterProvider(meterProvider)
            .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
            .buildAndRegisterGlobal()
            .also { it.shutdownOnStop() }

    install(KtorServerTelemetry) { setOpenTelemetry(openTelemetry) }

    val prometheusRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    monitor.subscribe(ApplicationStopped) { prometheusRegistry.close() }
    install(MicrometerMetrics) { registry = prometheusRegistry }

    routing { get("/metrics") { call.respond(prometheusRegistry.scrape()) } }

    return Monitoring(prometheusRegistry, openTelemetry)
}

context(app: Application)
private fun OpenTelemetrySdk.shutdownOnStop() =
    app.monitor.subscribe(ApplicationStopped) {
        // close() = shutdown().join(10, TimeUnit.SECONDS)
        val result = shutdown().join(10, TimeUnit.SECONDS)
        when {
            !result.isDone -> app.log.info("OpenTelemetry shutdown timed out")
            result.isSuccess -> app.log.info("OpenTelemetry shutdown completed successfully")
            else ->
                result.failureThrowable?.let { error ->
                    app.log.info("OpenTelemetry shutdown failed with error", error)
                } ?: app.log.info("OpenTelemetry shutdown failed without failureThrowable")
        }
    }
