plugins {
    application
}

dependencies {
    implementation(libs.pekko.actor.typed)
    implementation(libs.pekko.persistence.typed)
    implementation(libs.pekko.serialization.jackson)
    implementation(libs.pekko.slf4j)
    implementation(libs.logback.classic)

    // 순수 Java LevelDB 구현 (로컬 개발/테스트용)
    implementation(libs.leveldb)

    testImplementation(libs.pekko.actor.testkit.typed)
    testImplementation(libs.pekko.persistence.testkit)
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
