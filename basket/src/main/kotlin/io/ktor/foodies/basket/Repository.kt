package io.ktor.foodies.basket

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.coroutines
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
import kotlinx.serialization.json.Json

interface BasketRepository {
    suspend fun getBasket(buyerId: String): CustomerBasket?
    suspend fun updateBasket(basket: CustomerBasket): CustomerBasket
    suspend fun deleteBasket(buyerId: String): Boolean
}

@OptIn(ExperimentalLettuceCoroutinesApi::class)
class RedisBasketRepository(
    private val redis: RedisCoroutinesCommands<String, String>
) : BasketRepository {

    private fun basketKey(buyerId: String): String = "/basket/$buyerId"

    override suspend fun getBasket(buyerId: String): CustomerBasket? {
        val data = redis.get(basketKey(buyerId)) ?: return null
        return Json.decodeFromString<CustomerBasket>(data)
    }

    override suspend fun updateBasket(basket: CustomerBasket): CustomerBasket {
        val data = Json.encodeToString(CustomerBasket.serializer(), basket)
        redis.set(basketKey(basket.buyerId), data)
        return basket
    }

    override suspend fun deleteBasket(buyerId: String): Boolean {
        return (redis.del(basketKey(buyerId)) ?: 0L) > 0
    }
}

/**
 * Creates a Redis client and returns both the client (for lifecycle management)
 * and the coroutine-based commands interface.
 */
@OptIn(ExperimentalLettuceCoroutinesApi::class)
fun createRedisClient(config: RedisConfig): Pair<RedisClient, StatefulRedisConnection<String, String>> {
    val redisUri = if (config.password.isBlank()) {
        "redis://${config.host}:${config.port}"
    } else {
        "redis://:${config.password}@${config.host}:${config.port}"
    }
    val client = RedisClient.create(redisUri)
    val connection = client.connect()
    return client to connection
}

/**
 * In-memory implementation for testing purposes.
 */
class InMemoryBasketRepository : BasketRepository {
    private val baskets = mutableMapOf<String, CustomerBasket>()

    override suspend fun getBasket(buyerId: String): CustomerBasket? = baskets[buyerId]

    override suspend fun updateBasket(basket: CustomerBasket): CustomerBasket {
        baskets[basket.buyerId] = basket
        return basket
    }

    override suspend fun deleteBasket(buyerId: String): Boolean = baskets.remove(buyerId) != null
}

