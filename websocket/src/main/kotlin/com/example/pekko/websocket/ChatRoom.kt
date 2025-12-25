package com.example.pekko.websocket

import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.javadsl.Receive

/**
 * ChatRoom actor manages connected users and broadcasts messages.
 * Demonstrates actor-based WebSocket session management.
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
    data class Join(val username: String, val userActor: ActorRef<UserMessage>) : Command
    data class Leave(val username: String) : Command

    // Messages
    data class PostMessage(val username: String, val message: String) : Command
    data class GetUsers(val replyTo: ActorRef<UserList>) : Command

    // Responses to users
    sealed interface UserMessage
    data class ChatMessage(val username: String, val message: String, val timestamp: Long = System.currentTimeMillis()) : UserMessage
    data class SystemMessage(val message: String, val timestamp: Long = System.currentTimeMillis()) : UserMessage
    data class UserList(val users: List<String>) : UserMessage

    private val users = mutableMapOf<String, ActorRef<UserMessage>>()

    override fun createReceive(): Receive<Command> = newReceiveBuilder()
        .onMessage(Join::class.java, this::onJoin)
        .onMessage(Leave::class.java, this::onLeave)
        .onMessage(PostMessage::class.java, this::onPostMessage)
        .onMessage(GetUsers::class.java, this::onGetUsers)
        .build()

    private fun onJoin(cmd: Join): Behavior<Command> {
        if (users.containsKey(cmd.username)) {
            cmd.userActor.tell(SystemMessage("Username '${cmd.username}' is already taken"))
            return this
        }

        users[cmd.username] = cmd.userActor
        context.log.info("User '{}' joined. Total users: {}", cmd.username, users.size)

        // Notify all users
        val joinMessage = SystemMessage("${cmd.username} joined the chat")
        users.values.forEach { it.tell(joinMessage) }

        return this
    }

    private fun onLeave(cmd: Leave): Behavior<Command> {
        users.remove(cmd.username)
        context.log.info("User '{}' left. Total users: {}", cmd.username, users.size)

        // Notify remaining users
        val leaveMessage = SystemMessage("${cmd.username} left the chat")
        users.values.forEach { it.tell(leaveMessage) }

        return this
    }

    private fun onPostMessage(cmd: PostMessage): Behavior<Command> {
        if (!users.containsKey(cmd.username)) {
            context.log.warn("Message from unknown user: {}", cmd.username)
            return this
        }

        context.log.debug("Message from '{}': {}", cmd.username, cmd.message)

        // Broadcast to all users
        val chatMessage = ChatMessage(cmd.username, cmd.message)
        users.values.forEach { it.tell(chatMessage) }

        return this
    }

    private fun onGetUsers(cmd: GetUsers): Behavior<Command> {
        cmd.replyTo.tell(UserList(users.keys.toList()))
        return this
    }
}
