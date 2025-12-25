plugins {
    application
}

val scalaBinaryVersion: String by rootProject.extra

dependencies {
    implementation("org.apache.pekko:pekko-actor-typed_$scalaBinaryVersion")
    implementation("org.apache.pekko:pekko-persistence-typed_$scalaBinaryVersion")
    implementation("org.apache.pekko:pekko-serialization-jackson_$scalaBinaryVersion")
    implementation("org.apache.pekko:pekko-slf4j_$scalaBinaryVersion")
    implementation("ch.qos.logback:logback-classic:1.5.21")

    // 순수 Java LevelDB 구현 (로컬 개발/테스트용)
    implementation("org.iq80.leveldb:leveldb:0.12")

    testImplementation("org.apache.pekko:pekko-actor-testkit-typed_$scalaBinaryVersion")
    testImplementation("org.apache.pekko:pekko-persistence-testkit_$scalaBinaryVersion")
}

application {
    mainClass.set(
        project.findProperty("mainClass")?.toString() ?: "com.example.pekko.persistence.PersistenceMainKt"
    )
    applicationDefaultJvmArgs = listOf(
        "-Dfile.encoding=UTF-8",
        "-Dstdout.encoding=UTF-8",
        "-Dstderr.encoding=UTF-8"
    )
}
