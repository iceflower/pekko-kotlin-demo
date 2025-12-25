package com.example.pekko.websocket.cluster

import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.javadsl.Receive
import org.apache.pekko.cluster.ClusterEvent
import org.apache.pekko.cluster.typed.Cluster
import org.apache.pekko.cluster.typed.Subscribe

/**
 * 클러스터 이벤트를 수신하고 로깅하는 Actor.
 */
object ClusterListener {

    sealed interface Event
    private data class MemberChange(val event: ClusterEvent.MemberEvent) : Event
    private data class ReachabilityChange(val event: ClusterEvent.ReachabilityEvent) : Event

    fun create(): Behavior<Event> = Behaviors.setup { context ->
        ClusterListenerBehavior(context)
    }

    private class ClusterListenerBehavior(
        context: ActorContext<Event>
    ) : AbstractBehavior<Event>(context) {

        private val cluster = Cluster.get(context.system)

        init {
            context.log.info("========================================")
            context.log.info("WebSocket 클러스터 리스너 시작")
            context.log.info("노드: ${cluster.selfMember().address()}")
            context.log.info("역할: ${cluster.selfMember().roles}")
            context.log.info("========================================")

            // 멤버 이벤트 구독
            cluster.subscriptions().tell(
                Subscribe.create(
                    context.messageAdapter(ClusterEvent.MemberEvent::class.java) { MemberChange(it) },
                    ClusterEvent.MemberEvent::class.java
                )
            )

            // 도달성 이벤트 구독
            cluster.subscriptions().tell(
                Subscribe.create(
                    context.messageAdapter(ClusterEvent.ReachabilityEvent::class.java) { ReachabilityChange(it) },
                    ClusterEvent.ReachabilityEvent::class.java
                )
            )
        }

        override fun createReceive(): Receive<Event> {
            return newReceiveBuilder()
                .onMessage(MemberChange::class.java, this::onMemberChange)
                .onMessage(ReachabilityChange::class.java, this::onReachabilityChange)
                .build()
        }

        private fun onMemberChange(msg: MemberChange): Behavior<Event> {
            val event = msg.event
            when (event) {
                is ClusterEvent.MemberJoined ->
                    context.log.info("멤버 가입 중: ${event.member().address()}")
                is ClusterEvent.MemberWeaklyUp ->
                    context.log.info("멤버 약한 상태로 Up: ${event.member().address()}")
                is ClusterEvent.MemberUp ->
                    context.log.info("멤버 Up: ${event.member().address()}")
                is ClusterEvent.MemberLeft ->
                    context.log.info("멤버 탈퇴 중: ${event.member().address()}")
                is ClusterEvent.MemberExited ->
                    context.log.info("멤버 종료됨: ${event.member().address()}")
                is ClusterEvent.MemberDowned ->
                    context.log.info("멤버 다운됨: ${event.member().address()}")
                is ClusterEvent.MemberRemoved ->
                    context.log.info("멤버 제거됨: ${event.member().address()}")
                else ->
                    context.log.info("기타 멤버 이벤트: $event")
            }
            return this
        }

        private fun onReachabilityChange(msg: ReachabilityChange): Behavior<Event> {
            val event = msg.event
            when (event) {
                is ClusterEvent.UnreachableMember ->
                    context.log.warn("멤버 도달 불가: ${event.member().address()}")
                is ClusterEvent.ReachableMember ->
                    context.log.info("멤버 다시 도달 가능: ${event.member().address()}")
                else ->
                    context.log.info("도달성 이벤트: $event")
            }
            return this
        }
    }
}
