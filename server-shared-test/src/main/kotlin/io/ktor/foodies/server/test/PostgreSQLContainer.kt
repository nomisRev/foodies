package io.ktor.foodies.server.test

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import de.infix.testBalloon.framework.core.TestSuite
import io.ktor.foodies.server.DataSource
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.testcontainers.containers.PostgreSQLContainer as PostgreSQLTestContainer

class PostgreSQLContainer : PostgreSQLTestContainer<Nothing>("postgres:18-alpine") {
    fun config(): DataSource.Config = DataSource.Config(jdbcUrl, username, password)
}

fun TestSuite.postgresContainer(): TestSuite.Fixture<PostgreSQLContainer> =
    testFixture { PostgreSQLContainer().apply { start() } } closeWith { stop() }

context(suite: TestSuite)
fun PostgreSQLContainer.dataSource(): TestSuite.Fixture<DataSource> = suite.testFixture {
    val ds = hikariDataSource()()
    val database = ds.database()
    DataSource(ds, database())
}

context(suite: TestSuite)
fun HikariDataSource.database(): TestSuite.Fixture<Database> =
    suite.testFixture { Database.connect(this@database) } closeWith {
        TransactionManager.closeAndUnregister(this)
    }

context(suite: TestSuite)
fun PostgreSQLContainer.hikariDataSource() = suite.testFixture {
    HikariDataSource(HikariConfig().apply {
        jdbcUrl = this@hikariDataSource.jdbcUrl
        username = this@hikariDataSource.username
        password = this@hikariDataSource.password
    })
}
