plugins {
    application
}

dependencies {
    implementation(libs.pekko.actor.typed)
    implementation(libs.pekko.stream)
    implementation(libs.pekko.http)
    implementation(libs.pekko.http.jackson)
    implementation(libs.pekko.slf4j)
    implementation(libs.logback.classic)
    implementation(libs.jackson.module.kotlin)

    testImplementation(libs.pekko.actor.testkit.typed)
    testImplementation(libs.pekko.stream.testkit)
    testImplementation(libs.pekko.http.testkit)
}

application {
    mainClass.set("com.example.pekko.websocket.WebSocketMainKt")
}
