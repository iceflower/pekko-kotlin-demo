package com.example.pekko.quarkus.cluster.actor

import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.javadsl.Receive
import org.apache.pekko.cluster.ClusterEvent
import org.apache.pekko.cluster.typed.Cluster
import org.apache.pekko.cluster.typed.Subscribe

/**
 * Actor that listens to cluster membership events.
 * Logs when members join, leave, or become unreachable.
 */
class ClusterListener private constructor(
    context: ActorContext<ClusterEvent.ClusterDomainEvent>
) : AbstractBehavior<ClusterEvent.ClusterDomainEvent>(context) {

    companion object {
        fun create(): Behavior<ClusterEvent.ClusterDomainEvent> = Behaviors.setup { context ->
            val cluster = Cluster.get(context.system)

            // Subscribe to cluster events
            cluster.subscriptions().tell(Subscribe(context.self, ClusterEvent.ClusterDomainEvent::class.java))

            context.log.info(
                "ClusterListener started. Self member: {}",
                cluster.selfMember()
            )

            ClusterListener(context)
        }
    }

    override fun createReceive(): Receive<ClusterEvent.ClusterDomainEvent> = newReceiveBuilder()
        .onMessage(ClusterEvent.MemberUp::class.java, this::onMemberUp)
        .onMessage(ClusterEvent.MemberRemoved::class.java, this::onMemberRemoved)
        .onMessage(ClusterEvent.UnreachableMember::class.java, this::onUnreachableMember)
        .onMessage(ClusterEvent.ReachableMember::class.java, this::onReachableMember)
        .onMessage(ClusterEvent.LeaderChanged::class.java, this::onLeaderChanged)
        .onAnyMessage(this::onOtherEvent)
        .build()

    private fun onMemberUp(event: ClusterEvent.MemberUp): Behavior<ClusterEvent.ClusterDomainEvent> {
        context.log.info("Member is Up: {} with roles {}", event.member().address(), event.member().roles)
        return this
    }

    private fun onMemberRemoved(event: ClusterEvent.MemberRemoved): Behavior<ClusterEvent.ClusterDomainEvent> {
        context.log.info(
            "Member is Removed: {} after {}",
            event.member().address(),
            event.previousStatus()
        )
        return this
    }

    private fun onUnreachableMember(event: ClusterEvent.UnreachableMember): Behavior<ClusterEvent.ClusterDomainEvent> {
        context.log.warn("Member detected as unreachable: {}", event.member().address())
        return this
    }

    private fun onReachableMember(event: ClusterEvent.ReachableMember): Behavior<ClusterEvent.ClusterDomainEvent> {
        context.log.info("Member is reachable again: {}", event.member().address())
        return this
    }

    private fun onLeaderChanged(event: ClusterEvent.LeaderChanged): Behavior<ClusterEvent.ClusterDomainEvent> {
        context.log.info("Leader changed event received")
        return this
    }

    private fun onOtherEvent(event: ClusterEvent.ClusterDomainEvent): Behavior<ClusterEvent.ClusterDomainEvent> {
        context.log.debug("Cluster event: {}", event)
        return this
    }
}
