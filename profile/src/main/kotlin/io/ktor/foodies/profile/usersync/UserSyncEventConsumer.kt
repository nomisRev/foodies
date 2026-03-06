package io.ktor.foodies.profile.usersync

import io.ktor.foodies.events.user.UserEvent
import io.ktor.foodies.profile.persistence.ProfileRepository
import io.ktor.foodies.rabbitmq.Message
import io.ktor.foodies.rabbitmq.parConsumeMessage
import kotlinx.coroutines.flow.Flow
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("UserSyncEventConsumer")

fun userSyncEventConsumer(newUsers: Flow<Message<UserEvent>>, profileRepository: ProfileRepository) =
    newUsers.parConsumeMessage { event ->
        when (event) {
            is UserEvent.Registration -> {
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
