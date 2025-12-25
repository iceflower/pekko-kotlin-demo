package com.example.pekko.grpc

import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.javadsl.Receive

/**
 * Greeter Actor
 * gRPC 요청을 처리하고 인사 메시지 생성
 */
object GreeterActor {

    sealed interface Command
    data class Greet(val name: String, val replyTo: ActorRef<Greeting>) : Command
    data class GreetMultiple(val name: String, val count: Int, val replyTo: ActorRef<MultipleGreetings>) : Command

    data class Greeting(val message: String, val timestamp: Long)
    data class MultipleGreetings(val greetings: List<Greeting>)

    fun create(): Behavior<Command> = Behaviors.setup { context ->
        GreeterBehavior(context)
    }

    private class GreeterBehavior(
        context: ActorContext<Command>
    ) : AbstractBehavior<Command>(context) {

        private var greetingCount = 0L

        init {
            context.log.info("GreeterActor 시작됨")
        }

        override fun createReceive(): Receive<Command> {
            return newReceiveBuilder()
                .onMessage(Greet::class.java, this::onGreet)
                .onMessage(GreetMultiple::class.java, this::onGreetMultiple)
                .build()
        }

        private fun onGreet(cmd: Greet): Behavior<Command> {
            greetingCount++
            val message = "안녕하세요, ${cmd.name}님! (인사 #$greetingCount)"
            val greeting = Greeting(message, System.currentTimeMillis())
            context.log.info("인사 생성: {}", message)
            cmd.replyTo.tell(greeting)
            return this
        }

        private fun onGreetMultiple(cmd: GreetMultiple): Behavior<Command> {
            val greetings = (1..cmd.count).map { i ->
                greetingCount++
                val message = "[$i/${cmd.count}] 안녕하세요, ${cmd.name}님! (인사 #$greetingCount)"
                Greeting(message, System.currentTimeMillis())
            }
            context.log.info("다중 인사 생성: {} 개", greetings.size)
            cmd.replyTo.tell(MultipleGreetings(greetings))
            return this
        }
    }
}
