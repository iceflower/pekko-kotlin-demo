package com.example.pekko.websocket

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.http.javadsl.Http
import org.apache.pekko.http.javadsl.ServerBinding
import java.util.concurrent.CompletionStage

/**
 * Main entry point for WebSocket example using Pekko HTTP.
 *
 * Features:
 * - WebSocket chat room at ws://localhost:8080/ws/chat?username=<name>
 * - REST API to get users at GET /api/users
 * - Static HTML page at /
 */
fun main() {
    val system = ActorSystem.create(Behaviors.empty<Void>(), "websocket-system")

    // Create ChatRoom actor
    val chatRoom = system.systemActorOf(ChatRoom.create(), "chat-room", org.apache.pekko.actor.typed.Props.empty())

    // Create routes
    val routes = WebSocketRoutes(chatRoom, system)

    // Start HTTP server
    val http = Http.get(system)
    val binding: CompletionStage<ServerBinding> = http.newServerAt("localhost", 8080)
        .bind(routes.routes())

    binding.thenAccept { serverBinding ->
        val address = serverBinding.localAddress()
        system.log().info("Server online at http://{}:{}/", address.hostString, address.port)
        system.log().info("WebSocket endpoint: ws://{}:{}/ws/chat?username=<name>", address.hostString, address.port)
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
