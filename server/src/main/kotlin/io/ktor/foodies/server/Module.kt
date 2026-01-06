package io.ktor.app.io.ktor.foodies.server

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager

class Module(val database: Database)

fun Application.database(database: Config.DataSource): Database {
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
