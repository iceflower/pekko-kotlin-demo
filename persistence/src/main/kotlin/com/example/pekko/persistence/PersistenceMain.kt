package com.example.pekko.persistence

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.AskPattern
import org.apache.pekko.actor.typed.javadsl.Behaviors
import java.time.Duration

/**
 * Pekko Persistence 데모
 *
 * 이벤트 소싱(Event Sourcing) 패턴:
 * - 상태 변경을 이벤트로 저장
 * - 이벤트를 리플레이하여 상태 복원
 * - 애플리케이션 재시작 후에도 상태 유지
 */
fun main() {
    println("""
        ╔═══════════════════════════════════════════╗
        ║   Apache Pekko Persistence Demo           ║
        ║   이벤트 소싱 예제                         ║
        ╚═══════════════════════════════════════════╝
    """.trimIndent())

    val system = ActorSystem.create(RootBehavior.create(), "PersistenceSystem")

    // 시스템 종료 대기
    Runtime.getRuntime().addShutdownHook(Thread {
        println("\n시스템을 종료합니다...")
        system.terminate()
    })

    // 데모가 완료될 때까지 대기
    Thread.sleep(10000)
    system.terminate()
}

object RootBehavior {

    sealed interface Command
    data object RunDemo : Command

    fun create(): Behavior<Command> = Behaviors.setup { context ->
        context.log.info("PersistenceSystem 시작됨")

        // 영구 카운터 생성
        val counter = context.spawn(PersistentCounter.create("counter-1"), "counter")

        context.log.info("=== 카운터 조작 시작 ===")

        // 카운터 증가
        counter.tell(PersistentCounter.Increment)
        counter.tell(PersistentCounter.Increment)
        counter.tell(PersistentCounter.Increment)

        // 카운터 감소
        counter.tell(PersistentCounter.Decrement)

        // 잠시 대기 후 값 조회
        Thread.sleep(1000)

        // Ask 패턴으로 현재 값 조회
        val future = AskPattern.ask(
            counter,
            { replyTo -> PersistentCounter.GetValue(replyTo) },
            Duration.ofSeconds(3),
            context.system.scheduler()
        )

        future.whenComplete { state, error ->
            if (error != null) {
                context.log.error("값 조회 실패", error)
            } else {
                context.log.info("=== 최종 카운터 값: ${state.value} ===")
                context.log.info("(애플리케이션을 다시 실행하면 이 값이 복원됩니다)")
            }
        }

        Behaviors.empty()
    }
}
