package com.example.pekko

import org.apache.pekko.actor.typed.ActorSystem

/**
 * Pekko + Kotlin 애플리케이션 진입점
 */
fun main() {
    println("""
        ╔═══════════════════════════════════════════╗
        ║   Apache Pekko + Kotlin Demo              ║
        ║   Actor 기반 동시성 프레임워크 예제        ║
        ╚═══════════════════════════════════════════╝
    """.trimIndent())

    // ActorSystem 생성 (Guardian Actor로 MainActor 사용)
    val system = ActorSystem.create(MainActor.create(), "pekko-kotlin-demo")

    // 시작 명령 전송
    system.tell(MainActor.Start("Kotlin 개발자"))

    // 잠시 대기 후 시스템 종료
    Thread.sleep(3000)

    println("\n시스템을 종료합니다...")
    system.terminate()
}
