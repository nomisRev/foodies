plugins {
    id("foodies.kotlin-domain-conventions")
}

dependencies {
    implementation(libs.spine.api)
    implementation(libs.spine.client)
    implementation(libs.serialization.json)
}
