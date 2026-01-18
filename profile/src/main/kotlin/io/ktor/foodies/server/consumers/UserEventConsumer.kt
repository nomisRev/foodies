package io.ktor.foodies.server.consumers

import io.ktor.foodies.rabbitmq.Message
import io.ktor.foodies.rabbitmq.parConsumeMessage
import io.ktor.foodies.server.profile.ProfileRepository
import io.ktor.foodies.events.user.UserEvent
import kotlinx.coroutines.flow.Flow
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("NewUserConsumer")

fun userEventConsumer(newUsers: Flow<Message<UserEvent>>, profileRepository: ProfileRepository) =
    newUsers.parConsumeMessage { event ->
        when (event) {
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
    }
