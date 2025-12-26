plugins {
    alias(libs.plugins.kotlin.jvm) apply false
}

allprojects {
    group = "com.example"
    version = "1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    dependencies {
        "implementation"(platform(rootProject.libs.pekko.bom))
        "implementation"(rootProject.libs.kotlin.stdlib)

        "testImplementation"(rootProject.libs.kotlin.test)
        // Kotest
        "testImplementation"(rootProject.libs.kotest.runner.junit5)
        "testImplementation"(rootProject.libs.kotest.assertions.core)
        "testImplementation"(rootProject.libs.kotest.property)
    }

    tasks.withType<Test> {
        useJUnitPlatform()

        // Windows 한글 경로 문제 해결
        jvmArgs(
            "-Dfile.encoding=UTF-8",
            "-Djava.io.tmpdir=C:/Temp",
            "-Duser.home=C:/gradle-home"
        )

        // 테스트 실행 환경 설정
        environment("TEMP", "C:\\Temp")
        environment("TMP", "C:\\Temp")
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
    }

    configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
        jvmToolchain(25)
    }
}
