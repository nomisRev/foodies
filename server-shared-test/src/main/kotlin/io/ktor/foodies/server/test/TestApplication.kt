package io.ktor.foodies.server.test

import de.infix.testBalloon.framework.core.TestExecutionScope
import de.infix.testBalloon.framework.core.TestSuite
import de.infix.testBalloon.framework.shared.TestRegistering
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication

@TestRegistering
fun TestSuite.testApplication(
    name: String,
    block: suspend context(TestExecutionScope) ApplicationTestBuilder.() -> Unit,
) = test(name) { testApplication { block() } }

fun ApplicationTestBuilder.jsonClient() = createClient {
    install(ContentNegotiation) { json() }
}