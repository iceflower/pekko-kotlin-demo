package com.example.pekko.spring.config

import com.example.pekko.spring.actor.TaskActor
import com.example.pekko.spring.actor.TaskCommand
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.Props
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import jakarta.annotation.PreDestroy

/**
 * Pekko ActorSystem을 Spring Bean으로 설정
 *
 * Spring의 생명주기에 맞춰 ActorSystem을 관리합니다:
 * - 애플리케이션 시작 시 ActorSystem 생성
 * - 애플리케이션 종료 시 ActorSystem graceful shutdown
 */
@Configuration
class PekkoConfig {

    private val log = LoggerFactory.getLogger(PekkoConfig::class.java)

    private lateinit var system: ActorSystem<Void>

    /**
     * Pekko ActorSystem Bean
     *
     * Guardian Actor는 Behaviors.empty()를 사용하여 빈 동작으로 설정하고,
     * 필요한 Actor들은 spawn으로 생성합니다.
     */
    @Bean
    fun actorSystem(): ActorSystem<Void> {
        log.info("Creating Pekko ActorSystem...")
        system = ActorSystem.create(
            Behaviors.empty(),
            "spring-pekko-system"
        )
        log.info("Pekko ActorSystem created: {}", system.name())
        return system
    }

    /**
     * TaskActor를 ActorSystem에 스폰하고 Spring Bean으로 등록
     *
     * 이렇게 하면 Controller에서 @Autowired로 Actor 참조를 받을 수 있습니다.
     */
    @Bean
    fun taskActor(system: ActorSystem<Void>): ActorRef<TaskCommand> {
        log.info("Spawning TaskActor...")
        return system.systemActorOf(
            TaskActor.create(),
            "task-actor",
            Props.empty()
        )
    }

    /**
     * Spring 애플리케이션 종료 시 ActorSystem 정리
     */
    @PreDestroy
    fun shutdown() {
        log.info("Shutting down Pekko ActorSystem...")
        if (::system.isInitialized) {
            system.terminate()
            log.info("Pekko ActorSystem terminated")
        }
    }
}
