package com.example.pekko.http

import org.apache.pekko.actor.typed.ActorSystem

/**
 * Pekko HTTP 데모
 *
 * REST API 서버:
 *   GET    /api/tasks      - 모든 Task 조회
 *   GET    /api/tasks/{id} - 특정 Task 조회
 *   POST   /api/tasks      - Task 생성
 *   PUT    /api/tasks/{id} - Task 업데이트
 *   DELETE /api/tasks/{id} - Task 삭제
 */
fun main() {
    println("""
        ╔═══════════════════════════════════════════╗
        ║   Apache Pekko HTTP Demo                  ║
        ║   REST API 서버 예제                       ║
        ╚═══════════════════════════════════════════╝
    """.trimIndent())

    val host = "localhost"
    val port = 8080

    // ActorSystem 생성 - RootBehavior에서 TaskRegistry와 HTTP 서버 시작
    val system = ActorSystem.create(RootBehavior.create(host, port), "HttpSystem")

    // 시스템 종료 대기
    Runtime.getRuntime().addShutdownHook(Thread {
        println("\n서버를 종료합니다...")
        system.terminate()
    })
}
