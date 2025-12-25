package com.example.pekko.cluster

import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.javadsl.Receive

/**
 * 클러스터 싱글톤으로 실행되는 카운터 Actor
 * 클러스터 전체에서 단 하나의 인스턴스만 존재함
 */
object SingletonCounter {

    sealed interface Command : CborSerializable
    data object Increment : Command
    data object Decrement : Command
    data class GetValue(val replyTo: ActorRef<Value>) : Command
    data class Value(val count: Int) : CborSerializable

    fun create(): Behavior<Command> = Behaviors.setup { context ->
        SingletonCounterBehavior(context, 0)
    }

    private class SingletonCounterBehavior(
        context: ActorContext<Command>,
        private var count: Int
    ) : AbstractBehavior<Command>(context) {

        init {
            context.log.info("SingletonCounter 시작됨 (초기값: $count)")
        }

        override fun createReceive(): Receive<Command> {
            return newReceiveBuilder()
                .onMessage(Increment::class.java, this::onIncrement)
                .onMessage(Decrement::class.java, this::onDecrement)
                .onMessage(GetValue::class.java, this::onGetValue)
                .build()
        }

        private fun onIncrement(cmd: Increment): Behavior<Command> {
            count++
            context.log.info("카운터 증가: $count")
            return this
        }

        private fun onDecrement(cmd: Decrement): Behavior<Command> {
            count--
            context.log.info("카운터 감소: $count")
            return this
        }

        private fun onGetValue(cmd: GetValue): Behavior<Command> {
            context.log.info("현재 카운터 값 조회: $count")
            cmd.replyTo.tell(Value(count))
            return this
        }
    }
}
