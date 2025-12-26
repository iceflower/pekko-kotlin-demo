plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.kotlin.spring)
}

dependencies {
    // Spring Boot Web (for SSE)
    implementation(libs.spring.boot.starter.web)
    implementation(libs.jackson.module.kotlin)

    // Pekko Actor + Cluster
    implementation(libs.pekko.actor.typed)
    implementation(libs.pekko.stream)
    implementation(libs.pekko.slf4j)
    implementation(libs.pekko.cluster.typed)
    implementation(libs.pekko.cluster.tools)
    implementation(libs.pekko.serialization.jackson)

    // Testing
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.pekko.actor.testkit.typed)
}

tasks.withType<org.springframework.boot.gradle.tasks.bundling.BootJar> {
    mainClass.set("com.example.pekko.spring.sse.cluster.SpringBootSseClusterApplicationKt")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}
