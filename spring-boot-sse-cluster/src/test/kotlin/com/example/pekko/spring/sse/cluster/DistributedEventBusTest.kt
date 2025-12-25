package com.example.pekko.spring.sse.cluster

import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit
import org.apache.pekko.actor.typed.pubsub.Topic
import org.apache.pekko.cluster.MemberStatus
import org.apache.pekko.cluster.typed.Cluster
import org.apache.pekko.cluster.typed.Join
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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

        val receivedEvents = CopyOnWriteArrayList<DistributedEventBus.SseEvent>()
        val latch = CountDownLatch(1)
        val callback: (DistributedEventBus.SseEvent) -> Unit = { event ->
            receivedEvents.add(event)
            latch.countDown()
        }

        eventBus.tell(DistributedEventBus.Subscribe("sub-1", callback))

        // 이벤트 수신 대기
        assertTrue(latch.await(5, TimeUnit.SECONDS), "연결 이벤트를 받지 못했습니다")

        assertEquals(1, receivedEvents.size)
        assertEquals("connected", receivedEvents[0].eventType)
        assertTrue(receivedEvents[0].data.contains("sub-1"))
    }

    @Test
    fun `구독자 목록을 조회할 수 있다`() {
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
        assertTrue(latch.await(5, TimeUnit.SECONDS), "구독 처리 시간 초과")

        eventBus.tell(DistributedEventBus.GetSubscribers(listProbe.ref()))

        val subscriberList = listProbe.receiveMessage(Duration.ofSeconds(5))
        assertEquals(2, subscriberList.subscribers.size)
        assertTrue(subscriberList.subscribers.any { it.id == "sub-1" })
        assertTrue(subscriberList.subscribers.any { it.id == "sub-2" })
    }

    @Test
    fun `구독 취소 시 구독자 목록에서 제거된다`() {
        val topic = testKit.spawn(
            Topic.create(DistributedEventBus.ClusterEvent::class.java, "test-topic-3")
        )
        val eventBus = testKit.spawn(DistributedEventBus.create(topic))
        val listProbe = testKit.createTestProbe<DistributedEventBus.SubscriberList>()

        // 구독 완료를 확인하기 위한 latch
        val latch = CountDownLatch(1)
        eventBus.tell(DistributedEventBus.Subscribe("sub-1") { latch.countDown() })

        // 구독 처리 대기
        assertTrue(latch.await(5, TimeUnit.SECONDS), "구독 처리 시간 초과")

        eventBus.tell(DistributedEventBus.Unsubscribe("sub-1"))

        // Unsubscribe 메시지가 처리되도록 잠시 대기 후 조회
        Thread.sleep(200)

        eventBus.tell(DistributedEventBus.GetSubscribers(listProbe.ref()))

        val subscriberList = listProbe.receiveMessage(Duration.ofSeconds(5))
        assertEquals(0, subscriberList.subscribers.size)
    }

    @Test
    fun `클러스터 통계를 조회할 수 있다`() {
        val topic = testKit.spawn(
            Topic.create(DistributedEventBus.ClusterEvent::class.java, "test-topic-4")
        )
        val eventBus = testKit.spawn(DistributedEventBus.create(topic))
        val statsProbe = testKit.createTestProbe<DistributedEventBus.ClusterStats>()

        // 구독 완료를 확인하기 위한 latch
        val latch = CountDownLatch(1)
        eventBus.tell(DistributedEventBus.Subscribe("sub-1") { latch.countDown() })

        // 구독 처리 대기
        assertTrue(latch.await(5, TimeUnit.SECONDS), "구독 처리 시간 초과")

        eventBus.tell(DistributedEventBus.GetClusterStats(statsProbe.ref()))

        val stats = statsProbe.receiveMessage(Duration.ofSeconds(5))
        assertEquals(1, stats.localSubscribers)
        assertTrue(stats.nodeAddress.isNotEmpty())
    }
}
