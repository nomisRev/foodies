package io.ktor.foodies.server.telemetry

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
import io.opentelemetry.instrumentation.ktor.v3_0.KtorServerTelemetry
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import io.opentelemetry.sdk.trace.export.SpanExporter
import io.opentelemetry.sdk.trace.samplers.Sampler
import io.opentelemetry.semconv.ServiceAttributes


fun Application.openTelemetry(
    serviceName: String,
    serviceVersion: String,
    otlpEndpoint: String = "http://localhost:4317",
    sampler: Sampler = Sampler.alwaysOn()
): OpenTelemetrySdk {
    val resource = Resource.getDefault().merge(
        Resource.create(
            Attributes.builder()
                .put(ServiceAttributes.SERVICE_NAME, serviceName)
                .put(ServiceAttributes.SERVICE_VERSION, serviceVersion)
                .build()
        )
    )

    val spanExporter: SpanExporter = OtlpGrpcSpanExporter.builder().setEndpoint(otlpEndpoint).build()

    val tracerProvider = SdkTracerProvider.builder()
        .setResource(resource)
        .setSampler(sampler)
        .addSpanProcessor(BatchSpanProcessor.builder(spanExporter).build())
        .build()

    val propagators = ContextPropagators.create(W3CTraceContextPropagator.getInstance())

    monitor.subscribe(ApplicationStopped) { tracerProvider.shutdown() }

    val openTelemetry = OpenTelemetrySdk.builder()
        .setTracerProvider(tracerProvider)
        .setPropagators(propagators)
        .buildAndRegisterGlobal()

    install(KtorServerTelemetry) { setOpenTelemetry(openTelemetry) }

    return openTelemetry
}
