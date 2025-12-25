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
 * Pekko HTTP를 사용한 WebSocket 라우트.
 * 멀티 유저 채팅을 위해 MergeHub/BroadcastHub를 사용합니다.
 */
class WebSocketRoutes(
    private val chatRoom: ActorRef<ChatRoom.Command>,
    private val system: ActorSystem<*>
) : AllDirectives() {

    private val askTimeout = Duration.ofSeconds(3)
    private val objectMapper = jacksonObjectMapper()

    // 발신 메시지를 위한 공유 브로드캐스트 허브 생성
    private val broadcastPair: Pair<Sink<ChatRoom.UserMessage, NotUsed>, Source<ChatRoom.UserMessage, NotUsed>> = run {
        MergeHub.of(ChatRoom.UserMessage::class.java, 256)
            .toMat(BroadcastHub.of(ChatRoom.UserMessage::class.java, 256), Keep.both())
            .run(system)
    }

    private val broadcastSink: Sink<ChatRoom.UserMessage, NotUsed> = broadcastPair.first()
    private val broadcastSource: Source<ChatRoom.UserMessage, NotUsed> = broadcastPair.second()

    fun routes(): Route = concat(
        // 채팅용 WebSocket 엔드포인트
        pathPrefix("ws") {
            path("chat") {
                parameter("username") { username ->
                    handleWebSocketMessages(createChatFlow(username))
                }
            }
        },

        // 사용자 목록 조회용 REST 엔드포인트
        pathPrefix("api") {
            path("users") {
                get {
                    onSuccess(getUsers()) { userList ->
                        complete(userList.users.joinToString(", "))
                    }
                }
            }
        },

        // 테스트용 간단한 HTML 페이지
        pathSingleSlash {
            getFromResource("static/index.html")
        }
    )

    /**
     * 사용자를 채팅방에 연결하는 WebSocket 플로우를 생성합니다.
     */
    private fun createChatFlow(username: String): Flow<Message, Message, NotUsed> {
        // 브로드캐스트 허브로 메시지를 전달하는 사용자별 액터 생성
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

        // 사용자 액터로 채팅방에 참여
        chatRoom.tell(ChatRoom.Join(username, userActorRef))

        // 발신: WebSocket으로 브로드캐스트 메시지
        val outgoing: Source<Message, NotUsed> = broadcastSource
            .map { userMessage: ChatRoom.UserMessage -> toTextMessage(userMessage) }

        // 수신: WebSocket에서 ChatRoom으로 메시지
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
