package com.example.pekko.sse.cluster

import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.javadsl.Receive
import org.apache.pekko.actor.typed.pubsub.Topic
import org.apache.pekko.cluster.typed.Cluster

/**
 * 클러스터 전체에서 이벤트를 공유하는 분산 이벤트 버스.
 * Pekko Distributed PubSub(Topic)을 사용하여 노드 간 SSE 이벤트 브로드캐스트.
 */
class DistributedEventBus private constructor(
    context: ActorContext<Command>,
    private val topic: ActorRef<Topic.Command<ClusterEvent>>
) : AbstractBehavior<DistributedEventBus.Command>(context) {

    companion object {
        const val TOPIC_NAME = "sse-events"

        fun create(topic: ActorRef<Topic.Command<ClusterEvent>>): Behavior<Command> =
            Behaviors.setup { context ->
                context.log.info("분산 이벤트 버스 생성됨")
                DistributedEventBus(context, topic)
            }
    }

    // 외부 명령 (로컬 노드에서 수신)
    sealed interface Command : CborSerializable

    data class Subscribe(
        val subscriberId: String,
        val subscriber: ActorRef<SseEvent>
    ) : Command

    data class Unsubscribe(val subscriberId: String) : Command

    data class PublishEvent(
        val eventType: String,
        val data: String
    ) : Command

    data class GetSubscribers(val replyTo: ActorRef<SubscriberList>) : Command
    data class GetClusterStats(val replyTo: ActorRef<ClusterStats>) : Command

    // 클러스터를 통해 전파되는 이벤트
    sealed interface ClusterEvent : CborSerializable

    data class BroadcastEvent(
        val eventType: String,
        val data: String,
        val nodeAddress: String,
        val timestamp: Long = System.currentTimeMillis()
    ) : ClusterEvent

    data class NodeStats(
        val nodeAddress: String,
        val subscriberCount: Int
    ) : ClusterEvent

    // 내부 명령 (Topic에서 수신된 이벤트 래핑)
    private data class ClusterEventReceived(val event: ClusterEvent) : Command

    // 구독자에게 전송되는 SSE 이벤트
    data class SseEvent(
        val id: String,
        val eventType: String,
        val data: String,
        val nodeAddress: String,
        val timestamp: Long = System.currentTimeMillis()
    ) : CborSerializable

    data class SubscriberList(val subscribers: List<SubscriberInfo>) : CborSerializable
    data class SubscriberInfo(val id: String, val nodeAddress: String) : CborSerializable
    data class ClusterStats(
        val totalSubscribers: Int,
        val localSubscribers: Int,
        val nodeAddress: String
    ) : CborSerializable

    private val localSubscribers = mutableMapOf<String, ActorRef<SseEvent>>()
    private val cluster = Cluster.get(context.system)
    private val nodeAddress = cluster.selfMember().address().toString()
    private var eventCounter = 0L

    init {
        // Topic 구독
        val clusterEventAdapter = context.messageAdapter(ClusterEvent::class.java) { ClusterEventReceived(it) }
        topic.tell(Topic.subscribe(clusterEventAdapter))
    }

    override fun createReceive(): Receive<Command> = newReceiveBuilder()
        .onMessage(Subscribe::class.java, this::onSubscribe)
        .onMessage(Unsubscribe::class.java, this::onUnsubscribe)
        .onMessage(PublishEvent::class.java, this::onPublishEvent)
        .onMessage(GetSubscribers::class.java, this::onGetSubscribers)
        .onMessage(GetClusterStats::class.java, this::onGetClusterStats)
        .onMessage(ClusterEventReceived::class.java, this::onClusterEvent)
        .build()

    private fun onSubscribe(cmd: Subscribe): Behavior<Command> {
        localSubscribers[cmd.subscriberId] = cmd.subscriber
        context.log.info("구독자 추가: {} (노드: {}). 로컬 구독자 수: {}", cmd.subscriberId, nodeAddress, localSubscribers.size)

        // 연결 환영 메시지 전송
        cmd.subscriber.tell(
            SseEvent(
                id = generateEventId(),
                eventType = "connected",
                data = """{"subscriberId":"${cmd.subscriberId}","nodeAddress":"$nodeAddress"}""",
                nodeAddress = nodeAddress
            )
        )

        return this
    }

    private fun onUnsubscribe(cmd: Unsubscribe): Behavior<Command> {
        localSubscribers.remove(cmd.subscriberId)
        context.log.info("구독자 제거: {}. 로컬 구독자 수: {}", cmd.subscriberId, localSubscribers.size)
        return this
    }

    private fun onPublishEvent(cmd: PublishEvent): Behavior<Command> {
        context.log.debug("이벤트 발행: {} - {}", cmd.eventType, cmd.data)

        // 클러스터 전체에 이벤트 브로드캐스트
        topic.tell(Topic.publish(BroadcastEvent(cmd.eventType, cmd.data, nodeAddress)))

        return this
    }

    private fun onGetSubscribers(cmd: GetSubscribers): Behavior<Command> {
        val subscribers = localSubscribers.keys.map { SubscriberInfo(it, nodeAddress) }
        cmd.replyTo.tell(SubscriberList(subscribers))
        return this
    }

    private fun onGetClusterStats(cmd: GetClusterStats): Behavior<Command> {
        cmd.replyTo.tell(
            ClusterStats(
                totalSubscribers = localSubscribers.size,
                localSubscribers = localSubscribers.size,
                nodeAddress = nodeAddress
            )
        )
        return this
    }

    private fun onClusterEvent(cmd: ClusterEventReceived): Behavior<Command> {
        when (val event = cmd.event) {
            is BroadcastEvent -> {
                // 모든 로컬 구독자에게 이벤트 전달
                val sseEvent = SseEvent(
                    id = generateEventId(),
                    eventType = event.eventType,
                    data = event.data,
                    nodeAddress = event.nodeAddress,
                    timestamp = event.timestamp
                )
                localSubscribers.values.forEach { it.tell(sseEvent) }
            }

            is NodeStats -> {
                context.log.debug("노드 통계 수신: {} - {} 구독자", event.nodeAddress, event.subscriberCount)
            }
        }
        return this
    }

    private fun generateEventId(): String {
        eventCounter++
        return "$nodeAddress-$eventCounter"
    }
}
