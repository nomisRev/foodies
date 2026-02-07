package io.ktor.foodies.server.test

import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.TestSuite
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.framework.shared.TestDisplayName
import de.infix.testBalloon.framework.shared.TestElementName
import de.infix.testBalloon.framework.shared.TestRegistering

@TestRegistering
fun <A> ctxSuite(
    @TestElementName name: String = "",
    @TestDisplayName displayName: String = name,
    testConfig: TestConfig = TestConfig,
    context: TestSuite.() -> A,
    content:
        context(A)
        TestSuite.() -> Unit,
): Lazy<TestSuite> = testSuite(name, displayName, testConfig) { content(context(), this) }
