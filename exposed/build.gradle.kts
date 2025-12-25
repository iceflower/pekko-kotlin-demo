plugins {
    application
}

val scalaBinaryVersion: String by rootProject.extra
val exposedVersion = "0.61.0"

dependencies {
    // Pekko
    implementation("org.apache.pekko:pekko-actor-typed_$scalaBinaryVersion")
    implementation("org.apache.pekko:pekko-slf4j_$scalaBinaryVersion")

    // JetBrains Exposed
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-kotlin-datetime:$exposedVersion")

    // H2 Database (embedded, for demo)
    implementation("com.h2database:h2:2.2.224")

    // HikariCP for connection pooling
    implementation("com.zaxxer:HikariCP:5.1.0")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.5.21")

    // Test
    testImplementation("org.apache.pekko:pekko-actor-testkit-typed_$scalaBinaryVersion")
}

application {
    mainClass.set("com.example.pekko.exposed.ExposedMainKt")
}
