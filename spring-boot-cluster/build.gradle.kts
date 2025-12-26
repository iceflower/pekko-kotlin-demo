plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    // Spring Boot
    implementation(libs.spring.boot.starter.web)
    implementation(libs.jackson.module.kotlin)

    // Pekko Actor + Cluster
    implementation(libs.pekko.actor.typed)
    implementation(libs.pekko.stream)
    implementation(libs.pekko.slf4j)
    implementation(libs.pekko.cluster.typed)
    implementation(libs.pekko.serialization.jackson)

    // Testing
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.pekko.actor.testkit.typed)
}

springBoot {
    mainClass.set("com.example.pekko.spring.cluster.SpringBootClusterApplicationKt")
}
