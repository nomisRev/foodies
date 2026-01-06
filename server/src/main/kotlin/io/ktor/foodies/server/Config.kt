package io.ktor.app.io.ktor.foodies.server

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Config(
    val host: String,
    val port: Int,
    @SerialName("data_source") val dataSource: DataSource,
) {
    @Serializable data class DataSource(val url: String, val username: String, val password: String)
}
