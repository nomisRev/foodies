package io.ktor.foodies.server

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import de.infix.testBalloon.framework.core.TestSuite
import io.ktor.app.io.ktor.foodies.server.DataSource
import io.ktor.app.io.ktor.foodies.server.Env
import io.ktor.app.io.ktor.foodies.server.Module
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.testcontainers.containers.PostgreSQLContainer as PostgreSQLTestContainer

fun TestSuite.testModule() = testFixture {
    val container = postgresContainer()
    val env = container().env()
    val dataSource = hikari(env)
    val database = database(dataSource)
    Module(database())
}

private class PostgreSQLContainer : PostgreSQLTestContainer<Nothing>("postgres:16-alpine")

private fun TestSuite.postgresContainer(): TestSuite.Fixture<PostgreSQLContainer> =
    testFixture { PostgreSQLContainer().apply { start() } } closeWith { stop() }

private fun PostgreSQLContainer.env(): Env =
    Env(host = "0.0.0.0", port = 8080, dataSource = DataSource(jdbcUrl, username, password))

private fun TestSuite.database(
    dataSource: TestSuite.Fixture<HikariDataSource>
): TestSuite.Fixture<Database> =
    testFixture { Database.connect(dataSource()) } closeWith
        {
            TransactionManager.closeAndUnregister(this)
        }

private fun TestSuite.hikari(env: Env): TestSuite.Fixture<HikariDataSource> = testFixture {
    HikariDataSource(
        HikariConfig().apply {
            jdbcUrl = env.dataSource.url
            username = env.dataSource.username
            password = env.dataSource.password
        }
    )
}
