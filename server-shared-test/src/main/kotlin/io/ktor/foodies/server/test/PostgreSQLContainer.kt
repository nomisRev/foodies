package io.ktor.foodies.server.test

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import de.infix.testBalloon.framework.core.TestSuite
import io.ktor.foodies.server.DataSource
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.testcontainers.containers.PostgreSQLContainer as PostgreSQLTestContainer

class PostgreSQLContainer internal constructor() : PostgreSQLTestContainer<Nothing>("postgres:16-alpine") {
    fun config() = DataSource.Config(jdbcUrl, username, password)
}

fun TestSuite.postgresContainer(): TestSuite.Fixture<PostgreSQLContainer> =
    testFixture { PostgreSQLContainer().apply { start() } } closeWith { stop() }

fun TestSuite.postgresDataSource() =
    testFixture {
        val container = postgresContainer()()
        val hikari = testFixture {
            HikariDataSource(HikariConfig().apply {
                jdbcUrl = container.jdbcUrl
                username = container.username
                password = container.password
            })
        }
        val database = testFixture { Database.connect(hikari()) } closeWith {
            TransactionManager.closeAndUnregister(this)
        }
        DataSource(hikari(), database())
    }
