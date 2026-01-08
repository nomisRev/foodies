package io.ktor.foodies.keycloak

import org.keycloak.Config
import org.keycloak.events.EventListenerProvider
import org.keycloak.events.EventListenerProviderFactory
import org.keycloak.models.KeycloakSession
import org.keycloak.models.KeycloakSessionFactory

private const val DEFAULT_QUEUE_NAME = "profile.registration"

class ProfileWebhookEventListenerFactory : EventListenerProviderFactory {
    private val rabbitConfig: RabbitConfig = RabbitConfig(
        host = System.getenv("RABBITMQ_HOST") ?: "localhost",
        port = System.getenv("RABBITMQ_PORT")?.toIntOrNull() ?: 5672,
        username = System.getenv("RABBITMQ_USERNAME") ?: "guest",
        password = System.getenv("RABBITMQ_PASSWORD") ?: "guest",
        queue = System.getenv("RABBITMQ_QUEUE") ?: DEFAULT_QUEUE_NAME,
    )

    override fun create(session: KeycloakSession): EventListenerProvider =
        ProfileWebhookEventListener(rabbitConfig)

    override fun init(config: Config.Scope) {}
    override fun postInit(factory: KeycloakSessionFactory) = Unit
    override fun close() = Unit
    override fun getId(): String = "profile-webhook"
}