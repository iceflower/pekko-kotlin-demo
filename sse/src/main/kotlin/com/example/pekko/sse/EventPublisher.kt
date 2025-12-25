package com.example.pekko.sse

import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.javadsl.Receive
import org.apache.pekko.actor.typed.javadsl.TimerScheduler
import java.time.Duration
import java.util.UUID

/**
 * EventPublisher actor that manages SSE subscribers and publishes events.
 * Demonstrates actor-based event streaming with SSE.
 */
class EventPublisher private constructor(
    context: ActorContext<Command>,
    private val timers: TimerScheduler<Command>
) : AbstractBehavior<EventPublisher.Command>(context) {

    companion object {
        private val HEARTBEAT_KEY = "heartbeat"

        fun create(): Behavior<Command> = Behaviors.setup { context ->
            Behaviors.withTimers { timers ->
                // Send heartbeat every 30 seconds to keep connections alive
                timers.startTimerWithFixedDelay(HEARTBEAT_KEY, Heartbeat, Duration.ofSeconds(30))
                context.log.info("EventPublisher created")
                EventPublisher(context, timers)
            }
        }
    }

    sealed interface Command

    // Subscription management
    data class Subscribe(val subscriberId: String, val subscriber: ActorRef<Event>) : Command
    data class Unsubscribe(val subscriberId: String) : Command

    // Event publishing
    data class Publish(val eventType: String, val data: String) : Command
    data object Heartbeat : Command

    // Get stats
    data class GetStats(val replyTo: ActorRef<Stats>) : Command

    // Events sent to subscribers
    sealed interface Event {
        val id: String
        val timestamp: Long
    }

    data class DataEvent(
        override val id: String = UUID.randomUUID().toString(),
        val eventType: String,
        val data: String,
        override val timestamp: Long = System.currentTimeMillis()
    ) : Event

    data class HeartbeatEvent(
        override val id: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis()
    ) : Event

    data class Stats(
        val subscriberCount: Int,
        val totalEventsPublished: Long
    )

    private val subscribers = mutableMapOf<String, ActorRef<Event>>()
    private var totalEventsPublished: Long = 0

    override fun createReceive(): Receive<Command> = newReceiveBuilder()
        .onMessage(Subscribe::class.java, this::onSubscribe)
        .onMessage(Unsubscribe::class.java, this::onUnsubscribe)
        .onMessage(Publish::class.java, this::onPublish)
        .onMessage(Heartbeat::class.java) { onHeartbeat() }
        .onMessage(GetStats::class.java, this::onGetStats)
        .build()

    private fun onSubscribe(cmd: Subscribe): Behavior<Command> {
        subscribers[cmd.subscriberId] = cmd.subscriber
        context.log.info("Subscriber '{}' added. Total subscribers: {}", cmd.subscriberId, subscribers.size)
        return this
    }

    private fun onUnsubscribe(cmd: Unsubscribe): Behavior<Command> {
        subscribers.remove(cmd.subscriberId)
        context.log.info("Subscriber '{}' removed. Total subscribers: {}", cmd.subscriberId, subscribers.size)
        return this
    }

    private fun onPublish(cmd: Publish): Behavior<Command> {
        val event = DataEvent(eventType = cmd.eventType, data = cmd.data)
        totalEventsPublished++

        context.log.debug("Publishing event '{}' to {} subscribers", cmd.eventType, subscribers.size)
        subscribers.values.forEach { it.tell(event) }

        return this
    }

    private fun onHeartbeat(): Behavior<Command> {
        if (subscribers.isNotEmpty()) {
            val heartbeat = HeartbeatEvent()
            context.log.debug("Sending heartbeat to {} subscribers", subscribers.size)
            subscribers.values.forEach { it.tell(heartbeat) }
        }
        return this
    }

    private fun onGetStats(cmd: GetStats): Behavior<Command> {
        cmd.replyTo.tell(Stats(subscribers.size, totalEventsPublished))
        return this
    }
}
