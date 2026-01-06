package io.ktor.foodies.server

import de.infix.testBalloon.framework.core.TestExecutionScope
import de.infix.testBalloon.framework.core.TestSuite
import de.infix.testBalloon.framework.shared.TestRegistering
import io.ktor.server.testing.ApplicationTestBuilder

@TestRegistering
fun TestSuite.testApplication(
    name: String,
    block: suspend context(TestExecutionScope)ApplicationTestBuilder.() -> Unit,
) = test(name) { io.ktor.server.testing.testApplication { block() } }
