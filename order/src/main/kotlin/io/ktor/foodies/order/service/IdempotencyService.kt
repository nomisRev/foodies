package io.ktor.foodies.order.service

import io.ktor.foodies.order.repository.IdempotencyRepository
import io.ktor.foodies.order.repository.ProcessedRequest
import kotlin.time.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

class IdempotencyService(
    @PublishedApi internal val repository: IdempotencyRepository,
    @PublishedApi internal val json: Json = Json { ignoreUnknownKeys = true }
) {
    suspend inline fun <reified T> executeIdempotent(
        requestId: UUID,
        commandType: String,
        crossinline operation: suspend () -> T
    ): T {
        // Check if already processed
        val existing = repository.findByRequestId(requestId)
        if (existing != null) {
            return if (existing.result != null) {
                json.decodeFromString<T>(existing.result)
            } else {
                null as T
            }
        }

        // Execute operation
        val result = operation()

        // Store result for future duplicate requests
        repository.save(
            ProcessedRequest(
                requestId = requestId,
                commandType = commandType,
                result = json.encodeToString(result),
                createdAt = Instant.fromEpochMilliseconds(System.currentTimeMillis())
            )
        )

        return result
    }
}
