package com.example.pekko.sse

import org.apache.pekko.NotUsed
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.Props
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.http.javadsl.Http
import org.apache.pekko.http.javadsl.ServerBinding
import org.apache.pekko.http.javadsl.model.ContentTypes
import org.apache.pekko.http.javadsl.model.HttpEntities
import org.apache.pekko.http.javadsl.server.AllDirectives
import org.apache.pekko.http.javadsl.server.Route
import org.apache.pekko.japi.Pair
import org.apache.pekko.stream.javadsl.BroadcastHub
import org.apache.pekko.stream.javadsl.Keep
import org.apache.pekko.stream.javadsl.MergeHub
import org.apache.pekko.stream.javadsl.Sink
import org.apache.pekko.stream.javadsl.Source
import org.apache.pekko.util.ByteString
import java.util.concurrent.CompletionStage

/**
 * Main entry point for SSE example using Pekko HTTP.
 *
 * Features:
 * - SSE endpoint at GET /events
 * - Publish events via POST /api/publish?type=<eventType>
 * - Get stats via GET /api/stats
 * - Static HTML page at /
 */
fun main() {
    val system = ActorSystem.create(Behaviors.empty<Void>(), "sse-system")

    // Create EventPublisher actor
    val publisher = system.systemActorOf(EventPublisher.create(), "event-publisher", Props.empty())

    // Create and start HTTP server with SSE support
    val server = SseServer(publisher, system)
    val binding: CompletionStage<ServerBinding> = Http.get(system)
        .newServerAt("localhost", 8081)
        .bind(server.routes())

    binding.thenAccept { serverBinding ->
        val address = serverBinding.localAddress()
        system.log().info("SSE Server online at http://{}:{}/", address.hostString, address.port)
        system.log().info("SSE endpoint: http://{}:{}/events", address.hostString, address.port)
    }.exceptionally { ex ->
        system.log().error("Failed to bind HTTP server", ex)
        system.terminate()
        null
    }

    // Shutdown hook
    Runtime.getRuntime().addShutdownHook(Thread {
        binding.thenCompose { it.unbind() }
            .thenAccept { system.terminate() }
    })
}

/**
 * SSE Server with proper streaming support using BroadcastHub.
 */
class SseServer(
    private val publisher: ActorRef<EventPublisher.Command>,
    private val system: ActorSystem<*>
) : AllDirectives() {

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
            Props.empty()
        )
    }

    init {
        // Subscribe the bridge actor to receive all events
        publisher.tell(EventPublisher.Subscribe("bridge", bridgeActor))
    }

    fun routes(): Route = concat(
        // SSE endpoint
        path("events") {
            get {
                // Convert to SSE format as ByteString
                val sseSource: Source<ByteString, NotUsed> = eventSource
                    .map { event: EventPublisher.Event -> formatSseEvent(event) }

                // Return chunked SSE response
                complete(
                    org.apache.pekko.http.javadsl.model.HttpResponse.create()
                        .withEntity(
                            HttpEntities.createChunked(
                                org.apache.pekko.http.javadsl.model.MediaTypes.TEXT_EVENT_STREAM.toContentType(),
                                sseSource
                            )
                        )
                )
            }
        },

        // Publish event
        pathPrefix("api") {
            concat(
                path("publish") {
                    post {
                        parameter("type") { eventType ->
                            entity(org.apache.pekko.http.javadsl.unmarshalling.Unmarshaller.entityToString()) { data ->
                                publisher.tell(EventPublisher.Publish(eventType, data))
                                complete("Event published: $eventType")
                            }
                        }
                    }
                },
                path("stats") {
                    get {
                        // Simple stats response
                        complete("""{"status":"ok"}""")
                    }
                }
            )
        },

        // Static page
        pathSingleSlash {
            getFromResource("static/index.html")
        }
    )

    private fun formatSseEvent(event: EventPublisher.Event): ByteString {
        val sb = StringBuilder()

        when (event) {
            is EventPublisher.DataEvent -> {
                sb.append("id: ${event.id}\n")
                sb.append("event: ${event.eventType}\n")
                sb.append("data: ${event.data}\n")
            }
            is EventPublisher.HeartbeatEvent -> {
                sb.append("id: ${event.id}\n")
                sb.append("event: heartbeat\n")
                sb.append("data: \n")
            }
            else -> {
                sb.append("event: unknown\n")
                sb.append("data: \n")
            }
        }
        sb.append("\n")

        return ByteString.fromString(sb.toString())
    }
}
