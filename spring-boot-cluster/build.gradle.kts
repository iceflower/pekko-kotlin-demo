plugins {
    id("org.springframework.boot") version "4.0.1"
    id("io.spring.dependency-management") version "1.1.7"
}

val scalaBinaryVersion: String by rootProject.extra

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Pekko Actor + Cluster
    implementation("org.apache.pekko:pekko-actor-typed_$scalaBinaryVersion")
    implementation("org.apache.pekko:pekko-stream_$scalaBinaryVersion")
    implementation("org.apache.pekko:pekko-slf4j_$scalaBinaryVersion")
    implementation("org.apache.pekko:pekko-cluster-typed_$scalaBinaryVersion")
    implementation("org.apache.pekko:pekko-serialization-jackson_$scalaBinaryVersion")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.apache.pekko:pekko-actor-testkit-typed_$scalaBinaryVersion")
}

springBoot {
    mainClass.set("com.example.pekko.spring.cluster.SpringBootClusterApplicationKt")
}
