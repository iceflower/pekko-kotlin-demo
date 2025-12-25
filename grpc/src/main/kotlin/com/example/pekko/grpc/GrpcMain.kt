package com.example.pekko.grpc

import io.grpc.ServerBuilder
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.Behaviors

/**
 * Pekko + gRPC 데모
 *
 * gRPC 서버:
 *   SayHello       - 단순 인사 (Unary RPC)
 *   SayHelloStream - 스트리밍 인사 (Server Streaming RPC)
 */
fun main() {
    println("""
        ╔═══════════════════════════════════════════╗
        ║   Apache Pekko gRPC Demo                  ║
        ║   gRPC 서버 예제                           ║
        ╚═══════════════════════════════════════════╝
    """.trimIndent())

    val port = 50051

    // Root Behavior 생성
    val rootBehavior: Behavior<Void> = Behaviors.setup { context ->
        context.log.info("RootBehavior 시작됨")

        // GreeterActor 생성
        val greeterActor = context.spawn(GreeterActor.create(), "GreeterActor")
        context.log.info("GreeterActor 생성됨: {}", greeterActor.path())

        // gRPC 서비스 생성
        val greeterService = GreeterServiceImpl(context.system, greeterActor)

        // gRPC 서버 시작
        val server = ServerBuilder.forPort(port)
            .addService(greeterService)
            .build()
            .start()

        context.log.info("gRPC 서버 시작됨: localhost:{}", port)

        println("""

            gRPC 서버가 시작되었습니다!

            테스트 방법 (grpcurl 사용):
              # 서비스 목록 조회
              grpcurl -plaintext localhost:$port list

              # SayHello 호출
              grpcurl -plaintext -d '{"name": "Pekko"}' localhost:$port com.example.pekko.grpc.GreeterService/SayHello

              # SayHelloStream 호출
              grpcurl -plaintext -d '{"name": "Pekko"}' localhost:$port com.example.pekko.grpc.GreeterService/SayHelloStream

            종료: Ctrl+C
        """.trimIndent())

        // 시스템 종료 시 gRPC 서버도 종료
        Runtime.getRuntime().addShutdownHook(Thread {
            println("\ngRPC 서버를 종료합니다...")
            server.shutdown()
        })

        Behaviors.empty()
    }

    // ActorSystem 생성
    val system = ActorSystem.create(rootBehavior, "GrpcSystem")

    // 시스템 종료 대기
    Runtime.getRuntime().addShutdownHook(Thread {
        system.terminate()
    })
}
