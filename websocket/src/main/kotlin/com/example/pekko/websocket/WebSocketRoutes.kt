package com.example.pekko.websocket

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.apache.pekko.NotUsed
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.AskPattern
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.http.javadsl.model.ws.Message
import org.apache.pekko.http.javadsl.model.ws.TextMessage
import org.apache.pekko.http.javadsl.server.AllDirectives
import org.apache.pekko.http.javadsl.server.Route
import org.apache.pekko.japi.Pair
import org.apache.pekko.stream.OverflowStrategy
import org.apache.pekko.stream.javadsl.BroadcastHub
import org.apache.pekko.stream.javadsl.Flow
import org.apache.pekko.stream.javadsl.Keep
import org.apache.pekko.stream.javadsl.MergeHub
import org.apache.pekko.stream.javadsl.Sink
import org.apache.pekko.stream.javadsl.Source
import java.time.Duration
import java.util.concurrent.CompletionStage

/**
 * WebSocket routes using Pekko HTTP.
 * Uses MergeHub/BroadcastHub for multi-user chat.
 */
class WebSocketRoutes(
    private val chatRoom: ActorRef<ChatRoom.Command>,
    private val system: ActorSystem<*>
) : AllDirectives() {

    private val askTimeout = Duration.ofSeconds(3)
    private val objectMapper = jacksonObjectMapper()

    // Create a shared broadcast hub for outgoing messages
    private val broadcastPair: Pair<Sink<ChatRoom.UserMessage, NotUsed>, Source<ChatRoom.UserMessage, NotUsed>> = run {
        MergeHub.of(ChatRoom.UserMessage::class.java, 256)
            .toMat(BroadcastHub.of(ChatRoom.UserMessage::class.java, 256), Keep.both())
            .run(system)
    }

    private val broadcastSink: Sink<ChatRoom.UserMessage, NotUsed> = broadcastPair.first()
    private val broadcastSource: Source<ChatRoom.UserMessage, NotUsed> = broadcastPair.second()

    fun routes(): Route = concat(
        // WebSocket endpoint for chat
        pathPrefix("ws") {
            path("chat") {
                parameter("username") { username ->
                    handleWebSocketMessages(createChatFlow(username))
                }
            }
        },

        // REST endpoint to get user list
        pathPrefix("api") {
            path("users") {
                get {
                    onSuccess(getUsers()) { userList ->
                        complete(userList.users.joinToString(", "))
                    }
                }
            }
        },

        // Simple HTML page for testing
        pathSingleSlash {
            getFromResource("static/index.html")
        }
    )

    /**
     * Creates a WebSocket flow that connects a user to the chat room.
     */
    private fun createChatFlow(username: String): Flow<Message, Message, NotUsed> {
        // Create a user-specific actor that forwards messages to the broadcast hub
        val userActorBehavior: Behavior<ChatRoom.UserMessage> = Behaviors.setup { _ ->
            Behaviors.receiveMessage { msg ->
                broadcastSink.runWith(Source.single(msg), system)
                Behaviors.same()
            }
        }

        val userActorRef = system.systemActorOf(
            userActorBehavior,
            "ws-user-${username.replace(Regex("[^a-zA-Z0-9]"), "")}-${System.currentTimeMillis()}",
            org.apache.pekko.actor.typed.Props.empty()
        )

        // Join the chat room with the user's actor
        chatRoom.tell(ChatRoom.Join(username, userActorRef))

        // Outgoing: broadcast messages to WebSocket
        val outgoing: Source<Message, NotUsed> = broadcastSource
            .map { userMessage: ChatRoom.UserMessage -> toTextMessage(userMessage) }

        // Incoming: messages from WebSocket to ChatRoom
        val incoming: Sink<Message, NotUsed> = Flow.of(Message::class.java)
            .filter { it.isText }
            .map { msg -> (msg as TextMessage).getStrictText() }
            .watchTermination { _, done ->
                done.whenComplete { _, _ ->
                    chatRoom.tell(ChatRoom.Leave(username))
                }
                NotUsed.getInstance()
            }
            .to(Sink.foreach { text ->
                chatRoom.tell(ChatRoom.PostMessage(username, text))
            })

        return Flow.fromSinkAndSource(incoming, outgoing)
    }

    private fun toTextMessage(userMessage: ChatRoom.UserMessage): Message {
        val json = when (userMessage) {
            is ChatRoom.ChatMessage -> mapOf(
                "type" to "chat",
                "username" to userMessage.username,
                "message" to userMessage.message,
                "timestamp" to userMessage.timestamp
            )
            is ChatRoom.SystemMessage -> mapOf(
                "type" to "system",
                "message" to userMessage.message,
                "timestamp" to userMessage.timestamp
            )
            is ChatRoom.UserList -> mapOf(
                "type" to "users",
                "users" to userMessage.users
            )
        }
        return TextMessage.create(objectMapper.writeValueAsString(json))
    }

    private fun getUsers(): CompletionStage<ChatRoom.UserList> {
        return AskPattern.ask(
            chatRoom,
            { replyTo: ActorRef<ChatRoom.UserList> -> ChatRoom.GetUsers(replyTo) },
            askTimeout,
            system.scheduler()
        )
    }
}
