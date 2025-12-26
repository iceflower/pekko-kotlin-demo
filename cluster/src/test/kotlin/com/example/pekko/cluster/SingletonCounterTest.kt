package com.example.pekko.cluster

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit

/**
 * SingletonCounter Actor 테스트 (Kotest BDD Style)
 */
class SingletonCounterTest : DescribeSpec({

    val testKit = ActorTestKit.create()

    afterSpec {
        testKit.shutdownTestKit()
    }

    describe("SingletonCounter") {
        context("초기 상태에서") {
            it("카운터 값이 0이어야 한다") {
                val counter = testKit.spawn(SingletonCounter.create())
                val probe = testKit.createTestProbe<SingletonCounter.Value>()

                counter.tell(SingletonCounter.GetValue(probe.ref()))

                val response = probe.receiveMessage()
                response.count shouldBe 0
            }
        }

        context("Increment 메시지를 받으면") {
            it("카운터를 증가시켜야 한다") {
                val counter = testKit.spawn(SingletonCounter.create())
                val probe = testKit.createTestProbe<SingletonCounter.Value>()

                counter.tell(SingletonCounter.Increment)
                counter.tell(SingletonCounter.Increment)
                counter.tell(SingletonCounter.Increment)
                counter.tell(SingletonCounter.GetValue(probe.ref()))

                val response = probe.receiveMessage()
                response.count shouldBe 3
            }
        }

        context("Decrement 메시지를 받으면") {
            it("카운터를 감소시켜야 한다") {
                val counter = testKit.spawn(SingletonCounter.create())
                val probe = testKit.createTestProbe<SingletonCounter.Value>()

                counter.tell(SingletonCounter.Increment)
                counter.tell(SingletonCounter.Increment)
                counter.tell(SingletonCounter.Decrement)
                counter.tell(SingletonCounter.GetValue(probe.ref()))

                val response = probe.receiveMessage()
                response.count shouldBe 1
            }
        }

        context("증가와 감소를 혼합하여 사용하면") {
            it("올바른 값을 반환해야 한다") {
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
        }
    }
})
