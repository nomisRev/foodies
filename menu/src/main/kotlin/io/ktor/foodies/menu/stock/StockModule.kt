package io.ktor.foodies.menu.stock

import io.ktor.foodies.rabbitmq.RabbitMQSubscriber
import kotlinx.coroutines.flow.Flow
import org.jetbrains.exposed.v1.jdbc.Database

data class StockModule(
    val consumers: List<Flow<Unit>>
)

fun stockModule(
    database: Database,
    subscriber: RabbitMQSubscriber,
    eventPublisher: StockEventPublisher,
    queueName: String
): StockModule {
    val repository = ExposedStockRepository(database)
    val service = StockServiceImpl(repository)
    val consumers = listOf(
        orderAwaitingValidationConsumer(subscriber, queueName, service, eventPublisher),
        stockReturnedConsumer(subscriber, "menu.stock-returned", service)
    )
    return StockModule(consumers = consumers)
}
