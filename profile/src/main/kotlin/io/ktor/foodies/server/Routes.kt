package io.ktor.foodies.server

import io.ktor.foodies.server.openid.*
import io.ktor.foodies.server.profile.ProfileRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("ProfileRoutes")

fun Route.profileRoutes(profileRepository: ProfileRepository) {
    profileUserRoutes(profileRepository)
    profileServiceRoutes(profileRepository)
}

/**
 * User-to-service API: Routes for end-user interactions with their profile.
 */
fun Route.profileUserRoutes(profileRepository: ProfileRepository) = authenticatedUser {
    route("/profile") {
        requireAdminOrScope("profile:read") {
            get {
                val user = userPrincipal()
                val profile = profileRepository.findBySubject(user.userId)
                if (profile == null) {
                    call.respond(HttpStatusCode.NotFound, "Profile not found")
                } else {
                    call.respond(profile)
                }
            }
        }
    }
}

/**
 * Service-to-service API: Routes for internal service communication.
 * Supports both user tokens (delegation) and service tokens.
 */
fun Route.profileServiceRoutes(profileRepository: ProfileRepository) = authenticated {
    requireScope("profile:read") {
        get("/internal/profile/{userId}") {
            val principal = authPrincipal()
            val userId: String by call.parameters

            when (principal) {
                is ServicePrincipal -> logger.info("Service {} accessing profile for user {}", principal.serviceId, userId)
                is UserPrincipal -> logger.info("User {} (via delegation) accessing profile for user {}", principal.userId, userId)
            }

            val profile = profileRepository.findBySubject(userId)
            if (profile == null) {
                call.respond(HttpStatusCode.NotFound, "Profile not found")
            } else {
                call.respond(profile)
            }
        }
    }
}
