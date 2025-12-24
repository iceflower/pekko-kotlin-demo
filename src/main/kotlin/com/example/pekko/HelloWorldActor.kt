package com.example.pekko

import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.javadsl.Receive

/**
 * HelloWorld Actor 예제
 *
 * Pekko의 Typed Actor를 Kotlin으로 구현한 예제입니다.
 * Actor는 메시지를 받아 처리하고, 다른 Actor에게 응답을 보낼 수 있습니다.
 */
object HelloWorldActor {

    // === 메시지 정의 ===
    // Actor가 받을 수 있는 모든 메시지는 sealed interface로 정의
    sealed interface Command : CborSerializable

    // 인사 요청 메시지
    data class Greet(
        val name: String,
        val replyTo: ActorRef<Greeted>  // 응답을 보낼 Actor 참조
    ) : Command

    // 인사 응답 메시지
    data class Greeted(
        val name: String,
        val from: ActorRef<Command>
    ) : CborSerializable

    // === Actor Behavior 생성 ===
    fun create(): Behavior<Command> {
        return Behaviors.setup { context ->
            HelloWorldBehavior(context)
        }
    }

    // === Actor 구현 ===
    private class HelloWorldBehavior(
        context: ActorContext<Command>
    ) : AbstractBehavior<Command>(context) {

        init {
            context.log.info("HelloWorld Actor 시작됨")
        }

        override fun createReceive(): Receive<Command> {
            return newReceiveBuilder()
                .onMessage(Greet::class.java, this::onGreet)
                .build()
        }

        private fun onGreet(command: Greet): Behavior<Command> {
            context.log.info("${command.name}님에게 인사합니다!")

            // 응답 메시지 전송
            command.replyTo.tell(Greeted(command.name, context.self))

            return this  // 동일한 behavior 유지
        }
    }
}
