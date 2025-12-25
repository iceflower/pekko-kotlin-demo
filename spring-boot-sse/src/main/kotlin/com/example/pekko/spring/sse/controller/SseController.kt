package com.example.pekko.spring.sse.controller

import com.example.pekko.spring.sse.actor.EventPublisher
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.javadsl.AskPattern
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.codec.ServerSentEvent
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CompletionStage

/**
 * REST controller for SSE endpoints.
 * Bridges Spring WebFlux SSE with Pekko EventPublisher actor.
 */
@RestController
@RequestMapping("/api")
class SseController(
    private val eventPublisher: ActorRef<EventPublisher.Command>,
    private val actorSystem: ActorSystem<Void>,
    private val objectMapper: ObjectMapper
) {

    private val logger = LoggerFactory.getLogger(SseController::class.java)

    /**
     * SSE endpoint - clients connect here to receive events.
     */
    @GetMapping("/events", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun streamEvents(): Flux<ServerSentEvent<String>> {
        val subscriberId = UUID.randomUUID().toString()
        val sink = Sinks.many().multicast().onBackpressureBuffer<EventPublisher.Event>()

        // Subscribe to EventPublisher actor
        eventPublisher.tell(EventPublisher.Subscribe(subscriberId) { event ->
            sink.tryEmitNext(event)
        })

        logger.info("SSE client connected: {}", subscriberId)

        return sink.asFlux()
            .map { event ->
                ServerSentEvent.builder<String>()
                    .id(event.id)
                    .event(event.type)
                    .data(objectMapper.writeValueAsString(mapOf(
                        "data" to event.data,
                        "timestamp" to event.timestamp
                    )))
                    .build()
            }
            .doOnCancel {
                logger.info("SSE client disconnected: {}", subscriberId)
                eventPublisher.tell(EventPublisher.Unsubscribe(subscriberId))
            }
            .doOnError { e ->
                logger.error("SSE stream error for {}: {}", subscriberId, e.message)
                eventPublisher.tell(EventPublisher.Unsubscribe(subscriberId))
            }
    }

    /**
     * Publish an event to all subscribers.
     */
    @PostMapping("/publish")
    fun publishEvent(
        @RequestParam type: String,
        @RequestBody data: String
    ): ResponseEntity<Map<String, Any>> {
        eventPublisher.tell(EventPublisher.Publish(type, data))
        logger.info("Event published: type={}", type)

        return ResponseEntity.ok(mapOf(
            "status" to "published",
            "type" to type,
            "timestamp" to System.currentTimeMillis()
        ))
    }

    /**
     * Get publisher statistics.
     */
    @GetMapping("/stats")
    fun getStats(): CompletionStage<ResponseEntity<Map<String, Any>>> {
        return AskPattern.ask(
            eventPublisher,
            { replyTo: ActorRef<EventPublisher.Stats> -> EventPublisher.GetStats(replyTo) },
            Duration.ofSeconds(3),
            actorSystem.scheduler()
        ).thenApply { stats ->
            ResponseEntity.ok(mapOf(
                "subscriberCount" to stats.subscriberCount,
                "totalEventsPublished" to stats.totalEventsPublished
            ))
        }
    }
}
