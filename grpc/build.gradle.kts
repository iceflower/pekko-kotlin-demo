import com.google.protobuf.gradle.*

plugins {
    application
    alias(libs.plugins.protobuf)
}

// Version catalog에서 버전을 참조하기 위해 정의
val grpcVersion = "1.76.2"
val protobufVersion = "4.29.3"

dependencies {
    implementation(libs.pekko.actor.typed)
    implementation(libs.pekko.stream)
    implementation(libs.pekko.slf4j)
    implementation(libs.logback.classic)

    // gRPC dependencies
    implementation(libs.grpc.stub)
    implementation(libs.grpc.protobuf)
    implementation(libs.grpc.netty.shaded)
    implementation(libs.protobuf.java)

    // For gRPC service implementation
    implementation(libs.javax.annotation.api)

    testImplementation(libs.pekko.actor.testkit.typed)
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:$protobufVersion"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:$grpcVersion"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                create("grpc")
            }
        }
    }
}

application {
    mainClass.set(
        project.findProperty("mainClass")?.toString() ?: "com.example.pekko.grpc.GrpcMainKt"
    )
    applicationDefaultJvmArgs = listOf(
        "-Dfile.encoding=UTF-8",
        "-Dstdout.encoding=UTF-8",
        "-Dstderr.encoding=UTF-8"
    )
}
