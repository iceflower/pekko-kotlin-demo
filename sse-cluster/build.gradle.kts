plugins {
    application
}

val scalaBinaryVersion: String by rootProject.extra
val pekkoHttpVersion = "1.1.0"

dependencies {
    // Pekko Actor & Cluster
    implementation("org.apache.pekko:pekko-actor-typed_$scalaBinaryVersion")
    implementation("org.apache.pekko:pekko-cluster-typed_$scalaBinaryVersion")
    implementation("org.apache.pekko:pekko-cluster-tools_$scalaBinaryVersion")
    implementation("org.apache.pekko:pekko-serialization-jackson_$scalaBinaryVersion")

    // Pekko HTTP & Stream
    implementation("org.apache.pekko:pekko-stream_$scalaBinaryVersion")
    implementation("org.apache.pekko:pekko-http_$scalaBinaryVersion:$pekkoHttpVersion")
    implementation("org.apache.pekko:pekko-http-jackson_$scalaBinaryVersion:$pekkoHttpVersion")

    // Logging
    implementation("org.apache.pekko:pekko-slf4j_$scalaBinaryVersion")
    implementation("ch.qos.logback:logback-classic:1.5.21")

    // Jackson for Kotlin
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.0")

    // Test
    testImplementation("org.apache.pekko:pekko-actor-testkit-typed_$scalaBinaryVersion")
    testImplementation("org.apache.pekko:pekko-stream-testkit_$scalaBinaryVersion")
}

application {
    mainClass.set(
        project.findProperty("mainClass")?.toString()
            ?: "com.example.pekko.sse.cluster.SseClusterMainKt"
    )
    applicationDefaultJvmArgs = listOf(
        "-Dfile.encoding=UTF-8",
        "-Dstdout.encoding=UTF-8",
        "-Dstderr.encoding=UTF-8"
    )
}
