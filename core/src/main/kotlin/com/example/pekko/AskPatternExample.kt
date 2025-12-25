package com.example.pekko

import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.Props
import org.apache.pekko.actor.typed.javadsl.AskPattern
import java.time.Duration
import java.util.concurrent.CompletionStage

/**
 * Ask Pattern 예제
 *
 * Actor에게 메시지를 보내고 응답을 기다리는 패턴입니다.
 * CompletionStage(Future)를 반환하므로 비동기 프로그래밍과 잘 통합됩니다.
 */
object AskPatternExample {

    /**
     * Counter Actor에게 ask 패턴으로 값을 조회합니다.
     */
    fun askCounterValue(
        counter: ActorRef<CounterActor.Command>,
        system: ActorSystem<*>
    ): CompletionStage<CounterActor.Value> {
        return AskPattern.ask(
            counter,
            { replyTo: ActorRef<CounterActor.Value> ->
                CounterActor.GetValue(replyTo)
            },
            Duration.ofSeconds(3),  // 타임아웃
            system.scheduler()
        )
    }

    /**
     * Ask 패턴 데모
     */
    fun runDemo(system: ActorSystem<*>) {
        println("\n=== Ask Pattern 예제 ===")

        // Counter Actor 생성
        val counter = system.systemActorOf(
            CounterActor.create(),
            "counter-for-ask",
            Props.empty()
        )

        // 증가 명령 전송
        counter.tell(CounterActor.Increment)
        counter.tell(CounterActor.Increment)
        counter.tell(CounterActor.Increment)

        // Ask 패턴으로 현재 값 조회
        val valueFuture = askCounterValue(counter, system)

        valueFuture.thenAccept { result ->
            println("Ask 패턴으로 받은 카운터 값: ${result.count}")
        }

        // 결과 대기
        Thread.sleep(500)
    }
}
