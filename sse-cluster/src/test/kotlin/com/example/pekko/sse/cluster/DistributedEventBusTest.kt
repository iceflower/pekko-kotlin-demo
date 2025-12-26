package com.example.pekko.sse.cluster

import com.typesafe.config.ConfigFactory
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit
import org.apache.pekko.actor.typed.pubsub.Topic
import org.apache.pekko.cluster.MemberStatus
import org.apache.pekko.cluster.typed.Cluster
import org.apache.pekko.cluster.typed.Join

/**
 * DistributedEventBus 테스트 (Kotest BDD Style)
 */
class DistributedEventBusTest : DescribeSpec({

    val config = ConfigFactory.load()
    val testKit = ActorTestKit.create(config)

    beforeSpec {
        // 클러스터에 자기 자신을 조인
        val cluster = Cluster.get(testKit.system())
        cluster.manager().tell(Join.create(cluster.selfMember().address()))

        // 클러스터가 Up 상태가 될 때까지 대기
        val deadline = System.currentTimeMillis() + 10000
        while (cluster.selfMember().status() != MemberStatus.up() &&
            System.currentTimeMillis() < deadline) {
            Thread.sleep(100)
        }

        if (cluster.selfMember().status() != MemberStatus.up()) {
            throw IllegalStateException("클러스터 조인 실패")
        }
    }

    afterSpec {
        testKit.shutdownTestKit()
    }

    describe("DistributedEventBus") {
        context("Subscribe 메시지를 받으면") {
            it("구독자가 연결 이벤트를 받아야 한다") {
                val topic = testKit.spawn(
                    Topic.create(DistributedEventBus.ClusterEvent::class.java, "test-topic-1")
                )
                val eventBus = testKit.spawn(DistributedEventBus.create(topic))
                val subscriberProbe = testKit.createTestProbe<DistributedEventBus.SseEvent>()

                eventBus.tell(DistributedEventBus.Subscribe("sub-1", subscriberProbe.ref()))

                val event = subscriberProbe.receiveMessage()
                event.eventType shouldBe "connected"
                event.data.contains("sub-1") shouldBe true
            }
        }

        context("PublishEvent 메시지를 받으면") {
            it("모든 구독자에게 브로드캐스트해야 한다") {
                val topic = testKit.spawn(
                    Topic.create(DistributedEventBus.ClusterEvent::class.java, "test-topic-2")
                )
                val eventBus = testKit.spawn(DistributedEventBus.create(topic))
                val subscriber1 = testKit.createTestProbe<DistributedEventBus.SseEvent>()
                val subscriber2 = testKit.createTestProbe<DistributedEventBus.SseEvent>()

                eventBus.tell(DistributedEventBus.Subscribe("sub-1", subscriber1.ref()))
                eventBus.tell(DistributedEventBus.Subscribe("sub-2", subscriber2.ref()))

                // 연결 이벤트 비우기
                subscriber1.receiveMessage()
                subscriber2.receiveMessage()

                // 이벤트 발행
                eventBus.tell(DistributedEventBus.PublishEvent("notification", """{"msg":"hello"}"""))

                // 둘 다 이벤트를 받아야 함
                val event1 = subscriber1.receiveMessage()
                val event2 = subscriber2.receiveMessage()

                event1.eventType shouldBe "notification"
                event2.eventType shouldBe "notification"
                event1.data.contains("hello") shouldBe true
            }
        }

        context("GetSubscribers 메시지를 받으면") {
            it("구독자 목록을 반환해야 한다") {
                val topic = testKit.spawn(
                    Topic.create(DistributedEventBus.ClusterEvent::class.java, "test-topic-3")
                )
                val eventBus = testKit.spawn(DistributedEventBus.create(topic))
                val subscriber1 = testKit.createTestProbe<DistributedEventBus.SseEvent>()
                val subscriber2 = testKit.createTestProbe<DistributedEventBus.SseEvent>()
                val listProbe = testKit.createTestProbe<DistributedEventBus.SubscriberList>()

                eventBus.tell(DistributedEventBus.Subscribe("sub-1", subscriber1.ref()))
                eventBus.tell(DistributedEventBus.Subscribe("sub-2", subscriber2.ref()))
                eventBus.tell(DistributedEventBus.GetSubscribers(listProbe.ref()))

                val subscriberList = listProbe.receiveMessage()
                subscriberList.subscribers.size shouldBe 2
                subscriberList.subscribers.any { it.id == "sub-1" } shouldBe true
                subscriberList.subscribers.any { it.id == "sub-2" } shouldBe true
            }
        }

        context("Unsubscribe 메시지를 받으면") {
            it("구독자 목록에서 제거되어야 한다") {
                val topic = testKit.spawn(
                    Topic.create(DistributedEventBus.ClusterEvent::class.java, "test-topic-4")
                )
                val eventBus = testKit.spawn(DistributedEventBus.create(topic))
                val subscriber = testKit.createTestProbe<DistributedEventBus.SseEvent>()
                val listProbe = testKit.createTestProbe<DistributedEventBus.SubscriberList>()

                eventBus.tell(DistributedEventBus.Subscribe("sub-1", subscriber.ref()))
                eventBus.tell(DistributedEventBus.Unsubscribe("sub-1"))
                eventBus.tell(DistributedEventBus.GetSubscribers(listProbe.ref()))

                val subscriberList = listProbe.receiveMessage()
                subscriberList.subscribers.size shouldBe 0
            }
        }

        context("GetClusterStats 메시지를 받으면") {
            it("클러스터 통계를 반환해야 한다") {
                val topic = testKit.spawn(
                    Topic.create(DistributedEventBus.ClusterEvent::class.java, "test-topic-5")
                )
                val eventBus = testKit.spawn(DistributedEventBus.create(topic))
                val subscriber = testKit.createTestProbe<DistributedEventBus.SseEvent>()
                val statsProbe = testKit.createTestProbe<DistributedEventBus.ClusterStats>()

                eventBus.tell(DistributedEventBus.Subscribe("sub-1", subscriber.ref()))
                eventBus.tell(DistributedEventBus.GetClusterStats(statsProbe.ref()))

                val stats = statsProbe.receiveMessage()
                stats.localSubscribers shouldBe 1
                stats.nodeAddress.isNotEmpty() shouldBe true
            }
        }
    }
})
