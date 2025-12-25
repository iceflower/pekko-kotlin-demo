package com.example.pekko.quarkus.cluster.actor

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit

class SingletonCounterTest : FunSpec({

    val testKit = ActorTestKit.create()

    afterSpec {
        testKit.shutdownTestKit()
    }

    test("SingletonCounter should increment count") {
        val counter = testKit.spawn(SingletonCounter.create())
        val probe = testKit.createTestProbe<SingletonCounter.CountResponse>()

        counter.tell(SingletonCounter.Increment(5))
        counter.tell(SingletonCounter.GetCount(probe.ref()))

        val response = probe.receiveMessage()
        response.count shouldBe 5
    }

    test("SingletonCounter should decrement count") {
        val counter = testKit.spawn(SingletonCounter.create())
        val probe = testKit.createTestProbe<SingletonCounter.CountResponse>()

        counter.tell(SingletonCounter.Increment(10))
        counter.tell(SingletonCounter.Decrement(3))
        counter.tell(SingletonCounter.GetCount(probe.ref()))

        val response = probe.receiveMessage()
        response.count shouldBe 7
    }

    test("SingletonCounter should reset count") {
        val counter = testKit.spawn(SingletonCounter.create())
        val probe = testKit.createTestProbe<SingletonCounter.CountResponse>()

        counter.tell(SingletonCounter.Increment(100))
        counter.tell(SingletonCounter.Reset)
        counter.tell(SingletonCounter.GetCount(probe.ref()))

        val response = probe.receiveMessage()
        response.count shouldBe 0
    }
})
