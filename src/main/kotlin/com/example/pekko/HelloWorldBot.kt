package com.example.pekko

import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.javadsl.Receive

/**
 * HelloWorld Bot Actor
 *
 * HelloWorldActor에게 인사를 보내고, 응답을 받아 처리하는 Actor입니다.
 * 지정된 횟수만큼 인사를 주고받은 후 종료합니다.
 */
object HelloWorldBot {

    fun create(maxGreetings: Int): Behavior<HelloWorldActor.Greeted> {
        return Behaviors.setup { context ->
            HelloWorldBotBehavior(context, maxGreetings)
        }
    }

    private class HelloWorldBotBehavior(
        context: ActorContext<HelloWorldActor.Greeted>,
        private val maxGreetings: Int
    ) : AbstractBehavior<HelloWorldActor.Greeted>(context) {

        private var greetingCounter = 0

        override fun createReceive(): Receive<HelloWorldActor.Greeted> {
            return newReceiveBuilder()
                .onMessage(HelloWorldActor.Greeted::class.java, this::onGreeted)
                .build()
        }

        private fun onGreeted(message: HelloWorldActor.Greeted): Behavior<HelloWorldActor.Greeted> {
            greetingCounter++
            context.log.info("인사 응답 받음 #$greetingCounter: ${message.name}")

            return if (greetingCounter >= maxGreetings) {
                context.log.info("최대 인사 횟수($maxGreetings) 도달, Bot 종료")
                Behaviors.stopped()  // Actor 종료
            } else {
                // 다시 인사 요청
                message.from.tell(HelloWorldActor.Greet(message.name, context.self))
                this
            }
        }
    }
}
