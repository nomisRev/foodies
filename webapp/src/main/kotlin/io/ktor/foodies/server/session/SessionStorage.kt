package io.ktor.foodies.server.session

import io.ktor.server.sessions.SessionStorage
import kotlinx.serialization.Serializable

@Serializable
data class UserSession(val idToken: String)

/**
 * In-memory implementation for testing and development.
 * Not suitable for production use with multiple replicas.
 */
class InMemorySessionStorage : SessionStorage {
    private val sessions = mutableMapOf<String, String>()

    override suspend fun write(id: String, value: String) {
        sessions[id] = value
    }

    override suspend fun read(id: String): String {
        return sessions[id] ?: throw NoSuchElementException("Session $id not found")
    }

    override suspend fun invalidate(id: String) {
        sessions.remove(id)
    }
}
