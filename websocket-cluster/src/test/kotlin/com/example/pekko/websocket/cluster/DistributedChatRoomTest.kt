package com.example.pekko.websocket.cluster

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

class DistributedChatRoomTest {

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
    fun `사용자가 채팅방에 참여할 수 있다`() {
        val topic = testKit.spawn(
            Topic.create(DistributedChatRoom.ChatEvent::class.java, "test-topic-1")
        )
        val chatRoom = testKit.spawn(DistributedChatRoom.create(topic))
        val userProbe = testKit.createTestProbe<DistributedChatRoom.UserMessage>()

        chatRoom.tell(DistributedChatRoom.Join("alice", userProbe.ref()))

        val message = userProbe.receiveMessage()
        assertTrue(message is DistributedChatRoom.SystemMessage)
        assertTrue(message.message.contains("alice"))
    }

    @Test
    fun `메시지가 모든 사용자에게 브로드캐스트된다`() {
        val topic = testKit.spawn(
            Topic.create(DistributedChatRoom.ChatEvent::class.java, "test-topic-2")
        )
        val chatRoom = testKit.spawn(DistributedChatRoom.create(topic))
        val aliceProbe = testKit.createTestProbe<DistributedChatRoom.UserMessage>()
        val bobProbe = testKit.createTestProbe<DistributedChatRoom.UserMessage>()

        chatRoom.tell(DistributedChatRoom.Join("alice", aliceProbe.ref()))
        chatRoom.tell(DistributedChatRoom.Join("bob", bobProbe.ref()))

        // 가입 메시지 비우기 - ChatMessage가 올 때까지 모든 SystemMessage 소비
        fun drainSystemMessages(probe: org.apache.pekko.actor.testkit.typed.javadsl.TestProbe<DistributedChatRoom.UserMessage>): DistributedChatRoom.ChatMessage {
            while (true) {
                val msg = probe.receiveMessage()
                if (msg is DistributedChatRoom.ChatMessage) return msg
            }
        }

        // 메시지 전송
        chatRoom.tell(DistributedChatRoom.PostMessage("alice", "안녕하세요!"))

        // ChatMessage를 받을 때까지 대기 (SystemMessage는 건너뜀)
        val aliceMsg = drainSystemMessages(aliceProbe)
        val bobMsg = drainSystemMessages(bobProbe)

        assertEquals("alice", aliceMsg.username)
        assertEquals("안녕하세요!", aliceMsg.message)

        assertEquals("안녕하세요!", bobMsg.message)
    }

    @Test
    fun `사용자 목록을 조회할 수 있다`() {
        val topic = testKit.spawn(
            Topic.create(DistributedChatRoom.ChatEvent::class.java, "test-topic-3")
        )
        val chatRoom = testKit.spawn(DistributedChatRoom.create(topic))
        val aliceProbe = testKit.createTestProbe<DistributedChatRoom.UserMessage>()
        val bobProbe = testKit.createTestProbe<DistributedChatRoom.UserMessage>()
        val listProbe = testKit.createTestProbe<DistributedChatRoom.UserList>()

        chatRoom.tell(DistributedChatRoom.Join("alice", aliceProbe.ref()))
        chatRoom.tell(DistributedChatRoom.Join("bob", bobProbe.ref()))
        chatRoom.tell(DistributedChatRoom.GetUsers(listProbe.ref()))

        val userList = listProbe.receiveMessage()
        assertEquals(2, userList.users.size)
        assertTrue(userList.users.any { it.username == "alice" })
        assertTrue(userList.users.any { it.username == "bob" })
    }

    @Test
    fun `사용자 퇴장 시 다른 사용자에게 알림된다`() {
        val topic = testKit.spawn(
            Topic.create(DistributedChatRoom.ChatEvent::class.java, "test-topic-4")
        )
        val chatRoom = testKit.spawn(DistributedChatRoom.create(topic))
        val aliceProbe = testKit.createTestProbe<DistributedChatRoom.UserMessage>()
        val bobProbe = testKit.createTestProbe<DistributedChatRoom.UserMessage>()

        chatRoom.tell(DistributedChatRoom.Join("alice", aliceProbe.ref()))
        chatRoom.tell(DistributedChatRoom.Join("bob", bobProbe.ref()))

        // 가입 메시지 비우기
        aliceProbe.receiveMessage()
        aliceProbe.receiveMessage()
        bobProbe.receiveMessage()

        // Bob 퇴장
        chatRoom.tell(DistributedChatRoom.Leave("bob"))

        val leaveMsg = aliceProbe.receiveMessage()
        assertTrue(leaveMsg is DistributedChatRoom.SystemMessage)
        assertTrue(leaveMsg.message.contains("bob"))
    }
}
