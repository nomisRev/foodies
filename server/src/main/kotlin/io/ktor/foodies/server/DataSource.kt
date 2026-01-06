package io.ktor.app.io.ktor.foodies.server

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager

@Serializable data class DataSource(val url: String, val username: String, val password: String)

fun Application.database(database: DataSource): Database {
    val hikari =
        HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = database.url
                username = database.username
                password = database.password
            }
        )
    val db = Database.connect(hikari)
    monitor.subscribe(ApplicationStopped) {
        TransactionManager.closeAndUnregister(db)
        hikari.close()
    }
    return db
}
