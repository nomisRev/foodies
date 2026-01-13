package io.ktor.foodies.server.session

import de.infix.testBalloon.framework.core.testSuite
import io.ktor.foodies.server.security.UserSession
import io.ktor.foodies.server.security.withUserSession
import io.ktor.foodies.server.test.ctxSuite
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.foodies.server.test.testApplication
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.sessions.SessionStorageMemory
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.cookie
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import kotlin.test.assertEquals

val userSessionScopeSpec by testSuite {

    testApplication("returns 401 and HX-Redirect when no session") {
        install(Sessions) { cookie<UserSession>("USER_SESSION", SessionStorageMemory()) }
        routing {
            withUserSession {
                get("/protected") {
                    call.respondText("OK")
                }
            }
        }

        val response = client.get("/protected")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals("/login", response.headers["HX-Redirect"])
    }

    testApplication("returns 200 and session data when session exists") {
        install(Sessions) { cookie<UserSession>("USER_SESSION", SessionStorageMemory()) }
        routing {
            get("/set-session") {
                call.sessions.set(UserSession("test-token"))
                call.respondText("Session set")
            }
            route("/protected") {
                withUserSession {
                    get {
                        val session = session()
                        call.respondText("Hello ${session.idToken}")
                    }
                }
            }
        }

        val client = createClient { install(HttpCookies) }

        val setResp = client.get("/set-session")
        assertEquals(HttpStatusCode.OK, setResp.status)

        val response = client.get("/protected")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Hello test-token", response.bodyAsText())
    }
}
