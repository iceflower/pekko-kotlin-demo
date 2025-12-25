package com.example.pekko.spring.cluster.actor

import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.javadsl.Receive

/**
 * Cluster Singleton Counter Actor.
 * Only one instance of this actor exists across the entire cluster.
 * If the node hosting the singleton goes down, it's automatically
 * restarted on another node.
 */
class SingletonCounter private constructor(
    context: ActorContext<SingletonCounter.Command>,
    private var count: Long = 0
) : AbstractBehavior<SingletonCounter.Command>(context) {

    sealed interface Command : CborSerializable
    data class Increment(val delta: Long = 1) : Command
    data class Decrement(val delta: Long = 1) : Command
    data class GetCount(val replyTo: ActorRef<CountResponse>) : Command
    data object Reset : Command

    data class CountResponse(val count: Long) : CborSerializable

    companion object {
        const val SINGLETON_NAME = "SingletonCounter"
        const val SERVICE_KEY = "singleton-counter"

        fun create(): Behavior<Command> = Behaviors.setup { context ->
            context.log.info("SingletonCounter started on {}", context.system.address())
            SingletonCounter(context)
        }
    }

    override fun createReceive(): Receive<Command> = newReceiveBuilder()
        .onMessage(Increment::class.java, this::onIncrement)
        .onMessage(Decrement::class.java, this::onDecrement)
        .onMessage(GetCount::class.java, this::onGetCount)
        .onMessage(Reset::class.java, this::onReset)
        .build()

    private fun onIncrement(cmd: Increment): Behavior<Command> {
        count += cmd.delta
        context.log.info("Counter incremented by {}. Current value: {}", cmd.delta, count)
        return this
    }

    private fun onDecrement(cmd: Decrement): Behavior<Command> {
        count -= cmd.delta
        context.log.info("Counter decremented by {}. Current value: {}", cmd.delta, count)
        return this
    }

    private fun onGetCount(cmd: GetCount): Behavior<Command> {
        cmd.replyTo.tell(CountResponse(count))
        return this
    }

    private fun onReset(cmd: Reset): Behavior<Command> {
        context.log.info("Counter reset from {} to 0", count)
        count = 0
        return this
    }
}
