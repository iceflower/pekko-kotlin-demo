package com.example.pekko.spring.websocket.cluster

import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.javadsl.Receive
import org.apache.pekko.actor.typed.pubsub.Topic
import org.apache.pekko.cluster.typed.Cluster

/**
 * 클러스터 전체에서 메시지를 공유하는 분산 채팅방.
 * Pekko Distributed PubSub(Topic)을 사용하여 노드 간 메시지 브로드캐스트.
 */
class DistributedChatRoom private constructor(
    context: ActorContext<Command>,
    private val topic: ActorRef<Topic.Command<ChatEvent>>
) : AbstractBehavior<DistributedChatRoom.Command>(context) {

    companion object {
        const val TOPIC_NAME = "spring-chat-room"

        fun create(topic: ActorRef<Topic.Command<ChatEvent>>): Behavior<Command> =
            Behaviors.setup { context ->
                context.log.info("분산 채팅방 생성됨")
                DistributedChatRoom(context, topic)
            }
    }

    // 외부 명령
    sealed interface Command : CborSerializable

    data class Join(
        val username: String,
        val sessionId: String
    ) : Command

    data class Leave(val sessionId: String) : Command

    data class PostMessage(
        val sessionId: String,
        val message: String
    ) : Command

    data class GetUsers(val replyTo: ActorRef<UserList>) : Command

    // 클러스터를 통해 전파되는 이벤트
    sealed interface ChatEvent : CborSerializable

    data class UserJoined(
        val username: String,
        val sessionId: String,
        val nodeAddress: String
    ) : ChatEvent

    data class UserLeft(
        val sessionId: String,
        val nodeAddress: String
    ) : ChatEvent

    data class BroadcastMessage(
        val username: String,
        val message: String,
        val nodeAddress: String,
        val timestamp: Long = System.currentTimeMillis()
    ) : ChatEvent

    // 내부 명령
    private data class ChatEventReceived(val event: ChatEvent) : Command

    // 응답 타입
    data class UserList(val users: List<UserInfo>) : CborSerializable
    data class UserInfo(
        val username: String,
        val sessionId: String,
        val nodeAddress: String
    ) : CborSerializable

    // 메시지 리스너 인터페이스 (Spring WebSocket과 연동용)
    interface MessageListener {
        fun onChatMessage(username: String, message: String, nodeAddress: String, timestamp: Long)
        fun onSystemMessage(message: String)
    }

    // 로컬 세션만 관리
    private val localSessions = mutableMapOf<String, String>() // sessionId -> username
    private val allUsers = mutableMapOf<String, UserInfo>()
    private val cluster = Cluster.get(context.system)
    private val nodeAddress = cluster.selfMember().address().toString()

    // 메시지 리스너들 (Spring WebSocket 핸들러에서 등록)
    private val listeners = mutableListOf<MessageListener>()

    init {
        // Topic 구독
        val chatEventAdapter = context.messageAdapter(ChatEvent::class.java) { ChatEventReceived(it) }
        topic.tell(Topic.subscribe(chatEventAdapter))
    }

    fun addListener(listener: MessageListener) {
        listeners.add(listener)
    }

    override fun createReceive(): Receive<Command> = newReceiveBuilder()
        .onMessage(Join::class.java, this::onJoin)
        .onMessage(Leave::class.java, this::onLeave)
        .onMessage(PostMessage::class.java, this::onPostMessage)
        .onMessage(GetUsers::class.java, this::onGetUsers)
        .onMessage(ChatEventReceived::class.java, this::onChatEvent)
        .build()

    private fun onJoin(cmd: Join): Behavior<Command> {
        localSessions[cmd.sessionId] = cmd.username
        allUsers[cmd.sessionId] = UserInfo(cmd.username, cmd.sessionId, nodeAddress)

        context.log.info("사용자 '{}' 가입 (세션: {}, 노드: {})", cmd.username, cmd.sessionId, nodeAddress)

        // 클러스터 전체에 가입 알림
        topic.tell(Topic.publish(UserJoined(cmd.username, cmd.sessionId, nodeAddress)))

        return this
    }

    private fun onLeave(cmd: Leave): Behavior<Command> {
        val username = localSessions.remove(cmd.sessionId)
        if (username != null) {
            allUsers.remove(cmd.sessionId)
            context.log.info("사용자 '{}' 퇴장 (세션: {})", username, cmd.sessionId)

            // 클러스터 전체에 퇴장 알림
            topic.tell(Topic.publish(UserLeft(cmd.sessionId, nodeAddress)))
        }
        return this
    }

    private fun onPostMessage(cmd: PostMessage): Behavior<Command> {
        val username = localSessions[cmd.sessionId]
        if (username == null) {
            context.log.warn("알 수 없는 세션에서 메시지: {}", cmd.sessionId)
            return this
        }

        // 클러스터 전체에 메시지 브로드캐스트
        topic.tell(Topic.publish(BroadcastMessage(username, cmd.message, nodeAddress)))

        return this
    }

    private fun onGetUsers(cmd: GetUsers): Behavior<Command> {
        cmd.replyTo.tell(UserList(allUsers.values.toList()))
        return this
    }

    private fun onChatEvent(cmd: ChatEventReceived): Behavior<Command> {
        when (val event = cmd.event) {
            is UserJoined -> {
                if (!allUsers.containsKey(event.sessionId)) {
                    allUsers[event.sessionId] = UserInfo(event.username, event.sessionId, event.nodeAddress)
                }
                notifySystemMessage("${event.username} 님이 채팅에 참여했습니다 (${event.nodeAddress})")
            }

            is UserLeft -> {
                val user = allUsers.remove(event.sessionId)
                if (user != null) {
                    notifySystemMessage("${user.username} 님이 채팅을 떠났습니다")
                }
            }

            is BroadcastMessage -> {
                notifyChatMessage(event.username, event.message, event.nodeAddress, event.timestamp)
            }
        }
        return this
    }

    private fun notifyChatMessage(username: String, message: String, nodeAddress: String, timestamp: Long) {
        listeners.forEach { it.onChatMessage(username, message, nodeAddress, timestamp) }
    }

    private fun notifySystemMessage(message: String) {
        listeners.forEach { it.onSystemMessage(message) }
    }
}
