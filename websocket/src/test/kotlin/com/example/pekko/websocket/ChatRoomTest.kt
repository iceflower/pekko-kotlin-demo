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

        // Clear join messages
        // alice receives: "alice joined", "bob joined"
        aliceProbe.receiveMessage() // alice joined
        aliceProbe.receiveMessage() // bob joined
        // bob receives: "bob joined" (bob wasn't there when alice joined)
        bobProbe.receiveMessage()   // bob joined

        // Send message
        chatRoom.tell(ChatRoom.PostMessage("alice", "Hello everyone!"))

        // Both should receive the message
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

        // Clear join messages
        // alice receives: "alice joined", "bob joined"
        aliceProbe.receiveMessage()
        aliceProbe.receiveMessage()
        // bob receives: "bob joined"
        bobProbe.receiveMessage()

        // Bob leaves
        chatRoom.tell(ChatRoom.Leave("bob"))

        val leaveMsg = aliceProbe.receiveMessage()
        assertTrue(leaveMsg is ChatRoom.SystemMessage)
        assertTrue((leaveMsg as ChatRoom.SystemMessage).message.contains("bob left"))
    }
}
