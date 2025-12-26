package com.example.pekko.spring.websocket

import com.example.pekko.spring.websocket.actor.ChatRoom
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit
import java.util.concurrent.CopyOnWriteArrayList

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
                val receivedMessages = CopyOnWriteArrayList<ChatRoom.Message>()

                chatRoom.tell(ChatRoom.Join("session1", "Alice") { msg ->
                    receivedMessages.add(msg)
                })

                Thread.sleep(100)

                receivedMessages.any {
                    it is ChatRoom.SystemMessage && it.content.contains("Alice joined")
                } shouldBe true
            }

            it("중복 사용자 이름을 거부해야 한다") {
                val chatRoom = testKit.spawn(ChatRoom.create())
                val aliceMessages = CopyOnWriteArrayList<ChatRoom.Message>()
                val alice2Messages = CopyOnWriteArrayList<ChatRoom.Message>()

                chatRoom.tell(ChatRoom.Join("session1", "Alice") { msg ->
                    aliceMessages.add(msg)
                })

                Thread.sleep(100)

                chatRoom.tell(ChatRoom.Join("session2", "Alice") { msg ->
                    alice2Messages.add(msg)
                })

                Thread.sleep(100)

                alice2Messages.any {
                    it is ChatRoom.SystemMessage && it.content.contains("already taken")
                } shouldBe true
            }
        }

        context("SendMessage 메시지를 받으면") {
            it("모든 사용자에게 브로드캐스트해야 한다") {
                val chatRoom = testKit.spawn(ChatRoom.create())
                val aliceMessages = CopyOnWriteArrayList<ChatRoom.Message>()
                val bobMessages = CopyOnWriteArrayList<ChatRoom.Message>()

                chatRoom.tell(ChatRoom.Join("session1", "Alice") { msg ->
                    aliceMessages.add(msg)
                })
                chatRoom.tell(ChatRoom.Join("session2", "Bob") { msg ->
                    bobMessages.add(msg)
                })

                Thread.sleep(100)

                chatRoom.tell(ChatRoom.SendMessage("session1", "Hello from Alice"))

                Thread.sleep(100)

                aliceMessages.any {
                    it is ChatRoom.ChatMessage && it.content == "Hello from Alice" && it.username == "Alice"
                } shouldBe true
                bobMessages.any {
                    it is ChatRoom.ChatMessage && it.content == "Hello from Alice" && it.username == "Alice"
                } shouldBe true
            }
        }

        context("Leave 메시지를 받으면") {
            it("다른 사용자에게 퇴장을 알려야 한다") {
                val chatRoom = testKit.spawn(ChatRoom.create())
                val messages = CopyOnWriteArrayList<ChatRoom.Message>()

                chatRoom.tell(ChatRoom.Join("session1", "Alice") { msg ->
                    messages.add(msg)
                })
                chatRoom.tell(ChatRoom.Join("session2", "Bob") { msg ->
                    messages.add(msg)
                })

                Thread.sleep(100)

                chatRoom.tell(ChatRoom.Leave("session1"))

                Thread.sleep(100)

                messages.any {
                    it is ChatRoom.SystemMessage && it.content.contains("Alice left")
                } shouldBe true
            }
        }

        context("GetUsers 메시지를 받으면") {
            it("사용자 목록을 반환해야 한다") {
                val chatRoom = testKit.spawn(ChatRoom.create())
                val probe = testKit.createTestProbe<ChatRoom.UserList>()

                chatRoom.tell(ChatRoom.Join("session1", "Alice") {})
                chatRoom.tell(ChatRoom.Join("session2", "Bob") {})

                Thread.sleep(100)

                chatRoom.tell(ChatRoom.GetUsers(probe.ref()))

                val userList = probe.receiveMessage()
                userList.users.size shouldBe 2
                userList.users.contains("Alice") shouldBe true
                userList.users.contains("Bob") shouldBe true
            }
        }
    }
})
