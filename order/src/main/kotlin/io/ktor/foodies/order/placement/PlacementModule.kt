package io.ktor.foodies.order.placement

import io.ktor.client.HttpClient
import io.ktor.foodies.order.OrderConfig
import io.ktor.foodies.rabbitmq.Publisher
import io.ktor.foodies.server.DataSource

data class PlacementModule(
    val service: PlacementService,
)

fun placementModule(
    orderConfig: OrderConfig,
    basketBaseUrl: String,
    dataSource: DataSource,
    publisher: Publisher,
    httpClient: HttpClient,
): PlacementModule {
    val repository = ExposedPlacementRepository(dataSource.database)
    val basketClient = HttpBasketClient(httpClient, basketBaseUrl)
    val eventPublisher = RabbitPlacementEventPublisher(publisher)
    val service = DefaultPlacementService(repository, basketClient, eventPublisher, orderConfig)
    return PlacementModule(service)
}
