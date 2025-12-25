package com.example.pekko.cluster

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit

/**
 * SingletonCounter Actor 테스트 (Kotest)
 */
class SingletonCounterTest : FunSpec({

    val testKit = ActorTestKit.create()

    afterSpec {
        testKit.shutdownTestKit()
    }

    test("SingletonCounter는 초기값이 0이어야 한다") {
        val counter = testKit.spawn(SingletonCounter.create())
        val probe = testKit.createTestProbe<SingletonCounter.Value>()

        counter.tell(SingletonCounter.GetValue(probe.ref()))

        val response = probe.receiveMessage()
        response.count shouldBe 0
    }

    test("Increment는 카운터를 증가시켜야 한다") {
        val counter = testKit.spawn(SingletonCounter.create())
        val probe = testKit.createTestProbe<SingletonCounter.Value>()

        counter.tell(SingletonCounter.Increment)
        counter.tell(SingletonCounter.Increment)
        counter.tell(SingletonCounter.Increment)
        counter.tell(SingletonCounter.GetValue(probe.ref()))

        val response = probe.receiveMessage()
        response.count shouldBe 3
    }

    test("Decrement는 카운터를 감소시켜야 한다") {
        val counter = testKit.spawn(SingletonCounter.create())
        val probe = testKit.createTestProbe<SingletonCounter.Value>()

        counter.tell(SingletonCounter.Increment)
        counter.tell(SingletonCounter.Increment)
        counter.tell(SingletonCounter.Decrement)
        counter.tell(SingletonCounter.GetValue(probe.ref()))

        val response = probe.receiveMessage()
        response.count shouldBe 1
    }

    test("증가와 감소를 혼합하여 사용할 수 있다") {
        val counter = testKit.spawn(SingletonCounter.create())
        val probe = testKit.createTestProbe<SingletonCounter.Value>()

        counter.tell(SingletonCounter.Increment)
        counter.tell(SingletonCounter.Increment)
        counter.tell(SingletonCounter.Increment)
        counter.tell(SingletonCounter.Decrement)
        counter.tell(SingletonCounter.Increment)
        counter.tell(SingletonCounter.GetValue(probe.ref()))

        val response = probe.receiveMessage()
        response.count shouldBe 3
    }
})
