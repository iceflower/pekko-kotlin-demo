plugins {
    alias(libs.plugins.spring.boot.legacy)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    // Spring Boot
    implementation(libs.spring.boot.starter.web)
    implementation(libs.jackson.module.kotlin)

    // Pekko Actor (required)
    implementation(libs.pekko.actor.typed)
    implementation(libs.pekko.stream)
    implementation(libs.pekko.slf4j)

    // Pekko Cluster (optional - included but conditionally activated)
    implementation(libs.pekko.cluster.typed)
    implementation(libs.pekko.serialization.jackson)

    // Testing
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.pekko.actor.testkit.typed)
}

tasks.withType<org.springframework.boot.gradle.tasks.bundling.BootJar> {
    mainClass.set("com.example.pekko.spring.optionalcluster.SpringBootOptionalClusterApplicationKt")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}
