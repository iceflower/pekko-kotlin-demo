package com.example.pekko.spring.sse.config

import com.example.pekko.spring.sse.actor.EventPublisher
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
 * Pekko ActorSystem configuration for Spring Boot SSE integration.
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

        system = ActorSystem.create(Behaviors.empty(), "spring-sse-system", config)
        logger.info("Pekko ActorSystem 'spring-sse-system' created")
        return system
    }

    @Bean
    fun eventPublisher(actorSystem: ActorSystem<Void>): ActorRef<EventPublisher.Command> {
        val publisher = actorSystem.systemActorOf(
            EventPublisher.create(),
            "event-publisher",
            Props.empty()
        )
        logger.info("EventPublisher actor created")
        return publisher
    }

    @PreDestroy
    fun shutdown() {
        if (::system.isInitialized) {
            logger.info("Shutting down Pekko ActorSystem")
            system.terminate()
        }
    }
}
