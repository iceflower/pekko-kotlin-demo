package com.example.pekko.sse

import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
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
    fun `subscriber receives published events`() {
        val publisher = testKit.spawn(EventPublisher.create())
        val subscriberProbe = testKit.createTestProbe<EventPublisher.Event>()

        publisher.tell(EventPublisher.Subscribe("sub1", subscriberProbe.ref()))
        publisher.tell(EventPublisher.Publish("notification", "Hello!"))

        val event = subscriberProbe.receiveMessage()
        assertTrue(event is EventPublisher.DataEvent)
        assertEquals("notification", (event as EventPublisher.DataEvent).eventType)
        assertEquals("Hello!", event.data)
    }

    @Test
    fun `multiple subscribers receive the same event`() {
        val publisher = testKit.spawn(EventPublisher.create())
        val sub1Probe = testKit.createTestProbe<EventPublisher.Event>()
        val sub2Probe = testKit.createTestProbe<EventPublisher.Event>()

        publisher.tell(EventPublisher.Subscribe("sub1", sub1Probe.ref()))
        publisher.tell(EventPublisher.Subscribe("sub2", sub2Probe.ref()))
        publisher.tell(EventPublisher.Publish("alert", "Important!"))

        val event1 = sub1Probe.receiveMessage()
        val event2 = sub2Probe.receiveMessage()

        assertTrue(event1 is EventPublisher.DataEvent)
        assertTrue(event2 is EventPublisher.DataEvent)
        assertEquals("Important!", (event1 as EventPublisher.DataEvent).data)
        assertEquals("Important!", (event2 as EventPublisher.DataEvent).data)
    }

    @Test
    fun `unsubscribed client does not receive events`() {
        val publisher = testKit.spawn(EventPublisher.create())
        val subscriberProbe = testKit.createTestProbe<EventPublisher.Event>()

        publisher.tell(EventPublisher.Subscribe("sub1", subscriberProbe.ref()))
        publisher.tell(EventPublisher.Unsubscribe("sub1"))
        publisher.tell(EventPublisher.Publish("notification", "Should not receive"))

        subscriberProbe.expectNoMessage(java.time.Duration.ofMillis(100))
    }

    @Test
    fun `stats returns correct subscriber count`() {
        val publisher = testKit.spawn(EventPublisher.create())
        val subscriberProbe = testKit.createTestProbe<EventPublisher.Event>()
        val statsProbe = testKit.createTestProbe<EventPublisher.Stats>()

        publisher.tell(EventPublisher.Subscribe("sub1", subscriberProbe.ref()))
        publisher.tell(EventPublisher.Subscribe("sub2", subscriberProbe.ref()))
        publisher.tell(EventPublisher.GetStats(statsProbe.ref()))

        val stats = statsProbe.receiveMessage()
        assertEquals(2, stats.subscriberCount)
    }
}
