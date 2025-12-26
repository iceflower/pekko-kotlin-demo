package com.example.pekko.spring.sse.cluster

import com.typesafe.config.ConfigFactory
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit
import org.apache.pekko.actor.typed.pubsub.Topic
import org.apache.pekko.cluster.MemberStatus
import org.apache.pekko.cluster.typed.Cluster
import org.apache.pekko.cluster.typed.Join
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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

                val receivedEvents = CopyOnWriteArrayList<DistributedEventBus.SseEvent>()
                val latch = CountDownLatch(1)
                val callback: (DistributedEventBus.SseEvent) -> Unit = { event ->
                    receivedEvents.add(event)
                    latch.countDown()
                }

                eventBus.tell(DistributedEventBus.Subscribe("sub-1", callback))

                // 이벤트 수신 대기
                latch.await(5, TimeUnit.SECONDS) shouldBe true

                receivedEvents.size shouldBe 1
                receivedEvents[0].eventType shouldBe "connected"
                receivedEvents[0].data.contains("sub-1") shouldBe true
            }
        }

        context("GetSubscribers 메시지를 받으면") {
            it("구독자 목록을 반환해야 한다") {
                val topic = testKit.spawn(
                    Topic.create(DistributedEventBus.ClusterEvent::class.java, "test-topic-2")
                )
                val eventBus = testKit.spawn(DistributedEventBus.create(topic))
                val listProbe = testKit.createTestProbe<DistributedEventBus.SubscriberList>()

                // 구독 완료를 확인하기 위한 latch
                val latch = CountDownLatch(2)
                eventBus.tell(DistributedEventBus.Subscribe("sub-1") { latch.countDown() })
                eventBus.tell(DistributedEventBus.Subscribe("sub-2") { latch.countDown() })

                // 구독 처리 및 연결 이벤트 수신 대기
                latch.await(5, TimeUnit.SECONDS) shouldBe true

                eventBus.tell(DistributedEventBus.GetSubscribers(listProbe.ref()))

                val subscriberList = listProbe.receiveMessage(Duration.ofSeconds(5))
                subscriberList.subscribers.size shouldBe 2
                subscriberList.subscribers.any { it.id == "sub-1" } shouldBe true
                subscriberList.subscribers.any { it.id == "sub-2" } shouldBe true
            }
        }

        context("Unsubscribe 메시지를 받으면") {
            it("구독자 목록에서 제거되어야 한다") {
                val topic = testKit.spawn(
                    Topic.create(DistributedEventBus.ClusterEvent::class.java, "test-topic-3")
                )
                val eventBus = testKit.spawn(DistributedEventBus.create(topic))
                val listProbe = testKit.createTestProbe<DistributedEventBus.SubscriberList>()

                // 구독 완료를 확인하기 위한 latch
                val latch = CountDownLatch(1)
                eventBus.tell(DistributedEventBus.Subscribe("sub-1") { latch.countDown() })

                // 구독 처리 대기
                latch.await(5, TimeUnit.SECONDS) shouldBe true

                eventBus.tell(DistributedEventBus.Unsubscribe("sub-1"))

                // Unsubscribe 메시지가 처리되도록 잠시 대기 후 조회
                Thread.sleep(200)

                eventBus.tell(DistributedEventBus.GetSubscribers(listProbe.ref()))

                val subscriberList = listProbe.receiveMessage(Duration.ofSeconds(5))
                subscriberList.subscribers.size shouldBe 0
            }
        }

        context("GetClusterStats 메시지를 받으면") {
            it("클러스터 통계를 반환해야 한다") {
                val topic = testKit.spawn(
                    Topic.create(DistributedEventBus.ClusterEvent::class.java, "test-topic-4")
                )
                val eventBus = testKit.spawn(DistributedEventBus.create(topic))
                val statsProbe = testKit.createTestProbe<DistributedEventBus.ClusterStats>()

                // 구독 완료를 확인하기 위한 latch
                val latch = CountDownLatch(1)
                eventBus.tell(DistributedEventBus.Subscribe("sub-1") { latch.countDown() })

                // 구독 처리 대기
                latch.await(5, TimeUnit.SECONDS) shouldBe true

                eventBus.tell(DistributedEventBus.GetClusterStats(statsProbe.ref()))

                val stats = statsProbe.receiveMessage(Duration.ofSeconds(5))
                stats.localSubscribers shouldBe 1
                stats.nodeAddress.isNotEmpty() shouldBe true
            }
        }
    }
})
