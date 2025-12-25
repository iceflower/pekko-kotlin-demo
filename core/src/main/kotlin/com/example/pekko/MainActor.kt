package com.example.pekko

import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.javadsl.Receive

/**
 * Main (Guardian) Actor
 *
 * 애플리케이션의 최상위 Actor입니다.
 * 다른 Actor들을 생성하고 관리합니다.
 */
object MainActor {

    sealed interface Command : CborSerializable

    // 시작 명령
    data class Start(val name: String) : Command

    fun create(): Behavior<Command> {
        return Behaviors.setup { context ->
            MainBehavior(context)
        }
    }

    private class MainBehavior(
        context: ActorContext<Command>
    ) : AbstractBehavior<Command>(context) {

        override fun createReceive(): Receive<Command> {
            return newReceiveBuilder()
                .onMessage(Start::class.java, this::onStart)
                .build()
        }

        private fun onStart(command: Start): Behavior<Command> {
            // HelloWorld Actor 생성
            val helloWorld: ActorRef<HelloWorldActor.Command> =
                context.spawn(HelloWorldActor.create(), "hello-world")

            // Bot Actor 생성 (3번 인사 후 종료)
            val bot: ActorRef<HelloWorldActor.Greeted> =
                context.spawn(HelloWorldBot.create(3), "hello-world-bot")

            // 첫 인사 시작
            helloWorld.tell(HelloWorldActor.Greet(command.name, bot))

            return this
        }
    }
}
