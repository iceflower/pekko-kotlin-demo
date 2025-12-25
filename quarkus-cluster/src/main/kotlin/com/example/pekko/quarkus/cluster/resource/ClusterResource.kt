package com.example.pekko.quarkus.cluster.resource

import com.example.pekko.quarkus.cluster.actor.SingletonCounter
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.Scheduler
import org.apache.pekko.actor.typed.javadsl.AskPattern
import org.apache.pekko.cluster.typed.Cluster
import scala.jdk.javaapi.CollectionConverters
import java.time.Duration
import java.util.concurrent.CompletionStage

@Path("/api/cluster")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class ClusterResource {

    @Inject
    lateinit var system: ActorSystem<Void>

    @Inject
    lateinit var scheduler: Scheduler

    @Inject
    lateinit var singletonCounter: ActorRef<SingletonCounter.Command>

    private val askTimeout = Duration.ofSeconds(5)

    /**
     * Get cluster status information
     */
    @GET
    @Path("/status")
    fun getClusterStatus(): ClusterStatus {
        val cluster = Cluster.get(system)
        val selfMember = cluster.selfMember()
        val state = cluster.state()

        val selfRoles = CollectionConverters.asJava(selfMember.roles() as scala.collection.Iterable<String>).toList()
        val leader: String? = null  // Leader information requires Java API accessor
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
            leader = leader,
            members = members,
            unreachable = unreachable
        )
    }

    /**
     * Get current counter value from singleton
     */
    @GET
    @Path("/counter")
    fun getCounter(): CompletionStage<CounterResponse> {
        return AskPattern.ask(
            singletonCounter,
            { replyTo: ActorRef<SingletonCounter.CountResponse> ->
                SingletonCounter.GetCount(replyTo)
            },
            askTimeout,
            scheduler
        ).thenApply { response ->
            CounterResponse(response.count, "Counter value retrieved")
        }
    }

    /**
     * Increment counter
     */
    @POST
    @Path("/counter/increment")
    fun incrementCounter(@QueryParam("delta") @DefaultValue("1") delta: Long): CounterResponse {
        singletonCounter.tell(SingletonCounter.Increment(delta))
        return CounterResponse(delta, "Increment command sent")
    }

    /**
     * Decrement counter
     */
    @POST
    @Path("/counter/decrement")
    fun decrementCounter(@QueryParam("delta") @DefaultValue("1") delta: Long): CounterResponse {
        singletonCounter.tell(SingletonCounter.Decrement(delta))
        return CounterResponse(delta, "Decrement command sent")
    }

    /**
     * Reset counter
     */
    @POST
    @Path("/counter/reset")
    fun resetCounter(): CounterResponse {
        singletonCounter.tell(SingletonCounter.Reset)
        return CounterResponse(0, "Reset command sent")
    }

    // DTOs
    data class ClusterStatus(
        val selfAddress: String,
        val selfRoles: List<String>,
        val selfStatus: String,
        val leader: String?,
        val members: List<MemberInfo>,
        val unreachable: List<String>
    )

    data class MemberInfo(
        val address: String,
        val status: String,
        val roles: List<String>
    )

    data class CounterResponse(
        val value: Long,
        val message: String
    )
}
