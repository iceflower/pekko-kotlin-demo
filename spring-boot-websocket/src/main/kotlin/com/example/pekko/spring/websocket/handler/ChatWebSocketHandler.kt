package com.example.pekko.spring.websocket.handler

import com.example.pekko.spring.websocket.actor.ChatRoom
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.pekko.actor.typed.ActorRef
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.concurrent.ConcurrentHashMap

/**
 * WebSocket handler that bridges Spring WebSocket sessions with Pekko ChatRoom actor.
 */
@Component
class ChatWebSocketHandler(
    private val chatRoom: ActorRef<ChatRoom.Command>,
    private val objectMapper: ObjectMapper
) : TextWebSocketHandler() {

    private val logger = LoggerFactory.getLogger(ChatWebSocketHandler::class.java)
    private val sessions = ConcurrentHashMap<String, WebSocketSession>()

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val sessionId = session.id
        sessions[sessionId] = session
        logger.info("WebSocket connection established: {}", sessionId)
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        try {
            val payload = objectMapper.readTree(message.payload)
            val type = payload.get("type")?.asText() ?: return

            when (type) {
                "JOIN" -> {
                    val username = payload.get("username")?.asText() ?: "Anonymous"
                    chatRoom.tell(ChatRoom.Join(session.id, username) { msg ->
                        sendToSession(session.id, msg)
                    })
                    logger.info("User '{}' joining chat (session: {})", username, session.id)
                }

                "MESSAGE" -> {
                    val content = payload.get("content")?.asText() ?: return
                    chatRoom.tell(ChatRoom.SendMessage(session.id, content))
                }

                "LEAVE" -> {
                    chatRoom.tell(ChatRoom.Leave(session.id))
                }

                else -> {
                    logger.warn("Unknown message type: {}", type)
                }
            }
        } catch (e: Exception) {
            logger.error("Error processing message: {}", e.message)
        }
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        val sessionId = session.id
        sessions.remove(sessionId)
        chatRoom.tell(ChatRoom.Leave(sessionId))
        logger.info("WebSocket connection closed: {} (status: {})", sessionId, status)
    }

    override fun handleTransportError(session: WebSocketSession, exception: Throwable) {
        logger.error("WebSocket transport error for session {}: {}", session.id, exception.message)
        sessions.remove(session.id)
        chatRoom.tell(ChatRoom.Leave(session.id))
    }

    private fun sendToSession(sessionId: String, message: ChatRoom.Message) {
        sessions[sessionId]?.let { session ->
            if (session.isOpen) {
                try {
                    val json = objectMapper.writeValueAsString(message)
                    session.sendMessage(TextMessage(json))
                } catch (e: Exception) {
                    logger.error("Failed to send message to session {}: {}", sessionId, e.message)
                }
            }
        }
    }
}
