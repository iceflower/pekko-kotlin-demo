plugins {
    application
}

val scalaBinaryVersion: String by rootProject.extra

dependencies {
    implementation("org.apache.pekko:pekko-actor-typed_$scalaBinaryVersion")
    implementation("org.apache.pekko:pekko-cluster-typed_$scalaBinaryVersion")
    implementation("org.apache.pekko:pekko-serialization-jackson_$scalaBinaryVersion")
    implementation("org.apache.pekko:pekko-slf4j_$scalaBinaryVersion")
    implementation("ch.qos.logback:logback-classic:1.5.21")

    testImplementation("org.apache.pekko:pekko-actor-testkit-typed_$scalaBinaryVersion")
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
