package com.example.pekko

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit

/**
 * Counter Actor 테스트 (Kotest)
 */
class CounterActorTest : FunSpec({

    val testKit = ActorTestKit.create()

    afterSpec {
        testKit.shutdownTestKit()
    }

    test("Counter는 초기값이 0이어야 한다") {
        val counter = testKit.spawn(CounterActor.create())
        val probe = testKit.createTestProbe<CounterActor.Value>()

        counter.tell(CounterActor.GetValue(probe.ref()))

        val response = probe.receiveMessage()
        response.count shouldBe 0
    }

    test("Increment는 카운터를 증가시켜야 한다") {
        val counter = testKit.spawn(CounterActor.create())
        val probe = testKit.createTestProbe<CounterActor.Value>()

        counter.tell(CounterActor.Increment)
        counter.tell(CounterActor.Increment)
        counter.tell(CounterActor.GetValue(probe.ref()))

        val response = probe.receiveMessage()
        response.count shouldBe 2
    }

    test("Decrement는 카운터를 감소시켜야 한다") {
        val counter = testKit.spawn(CounterActor.create())
        val probe = testKit.createTestProbe<CounterActor.Value>()

        counter.tell(CounterActor.Increment)
        counter.tell(CounterActor.Increment)
        counter.tell(CounterActor.Decrement)
        counter.tell(CounterActor.GetValue(probe.ref()))

        val response = probe.receiveMessage()
        response.count shouldBe 1
    }
})
