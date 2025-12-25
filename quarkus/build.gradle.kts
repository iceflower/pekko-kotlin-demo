plugins {
    application
    id("io.quarkus") version "3.30.5"
    kotlin("plugin.allopen") version "2.3.0"
}

val scalaBinaryVersion: String by rootProject.extra
val quarkusVersion = "3.30.5"

dependencies {
    // Quarkus
    implementation(enforcedPlatform("io.quarkus.platform:quarkus-bom:$quarkusVersion"))
    implementation("io.quarkus:quarkus-kotlin")
    implementation("io.quarkus:quarkus-arc")
    implementation("io.quarkus:quarkus-rest")
    implementation("io.quarkus:quarkus-rest-jackson")

    // Pekko Actor
    implementation("org.apache.pekko:pekko-actor-typed_$scalaBinaryVersion")
    implementation("org.apache.pekko:pekko-stream_$scalaBinaryVersion")
    implementation("org.apache.pekko:pekko-slf4j_$scalaBinaryVersion")

    // Testing
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("io.rest-assured:rest-assured")
    testImplementation("org.apache.pekko:pekko-actor-testkit-typed_$scalaBinaryVersion")
}

allOpen {
    annotation("jakarta.ws.rs.Path")
    annotation("jakarta.enterprise.context.ApplicationScoped")
    annotation("jakarta.enterprise.context.RequestScoped")
    annotation("io.quarkus.test.junit.QuarkusTest")
}

application {
    mainClass.set("io.quarkus.runner.GeneratedMain")
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
