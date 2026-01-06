package io.ktor.app.io.ktor.foodies.server

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Env(
    val host: String,
    val port: Int,
    @SerialName("data_source") val dataSource: DataSource,
)
