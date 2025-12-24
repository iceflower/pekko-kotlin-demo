plugins {
    kotlin("jvm") version "2.3.0"
    application
}

group = "com.example"
version = "1.0-SNAPSHOT"

val scalaBinaryVersion = "2.13"
val pekkoVersion = "1.3.0"  // 안정 버전

repositories {
    mavenCentral()
}

dependencies {
    // Pekko BOM (Bill of Materials)
    implementation(platform("org.apache.pekko:pekko-bom_$scalaBinaryVersion:$pekkoVersion"))

    // Pekko Actor (Typed API - 권장)
    implementation("org.apache.pekko:pekko-actor-typed_$scalaBinaryVersion")

    // Pekko Streams
    implementation("org.apache.pekko:pekko-stream_$scalaBinaryVersion")

    // Pekko SLF4J 로깅
    implementation("org.apache.pekko:pekko-slf4j_$scalaBinaryVersion")

    // Logback (로깅 구현체)
    implementation("ch.qos.logback:logback-classic:1.5.21")

    // Kotlin 표준 라이브러리
    implementation(kotlin("stdlib"))

    // 테스트
    testImplementation("org.apache.pekko:pekko-actor-testkit-typed_$scalaBinaryVersion")
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
}

application {
    mainClass.set(
        project.findProperty("mainClass")?.toString() ?: "com.example.pekko.MainKt"
    )
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(25)
}
