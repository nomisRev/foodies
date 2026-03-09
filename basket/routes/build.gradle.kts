plugins {
    id("foodies.kotlin-domain-conventions")
}

dependencies {
    implementation(project(":server-shared"))
    implementation(libs.spine.api)
    implementation(libs.serialization.json)
}
