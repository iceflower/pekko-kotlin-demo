plugins {
    application
}

dependencies {
    implementation(libs.pekko.actor.typed)
    implementation(libs.pekko.cluster.typed)
    implementation(libs.pekko.serialization.jackson)
    implementation(libs.pekko.slf4j)
    implementation(libs.logback.classic)

    testImplementation(libs.pekko.actor.testkit.typed)
}

application {
    mainClass.set(
        project.findProperty("mainClass")?.toString() ?: "com.example.pekko.cluster.ClusterMainKt"
    )
    applicationDefaultJvmArgs = listOf(
        "-Dfile.encoding=UTF-8",
        "-Dstdout.encoding=UTF-8",
        "-Dstderr.encoding=UTF-8"
    )
}
