package io.ktor.foodies.menu

import io.ktor.foodies.server.DataSource
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Config(
    val host: String,
    val port: Int,
    @SerialName("data_source") val dataSource: DataSource.Config,
)