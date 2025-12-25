package com.example.pekko.spring.websocket

import com.example.pekko.spring.websocket.actor.ChatRoom
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
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
    fun `ChatRoom should allow user to join`() {
        val chatRoom = testKit.spawn(ChatRoom.create())
        val receivedMessages = CopyOnWriteArrayList<ChatRoom.Message>()

        chatRoom.tell(ChatRoom.Join("session1", "Alice") { msg ->
            receivedMessages.add(msg)
        })

        Thread.sleep(100)

        assertTrue(receivedMessages.any {
            it is ChatRoom.SystemMessage && it.content.contains("Alice joined")
        })
    }

    @Test
    fun `ChatRoom should broadcast messages to all users`() {
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

        assertTrue(aliceMessages.any {
            it is ChatRoom.ChatMessage && it.content == "Hello from Alice" && it.username == "Alice"
        })
        assertTrue(bobMessages.any {
            it is ChatRoom.ChatMessage && it.content == "Hello from Alice" && it.username == "Alice"
        })
    }

    @Test
    fun `ChatRoom should notify when user leaves`() {
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

        assertTrue(messages.any {
            it is ChatRoom.SystemMessage && it.content.contains("Alice left")
        })
    }

    @Test
    fun `ChatRoom should reject duplicate usernames`() {
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

        assertTrue(alice2Messages.any {
            it is ChatRoom.SystemMessage && it.content.contains("already taken")
        })
    }

    @Test
    fun `ChatRoom should return user list`() {
        val chatRoom = testKit.spawn(ChatRoom.create())
        val probe = testKit.createTestProbe<ChatRoom.UserList>()

        chatRoom.tell(ChatRoom.Join("session1", "Alice") {})
        chatRoom.tell(ChatRoom.Join("session2", "Bob") {})

        Thread.sleep(100)

        chatRoom.tell(ChatRoom.GetUsers(probe.ref()))

        val userList = probe.receiveMessage()
        assertEquals(2, userList.users.size)
        assertTrue(userList.users.contains("Alice"))
        assertTrue(userList.users.contains("Bob"))
    }
}
