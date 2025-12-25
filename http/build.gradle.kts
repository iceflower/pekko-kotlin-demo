plugins {
    application
}

val scalaBinaryVersion: String by rootProject.extra
val pekkoHttpVersion = "1.1.0"

dependencies {
    implementation("org.apache.pekko:pekko-actor-typed_$scalaBinaryVersion")
    implementation("org.apache.pekko:pekko-stream_$scalaBinaryVersion")
    implementation("org.apache.pekko:pekko-http_$scalaBinaryVersion:$pekkoHttpVersion")
    implementation("org.apache.pekko:pekko-http-jackson_$scalaBinaryVersion:$pekkoHttpVersion")
    implementation("org.apache.pekko:pekko-slf4j_$scalaBinaryVersion")
    implementation("ch.qos.logback:logback-classic:1.5.21")

    // Jackson for JSON serialization
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.0")

    testImplementation("org.apache.pekko:pekko-actor-testkit-typed_$scalaBinaryVersion")
    testImplementation("org.apache.pekko:pekko-http-testkit_$scalaBinaryVersion")
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
