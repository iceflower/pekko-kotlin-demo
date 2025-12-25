package com.example.pekko.websocket

import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChatRoomTest {

    companion object {
        private val testKit = ActorTestKit.create()

        @AfterAll
        @JvmStatic
        fun cleanup() {
            testKit.shutdownTestKit()
        }
    }

    @Test
    fun `user can join chat room`() {
        val chatRoom = testKit.spawn(ChatRoom.create())
        val userProbe = testKit.createTestProbe<ChatRoom.UserMessage>()

        chatRoom.tell(ChatRoom.Join("alice", userProbe.ref()))

        val message = userProbe.receiveMessage()
        assertTrue(message is ChatRoom.SystemMessage)
        assertTrue((message as ChatRoom.SystemMessage).message.contains("alice joined"))
    }

    @Test
    fun `message is broadcast to all users`() {
        val chatRoom = testKit.spawn(ChatRoom.create())
        val aliceProbe = testKit.createTestProbe<ChatRoom.UserMessage>()
        val bobProbe = testKit.createTestProbe<ChatRoom.UserMessage>()

        chatRoom.tell(ChatRoom.Join("alice", aliceProbe.ref()))
        chatRoom.tell(ChatRoom.Join("bob", bobProbe.ref()))

        // 가입 메시지 비우기
        // alice 수신: "alice joined", "bob joined"
        aliceProbe.receiveMessage() // alice 가입
        aliceProbe.receiveMessage() // bob 가입
        // bob 수신: "bob joined" (alice 가입 시 bob은 없었음)
        bobProbe.receiveMessage()   // bob 가입

        // 메시지 전송
        chatRoom.tell(ChatRoom.PostMessage("alice", "Hello everyone!"))

        // 둘 다 메시지를 받아야 함
        val aliceMsg = aliceProbe.receiveMessage()
        val bobMsg = bobProbe.receiveMessage()

        assertTrue(aliceMsg is ChatRoom.ChatMessage)
        assertEquals("alice", (aliceMsg as ChatRoom.ChatMessage).username)
        assertEquals("Hello everyone!", aliceMsg.message)

        assertTrue(bobMsg is ChatRoom.ChatMessage)
        assertEquals("Hello everyone!", (bobMsg as ChatRoom.ChatMessage).message)
    }

    @Test
    fun `can get user list`() {
        val chatRoom = testKit.spawn(ChatRoom.create())
        val aliceProbe = testKit.createTestProbe<ChatRoom.UserMessage>()
        val bobProbe = testKit.createTestProbe<ChatRoom.UserMessage>()
        val listProbe = testKit.createTestProbe<ChatRoom.UserList>()

        chatRoom.tell(ChatRoom.Join("alice", aliceProbe.ref()))
        chatRoom.tell(ChatRoom.Join("bob", bobProbe.ref()))
        chatRoom.tell(ChatRoom.GetUsers(listProbe.ref()))

        val userList = listProbe.receiveMessage()
        assertEquals(2, userList.users.size)
        assertTrue(userList.users.contains("alice"))
        assertTrue(userList.users.contains("bob"))
    }

    @Test
    fun `user leaving notifies others`() {
        val chatRoom = testKit.spawn(ChatRoom.create())
        val aliceProbe = testKit.createTestProbe<ChatRoom.UserMessage>()
        val bobProbe = testKit.createTestProbe<ChatRoom.UserMessage>()

        chatRoom.tell(ChatRoom.Join("alice", aliceProbe.ref()))
        chatRoom.tell(ChatRoom.Join("bob", bobProbe.ref()))

        // 가입 메시지 비우기
        // alice 수신: "alice joined", "bob joined"
        aliceProbe.receiveMessage()
        aliceProbe.receiveMessage()
        // bob 수신: "bob joined"
        bobProbe.receiveMessage()

        // Bob 퇴장
        chatRoom.tell(ChatRoom.Leave("bob"))

        val leaveMsg = aliceProbe.receiveMessage()
        assertTrue(leaveMsg is ChatRoom.SystemMessage)
        assertTrue((leaveMsg as ChatRoom.SystemMessage).message.contains("bob left"))
    }
}
