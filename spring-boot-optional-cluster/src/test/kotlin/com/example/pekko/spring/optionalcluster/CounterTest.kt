package com.example.pekko.spring.optionalcluster

import com.example.pekko.spring.optionalcluster.actor.Counter
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class CounterTest {

    companion object {
        private val testKit = ActorTestKit.create()

        @AfterAll
        @JvmStatic
        fun cleanup() {
            testKit.shutdownTestKit()
        }
    }

    @Test
    fun `Counter should increment count`() {
        val counter = testKit.spawn(Counter.create())
        val probe = testKit.createTestProbe<Counter.CountResponse>()

        counter.tell(Counter.Increment(5))
        counter.tell(Counter.GetCount(probe.ref()))

        val response = probe.receiveMessage()
        assertEquals(5, response.count)
    }

    @Test
    fun `Counter should decrement count`() {
        val counter = testKit.spawn(Counter.create())
        val probe = testKit.createTestProbe<Counter.CountResponse>()

        counter.tell(Counter.Increment(10))
        counter.tell(Counter.Decrement(3))
        counter.tell(Counter.GetCount(probe.ref()))

        val response = probe.receiveMessage()
        assertEquals(7, response.count)
    }

    @Test
    fun `Counter should reset count`() {
        val counter = testKit.spawn(Counter.create())
        val probe = testKit.createTestProbe<Counter.CountResponse>()

        counter.tell(Counter.Increment(100))
        counter.tell(Counter.Reset)
        counter.tell(Counter.GetCount(probe.ref()))

        val response = probe.receiveMessage()
        assertEquals(0, response.count)
    }
}
