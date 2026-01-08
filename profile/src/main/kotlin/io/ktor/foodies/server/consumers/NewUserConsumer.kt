package io.ktor.foodies.server.consumers

import io.ktor.foodies.server.profile.ProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retry
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("NewUserConsumer")

@Serializable
@JsonIgnoreUnknownKeys
data class NewUserEvent(
    val subject: String,
    val email: String,
    val firstName: String,
    val lastName: String,
)

fun newUserConsumer(newUsers: Flow<Message<NewUserEvent>>, profileRepository: ProfileRepository) = Consumer {
    newUsers.map { message ->
        try {
            val user = message.value
            // Ignores already existing users, consuming a message must be idempotent
            profileRepository.insertOrIgnore(user.subject, user.email, user.firstName, user.lastName)
            logger.info("Processed registration message for subject ${user.subject}")
            message.ack()
        } catch (e: Exception) {
            logger.error("Failed to process registration message", e)
            message.nack()
        }
    }.retry { e ->
        logger.error("Failed to process registration message, retrying", e)
        true // Retry and continue processing forever
    }
}
