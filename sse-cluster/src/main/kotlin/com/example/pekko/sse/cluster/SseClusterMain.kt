package com.example.pekko.sse.cluster

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.pubsub.Topic
import org.apache.pekko.http.javadsl.Http
import org.apache.pekko.http.javadsl.ServerBinding
import java.util.concurrent.CompletionStage

/**
 * 클러스터 SSE 서버 진입점.
 *
 * 실행 예시:
 * - 노드 1: ./gradlew :sse-cluster:run -PCLUSTER_PORT=2551 -PHTTP_PORT=8091
 * - 노드 2: ./gradlew :sse-cluster:run -PCLUSTER_PORT=2552 -PHTTP_PORT=8092
 * - 노드 3: ./gradlew :sse-cluster:run -PCLUSTER_PORT=2553 -PHTTP_PORT=8093
 */
fun main() {
    val httpPort = System.getProperty("http.port", "8091").toInt()

    val system: ActorSystem<Nothing> = ActorSystem.create(
        rootBehavior(),
        "SseClusterSystem"
    )

    val routes = SseClusterRoutes(
        getEventBus(system),
        system
    )

    val http = Http.get(system)
    val binding: CompletionStage<ServerBinding> = http.newServerAt("0.0.0.0", httpPort)
        .bind(routes.routes())

    binding.thenAccept { serverBinding ->
        val address = serverBinding.localAddress()
        system.log().info("========================================")
        system.log().info("클러스터 SSE 서버 시작")
        system.log().info("주소: http://{}:{}/", address.hostString, address.port)
        system.log().info("SSE 스트림: http://{}:{}/events/stream", address.hostString, address.port)
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
        Topic.create(DistributedEventBus.ClusterEvent::class.java, DistributedEventBus.TOPIC_NAME),
        "sse-topic"
    )

    // 분산 이벤트 버스 생성
    context.spawn(DistributedEventBus.create(topic), "event-bus")

    Behaviors.empty()
}

@Suppress("UNCHECKED_CAST")
private fun getEventBus(system: ActorSystem<*>): org.apache.pekko.actor.typed.ActorRef<DistributedEventBus.Command> {
    return org.apache.pekko.actor.typed.ActorRefResolver.get(system)
        .resolveActorRef("pekko://SseClusterSystem/user/event-bus")
}
