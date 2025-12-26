package com.example.pekko.spring.optionalcluster

import com.example.pekko.spring.optionalcluster.actor.Counter
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit

/**
 * Counter 테스트 (Kotest BDD Style)
 */
class CounterTest : DescribeSpec({

    val testKit = ActorTestKit.create()

    afterSpec {
        testKit.shutdownTestKit()
    }

    describe("Counter") {
        context("Increment 메시지를 받으면") {
            it("카운트를 증가시켜야 한다") {
                val counter = testKit.spawn(Counter.create())
                val probe = testKit.createTestProbe<Counter.CountResponse>()

                counter.tell(Counter.Increment(5))
                counter.tell(Counter.GetCount(probe.ref()))

                val response = probe.receiveMessage()
                response.count shouldBe 5
            }
        }

        context("Decrement 메시지를 받으면") {
            it("카운트를 감소시켜야 한다") {
                val counter = testKit.spawn(Counter.create())
                val probe = testKit.createTestProbe<Counter.CountResponse>()

                counter.tell(Counter.Increment(10))
                counter.tell(Counter.Decrement(3))
                counter.tell(Counter.GetCount(probe.ref()))

                val response = probe.receiveMessage()
                response.count shouldBe 7
            }
        }

        context("Reset 메시지를 받으면") {
            it("카운트를 0으로 초기화해야 한다") {
                val counter = testKit.spawn(Counter.create())
                val probe = testKit.createTestProbe<Counter.CountResponse>()

                counter.tell(Counter.Increment(100))
                counter.tell(Counter.Reset)
                counter.tell(Counter.GetCount(probe.ref()))

                val response = probe.receiveMessage()
                response.count shouldBe 0
            }
        }
    }
})
