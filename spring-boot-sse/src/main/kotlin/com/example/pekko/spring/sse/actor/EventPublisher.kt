package com.example.pekko.spring.sse.actor

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
 * EventPublisher actor manages SSE subscriptions and broadcasts events.
 * Integrates with Spring WebFlux for reactive event streaming.
 */
class EventPublisher private constructor(
    context: ActorContext<Command>,
    private val timers: TimerScheduler<Command>
) : AbstractBehavior<EventPublisher.Command>(context) {

    companion object {
        private const val HEARTBEAT_KEY = "heartbeat"
        private val HEARTBEAT_INTERVAL = Duration.ofSeconds(30)

        fun create(): Behavior<Command> = Behaviors.setup { context ->
            Behaviors.withTimers { timers ->
                context.log.info("EventPublisher created")
                timers.startTimerWithFixedDelay(HEARTBEAT_KEY, Heartbeat, HEARTBEAT_INTERVAL)
                EventPublisher(context, timers)
            }
        }
    }

    sealed interface Command

    // Subscription management
    data class Subscribe(val subscriberId: String, val callback: (Event) -> Unit) : Command
    data class Unsubscribe(val subscriberId: String) : Command

    // Event publishing
    data class Publish(val eventType: String, val data: String) : Command

    // Internal
    private data object Heartbeat : Command

    // Stats
    data class GetStats(val replyTo: ActorRef<Stats>) : Command
    data class Stats(val subscriberCount: Int, val totalEventsPublished: Long)

    // Events sent to subscribers
    data class Event(
        val id: String = UUID.randomUUID().toString(),
        val type: String,
        val data: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    private data class Subscriber(
        val id: String,
        val callback: (Event) -> Unit
    )

    private val subscribers = mutableMapOf<String, Subscriber>()
    private var totalEventsPublished: Long = 0

    override fun createReceive(): Receive<Command> = newReceiveBuilder()
        .onMessage(Subscribe::class.java, this::onSubscribe)
        .onMessage(Unsubscribe::class.java, this::onUnsubscribe)
        .onMessage(Publish::class.java, this::onPublish)
        .onMessage(Heartbeat::class.java) { onHeartbeat() }
        .onMessage(GetStats::class.java, this::onGetStats)
        .build()

    private fun onSubscribe(cmd: Subscribe): Behavior<Command> {
        subscribers[cmd.subscriberId] = Subscriber(cmd.subscriberId, cmd.callback)
        context.log.info("Subscriber added: {}. Total: {}", cmd.subscriberId, subscribers.size)

        // Send welcome event
        cmd.callback(Event(type = "connected", data = "Welcome! Subscriber ID: ${cmd.subscriberId}"))

        return this
    }

    private fun onUnsubscribe(cmd: Unsubscribe): Behavior<Command> {
        subscribers.remove(cmd.subscriberId)
        context.log.info("Subscriber removed: {}. Total: {}", cmd.subscriberId, subscribers.size)
        return this
    }

    private fun onPublish(cmd: Publish): Behavior<Command> {
        val event = Event(type = cmd.eventType, data = cmd.data)
        broadcast(event)
        totalEventsPublished++
        context.log.debug("Published event: type={}, subscribers={}", cmd.eventType, subscribers.size)
        return this
    }

    private fun onHeartbeat(): Behavior<Command> {
        if (subscribers.isNotEmpty()) {
            val event = Event(type = "heartbeat", data = "ping")
            broadcast(event)
            context.log.debug("Heartbeat sent to {} subscribers", subscribers.size)
        }
        return this
    }

    private fun onGetStats(cmd: GetStats): Behavior<Command> {
        cmd.replyTo.tell(Stats(subscribers.size, totalEventsPublished))
        return this
    }

    private fun broadcast(event: Event) {
        val failedSubscribers = mutableListOf<String>()

        subscribers.forEach { (id, subscriber) ->
            try {
                subscriber.callback(event)
            } catch (e: Exception) {
                context.log.warn("Failed to send event to subscriber {}: {}", id, e.message)
                failedSubscribers.add(id)
            }
        }

        // Remove failed subscribers
        failedSubscribers.forEach { subscribers.remove(it) }
    }
}
