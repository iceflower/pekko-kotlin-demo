package com.example.pekko.websocket.cluster

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.pubsub.Topic
import org.apache.pekko.http.javadsl.Http
import org.apache.pekko.http.javadsl.ServerBinding
import java.util.concurrent.CompletionStage

/**
 * 클러스터 WebSocket 서버 진입점.
 *
 * 실행 예시:
 * - 노드 1: ./gradlew :websocket-cluster:run -PclusterPort=2551 -PhttpPort=8081
 * - 노드 2: ./gradlew :websocket-cluster:run -PclusterPort=2552 -PhttpPort=8082
 * - 노드 3: ./gradlew :websocket-cluster:run -PclusterPort=2553 -PhttpPort=8083
 */
fun main() {
    val httpPort = System.getProperty("http.port", "8081").toInt()

    val system: ActorSystem<Nothing> = ActorSystem.create(
        rootBehavior(),
        "WebSocketClusterSystem"
    )

    val routes = WebSocketClusterRoutes(
        getChatRoom(system),
        system
    )

    val http = Http.get(system)
    val binding: CompletionStage<ServerBinding> = http.newServerAt("0.0.0.0", httpPort)
        .bind(routes.routes())

    binding.thenAccept { serverBinding ->
        val address = serverBinding.localAddress()
        system.log().info("========================================")
        system.log().info("클러스터 WebSocket 서버 시작")
        system.log().info("주소: http://{}:{}/", address.hostString, address.port)
        system.log().info("WebSocket: ws://{}:{}/ws/chat?username=USER", address.hostString, address.port)
        system.log().info("========================================")
    }.exceptionally { ex ->
        system.log().error("서버 바인딩 실패", ex)
        system.terminate()
        null
    }
}

private fun rootBehavior(): Behavior<Nothing> = Behaviors.setup { context ->
    // 클러스터 리스너 생성
    context.spawn(ClusterListener.create(), "cluster-listener")

    // 분산 PubSub Topic 생성
    val topic = context.spawn(
        Topic.create(DistributedChatRoom.ChatEvent::class.java, DistributedChatRoom.TOPIC_NAME),
        "chat-topic"
    )

    // 분산 채팅방 생성
    context.spawn(DistributedChatRoom.create(topic), "chat-room")

    Behaviors.empty()
}

@Suppress("UNCHECKED_CAST")
private fun getChatRoom(system: ActorSystem<*>): org.apache.pekko.actor.typed.ActorRef<DistributedChatRoom.Command> {
    // Guardian actor의 자식 중 chat-room 찾기
    return org.apache.pekko.actor.typed.ActorRefResolver.get(system)
        .resolveActorRef("pekko://WebSocketClusterSystem/user/chat-room")
}
