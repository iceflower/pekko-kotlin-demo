package com.example.pekko.sse.cluster

import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit
import org.apache.pekko.actor.typed.pubsub.Topic
import org.apache.pekko.cluster.MemberStatus
import org.apache.pekko.cluster.typed.Cluster
import org.apache.pekko.cluster.typed.Join
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DistributedEventBusTest {

    companion object {
        private lateinit var testKit: ActorTestKit

        @BeforeAll
        @JvmStatic
        fun setup() {
            // 클러스터 설정을 포함한 테스트 환경 생성
            val config = ConfigFactory.load()
            testKit = ActorTestKit.create(config)

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

        @AfterAll
        @JvmStatic
        fun cleanup() {
            testKit.shutdownTestKit()
        }
    }

    @Test
    fun `구독자가 연결 이벤트를 받는다`() {
        val topic = testKit.spawn(
            Topic.create(DistributedEventBus.ClusterEvent::class.java, "test-topic-1")
        )
        val eventBus = testKit.spawn(DistributedEventBus.create(topic))
        val subscriberProbe = testKit.createTestProbe<DistributedEventBus.SseEvent>()

        eventBus.tell(DistributedEventBus.Subscribe("sub-1", subscriberProbe.ref()))

        val event = subscriberProbe.receiveMessage()
        assertEquals("connected", event.eventType)
        assertTrue(event.data.contains("sub-1"))
    }

    @Test
    fun `이벤트가 모든 구독자에게 브로드캐스트된다`() {
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

        assertEquals("notification", event1.eventType)
        assertEquals("notification", event2.eventType)
        assertTrue(event1.data.contains("hello"))
    }

    @Test
    fun `구독자 목록을 조회할 수 있다`() {
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
        assertEquals(2, subscriberList.subscribers.size)
        assertTrue(subscriberList.subscribers.any { it.id == "sub-1" })
        assertTrue(subscriberList.subscribers.any { it.id == "sub-2" })
    }

    @Test
    fun `구독 취소 시 구독자 목록에서 제거된다`() {
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
        assertEquals(0, subscriberList.subscribers.size)
    }

    @Test
    fun `클러스터 통계를 조회할 수 있다`() {
        val topic = testKit.spawn(
            Topic.create(DistributedEventBus.ClusterEvent::class.java, "test-topic-5")
        )
        val eventBus = testKit.spawn(DistributedEventBus.create(topic))
        val subscriber = testKit.createTestProbe<DistributedEventBus.SseEvent>()
        val statsProbe = testKit.createTestProbe<DistributedEventBus.ClusterStats>()

        eventBus.tell(DistributedEventBus.Subscribe("sub-1", subscriber.ref()))
        eventBus.tell(DistributedEventBus.GetClusterStats(statsProbe.ref()))

        val stats = statsProbe.receiveMessage()
        assertEquals(1, stats.localSubscribers)
        assertTrue(stats.nodeAddress.isNotEmpty())
    }
}
