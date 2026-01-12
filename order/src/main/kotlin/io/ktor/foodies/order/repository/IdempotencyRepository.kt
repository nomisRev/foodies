package io.ktor.foodies.order.repository

import io.ktor.foodies.order.database.ProcessedRequests
import kotlin.time.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

object UUIDSerializer : KSerializer<UUID> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("UUID", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): UUID = UUID.fromString(decoder.decodeString())
    override fun serialize(encoder: Encoder, value: UUID) = encoder.encodeString(value.toString())
}

@Serializable
data class ProcessedRequest(
    @Serializable(with = UUIDSerializer::class)
    val requestId: UUID,
    val commandType: String,
    val result: String?,
    val createdAt: Instant
)

interface IdempotencyRepository {
    fun findByRequestId(requestId: UUID): ProcessedRequest?
    fun save(request: ProcessedRequest)
}

class ExposedIdempotencyRepository(private val database: Database) : IdempotencyRepository {
    override fun findByRequestId(requestId: UUID): ProcessedRequest? = transaction(database) {
        ProcessedRequests.selectAll().where { ProcessedRequests.requestId eq requestId }
            .singleOrNull()
            ?.toProcessedRequest()
    }

    override fun save(request: ProcessedRequest): Unit = transaction(database) {
        ProcessedRequests.insert {
            it[requestId] = request.requestId
            it[commandType] = request.commandType
            it[result] = request.result
            it[createdAt] = request.createdAt
        }
    }

    private fun ResultRow.toProcessedRequest() = ProcessedRequest(
        requestId = this[ProcessedRequests.requestId],
        commandType = this[ProcessedRequests.commandType],
        result = this[ProcessedRequests.result],
        createdAt = this[ProcessedRequests.createdAt]
    )
}
