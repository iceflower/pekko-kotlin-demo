package com.example.pekko.spring.optionalcluster.controller

import com.example.pekko.spring.optionalcluster.actor.Counter
import com.example.pekko.spring.optionalcluster.config.PekkoProperties
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.Scheduler
import org.apache.pekko.actor.typed.javadsl.AskPattern
import org.apache.pekko.cluster.typed.Cluster
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.*
import scala.jdk.javaapi.CollectionConverters
import java.time.Duration
import java.util.concurrent.CompletionStage

/**
 * REST controller for counter operations.
 * Works in both standalone and cluster modes.
 */
@RestController
@RequestMapping("/api/counter")
class CounterController(
    private val counterActor: ActorRef<Counter.Command>,
    private val scheduler: Scheduler,
    private val properties: PekkoProperties
) {
    private val askTimeout = Duration.ofSeconds(5)

    @GetMapping
    fun getCounter(): CompletionStage<CounterResponse> {
        return AskPattern.ask(
            counterActor,
            { replyTo: ActorRef<Counter.CountResponse> -> Counter.GetCount(replyTo) },
            askTimeout,
            scheduler
        ).thenApply { response ->
            CounterResponse(
                value = response.count,
                message = "Counter value retrieved",
                mode = if (properties.cluster.enabled) "cluster" else "standalone"
            )
        }
    }

    @PostMapping("/increment")
    fun incrementCounter(@RequestParam(defaultValue = "1") delta: Long): CounterResponse {
        counterActor.tell(Counter.Increment(delta))
        return CounterResponse(
            value = delta,
            message = "Increment command sent",
            mode = if (properties.cluster.enabled) "cluster" else "standalone"
        )
    }

    @PostMapping("/decrement")
    fun decrementCounter(@RequestParam(defaultValue = "1") delta: Long): CounterResponse {
        counterActor.tell(Counter.Decrement(delta))
        return CounterResponse(
            value = delta,
            message = "Decrement command sent",
            mode = if (properties.cluster.enabled) "cluster" else "standalone"
        )
    }

    @PostMapping("/reset")
    fun resetCounter(): CounterResponse {
        counterActor.tell(Counter.Reset)
        return CounterResponse(
            value = 0,
            message = "Reset command sent",
            mode = if (properties.cluster.enabled) "cluster" else "standalone"
        )
    }

    @GetMapping("/mode")
    fun getMode(): ModeResponse {
        return ModeResponse(
            clusterEnabled = properties.cluster.enabled,
            mode = if (properties.cluster.enabled) "cluster" else "standalone",
            description = if (properties.cluster.enabled)
                "Counter is running as a ClusterSingleton"
            else
                "Counter is running as a standalone actor"
        )
    }

    data class CounterResponse(
        val value: Long,
        val message: String,
        val mode: String
    )

    data class ModeResponse(
        val clusterEnabled: Boolean,
        val mode: String,
        val description: String
    )
}

/**
 * Cluster status endpoint - only available in cluster mode.
 */
@RestController
@RequestMapping("/api/cluster")
@ConditionalOnProperty(name = ["pekko.cluster.enabled"], havingValue = "true")
class ClusterStatusController(
    private val system: ActorSystem<Void>
) {
    @GetMapping("/status")
    fun getClusterStatus(): ClusterStatus {
        val cluster = Cluster.get(system)
        val selfMember = cluster.selfMember()
        val state = cluster.state()

        val selfRoles = CollectionConverters.asJava(selfMember.roles() as scala.collection.Iterable<String>).toList()
        val members = CollectionConverters.asJava(state.members as scala.collection.Iterable<org.apache.pekko.cluster.Member>).map { member ->
            MemberInfo(
                address = member.address().toString(),
                status = member.status().toString(),
                roles = CollectionConverters.asJava(member.roles() as scala.collection.Iterable<String>).toList()
            )
        }
        val unreachable = CollectionConverters.asJava(state.unreachable as scala.collection.Iterable<org.apache.pekko.cluster.Member>).map { it.address().toString() }

        return ClusterStatus(
            selfAddress = selfMember.address().toString(),
            selfRoles = selfRoles,
            selfStatus = selfMember.status().toString(),
            members = members,
            unreachable = unreachable
        )
    }

    data class ClusterStatus(
        val selfAddress: String,
        val selfRoles: List<String>,
        val selfStatus: String,
        val members: List<MemberInfo>,
        val unreachable: List<String>
    )

    data class MemberInfo(
        val address: String,
        val status: String,
        val roles: List<String>
    )
}
