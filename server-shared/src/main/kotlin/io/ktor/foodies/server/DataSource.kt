package io.ktor.foodies.server

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager

class DataSource internal constructor(val hikari: HikariDataSource, val database: Database) {
    @Serializable
    data class Config(val url: String, val username: String, val password: String)
}

fun Application.dataSource(database: DataSource.Config): DataSource {
    val hikari = HikariDataSource(HikariConfig().apply {
        jdbcUrl = database.url
        username = database.username
        password = database.password
    })
    val db = Database.connect(hikari)
    monitor.subscribe(ApplicationStopped) {
        TransactionManager.closeAndUnregister(db)
        hikari.close()
    }
    return DataSource(hikari, db)
}
