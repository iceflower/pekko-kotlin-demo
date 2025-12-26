plugins {
    application
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.kotlin.spring)
}

dependencies {
    // Spring Boot
    implementation(libs.spring.boot.starter.web)
    implementation(libs.jackson.module.kotlin)

    // Pekko Actor
    implementation(libs.pekko.actor.typed)
    implementation(libs.pekko.stream)
    implementation(libs.pekko.slf4j)

    // Logging (Spring Boot uses Logback by default)

    // Testing
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.pekko.actor.testkit.typed)
}

application {
    mainClass.set("com.example.pekko.spring.SpringBootPekkoApplicationKt")
    applicationDefaultJvmArgs = listOf(
        "-Dfile.encoding=UTF-8",
        "-Dstdout.encoding=UTF-8",
        "-Dstderr.encoding=UTF-8"
    )
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}
