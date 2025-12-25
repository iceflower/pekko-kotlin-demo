plugins {
    kotlin("jvm") version "2.3.0" apply false
}

val scalaBinaryVersion by extra("2.13")
val pekkoVersion by extra("1.3.0")

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
        "implementation"(platform("org.apache.pekko:pekko-bom_${rootProject.extra["scalaBinaryVersion"]}:${rootProject.extra["pekkoVersion"]}"))
        "implementation"(kotlin("stdlib"))

        "testImplementation"(kotlin("test"))
        "testImplementation"("org.junit.jupiter:junit-jupiter:5.10.1")
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
