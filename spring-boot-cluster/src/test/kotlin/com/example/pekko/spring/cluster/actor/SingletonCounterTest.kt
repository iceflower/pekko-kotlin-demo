package com.example.pekko.spring.cluster.actor

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit

/**
 * SingletonCounter 테스트 (Kotest BDD Style)
 */
class SingletonCounterTest : DescribeSpec({

    val testKit = ActorTestKit.create()

    afterSpec {
        testKit.shutdownTestKit()
    }

    describe("SingletonCounter") {
        context("Increment 메시지를 받으면") {
            it("카운트를 증가시켜야 한다") {
                val counter = testKit.spawn(SingletonCounter.create())
                val probe = testKit.createTestProbe<SingletonCounter.CountResponse>()

                counter.tell(SingletonCounter.Increment(5))
                counter.tell(SingletonCounter.GetCount(probe.ref()))

                val response = probe.receiveMessage()
                response.count shouldBe 5
            }
        }

        context("Decrement 메시지를 받으면") {
            it("카운트를 감소시켜야 한다") {
                val counter = testKit.spawn(SingletonCounter.create())
                val probe = testKit.createTestProbe<SingletonCounter.CountResponse>()

                counter.tell(SingletonCounter.Increment(10))
                counter.tell(SingletonCounter.Decrement(3))
                counter.tell(SingletonCounter.GetCount(probe.ref()))

                val response = probe.receiveMessage()
                response.count shouldBe 7
            }
        }

        context("Reset 메시지를 받으면") {
            it("카운트를 0으로 초기화해야 한다") {
                val counter = testKit.spawn(SingletonCounter.create())
                val probe = testKit.createTestProbe<SingletonCounter.CountResponse>()

                counter.tell(SingletonCounter.Increment(100))
                counter.tell(SingletonCounter.Reset)
                counter.tell(SingletonCounter.GetCount(probe.ref()))

                val response = probe.receiveMessage()
                response.count shouldBe 0
            }
        }
    }
})
