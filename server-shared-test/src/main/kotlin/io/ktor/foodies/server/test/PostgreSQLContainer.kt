package io.ktor.foodies.server.test

import de.infix.testBalloon.framework.core.TestSuite
import io.ktor.foodies.server.DataSource
import org.testcontainers.containers.PostgreSQLContainer as PostgreSQLTestContainer

class PostgreSQLContainer internal constructor() : PostgreSQLTestContainer<Nothing>("postgres:16-alpine") {
    fun config() = DataSource.Config(jdbcUrl, username, password)
}

fun TestSuite.postgresContainer(): TestSuite.Fixture<PostgreSQLContainer> =
    testFixture { PostgreSQLContainer().apply { start() } } closeWith { stop() }
