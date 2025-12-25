package com.example.pekko.quarkus.config

import com.example.pekko.quarkus.actor.TaskActor
import com.example.pekko.quarkus.actor.TaskCommand
import io.quarkus.runtime.ShutdownEvent
import io.quarkus.runtime.StartupEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import jakarta.enterprise.inject.Produces
import jakarta.inject.Named
import jakarta.inject.Singleton
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.Scheduler
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.slf4j.LoggerFactory
import java.time.Duration

/**
 * Quarkus CDI를 사용한 Pekko ActorSystem 설정
 *
 * Quarkus의 생명주기 이벤트(@Observes)를 활용하여
 * ActorSystem을 관리합니다.
 */
@ApplicationScoped
class PekkoConfig {

    private val logger = LoggerFactory.getLogger(PekkoConfig::class.java)

    private lateinit var actorSystem: ActorSystem<TaskCommand>
    private lateinit var taskActor: ActorRef<TaskCommand>

    /**
     * Quarkus 시작 시 ActorSystem과 TaskActor 초기화
     */
    fun onStart(@Observes ev: StartupEvent) {
        logger.info("Quarkus 시작 - Pekko ActorSystem 초기화")

        // Root behavior로 TaskActor 생성
        actorSystem = ActorSystem.create(
            Behaviors.setup { context ->
                taskActor = context.spawn(TaskActor.create(), "TaskActor")
                logger.info("TaskActor spawned: {}", taskActor.path())
                Behaviors.empty()
            },
            "QuarkusPekkoSystem"
        )

        logger.info("Pekko ActorSystem 시작됨: {}", actorSystem.name())
    }

    /**
     * Quarkus 종료 시 ActorSystem graceful shutdown
     */
    fun onStop(@Observes ev: ShutdownEvent) {
        logger.info("Quarkus 종료 - Pekko ActorSystem 종료 시작")
        actorSystem.terminate()

        try {
            // 최대 10초 대기
            actorSystem.getWhenTerminated().toCompletableFuture().get(
                Duration.ofSeconds(10).toMillis(),
                java.util.concurrent.TimeUnit.MILLISECONDS
            )
            logger.info("Pekko ActorSystem 정상 종료됨")
        } catch (e: Exception) {
            logger.warn("ActorSystem 종료 중 타임아웃 또는 오류: {}", e.message)
        }
    }

    /**
     * Scheduler를 CDI Bean으로 제공 (Ask 패턴에서 사용)
     */
    @Produces
    @Singleton
    fun scheduler(): Scheduler = actorSystem.scheduler()

    /**
     * TaskActor의 ActorRef를 CDI Bean으로 제공
     * @Named로 한정하여 ActorSystem과 구분
     */
    @Produces
    @Singleton
    @Named("taskActor")
    fun taskActor(): ActorRef<TaskCommand> = taskActor
}
