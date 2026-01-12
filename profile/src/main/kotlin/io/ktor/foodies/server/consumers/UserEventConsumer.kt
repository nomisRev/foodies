package io.ktor.foodies.server.consumers

import io.ktor.foodies.rabbitmq.Consumer
import io.ktor.foodies.rabbitmq.Message
import io.ktor.foodies.server.profile.ProfileRepository
import io.ktor.foodies.user.event.UserEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retry
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("NewUserConsumer")

fun userEventConsumer(newUsers: Flow<Message<UserEvent>>, profileRepository: ProfileRepository) = Consumer {
    newUsers.map { message ->
        try {
            when (val event = message.value) {
                is UserEvent.Registration -> {
                    // Ignores already existing users, consuming a message must be idempotent
                    profileRepository.insertOrIgnore(event.subject, event.email, event.firstName, event.lastName)
                    logger.info("Processed registration message for subject ${event.subject}")
                }
                is UserEvent.UpdateProfile -> {
                    profileRepository.upsert(event.subject, event.email, event.firstName, event.lastName)
                    logger.info("Processed update message for subject ${event.subject}")
                }

                is UserEvent.Delete -> {
                    profileRepository.deleteBySubject(event.subject)
                    logger.info("Processed delete message for subject ${event.subject}")
                }
            }

            message.ack()
        } catch (e: Exception) {
            logger.error("Failed to process user message", e)
            message.nack()
        }
    }.retry { e ->
        delay(1000) // TODO: Introduce proper resilience schedule
        logger.error("Failed to process user message, retrying", e)
        true // Retry and continue processing forever
    }
}
