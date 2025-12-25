package com.example.pekko.sse

import org.apache.pekko.NotUsed
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.AskPattern
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.http.javadsl.model.sse.ServerSentEvent
import org.apache.pekko.http.javadsl.server.AllDirectives
import org.apache.pekko.http.javadsl.server.Route
import org.apache.pekko.japi.Pair
import org.apache.pekko.stream.javadsl.BroadcastHub
import org.apache.pekko.stream.javadsl.Keep
import org.apache.pekko.stream.javadsl.MergeHub
import org.apache.pekko.stream.javadsl.Sink
import org.apache.pekko.stream.javadsl.Source
import java.time.Duration
import java.util.concurrent.CompletionStage

/**
 * SSE routes using Pekko HTTP.
 * Uses MergeHub/BroadcastHub for event streaming.
 */
class SseRoutes(
    private val publisher: ActorRef<EventPublisher.Command>,
    private val system: ActorSystem<*>
) : AllDirectives() {

    private val askTimeout = Duration.ofSeconds(3)

    // Create a shared broadcast hub for SSE events
    private val broadcastPair: Pair<Sink<EventPublisher.Event, NotUsed>, Source<EventPublisher.Event, NotUsed>> = run {
        MergeHub.of(EventPublisher.Event::class.java, 256)
            .toMat(BroadcastHub.of(EventPublisher.Event::class.java, 256), Keep.both())
            .run(system)
    }

    private val eventSink: Sink<EventPublisher.Event, NotUsed> = broadcastPair.first()
    private val eventSource: Source<EventPublisher.Event, NotUsed> = broadcastPair.second()

    // Actor that forwards events from EventPublisher to the broadcast hub
    private val bridgeActor: ActorRef<EventPublisher.Event> = run {
        val bridgeBehavior: Behavior<EventPublisher.Event> = Behaviors.setup { _ ->
            Behaviors.receiveMessage { event ->
                eventSink.runWith(Source.single(event), system)
                Behaviors.same()
            }
        }
        system.systemActorOf(
            bridgeBehavior,
            "sse-bridge-${System.currentTimeMillis()}",
            org.apache.pekko.actor.typed.Props.empty()
        )
    }

    init {
        // Subscribe the bridge actor to receive all events
        publisher.tell(EventPublisher.Subscribe("bridge", bridgeActor))
    }

    fun routes(): Route = concat(
        // SSE endpoint - clients subscribe here
        path("events") {
            get {
                completeOK(
                    createEventSource(),
                    org.apache.pekko.http.javadsl.marshalling.sse.EventStreamMarshalling.toEventStream()
                )
            }
        },

        // Publish event via REST
        pathPrefix("api") {
            concat(
                path("publish") {
                    post {
                        parameter("type") { eventType ->
                            entity(org.apache.pekko.http.javadsl.unmarshalling.Unmarshaller.entityToString()) { data ->
                                publisher.tell(EventPublisher.Publish(eventType, data))
                                complete("Event published")
                            }
                        }
                    }
                },
                path("stats") {
                    get {
                        onSuccess(getStats()) { stats ->
                            complete("""{"subscribers":${stats.subscriberCount},"totalEvents":${stats.totalEventsPublished}}""")
                        }
                    }
                }
            )
        },

        // Static HTML page
        pathSingleSlash {
            getFromResource("static/index.html")
        }
    )

    /**
     * Creates an SSE source from the broadcast hub.
     */
    private fun createEventSource(): Source<ServerSentEvent, NotUsed> {
        return eventSource
            .map { event: EventPublisher.Event ->
                when (event) {
                    is EventPublisher.DataEvent -> ServerSentEvent.create(
                        """{"data":"${escapeJson(event.data)}","timestamp":${event.timestamp}}""",
                        java.util.Optional.of(event.eventType),
                        java.util.Optional.of(event.id),
                        java.util.OptionalInt.empty()
                    )

                    is EventPublisher.HeartbeatEvent -> ServerSentEvent.create(
                        "",
                        java.util.Optional.of("heartbeat"),
                        java.util.Optional.of(event.id),
                        java.util.OptionalInt.empty()
                    )

                    else -> ServerSentEvent.create("unknown event")
                }
            }
    }

    private fun getStats(): CompletionStage<EventPublisher.Stats> {
        return AskPattern.ask(
            publisher,
            { replyTo: ActorRef<EventPublisher.Stats> -> EventPublisher.GetStats(replyTo) },
            askTimeout,
            system.scheduler()
        )
    }

    private fun escapeJson(text: String): String =
        text.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
}
