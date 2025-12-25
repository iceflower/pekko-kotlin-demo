package com.example.pekko.sse.cluster

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.apache.pekko.NotUsed
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.AskPattern
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.http.javadsl.model.HttpEntities
import org.apache.pekko.http.javadsl.model.MediaTypes
import org.apache.pekko.http.javadsl.model.sse.ServerSentEvent
import org.apache.pekko.http.javadsl.server.AllDirectives
import org.apache.pekko.http.javadsl.server.Route
import org.apache.pekko.http.javadsl.marshalling.sse.EventStreamMarshalling
import org.apache.pekko.stream.javadsl.BroadcastHub
import org.apache.pekko.stream.javadsl.Keep
import org.apache.pekko.stream.javadsl.MergeHub
import org.apache.pekko.stream.javadsl.Sink
import org.apache.pekko.stream.javadsl.Source
import java.time.Duration
import java.util.*
import java.util.concurrent.CompletionStage

/**
 * 클러스터 환경의 SSE 라우트.
 * 분산 PubSub을 통해 모든 노드의 구독자에게 이벤트 전송.
 */
class SseClusterRoutes(
    private val eventBus: ActorRef<DistributedEventBus.Command>,
    private val system: ActorSystem<*>
) : AllDirectives() {

    private val askTimeout = Duration.ofSeconds(3)
    private val objectMapper = jacksonObjectMapper()

    // SSE 이벤트를 위한 공유 브로드캐스트 허브
    private val broadcastPair: org.apache.pekko.japi.Pair<Sink<DistributedEventBus.SseEvent, NotUsed>, Source<DistributedEventBus.SseEvent, NotUsed>> =
        MergeHub.of(DistributedEventBus.SseEvent::class.java, 256)
            .toMat(BroadcastHub.of(DistributedEventBus.SseEvent::class.java, 256), Keep.both())
            .run(system)

    private val broadcastSink: Sink<DistributedEventBus.SseEvent, NotUsed> = broadcastPair.first()
    private val broadcastSource: Source<DistributedEventBus.SseEvent, NotUsed> = broadcastPair.second()

    fun routes(): Route = concat(
        // SSE 스트림 엔드포인트
        pathPrefix("events") {
            concat(
                // SSE 구독
                path("stream") {
                    get {
                        val subscriberId = UUID.randomUUID().toString()
                        handleSseSubscription(subscriberId)
                    }
                },
                // 이벤트 발행
                path("publish") {
                    post {
                        entity(jackson(Map::class.java)) { body ->
                            @Suppress("UNCHECKED_CAST")
                            val eventType = (body as Map<String, Any>)["type"]?.toString() ?: "message"
                            val data = body["data"]?.toString() ?: "{}"

                            eventBus.tell(DistributedEventBus.PublishEvent(eventType, data))
                            complete("이벤트 발행됨")
                        }
                    }
                }
            )
        },

        // REST API
        pathPrefix("api") {
            concat(
                // 구독자 목록
                path("subscribers") {
                    get {
                        onSuccess(getSubscribers()) { subscriberList ->
                            complete(objectMapper.writeValueAsString(subscriberList.subscribers))
                        }
                    }
                },
                // 클러스터 통계
                path("stats") {
                    get {
                        onSuccess(getClusterStats()) { stats ->
                            complete(objectMapper.writeValueAsString(stats))
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
     * SSE 구독 처리.
     */
    private fun handleSseSubscription(subscriberId: String): Route {
        // 구독자용 액터 생성
        val subscriberBehavior: Behavior<DistributedEventBus.SseEvent> = Behaviors.setup { _ ->
            Behaviors.receiveMessage { event ->
                broadcastSink.runWith(Source.single(event), system)
                Behaviors.same()
            }
        }

        val subscriberRef = system.systemActorOf(
            subscriberBehavior,
            "sse-subscriber-$subscriberId",
            org.apache.pekko.actor.typed.Props.empty()
        )

        // 이벤트 버스에 구독 등록
        eventBus.tell(DistributedEventBus.Subscribe(subscriberId, subscriberRef))

        // SSE 스트림 생성
        val sseSource: Source<ServerSentEvent, NotUsed> = broadcastSource
            .map { event ->
                val data = objectMapper.writeValueAsString(
                    mapOf(
                        "data" to event.data,
                        "nodeAddress" to event.nodeAddress,
                        "timestamp" to event.timestamp
                    )
                )
                ServerSentEvent.create(data, event.eventType, event.id)
            }
            .watchTermination { _, done ->
                done.whenComplete { _, _ ->
                    eventBus.tell(DistributedEventBus.Unsubscribe(subscriberId))
                }
                NotUsed.getInstance()
            }

        return completeOK(sseSource, EventStreamMarshalling.toEventStream())
    }

    private fun <T> jackson(clazz: Class<T>): org.apache.pekko.http.javadsl.unmarshalling.Unmarshaller<org.apache.pekko.http.javadsl.model.HttpEntity, T> {
        return org.apache.pekko.http.javadsl.unmarshalling.Unmarshaller.entityToString()
            .thenApply { str -> objectMapper.readValue(str, clazz) }
    }

    private fun getSubscribers(): CompletionStage<DistributedEventBus.SubscriberList> {
        return AskPattern.ask(
            eventBus,
            { replyTo: ActorRef<DistributedEventBus.SubscriberList> -> DistributedEventBus.GetSubscribers(replyTo) },
            askTimeout,
            system.scheduler()
        )
    }

    private fun getClusterStats(): CompletionStage<DistributedEventBus.ClusterStats> {
        return AskPattern.ask(
            eventBus,
            { replyTo: ActorRef<DistributedEventBus.ClusterStats> -> DistributedEventBus.GetClusterStats(replyTo) },
            askTimeout,
            system.scheduler()
        )
    }
}
