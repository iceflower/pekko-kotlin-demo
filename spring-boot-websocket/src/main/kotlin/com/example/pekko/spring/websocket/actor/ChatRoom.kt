package com.example.pekko.spring.websocket.actor

import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.javadsl.Receive

/**
 * ChatRoom actor manages connected users and broadcasts messages.
 * Integrates with Spring WebSocket sessions.
 */
class ChatRoom private constructor(
    context: ActorContext<Command>
) : AbstractBehavior<ChatRoom.Command>(context) {

    companion object {
        fun create(): Behavior<Command> = Behaviors.setup { context ->
            context.log.info("ChatRoom created")
            ChatRoom(context)
        }
    }

    sealed interface Command

    // User management
    data class Join(val sessionId: String, val username: String, val callback: (Message) -> Unit) : Command
    data class Leave(val sessionId: String) : Command

    // Messages
    data class SendMessage(val sessionId: String, val content: String) : Command
    data class GetUsers(val replyTo: ActorRef<UserList>) : Command

    // Output messages
    sealed interface Message {
        val timestamp: Long
    }

    data class ChatMessage(
        val username: String,
        val content: String,
        val type: String = "CHAT",
        override val timestamp: Long = System.currentTimeMillis()
    ) : Message

    data class SystemMessage(
        val content: String,
        val type: String = "SYSTEM",
        override val timestamp: Long = System.currentTimeMillis()
    ) : Message

    data class UserList(val users: List<String>)

    private data class UserSession(
        val username: String,
        val callback: (Message) -> Unit
    )

    private val sessions = mutableMapOf<String, UserSession>()

    override fun createReceive(): Receive<Command> = newReceiveBuilder()
        .onMessage(Join::class.java, this::onJoin)
        .onMessage(Leave::class.java, this::onLeave)
        .onMessage(SendMessage::class.java, this::onSendMessage)
        .onMessage(GetUsers::class.java, this::onGetUsers)
        .build()

    private fun onJoin(cmd: Join): Behavior<Command> {
        // Check if username already exists
        if (sessions.values.any { it.username == cmd.username }) {
            cmd.callback(SystemMessage("Username '${cmd.username}' is already taken"))
            return this
        }

        sessions[cmd.sessionId] = UserSession(cmd.username, cmd.callback)
        context.log.info("User '{}' joined (session: {}). Total users: {}", cmd.username, cmd.sessionId, sessions.size)

        // Notify all users
        broadcast(SystemMessage("${cmd.username} joined the chat"))

        return this
    }

    private fun onLeave(cmd: Leave): Behavior<Command> {
        val session = sessions.remove(cmd.sessionId)
        if (session != null) {
            context.log.info("User '{}' left. Total users: {}", session.username, sessions.size)
            broadcast(SystemMessage("${session.username} left the chat"))
        }
        return this
    }

    private fun onSendMessage(cmd: SendMessage): Behavior<Command> {
        val session = sessions[cmd.sessionId]
        if (session == null) {
            context.log.warn("Message from unknown session: {}", cmd.sessionId)
            return this
        }

        context.log.debug("Message from '{}': {}", session.username, cmd.content)
        broadcast(ChatMessage(session.username, cmd.content))

        return this
    }

    private fun onGetUsers(cmd: GetUsers): Behavior<Command> {
        cmd.replyTo.tell(UserList(sessions.values.map { it.username }))
        return this
    }

    private fun broadcast(message: Message) {
        sessions.values.forEach { session ->
            try {
                session.callback(message)
            } catch (e: Exception) {
                context.log.warn("Failed to send message to {}: {}", session.username, e.message)
            }
        }
    }
}
