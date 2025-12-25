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

    // Pekko Actor + Cluster
    implementation("org.apache.pekko:pekko-actor-typed_$scalaBinaryVersion")
    implementation("org.apache.pekko:pekko-stream_$scalaBinaryVersion")
    implementation("org.apache.pekko:pekko-slf4j_$scalaBinaryVersion")
    implementation("org.apache.pekko:pekko-cluster-typed_$scalaBinaryVersion")
    implementation("org.apache.pekko:pekko-serialization-jackson_$scalaBinaryVersion")

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

// Quarkus Gradle 플러그인 Worker 프로세스 설정
// WSL 환경에서의 호환성 문제 해결을 위해 fork 비활성화
tasks.withType<io.quarkus.gradle.tasks.QuarkusGenerateCode> {
    // Quarkus 3.8+ 에서는 fork 옵션이 제거됨
}
