package com.example.pekko.spring.sse.cluster

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.javadsl.AskPattern
import org.apache.pekko.cluster.typed.Cluster
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.time.Duration
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/**
 * 클러스터 환경의 SSE 컨트롤러.
 */
@RestController
class SseController(
    private val eventBus: ActorRef<DistributedEventBus.Command>,
    private val actorSystem: ActorSystem<Nothing>
) {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val objectMapper = jacksonObjectMapper()
    private val emitters = ConcurrentHashMap<String, SseEmitter>()

    /**
     * SSE 이벤트 스트림 구독.
     */
    @GetMapping("/events/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun subscribe(): SseEmitter {
        val subscriberId = UUID.randomUUID().toString()
        val emitter = SseEmitter(0L) // 무제한 타임아웃

        emitters[subscriberId] = emitter

        // 콜백 등록
        val callback: (DistributedEventBus.SseEvent) -> Unit = { event ->
            try {
                val data = objectMapper.writeValueAsString(
                    mapOf(
                        "data" to event.data,
                        "nodeAddress" to event.nodeAddress,
                        "timestamp" to event.timestamp
                    )
                )
                emitter.send(
                    SseEmitter.event()
                        .id(event.id)
                        .name(event.eventType)
                        .data(data)
                )
            } catch (e: Exception) {
                logger.debug("SSE 전송 실패: {}", e.message)
                emitters.remove(subscriberId)
            }
        }

        eventBus.tell(DistributedEventBus.Subscribe(subscriberId, callback))

        emitter.onCompletion {
            emitters.remove(subscriberId)
            eventBus.tell(DistributedEventBus.Unsubscribe(subscriberId))
            logger.info("SSE 연결 종료: {}", subscriberId)
        }

        emitter.onTimeout {
            emitters.remove(subscriberId)
            eventBus.tell(DistributedEventBus.Unsubscribe(subscriberId))
            logger.info("SSE 연결 타임아웃: {}", subscriberId)
        }

        emitter.onError { ex ->
            emitters.remove(subscriberId)
            eventBus.tell(DistributedEventBus.Unsubscribe(subscriberId))
            logger.debug("SSE 연결 오류: {} - {}", subscriberId, ex.message)
        }

        logger.info("SSE 연결 시작: {}", subscriberId)
        return emitter
    }

    /**
     * 이벤트 발행.
     */
    @PostMapping("/events/publish")
    fun publish(@RequestBody body: Map<String, String>): Map<String, String> {
        val eventType = body["type"] ?: "message"
        val data = body["data"] ?: "{}"

        eventBus.tell(DistributedEventBus.PublishEvent(eventType, data))

        return mapOf("status" to "published", "type" to eventType)
    }

    /**
     * 구독자 목록 조회.
     */
    @GetMapping("/api/subscribers")
    fun getSubscribers(): CompletableFuture<List<DistributedEventBus.SubscriberInfo>> {
        val future = AskPattern.ask(
            eventBus,
            { replyTo: ActorRef<DistributedEventBus.SubscriberList> -> DistributedEventBus.GetSubscribers(replyTo) },
            Duration.ofSeconds(3),
            actorSystem.scheduler()
        )
        return future.toCompletableFuture().thenApply { it.subscribers }
    }

    /**
     * 클러스터 통계 조회.
     */
    @GetMapping("/api/stats")
    fun getStats(): CompletableFuture<DistributedEventBus.ClusterStats> {
        val future = AskPattern.ask(
            eventBus,
            { replyTo: ActorRef<DistributedEventBus.ClusterStats> -> DistributedEventBus.GetClusterStats(replyTo) },
            Duration.ofSeconds(3),
            actorSystem.scheduler()
        )
        return future.toCompletableFuture()
    }

    /**
     * 클러스터 정보 조회.
     */
    @GetMapping("/api/cluster-info")
    fun getClusterInfo(): Map<String, Any> {
        val cluster = Cluster.get(actorSystem)
        return mapOf(
            "selfAddress" to cluster.selfMember().address().toString(),
            "roles" to cluster.selfMember().roles.toList(),
            "state" to cluster.selfMember().status().toString()
        )
    }
}
