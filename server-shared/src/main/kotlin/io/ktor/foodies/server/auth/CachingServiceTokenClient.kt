package io.ktor.foodies.server.auth

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory

class CachingServiceTokenClient(
    private val delegate: KeycloakServiceTokenClient,
    private val bufferSeconds: Long = 30,
    private val clock: () -> Long = { System.currentTimeMillis() }
) : ServiceTokenClient {
    private val logger = LoggerFactory.getLogger(CachingServiceTokenClient::class.java)
    private val cache = ConcurrentHashMap<List<String>, CachedToken>()
    private val mutex = Mutex()

    private data class CachedToken(
        val token: String,
        val expiresAt: Long
    )

    override suspend fun getToken(): String = getTokenWithScopes(delegate.config.defaultScopes)

    override suspend fun getTokenWithScopes(scopes: List<String>): String {
        val sortedScopes = scopes.sorted()
        val now = clock()
        
        cache[sortedScopes]?.let { cached ->
            if (cached.expiresAt > now + (bufferSeconds * 1000)) {
                return cached.token
            }
        }

        return mutex.withLock {
            // Re-check after acquiring lock
            val nowInLock = clock()
            cache[sortedScopes]?.let { cached ->
                if (cached.expiresAt > nowInLock + (bufferSeconds * 1000)) {
                    return cached.token
                }
            }

            logger.info("Cache miss or expired for scopes {}. Fetching new token.", sortedScopes)
            val response = delegate.getServiceToken(sortedScopes)
            val expiresAt = nowInLock + (response.expiresIn * 1000)
            val token = response.accessToken
            
            cache[sortedScopes] = CachedToken(token, expiresAt)
            token
        }
    }
}
