package com.example.pekko.spring.sse

import com.example.pekko.spring.sse.actor.EventPublisher
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit
import java.util.concurrent.CopyOnWriteArrayList

/**
 * EventPublisher 테스트 (Kotest BDD Style)
 */
class EventPublisherTest : DescribeSpec({

    val testKit = ActorTestKit.create()

    afterSpec {
        testKit.shutdownTestKit()
    }

    describe("EventPublisher") {
        context("Subscribe 메시지를 받으면") {
            it("연결 이벤트를 전송해야 한다") {
                val publisher = testKit.spawn(EventPublisher.create())
                val receivedEvents = CopyOnWriteArrayList<EventPublisher.Event>()

                publisher.tell(EventPublisher.Subscribe("sub1") { event ->
                    receivedEvents.add(event)
                })

                Thread.sleep(100)

                receivedEvents.any {
                    it.type == "connected" && it.data.contains("sub1")
                } shouldBe true
            }
        }

        context("Publish 메시지를 받으면") {
            it("모든 구독자에게 이벤트를 브로드캐스트해야 한다") {
                val publisher = testKit.spawn(EventPublisher.create())
                val sub1Events = CopyOnWriteArrayList<EventPublisher.Event>()
                val sub2Events = CopyOnWriteArrayList<EventPublisher.Event>()

                publisher.tell(EventPublisher.Subscribe("sub1") { event ->
                    sub1Events.add(event)
                })
                publisher.tell(EventPublisher.Subscribe("sub2") { event ->
                    sub2Events.add(event)
                })

                Thread.sleep(100)

                publisher.tell(EventPublisher.Publish("notification", "Hello World"))

                Thread.sleep(100)

                sub1Events.any {
                    it.type == "notification" && it.data == "Hello World"
                } shouldBe true
                sub2Events.any {
                    it.type == "notification" && it.data == "Hello World"
                } shouldBe true
            }

            it("다양한 이벤트 타입을 지원해야 한다") {
                val publisher = testKit.spawn(EventPublisher.create())
                val events = CopyOnWriteArrayList<EventPublisher.Event>()

                publisher.tell(EventPublisher.Subscribe("sub1") { event ->
                    events.add(event)
                })

                Thread.sleep(100)

                publisher.tell(EventPublisher.Publish("notification", "info message"))
                publisher.tell(EventPublisher.Publish("alert", "warning message"))
                publisher.tell(EventPublisher.Publish("update", "data changed"))

                Thread.sleep(100)

                events.any { it.type == "notification" } shouldBe true
                events.any { it.type == "alert" } shouldBe true
                events.any { it.type == "update" } shouldBe true
            }
        }

        context("Unsubscribe 메시지를 받으면") {
            it("구독 취소 후에는 이벤트를 받지 않아야 한다") {
                val publisher = testKit.spawn(EventPublisher.create())
                val events = CopyOnWriteArrayList<EventPublisher.Event>()

                publisher.tell(EventPublisher.Subscribe("sub1") { event ->
                    events.add(event)
                })

                Thread.sleep(100)
                val countAfterSubscribe = events.size

                publisher.tell(EventPublisher.Unsubscribe("sub1"))
                Thread.sleep(100)

                publisher.tell(EventPublisher.Publish("notification", "After unsubscribe"))
                Thread.sleep(100)

                events.size shouldBe countAfterSubscribe
            }
        }

        context("GetStats 메시지를 받으면") {
            it("올바른 통계를 반환해야 한다") {
                val publisher = testKit.spawn(EventPublisher.create())
                val statsProbe = testKit.createTestProbe<EventPublisher.Stats>()

                publisher.tell(EventPublisher.Subscribe("sub1") {})
                publisher.tell(EventPublisher.Subscribe("sub2") {})
                publisher.tell(EventPublisher.Publish("test", "event1"))
                publisher.tell(EventPublisher.Publish("test", "event2"))

                Thread.sleep(100)

                publisher.tell(EventPublisher.GetStats(statsProbe.ref()))

                val stats = statsProbe.receiveMessage()
                stats.subscriberCount shouldBe 2
                stats.totalEventsPublished shouldBe 2
            }
        }
    }
})
