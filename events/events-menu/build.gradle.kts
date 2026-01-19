plugins { id("foodies.kotlin-conventions") }

dependencies {
    implementation(project(":events-common"))
    implementation(project(":rabbitmq-ext"))
    implementation(project(":server-shared"))
}
