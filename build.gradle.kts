plugins {
    kotlin("jvm") version "2.3.0" apply false
}

val scalaBinaryVersion by extra("2.13")
val pekkoVersion by extra("1.4.0")

allprojects {
    group = "com.example"
    version = "1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    val kotestVersion = "6.0.5"

    dependencies {
        "implementation"(platform("org.apache.pekko:pekko-bom_${rootProject.extra["scalaBinaryVersion"]}:${rootProject.extra["pekkoVersion"]}"))
        "implementation"(kotlin("stdlib"))

        "testImplementation"(kotlin("test"))
        // Kotest
        "testImplementation"("io.kotest:kotest-runner-junit5:$kotestVersion")
        "testImplementation"("io.kotest:kotest-assertions-core:$kotestVersion")
        "testImplementation"("io.kotest:kotest-property:$kotestVersion")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
    }

    configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
        jvmToolchain(25)
    }
}
