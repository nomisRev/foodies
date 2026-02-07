plugins { id("foodies.kotlin-domain-conventions") }

dependencies {
    implementation(project(":events-common"))
    implementation(project(":rabbitmq-ext"))
    implementation(project(":server-shared"))
}
