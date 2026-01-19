package io.ktor.foodies.keycloak

import com.rabbitmq.client.ConnectionFactory
import org.jboss.logging.Logger
import org.keycloak.Config
import org.keycloak.events.EventListenerProvider
import org.keycloak.events.EventListenerProviderFactory
import org.keycloak.models.KeycloakSession
import org.keycloak.models.KeycloakSessionFactory

private const val DEFAULT_QUEUE_NAME = "profile.registration"

class ProfileWebhookEventListenerFactory : EventListenerProviderFactory {
    private val logger = Logger.getLogger(ProfileWebhookEventListenerFactory::class.java)

    private val rabbitConfig: RabbitConfig =
        RabbitConfig(
            host = System.getenv("RABBITMQ_HOST") ?: "localhost",
            port = System.getenv("RABBITMQ_PORT")?.toIntOrNull() ?: 5672,
            username = System.getenv("RABBITMQ_USERNAME") ?: "guest",
            password = System.getenv("RABBITMQ_PASSWORD") ?: "guest",
            queue = System.getenv("RABBITMQ_QUEUE") ?: DEFAULT_QUEUE_NAME,
        )

    private val connectionFactory =
        ConnectionFactory().apply {
            host = rabbitConfig.host
            port = rabbitConfig.port
            username = rabbitConfig.username
            password = rabbitConfig.password
        }
    private val connection = lazy { connectionFactory.newConnection("profile-webhook") }

    override fun init(config: Config.Scope) {}

    override fun postInit(factory: KeycloakSessionFactory) = Unit

    override fun getId(): String = "profile-webhook"

    override fun create(session: KeycloakSession): EventListenerProvider =
        ProfileWebhookEventListener(rabbitConfig, connection)

    override fun close() {
        runCatching { if (connection.isInitialized()) connection.value.close() }
            .onFailure { logger.warn("Failed to close RabbitMQ connection", it) }
    }
}
