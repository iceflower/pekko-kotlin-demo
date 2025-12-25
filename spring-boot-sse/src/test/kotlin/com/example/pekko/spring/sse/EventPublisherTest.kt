package com.example.pekko.spring.sse

import com.example.pekko.spring.sse.actor.EventPublisher
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EventPublisherTest {

    companion object {
        private val testKit = ActorTestKit.create()

        @AfterAll
        @JvmStatic
        fun cleanup() {
            testKit.shutdownTestKit()
        }
    }

    @Test
    fun `EventPublisher should send welcome event on subscribe`() {
        val publisher = testKit.spawn(EventPublisher.create())
        val receivedEvents = CopyOnWriteArrayList<EventPublisher.Event>()

        publisher.tell(EventPublisher.Subscribe("sub1") { event ->
            receivedEvents.add(event)
        })

        Thread.sleep(100)

        assertTrue(receivedEvents.any {
            it.type == "connected" && it.data.contains("sub1")
        })
    }

    @Test
    fun `EventPublisher should broadcast events to all subscribers`() {
        val publisher = testKit.spawn(EventPublisher.create())
        val sub1Events = CopyOnWriteArrayList<EventPublisher.Event>()
        val sub2Events = CopyOnWriteArrayList<EventPublisher.Event>()

        publisher.tell(EventPublisher.Subscribe("sub1") { event ->
            sub1Events.add(event)
        })
        publisher.tell(EventPublisher.Subscribe("sub2") { event ->
            sub2Events.add(event)
        })

        Thread.sleep(100)

        publisher.tell(EventPublisher.Publish("notification", "Hello World"))

        Thread.sleep(100)

        assertTrue(sub1Events.any {
            it.type == "notification" && it.data == "Hello World"
        })
        assertTrue(sub2Events.any {
            it.type == "notification" && it.data == "Hello World"
        })
    }

    @Test
    fun `EventPublisher should not send events after unsubscribe`() {
        val publisher = testKit.spawn(EventPublisher.create())
        val events = CopyOnWriteArrayList<EventPublisher.Event>()

        publisher.tell(EventPublisher.Subscribe("sub1") { event ->
            events.add(event)
        })

        Thread.sleep(100)
        val countAfterSubscribe = events.size

        publisher.tell(EventPublisher.Unsubscribe("sub1"))
        Thread.sleep(100)

        publisher.tell(EventPublisher.Publish("notification", "After unsubscribe"))
        Thread.sleep(100)

        // Should not receive the event after unsubscribe
        assertEquals(countAfterSubscribe, events.size)
    }

    @Test
    fun `EventPublisher should return correct stats`() {
        val publisher = testKit.spawn(EventPublisher.create())
        val statsProbe = testKit.createTestProbe<EventPublisher.Stats>()

        publisher.tell(EventPublisher.Subscribe("sub1") {})
        publisher.tell(EventPublisher.Subscribe("sub2") {})
        publisher.tell(EventPublisher.Publish("test", "event1"))
        publisher.tell(EventPublisher.Publish("test", "event2"))

        Thread.sleep(100)

        publisher.tell(EventPublisher.GetStats(statsProbe.ref()))

        val stats = statsProbe.receiveMessage()
        assertEquals(2, stats.subscriberCount)
        assertEquals(2, stats.totalEventsPublished)
    }

    @Test
    fun `EventPublisher should support different event types`() {
        val publisher = testKit.spawn(EventPublisher.create())
        val events = CopyOnWriteArrayList<EventPublisher.Event>()

        publisher.tell(EventPublisher.Subscribe("sub1") { event ->
            events.add(event)
        })

        Thread.sleep(100)

        publisher.tell(EventPublisher.Publish("notification", "info message"))
        publisher.tell(EventPublisher.Publish("alert", "warning message"))
        publisher.tell(EventPublisher.Publish("update", "data changed"))

        Thread.sleep(100)

        assertTrue(events.any { it.type == "notification" })
        assertTrue(events.any { it.type == "alert" })
        assertTrue(events.any { it.type == "update" })
    }
}
