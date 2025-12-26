plugins {
    application
}

dependencies {
    // Pekko
    implementation(libs.pekko.actor.typed)
    implementation(libs.pekko.slf4j)

    // JetBrains Exposed
    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.kotlin.datetime)

    // H2 Database (embedded, for demo)
    implementation(libs.h2.database)

    // HikariCP for connection pooling
    implementation(libs.hikaricp)

    // Logging
    implementation(libs.logback.classic)

    // Test
    testImplementation(libs.pekko.actor.testkit.typed)
}

application {
    mainClass.set("com.example.pekko.exposed.ExposedMainKt")
}
