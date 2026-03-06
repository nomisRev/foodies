package io.ktor.foodies.profile

import com.sksamuel.cohort.HealthCheckRegistry
import io.ktor.foodies.profile.usersync.userSyncModule
import io.ktor.foodies.server.dataSource
import io.ktor.server.application.Application
import io.opentelemetry.api.OpenTelemetry
import kotlinx.coroutines.flow.Flow

class ProfileModule(
    val consumers: List<Flow<Unit>>,
    val readinessCheck: HealthCheckRegistry
)

fun Application.module(config: Config, telemetry: OpenTelemetry): ProfileModule {
    val dataSource = dataSource(config.dataSource, telemetry)
    val userSync = userSyncModule(config, dataSource)
    return ProfileModule(consumers = userSync.consumers, readinessCheck = userSync.readinessCheck)
}
