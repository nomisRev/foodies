package io.ktor.foodies.order.tracking

import io.ktor.foodies.order.persistence.OrderRepository
import io.ktor.foodies.rabbitmq.Publisher
import io.ktor.foodies.server.DataSource

data class TrackingModule(
    val service: TrackingService,
)

fun trackingModule(
    dataSource: DataSource,
    orderRepository: OrderRepository,
    publisher: Publisher,
): TrackingModule {
    val repository = ExposedTrackingRepository(dataSource.database)
    val eventPublisher = RabbitTrackingEventPublisher(publisher)
    val service = DefaultTrackingService(repository, orderRepository, eventPublisher)
    return TrackingModule(service)
}
