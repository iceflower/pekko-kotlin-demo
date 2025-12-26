package com.example.pekko.websocket

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit

/**
 * ChatRoom 테스트 (Kotest BDD Style)
 */
class ChatRoomTest : DescribeSpec({

    val testKit = ActorTestKit.create()

    afterSpec {
        testKit.shutdownTestKit()
    }

    describe("ChatRoom") {
        context("Join 메시지를 받으면") {
            it("사용자가 채팅방에 참여할 수 있다") {
                val chatRoom = testKit.spawn(ChatRoom.create())
                val userProbe = testKit.createTestProbe<ChatRoom.UserMessage>()

                chatRoom.tell(ChatRoom.Join("alice", userProbe.ref()))

                val message = userProbe.receiveMessage()
                message.shouldBeInstanceOf<ChatRoom.SystemMessage>()
                (message as ChatRoom.SystemMessage).message.contains("alice joined") shouldBe true
            }
        }

        context("PostMessage 메시지를 받으면") {
            it("모든 사용자에게 메시지를 브로드캐스트해야 한다") {
                val chatRoom = testKit.spawn(ChatRoom.create())
                val aliceProbe = testKit.createTestProbe<ChatRoom.UserMessage>()
                val bobProbe = testKit.createTestProbe<ChatRoom.UserMessage>()

                chatRoom.tell(ChatRoom.Join("alice", aliceProbe.ref()))
                chatRoom.tell(ChatRoom.Join("bob", bobProbe.ref()))

                // 가입 메시지 비우기
                aliceProbe.receiveMessage() // alice 가입
                aliceProbe.receiveMessage() // bob 가입
                bobProbe.receiveMessage()   // bob 가입

                // 메시지 전송
                chatRoom.tell(ChatRoom.PostMessage("alice", "Hello everyone!"))

                // 둘 다 메시지를 받아야 함
                val aliceMsg = aliceProbe.receiveMessage()
                val bobMsg = bobProbe.receiveMessage()

                aliceMsg.shouldBeInstanceOf<ChatRoom.ChatMessage>()
                (aliceMsg as ChatRoom.ChatMessage).username shouldBe "alice"
                aliceMsg.message shouldBe "Hello everyone!"

                bobMsg.shouldBeInstanceOf<ChatRoom.ChatMessage>()
                (bobMsg as ChatRoom.ChatMessage).message shouldBe "Hello everyone!"
            }
        }

        context("GetUsers 메시지를 받으면") {
            it("사용자 목록을 반환해야 한다") {
                val chatRoom = testKit.spawn(ChatRoom.create())
                val aliceProbe = testKit.createTestProbe<ChatRoom.UserMessage>()
                val bobProbe = testKit.createTestProbe<ChatRoom.UserMessage>()
                val listProbe = testKit.createTestProbe<ChatRoom.UserList>()

                chatRoom.tell(ChatRoom.Join("alice", aliceProbe.ref()))
                chatRoom.tell(ChatRoom.Join("bob", bobProbe.ref()))
                chatRoom.tell(ChatRoom.GetUsers(listProbe.ref()))

                val userList = listProbe.receiveMessage()
                userList.users.size shouldBe 2
                userList.users.contains("alice") shouldBe true
                userList.users.contains("bob") shouldBe true
            }
        }

        context("Leave 메시지를 받으면") {
            it("다른 사용자에게 퇴장을 알려야 한다") {
                val chatRoom = testKit.spawn(ChatRoom.create())
                val aliceProbe = testKit.createTestProbe<ChatRoom.UserMessage>()
                val bobProbe = testKit.createTestProbe<ChatRoom.UserMessage>()

                chatRoom.tell(ChatRoom.Join("alice", aliceProbe.ref()))
                chatRoom.tell(ChatRoom.Join("bob", bobProbe.ref()))

                // 가입 메시지 비우기
                aliceProbe.receiveMessage()
                aliceProbe.receiveMessage()
                bobProbe.receiveMessage()

                // Bob 퇴장
                chatRoom.tell(ChatRoom.Leave("bob"))

                val leaveMsg = aliceProbe.receiveMessage()
                leaveMsg.shouldBeInstanceOf<ChatRoom.SystemMessage>()
                (leaveMsg as ChatRoom.SystemMessage).message.contains("bob left") shouldBe true
            }
        }
    }
})
