package io.ktor.foodies.server.test

import dasniko.testcontainers.keycloak.KeycloakContainer
import de.infix.testBalloon.framework.core.TestSuite
import org.testcontainers.utility.MountableFile
import java.nio.file.Paths

class KeycloakContainer : KeycloakContainer("quay.io/keycloak/keycloak:26.0") {
    init {
        val realmFile = Paths.get(System.getProperty("user.dir")).parent.resolve("keycloak/realm.json")
        withCopyFileToContainer(MountableFile.forHostPath(realmFile), "/opt/keycloak/data/import/realm.json")
    }
}

fun TestSuite.keycloakContainer(): TestSuite.Fixture<KeycloakContainer> {
    val realmFile = Paths.get(System.getProperty("user.dir")).parent.resolve("keycloak/realm.json")
    return testFixture {
        KeycloakContainer("quay.io/keycloak/keycloak:26.0").apply {
            withCopyFileToContainer(MountableFile.forHostPath(realmFile), "/opt/keycloak/data/import/realm.json")
            start()
        }
    }
}
