package com.example.pekko.persistence

import com.typesafe.config.ConfigFactory
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit

/**
 * PersistentCounter Actor 테스트 (Kotest BDD Style)
 *
 * Pekko Persistence TestKit을 사용한 이벤트 소싱 테스트
 */
class PersistentCounterTest : DescribeSpec({

    // Persistence TestKit 설정
    val config = ConfigFactory.parseString("""
        pekko.persistence.journal.plugin = "pekko.persistence.journal.inmem"
        pekko.persistence.snapshot-store.plugin = "pekko.persistence.snapshot-store.local"
        pekko.persistence.snapshot-store.local.dir = "build/snapshots"
    """).withFallback(ConfigFactory.load())

    val testKit = ActorTestKit.create(config)

    afterSpec {
        testKit.shutdownTestKit()
    }

    describe("PersistentCounter") {
        context("초기 상태에서") {
            it("카운터 값이 0이어야 한다") {
                val counter = testKit.spawn(PersistentCounter.create("test-counter-1"))
                val probe = testKit.createTestProbe<PersistentCounter.State>()

                counter.tell(PersistentCounter.GetValue(probe.ref()))

                val response = probe.receiveMessage()
                response.value shouldBe 0
            }
        }

        context("Increment 메시지를 받으면") {
            it("카운터를 증가시켜야 한다") {
                val counter = testKit.spawn(PersistentCounter.create("test-counter-2"))
                val probe = testKit.createTestProbe<PersistentCounter.State>()

                counter.tell(PersistentCounter.Increment)
                counter.tell(PersistentCounter.Increment)
                counter.tell(PersistentCounter.GetValue(probe.ref()))

                val response = probe.receiveMessage()
                response.value shouldBe 2
            }
        }

        context("Decrement 메시지를 받으면") {
            it("카운터를 감소시켜야 한다") {
                val counter = testKit.spawn(PersistentCounter.create("test-counter-3"))
                val probe = testKit.createTestProbe<PersistentCounter.State>()

                counter.tell(PersistentCounter.Increment)
                counter.tell(PersistentCounter.Increment)
                counter.tell(PersistentCounter.Decrement)
                counter.tell(PersistentCounter.GetValue(probe.ref()))

                val response = probe.receiveMessage()
                response.value shouldBe 1
            }
        }

        context("증가와 감소를 혼합하여 사용하면") {
            it("올바른 값을 반환해야 한다") {
                val counter = testKit.spawn(PersistentCounter.create("test-counter-4"))
                val probe = testKit.createTestProbe<PersistentCounter.State>()

                counter.tell(PersistentCounter.Increment)
                counter.tell(PersistentCounter.Increment)
                counter.tell(PersistentCounter.Increment)
                counter.tell(PersistentCounter.Decrement)
                counter.tell(PersistentCounter.Increment)
                counter.tell(PersistentCounter.GetValue(probe.ref()))

                val response = probe.receiveMessage()
                response.value shouldBe 3
            }
        }
    }
})
