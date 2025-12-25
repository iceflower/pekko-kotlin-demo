package com.example.pekko.websocket.cluster

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
        const val TOPIC_NAME = "chat-room"

        fun create(topic: ActorRef<Topic.Command<ChatEvent>>): Behavior<Command> =
            Behaviors.setup { context ->
                context.log.info("분산 채팅방 생성됨")
                DistributedChatRoom(context, topic)
            }
    }

    // 외부 명령 (로컬 노드에서 수신)
    sealed interface Command : CborSerializable

    data class Join(
        val username: String,
        val userActor: ActorRef<UserMessage>
    ) : Command

    data class Leave(val username: String) : Command

    data class PostMessage(
        val username: String,
        val message: String
    ) : Command

    data class GetUsers(val replyTo: ActorRef<UserList>) : Command

    // 클러스터를 통해 전파되는 이벤트
    sealed interface ChatEvent : CborSerializable

    data class UserJoined(
        val username: String,
        val nodeAddress: String
    ) : ChatEvent

    data class UserLeft(
        val username: String,
        val nodeAddress: String
    ) : ChatEvent

    data class BroadcastMessage(
        val username: String,
        val message: String,
        val nodeAddress: String,
        val timestamp: Long = System.currentTimeMillis()
    ) : ChatEvent

    // 내부 명령 (Topic에서 수신된 이벤트 래핑)
    private data class ChatEventReceived(val event: ChatEvent) : Command

    // 사용자에게 전송되는 메시지
    sealed interface UserMessage : CborSerializable

    data class ChatMessage(
        val username: String,
        val message: String,
        val nodeAddress: String,
        val timestamp: Long = System.currentTimeMillis()
    ) : UserMessage

    data class SystemMessage(
        val message: String,
        val timestamp: Long = System.currentTimeMillis()
    ) : UserMessage

    data class UserList(val users: List<UserInfo>) : UserMessage

    data class UserInfo(
        val username: String,
        val nodeAddress: String
    ) : CborSerializable

    // 로컬 사용자만 관리 (다른 노드의 사용자는 Topic을 통해 통지만 받음)
    private val localUsers = mutableMapOf<String, ActorRef<UserMessage>>()
    private val allUsers = mutableMapOf<String, UserInfo>()
    private val cluster = Cluster.get(context.system)
    private val nodeAddress = cluster.selfMember().address().toString()

    init {
        // Topic 구독
        val chatEventAdapter = context.messageAdapter(ChatEvent::class.java) { ChatEventReceived(it) }
        topic.tell(Topic.subscribe(chatEventAdapter))
    }

    override fun createReceive(): Receive<Command> = newReceiveBuilder()
        .onMessage(Join::class.java, this::onJoin)
        .onMessage(Leave::class.java, this::onLeave)
        .onMessage(PostMessage::class.java, this::onPostMessage)
        .onMessage(GetUsers::class.java, this::onGetUsers)
        .onMessage(ChatEventReceived::class.java, this::onChatEvent)
        .build()

    private fun onJoin(cmd: Join): Behavior<Command> {
        if (allUsers.containsKey(cmd.username)) {
            cmd.userActor.tell(SystemMessage("사용자명 '${cmd.username}'은(는) 이미 사용 중입니다"))
            return this
        }

        // 로컬 사용자로 등록
        localUsers[cmd.username] = cmd.userActor
        allUsers[cmd.username] = UserInfo(cmd.username, nodeAddress)

        context.log.info("사용자 '{}' 가입 (노드: {}). 전체 사용자: {}", cmd.username, nodeAddress, allUsers.size)

        // 클러스터 전체에 가입 알림
        topic.tell(Topic.publish(UserJoined(cmd.username, nodeAddress)))

        return this
    }

    private fun onLeave(cmd: Leave): Behavior<Command> {
        if (localUsers.remove(cmd.username) != null) {
            allUsers.remove(cmd.username)
            context.log.info("사용자 '{}' 퇴장. 전체 사용자: {}", cmd.username, allUsers.size)

            // 클러스터 전체에 퇴장 알림
            topic.tell(Topic.publish(UserLeft(cmd.username, nodeAddress)))
        }
        return this
    }

    private fun onPostMessage(cmd: PostMessage): Behavior<Command> {
        if (!localUsers.containsKey(cmd.username)) {
            context.log.warn("알 수 없는 사용자로부터 메시지: {}", cmd.username)
            return this
        }

        context.log.debug("'{}' 사용자의 메시지: {}", cmd.username, cmd.message)

        // 클러스터 전체에 메시지 브로드캐스트
        topic.tell(Topic.publish(BroadcastMessage(cmd.username, cmd.message, nodeAddress)))

        return this
    }

    private fun onGetUsers(cmd: GetUsers): Behavior<Command> {
        cmd.replyTo.tell(UserList(allUsers.values.toList()))
        return this
    }

    private fun onChatEvent(cmd: ChatEventReceived): Behavior<Command> {
        when (val event = cmd.event) {
            is UserJoined -> {
                // 다른 노드의 사용자 등록
                if (!allUsers.containsKey(event.username)) {
                    allUsers[event.username] = UserInfo(event.username, event.nodeAddress)
                    context.log.info("원격 사용자 가입: {} (노드: {})", event.username, event.nodeAddress)
                }
                // 로컬 사용자들에게 알림
                val msg = SystemMessage("${event.username} 님이 채팅에 참여했습니다 (${event.nodeAddress})")
                localUsers.values.forEach { it.tell(msg) }
            }

            is UserLeft -> {
                allUsers.remove(event.username)
                context.log.info("원격 사용자 퇴장: {} (노드: {})", event.username, event.nodeAddress)
                // 로컬 사용자들에게 알림
                val msg = SystemMessage("${event.username} 님이 채팅을 떠났습니다 (${event.nodeAddress})")
                localUsers.values.forEach { it.tell(msg) }
            }

            is BroadcastMessage -> {
                // 모든 로컬 사용자에게 메시지 전달
                val chatMsg = ChatMessage(event.username, event.message, event.nodeAddress, event.timestamp)
                localUsers.values.forEach { it.tell(chatMsg) }
            }
        }
        return this
    }
}
