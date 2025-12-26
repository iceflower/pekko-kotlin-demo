package com.example.pekko.sse

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit
import java.time.Duration

/**
 * EventPublisher 테스트 (Kotest BDD Style)
 */
class EventPublisherTest : DescribeSpec({

    val testKit = ActorTestKit.create()

    afterSpec {
        testKit.shutdownTestKit()
    }

    describe("EventPublisher") {
        context("Subscribe 후 Publish 메시지를 받으면") {
            it("구독자에게 이벤트를 전송해야 한다") {
                val publisher = testKit.spawn(EventPublisher.create())
                val subscriberProbe = testKit.createTestProbe<EventPublisher.Event>()

                publisher.tell(EventPublisher.Subscribe("sub1", subscriberProbe.ref()))
                publisher.tell(EventPublisher.Publish("notification", "Hello!"))

                val event = subscriberProbe.receiveMessage()
                event.shouldBeInstanceOf<EventPublisher.DataEvent>()
                (event as EventPublisher.DataEvent).eventType shouldBe "notification"
                event.data shouldBe "Hello!"
            }

            it("모든 구독자에게 동일한 이벤트를 전송해야 한다") {
                val publisher = testKit.spawn(EventPublisher.create())
                val sub1Probe = testKit.createTestProbe<EventPublisher.Event>()
                val sub2Probe = testKit.createTestProbe<EventPublisher.Event>()

                publisher.tell(EventPublisher.Subscribe("sub1", sub1Probe.ref()))
                publisher.tell(EventPublisher.Subscribe("sub2", sub2Probe.ref()))
                publisher.tell(EventPublisher.Publish("alert", "Important!"))

                val event1 = sub1Probe.receiveMessage()
                val event2 = sub2Probe.receiveMessage()

                event1.shouldBeInstanceOf<EventPublisher.DataEvent>()
                event2.shouldBeInstanceOf<EventPublisher.DataEvent>()
                (event1 as EventPublisher.DataEvent).data shouldBe "Important!"
                (event2 as EventPublisher.DataEvent).data shouldBe "Important!"
            }
        }

        context("Unsubscribe 메시지를 받으면") {
            it("구독 취소된 클라이언트는 이벤트를 받지 않아야 한다") {
                val publisher = testKit.spawn(EventPublisher.create())
                val subscriberProbe = testKit.createTestProbe<EventPublisher.Event>()

                publisher.tell(EventPublisher.Subscribe("sub1", subscriberProbe.ref()))
                publisher.tell(EventPublisher.Unsubscribe("sub1"))
                publisher.tell(EventPublisher.Publish("notification", "Should not receive"))

                subscriberProbe.expectNoMessage(Duration.ofMillis(100))
            }
        }

        context("GetStats 메시지를 받으면") {
            it("올바른 구독자 수를 반환해야 한다") {
                val publisher = testKit.spawn(EventPublisher.create())
                val subscriberProbe = testKit.createTestProbe<EventPublisher.Event>()
                val statsProbe = testKit.createTestProbe<EventPublisher.Stats>()

                publisher.tell(EventPublisher.Subscribe("sub1", subscriberProbe.ref()))
                publisher.tell(EventPublisher.Subscribe("sub2", subscriberProbe.ref()))
                publisher.tell(EventPublisher.GetStats(statsProbe.ref()))

                val stats = statsProbe.receiveMessage()
                stats.subscriberCount shouldBe 2
            }
        }
    }
})
