package com.example.pekko.spring.websocket.cluster

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
        val listProbe = testKit.createTestProbe<DistributedChatRoom.UserList>()

        chatRoom.tell(DistributedChatRoom.Join("alice", "session-1"))

        // 가입 처리 대기
        Thread.sleep(500)

        chatRoom.tell(DistributedChatRoom.GetUsers(listProbe.ref()))

        val userList = listProbe.receiveMessage(Duration.ofSeconds(5))
        assertEquals(1, userList.users.size)
        assertEquals("alice", userList.users[0].username)
    }

    @Test
    fun `여러 사용자가 채팅방에 참여할 수 있다`() {
        val topic = testKit.spawn(
            Topic.create(DistributedChatRoom.ChatEvent::class.java, "test-topic-2")
        )
        val chatRoom = testKit.spawn(DistributedChatRoom.create(topic))
        val listProbe = testKit.createTestProbe<DistributedChatRoom.UserList>()

        chatRoom.tell(DistributedChatRoom.Join("alice", "session-1"))
        chatRoom.tell(DistributedChatRoom.Join("bob", "session-2"))

        // 가입 처리 대기
        Thread.sleep(500)

        chatRoom.tell(DistributedChatRoom.GetUsers(listProbe.ref()))

        val userList = listProbe.receiveMessage(Duration.ofSeconds(5))
        assertEquals(2, userList.users.size)
        assertTrue(userList.users.any { it.username == "alice" })
        assertTrue(userList.users.any { it.username == "bob" })
    }

    @Test
    fun `사용자 퇴장 시 목록에서 제거된다`() {
        val topic = testKit.spawn(
            Topic.create(DistributedChatRoom.ChatEvent::class.java, "test-topic-3")
        )
        val chatRoom = testKit.spawn(DistributedChatRoom.create(topic))
        val listProbe = testKit.createTestProbe<DistributedChatRoom.UserList>()

        chatRoom.tell(DistributedChatRoom.Join("alice", "session-1"))
        chatRoom.tell(DistributedChatRoom.Join("bob", "session-2"))

        // 가입 처리 대기
        Thread.sleep(500)

        chatRoom.tell(DistributedChatRoom.Leave("session-2"))

        // 퇴장 처리 대기
        Thread.sleep(500)

        chatRoom.tell(DistributedChatRoom.GetUsers(listProbe.ref()))

        val userList = listProbe.receiveMessage(Duration.ofSeconds(5))
        assertEquals(1, userList.users.size)
        assertEquals("alice", userList.users[0].username)
    }
}
