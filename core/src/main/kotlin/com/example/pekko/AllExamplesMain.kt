package com.example.pekko

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.Props
import org.apache.pekko.actor.typed.javadsl.Behaviors

/**
 * 모든 예제를 실행하는 메인 함수
 */
fun main() {
    println("""
        ╔═══════════════════════════════════════════════════════╗
        ║   Apache Pekko + Kotlin - 전체 예제 실행               ║
        ╚═══════════════════════════════════════════════════════╝
    """.trimIndent())

    // 빈 Guardian Actor로 시스템 생성
    val system = ActorSystem.create(
        Behaviors.empty<Void>(),
        "pekko-examples"
    )

    try {
        // 1. Actor 기본 예제
        runActorExample(system)

        // 2. Ask Pattern 예제
        AskPatternExample.runDemo(system)

        // 3. Streams 예제
        val streamResult = StreamExample.runBasicStream(system)
        streamResult.toCompletableFuture().join()

        val asyncResult = StreamExample.runAsyncStream(system)
        asyncResult.toCompletableFuture().join()

        println("\n✓ 모든 예제 실행 완료!")

    } finally {
        Thread.sleep(1000)
        println("\n시스템을 종료합니다...")
        system.terminate()
    }
}

private fun runActorExample(system: ActorSystem<*>) {
    println("\n=== Actor 기본 예제 ===")

    // HelloWorld Actor 스폰
    val helloWorld = system.systemActorOf(
        HelloWorldActor.create(),
        "hello-world-example",
        Props.empty()
    )

    // Bot Actor 스폰
    val bot = system.systemActorOf(
        HelloWorldBot.create(2),
        "bot-example",
        Props.empty()
    )

    // 인사 시작
    helloWorld.tell(HelloWorldActor.Greet("Pekko 학습자", bot))

    Thread.sleep(500)
}
