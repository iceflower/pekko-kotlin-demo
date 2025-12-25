plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "pekko-kotlin-demo"

include("core")
include("cluster")
include("persistence")
include("http")
include("grpc")
include("spring-boot")
include("spring-boot-cluster")
include("quarkus")
include("quarkus-cluster")
