package com.example.pekko.persistence

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit
import org.apache.pekko.persistence.testkit.javadsl.PersistenceTestKit
import org.apache.pekko.persistence.typed.PersistenceId

/**
 * PersistentCounter Actor 테스트 (Kotest)
 *
 * Pekko Persistence TestKit을 사용한 이벤트 소싱 테스트
 */
class PersistentCounterTest : FunSpec({

    val testKit = ActorTestKit.create()
    val persistenceTestKit = PersistenceTestKit.create(testKit.system())

    beforeTest {
        persistenceTestKit.clearAll()
    }

    afterSpec {
        testKit.shutdownTestKit()
    }

    test("PersistentCounter는 초기값이 0이어야 한다") {
        val counter = testKit.spawn(PersistentCounter.create("test-counter-1"))
        val probe = testKit.createTestProbe<PersistentCounter.State>()

        counter.tell(PersistentCounter.GetValue(probe.ref()))

        val response = probe.receiveMessage()
        response.value shouldBe 0
    }

    test("Increment는 카운터를 증가시켜야 한다") {
        val counter = testKit.spawn(PersistentCounter.create("test-counter-2"))
        val probe = testKit.createTestProbe<PersistentCounter.State>()

        counter.tell(PersistentCounter.Increment)
        counter.tell(PersistentCounter.Increment)
        counter.tell(PersistentCounter.GetValue(probe.ref()))

        val response = probe.receiveMessage()
        response.value shouldBe 2
    }

    test("Decrement는 카운터를 감소시켜야 한다") {
        val counter = testKit.spawn(PersistentCounter.create("test-counter-3"))
        val probe = testKit.createTestProbe<PersistentCounter.State>()

        counter.tell(PersistentCounter.Increment)
        counter.tell(PersistentCounter.Increment)
        counter.tell(PersistentCounter.Decrement)
        counter.tell(PersistentCounter.GetValue(probe.ref()))

        val response = probe.receiveMessage()
        response.value shouldBe 1
    }

    test("이벤트가 영속화되어야 한다") {
        val persistenceId = "test-counter-4"
        val counter = testKit.spawn(PersistentCounter.create(persistenceId))
        val probe = testKit.createTestProbe<PersistentCounter.State>()

        counter.tell(PersistentCounter.Increment)
        counter.tell(PersistentCounter.Increment)
        counter.tell(PersistentCounter.GetValue(probe.ref()))

        probe.receiveMessage()

        // 영속화된 이벤트 확인
        persistenceTestKit.expectNextPersisted(
            persistenceId,
            PersistentCounter.Incremented
        )
        persistenceTestKit.expectNextPersisted(
            persistenceId,
            PersistentCounter.Incremented
        )
    }
})
