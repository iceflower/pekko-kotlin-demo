package com.example.pekko.websocket.cluster

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
import org.apache.pekko.stream.javadsl.BroadcastHub
import org.apache.pekko.stream.javadsl.Flow
import org.apache.pekko.stream.javadsl.Keep
import org.apache.pekko.stream.javadsl.MergeHub
import org.apache.pekko.stream.javadsl.Sink
import org.apache.pekko.stream.javadsl.Source
import java.time.Duration
import java.util.concurrent.CompletionStage

/**
 * 클러스터 환경의 WebSocket 라우트.
 * 분산 PubSub을 통해 모든 노드의 사용자와 통신.
 */
class WebSocketClusterRoutes(
    private val chatRoom: ActorRef<DistributedChatRoom.Command>,
    private val system: ActorSystem<*>
) : AllDirectives() {

    private val askTimeout = Duration.ofSeconds(3)
    private val objectMapper = jacksonObjectMapper()

    // 발신 메시지를 위한 공유 브로드캐스트 허브 생성
    private val broadcastPair: org.apache.pekko.japi.Pair<Sink<DistributedChatRoom.UserMessage, NotUsed>, Source<DistributedChatRoom.UserMessage, NotUsed>> =
        MergeHub.of(DistributedChatRoom.UserMessage::class.java, 256)
            .toMat(BroadcastHub.of(DistributedChatRoom.UserMessage::class.java, 256), Keep.both())
            .run(system)

    private val broadcastSink: Sink<DistributedChatRoom.UserMessage, NotUsed> = broadcastPair.first()
    private val broadcastSource: Source<DistributedChatRoom.UserMessage, NotUsed> = broadcastPair.second()

    fun routes(): Route = concat(
        // 채팅용 WebSocket 엔드포인트
        pathPrefix("ws") {
            path("chat") {
                parameter("username") { username ->
                    handleWebSocketMessages(createChatFlow(username))
                }
            }
        },

        // REST API 엔드포인트
        pathPrefix("api") {
            concat(
                // 사용자 목록 조회
                path("users") {
                    get {
                        onSuccess(getUsers()) { userList ->
                            val json = objectMapper.writeValueAsString(userList.users)
                            complete(json)
                        }
                    }
                },
                // 클러스터 정보
                path("cluster-info") {
                    get {
                        val cluster = org.apache.pekko.cluster.typed.Cluster.get(system)
                        val info = mapOf(
                            "selfAddress" to cluster.selfMember().address().toString(),
                            "roles" to cluster.selfMember().roles.toList(),
                            "state" to cluster.selfMember().status().toString()
                        )
                        complete(objectMapper.writeValueAsString(info))
                    }
                }
            )
        },

        // 테스트용 HTML 페이지
        pathSingleSlash {
            getFromResource("static/index.html")
        }
    )

    /**
     * 사용자를 채팅방에 연결하는 WebSocket 플로우 생성.
     */
    private fun createChatFlow(username: String): Flow<Message, Message, NotUsed> {
        // 브로드캐스트 허브로 메시지를 전달하는 사용자별 액터 생성
        val userActorBehavior: Behavior<DistributedChatRoom.UserMessage> = Behaviors.setup { _ ->
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
        chatRoom.tell(DistributedChatRoom.Join(username, userActorRef))

        // 발신: WebSocket으로 브로드캐스트 메시지
        val outgoing: Source<Message, NotUsed> = broadcastSource
            .map { userMessage: DistributedChatRoom.UserMessage -> toTextMessage(userMessage) }

        // 수신: WebSocket에서 ChatRoom으로 메시지
        val incoming: Sink<Message, NotUsed> = Flow.of(Message::class.java)
            .filter { it.isText }
            .map { msg -> (msg as TextMessage).getStrictText() }
            .watchTermination { _, done ->
                done.whenComplete { _, _ ->
                    chatRoom.tell(DistributedChatRoom.Leave(username))
                }
                NotUsed.getInstance()
            }
            .to(
                Sink.foreach { text: String ->
                    chatRoom.tell(DistributedChatRoom.PostMessage(username, text))
                }
            )

        return Flow.fromSinkAndSource(incoming, outgoing)
    }

    private fun toTextMessage(userMessage: DistributedChatRoom.UserMessage): Message {
        val json = when (userMessage) {
            is DistributedChatRoom.ChatMessage -> objectMapper.writeValueAsString(
                mapOf(
                    "type" to "chat",
                    "username" to userMessage.username,
                    "message" to userMessage.message,
                    "nodeAddress" to userMessage.nodeAddress,
                    "timestamp" to userMessage.timestamp
                )
            )

            is DistributedChatRoom.SystemMessage -> objectMapper.writeValueAsString(
                mapOf(
                    "type" to "system",
                    "message" to userMessage.message,
                    "timestamp" to userMessage.timestamp
                )
            )

            is DistributedChatRoom.UserList -> objectMapper.writeValueAsString(
                mapOf(
                    "type" to "users",
                    "users" to userMessage.users
                )
            )
        }
        return TextMessage.create(json)
    }

    private fun getUsers(): CompletionStage<DistributedChatRoom.UserList> {
        return AskPattern.ask(
            chatRoom,
            { replyTo: ActorRef<DistributedChatRoom.UserList> -> DistributedChatRoom.GetUsers(replyTo) },
            askTimeout,
            system.scheduler()
        )
    }
}
