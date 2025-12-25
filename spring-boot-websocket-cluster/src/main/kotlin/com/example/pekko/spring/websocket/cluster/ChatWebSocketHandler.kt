package com.example.pekko.spring.websocket.cluster

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.javadsl.AskPattern
import org.apache.pekko.cluster.typed.Cluster
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * 클러스터 환경의 WebSocket 핸들러.
 */
@Component
class ChatWebSocketHandler(
    private val chatRoom: ActorRef<DistributedChatRoom.Command>,
    private val actorSystem: ActorSystem<Nothing>
) : TextWebSocketHandler(), DistributedChatRoom.MessageListener {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val objectMapper = jacksonObjectMapper()
    private val sessions = ConcurrentHashMap<String, WebSocketSession>()
    private val sessionUsernames = ConcurrentHashMap<String, String>()

    init {
        // 메시지 리스너 등록은 별도 초기화 필요
        // (Actor에서 직접 접근하기 어려우므로 브로드캐스트 방식 사용)
    }

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val username = session.uri?.query?.let { query ->
            query.split("&")
                .map { it.split("=") }
                .find { it[0] == "username" }
                ?.getOrNull(1)
        } ?: "Guest-${session.id.take(8)}"

        sessions[session.id] = session
        sessionUsernames[session.id] = username

        chatRoom.tell(DistributedChatRoom.Join(username, session.id))
        logger.info("WebSocket 연결됨: {} (세션: {})", username, session.id)

        // 환영 메시지 전송
        sendToSession(session, mapOf(
            "type" to "connected",
            "username" to username,
            "sessionId" to session.id,
            "nodeAddress" to getNodeAddress()
        ))
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        val username = sessionUsernames.remove(session.id)
        sessions.remove(session.id)

        chatRoom.tell(DistributedChatRoom.Leave(session.id))
        logger.info("WebSocket 연결 해제: {} (세션: {})", username, session.id)
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        val text = message.payload
        if (text.isBlank()) return

        try {
            // JSON 파싱 시도
            val parsed = objectMapper.readValue(text, Map::class.java)
            when (parsed["type"]) {
                "message" -> {
                    chatRoom.tell(DistributedChatRoom.PostMessage(session.id, parsed["content"]?.toString() ?: ""))
                }
                "getUsers" -> {
                    val future = AskPattern.ask(
                        chatRoom,
                        { replyTo: ActorRef<DistributedChatRoom.UserList> -> DistributedChatRoom.GetUsers(replyTo) },
                        Duration.ofSeconds(3),
                        actorSystem.scheduler()
                    )
                    future.whenComplete { userList, error ->
                        if (error == null) {
                            sendToSession(session, mapOf(
                                "type" to "users",
                                "users" to userList.users
                            ))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // 일반 텍스트 메시지로 처리
            chatRoom.tell(DistributedChatRoom.PostMessage(session.id, text))
        }
    }

    // DistributedChatRoom.MessageListener 구현
    override fun onChatMessage(username: String, message: String, nodeAddress: String, timestamp: Long) {
        broadcastToAll(mapOf(
            "type" to "chat",
            "username" to username,
            "message" to message,
            "nodeAddress" to nodeAddress,
            "timestamp" to timestamp
        ))
    }

    override fun onSystemMessage(message: String) {
        broadcastToAll(mapOf(
            "type" to "system",
            "message" to message,
            "timestamp" to System.currentTimeMillis()
        ))
    }

    private fun broadcastToAll(data: Map<String, Any>) {
        val json = objectMapper.writeValueAsString(data)
        val message = TextMessage(json)
        sessions.values.forEach { session ->
            try {
                if (session.isOpen) {
                    session.sendMessage(message)
                }
            } catch (e: Exception) {
                logger.warn("메시지 전송 실패: {}", e.message)
            }
        }
    }

    private fun sendToSession(session: WebSocketSession, data: Map<String, Any>) {
        try {
            val json = objectMapper.writeValueAsString(data)
            session.sendMessage(TextMessage(json))
        } catch (e: Exception) {
            logger.warn("세션에 메시지 전송 실패: {}", e.message)
        }
    }

    private fun getNodeAddress(): String {
        return Cluster.get(actorSystem).selfMember().address().toString()
    }
}
