package com.example.pekko.spring.optionalcluster.actor

import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.javadsl.Receive

/**
 * A counter actor that works in both standalone and cluster modes.
 * In cluster mode, it can be used as a ClusterSingleton.
 * In standalone mode, it works as a regular actor.
 */
class Counter private constructor(
    context: ActorContext<Command>
) : AbstractBehavior<Counter.Command>(context) {

    companion object {
        const val ACTOR_NAME = "counter"

        fun create(): Behavior<Command> = Behaviors.setup { context ->
            context.log.info("Counter actor created")
            Counter(context)
        }
    }

    // Commands - implement CborSerializable for cluster mode
    sealed interface Command : CborSerializable

    data class Increment(val delta: Long = 1) : Command
    data class Decrement(val delta: Long = 1) : Command
    data object Reset : Command
    data class GetCount(val replyTo: ActorRef<CountResponse>) : Command

    // Response
    data class CountResponse(val count: Long) : CborSerializable

    private var count: Long = 0

    override fun createReceive(): Receive<Command> = newReceiveBuilder()
        .onMessage(Increment::class.java, this::onIncrement)
        .onMessage(Decrement::class.java, this::onDecrement)
        .onMessage(Reset::class.java) { onReset() }
        .onMessage(GetCount::class.java, this::onGetCount)
        .build()

    private fun onIncrement(cmd: Increment): Behavior<Command> {
        count += cmd.delta
        context.log.info("Counter incremented by {}, new value: {}", cmd.delta, count)
        return this
    }

    private fun onDecrement(cmd: Decrement): Behavior<Command> {
        count -= cmd.delta
        context.log.info("Counter decremented by {}, new value: {}", cmd.delta, count)
        return this
    }

    private fun onReset(): Behavior<Command> {
        count = 0
        context.log.info("Counter reset to 0")
        return this
    }

    private fun onGetCount(cmd: GetCount): Behavior<Command> {
        cmd.replyTo.tell(CountResponse(count))
        return this
    }
}
