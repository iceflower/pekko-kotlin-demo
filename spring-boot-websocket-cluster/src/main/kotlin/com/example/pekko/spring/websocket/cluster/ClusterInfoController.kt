package com.example.pekko.spring.websocket.cluster

import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.javadsl.AskPattern
import org.apache.pekko.cluster.typed.Cluster
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Duration
import java.util.concurrent.CompletableFuture

/**
 * 클러스터 정보 및 사용자 목록 REST API.
 */
@RestController
@RequestMapping("/api")
class ClusterInfoController(
    private val chatRoom: ActorRef<DistributedChatRoom.Command>,
    private val actorSystem: ActorSystem<Nothing>
) {

    @GetMapping("/cluster-info")
    fun getClusterInfo(): Map<String, Any> {
        val cluster = Cluster.get(actorSystem)
        return mapOf(
            "selfAddress" to cluster.selfMember().address().toString(),
            "roles" to cluster.selfMember().roles.toList(),
            "state" to cluster.selfMember().status().toString()
        )
    }

    @GetMapping("/users")
    fun getUsers(): CompletableFuture<List<DistributedChatRoom.UserInfo>> {
        val future = AskPattern.ask(
            chatRoom,
            { replyTo: ActorRef<DistributedChatRoom.UserList> -> DistributedChatRoom.GetUsers(replyTo) },
            Duration.ofSeconds(3),
            actorSystem.scheduler()
        )
        return future.toCompletableFuture().thenApply { it.users }
    }
}
