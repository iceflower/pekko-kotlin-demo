plugins {
    application
}

dependencies {
    // Pekko Actor & Cluster
    implementation(libs.pekko.actor.typed)
    implementation(libs.pekko.cluster.typed)
    implementation(libs.pekko.cluster.tools)
    implementation(libs.pekko.serialization.jackson)

    // Pekko HTTP & Stream
    implementation(libs.pekko.stream)
    implementation(libs.pekko.http)
    implementation(libs.pekko.http.jackson)

    // Logging
    implementation(libs.pekko.slf4j)
    implementation(libs.logback.classic)

    // Jackson for Kotlin
    implementation(libs.jackson.module.kotlin)

    // Test
    testImplementation(libs.pekko.actor.testkit.typed)
    testImplementation(libs.pekko.stream.testkit)
    testImplementation(libs.pekko.http.testkit)
}

application {
    mainClass.set(
        project.findProperty("mainClass")?.toString()
            ?: "com.example.pekko.websocket.cluster.WebSocketClusterMainKt"
    )
    applicationDefaultJvmArgs = listOf(
        "-Dfile.encoding=UTF-8",
        "-Dstdout.encoding=UTF-8",
        "-Dstderr.encoding=UTF-8"
    )
}
