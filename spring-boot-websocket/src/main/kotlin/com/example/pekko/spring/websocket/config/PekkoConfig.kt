package com.example.pekko.spring.websocket.config

import com.example.pekko.spring.websocket.actor.ChatRoom
import com.typesafe.config.ConfigFactory
import jakarta.annotation.PreDestroy
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.Props
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Pekko ActorSystem configuration for Spring Boot WebSocket integration.
 */
@Configuration
class PekkoConfig {

    private val logger = LoggerFactory.getLogger(PekkoConfig::class.java)

    private lateinit var system: ActorSystem<Void>

    @Bean
    fun actorSystem(): ActorSystem<Void> {
        val config = ConfigFactory.parseString(
            """
            pekko {
              loglevel = "INFO"
              loggers = ["org.apache.pekko.event.slf4j.Slf4jLogger"]
              logging-filter = "org.apache.pekko.event.slf4j.Slf4jLoggingFilter"
            }
            """.trimIndent()
        ).withFallback(ConfigFactory.load())

        system = ActorSystem.create(Behaviors.empty(), "spring-websocket-system", config)
        logger.info("Pekko ActorSystem 'spring-websocket-system' created")
        return system
    }

    @Bean
    fun chatRoom(actorSystem: ActorSystem<Void>): ActorRef<ChatRoom.Command> {
        val chatRoom = actorSystem.systemActorOf(
            ChatRoom.create(),
            "chat-room",
            Props.empty()
        )
        logger.info("ChatRoom actor created")
        return chatRoom
    }

    @PreDestroy
    fun shutdown() {
        if (::system.isInitialized) {
            logger.info("Shutting down Pekko ActorSystem")
            system.terminate()
        }
    }
}
