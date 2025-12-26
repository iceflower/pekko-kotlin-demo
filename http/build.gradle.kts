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

    // Jackson for JSON serialization
    implementation(libs.jackson.module.kotlin)

    testImplementation(libs.pekko.actor.testkit.typed)
    testImplementation(libs.pekko.http.testkit)
}

application {
    mainClass.set(
        project.findProperty("mainClass")?.toString() ?: "com.example.pekko.http.HttpMainKt"
    )
    applicationDefaultJvmArgs = listOf(
        "-Dfile.encoding=UTF-8",
        "-Dstdout.encoding=UTF-8",
        "-Dstderr.encoding=UTF-8"
    )
}
