package io.ktor.foodies.server.test

import dasniko.testcontainers.keycloak.KeycloakContainer
import org.testcontainers.utility.MountableFile
import java.io.File
import java.nio.file.Paths

fun keycloakContainer(realmJsonPath: String = "keycloak/realm.json"): KeycloakContainer {
    val file = File(realmJsonPath)
    val actualFile = if (file.exists()) file else File("../$realmJsonPath")

    if (!actualFile.exists()) {
        throw IllegalArgumentException("Realm file not found at ${actualFile.absolutePath}")
    }

    return KeycloakContainer("quay.io/keycloak/keycloak:26.5.1")
        .withCopyFileToContainer(
            MountableFile.forHostPath(actualFile.toPath()),
            "/opt/keycloak/data/import/realm.json"
        )
}
