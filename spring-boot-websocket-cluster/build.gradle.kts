plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.kotlin.spring)
}

dependencies {
    // Spring Boot WebSocket
    implementation(libs.spring.boot.starter.websocket)
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
    mainClass.set("com.example.pekko.spring.websocket.cluster.SpringBootWebSocketClusterApplicationKt")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}
