package com.example.pekko

import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.Behaviors

/**
 * Counter Actor 예제
 *
 * 함수형 스타일로 구현한 Actor 예제입니다.
 * Actor의 상태(count)가 변경될 때마다 새로운 Behavior를 반환합니다.
 */
object CounterActor {

    // === 메시지 정의 ===
    sealed interface Command : CborSerializable

    object Increment : Command
    object Decrement : Command
    data class GetValue(val replyTo: ActorRef<Value>) : Command

    // 응답 메시지
    data class Value(val count: Int) : CborSerializable

    // === 함수형 스타일 Actor 생성 ===
    fun create(): Behavior<Command> = counter(0)

    // 재귀적으로 새로운 Behavior를 생성하는 함수형 패턴
    private fun counter(count: Int): Behavior<Command> {
        return Behaviors.receive { context, message ->
            when (message) {
                is Increment -> {
                    val newCount = count + 1
                    context.log.info("증가: $count -> $newCount")
                    counter(newCount)  // 새로운 상태로 Behavior 반환
                }
                is Decrement -> {
                    val newCount = count - 1
                    context.log.info("감소: $count -> $newCount")
                    counter(newCount)
                }
                is GetValue -> {
                    context.log.info("현재 값 조회: $count")
                    message.replyTo.tell(Value(count))
                    Behaviors.same()  // 상태 유지
                }
            }
        }
    }
}
