package com.example.pekko

import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * Counter Actor 테스트
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CounterActorTest {

    private val testKit = ActorTestKit.create()

    @AfterAll
    fun cleanup() {
        testKit.shutdownTestKit()
    }

    @Test
    fun `Counter는 초기값이 0이어야 한다`() {
        val counter = testKit.spawn(CounterActor.create())
        val probe = testKit.createTestProbe<CounterActor.Value>()

        counter.tell(CounterActor.GetValue(probe.ref()))

        val response = probe.receiveMessage()
        assertEquals(0, response.count)
    }

    @Test
    fun `Increment는 카운터를 증가시켜야 한다`() {
        val counter = testKit.spawn(CounterActor.create())
        val probe = testKit.createTestProbe<CounterActor.Value>()

        counter.tell(CounterActor.Increment)
        counter.tell(CounterActor.Increment)
        counter.tell(CounterActor.GetValue(probe.ref()))

        val response = probe.receiveMessage()
        assertEquals(2, response.count)
    }

    @Test
    fun `Decrement는 카운터를 감소시켜야 한다`() {
        val counter = testKit.spawn(CounterActor.create())
        val probe = testKit.createTestProbe<CounterActor.Value>()

        counter.tell(CounterActor.Increment)
        counter.tell(CounterActor.Increment)
        counter.tell(CounterActor.Decrement)
        counter.tell(CounterActor.GetValue(probe.ref()))

        val response = probe.receiveMessage()
        assertEquals(1, response.count)
    }
}
