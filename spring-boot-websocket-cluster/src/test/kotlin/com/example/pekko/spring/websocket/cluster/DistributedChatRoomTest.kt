package com.example.pekko.spring.websocket.cluster

import com.typesafe.config.ConfigFactory
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit
import org.apache.pekko.actor.typed.pubsub.Topic
import org.apache.pekko.cluster.MemberStatus
import org.apache.pekko.cluster.typed.Cluster
import org.apache.pekko.cluster.typed.Join
import java.time.Duration

/**
 * DistributedChatRoom 테스트 (Kotest BDD Style)
 */
class DistributedChatRoomTest : DescribeSpec({

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

    describe("DistributedChatRoom") {
        context("Join 메시지를 받으면") {
            it("사용자가 채팅방에 참여할 수 있다") {
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
                userList.users.size shouldBe 1
                userList.users[0].username shouldBe "alice"
            }

            it("여러 사용자가 참여할 수 있다") {
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
                userList.users.size shouldBe 2
                userList.users.any { it.username == "alice" } shouldBe true
                userList.users.any { it.username == "bob" } shouldBe true
            }
        }

        context("Leave 메시지를 받으면") {
            it("사용자가 목록에서 제거되어야 한다") {
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
                userList.users.size shouldBe 1
                userList.users[0].username shouldBe "alice"
            }
        }
    }
})
