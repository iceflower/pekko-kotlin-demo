plugins {
    application
    id("org.springframework.boot") version "3.4.1"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("plugin.spring") version "2.3.0"
}

val scalaBinaryVersion: String by rootProject.extra

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Pekko Actor
    implementation("org.apache.pekko:pekko-actor-typed_$scalaBinaryVersion")
    implementation("org.apache.pekko:pekko-stream_$scalaBinaryVersion")
    implementation("org.apache.pekko:pekko-slf4j_$scalaBinaryVersion")

    // Logging (Spring Boot uses Logback by default)

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.apache.pekko:pekko-actor-testkit-typed_$scalaBinaryVersion")
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
