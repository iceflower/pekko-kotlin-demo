package com.example.pekko

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit

/**
 * Counter Actor 테스트 (Kotest BDD Style)
 */
class CounterActorTest : DescribeSpec({

    val testKit = ActorTestKit.create()

    afterSpec {
        testKit.shutdownTestKit()
    }

    describe("CounterActor") {
        context("초기 상태에서") {
            it("카운터 값이 0이어야 한다") {
                val counter = testKit.spawn(CounterActor.create())
                val probe = testKit.createTestProbe<CounterActor.Value>()

                counter.tell(CounterActor.GetValue(probe.ref()))

                val response = probe.receiveMessage()
                response.count shouldBe 0
            }
        }

        context("Increment 메시지를 받으면") {
            it("카운터를 증가시켜야 한다") {
                val counter = testKit.spawn(CounterActor.create())
                val probe = testKit.createTestProbe<CounterActor.Value>()

                counter.tell(CounterActor.Increment)
                counter.tell(CounterActor.Increment)
                counter.tell(CounterActor.GetValue(probe.ref()))

                val response = probe.receiveMessage()
                response.count shouldBe 2
            }
        }

        context("Decrement 메시지를 받으면") {
            it("카운터를 감소시켜야 한다") {
                val counter = testKit.spawn(CounterActor.create())
                val probe = testKit.createTestProbe<CounterActor.Value>()

                counter.tell(CounterActor.Increment)
                counter.tell(CounterActor.Increment)
                counter.tell(CounterActor.Decrement)
                counter.tell(CounterActor.GetValue(probe.ref()))

                val response = probe.receiveMessage()
                response.count shouldBe 1
            }
        }
    }
})
