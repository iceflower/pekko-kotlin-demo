package com.example.pekko.spring.cluster.config

import com.example.pekko.spring.cluster.actor.ClusterListener
import com.example.pekko.spring.cluster.actor.SingletonCounter
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.Scheduler
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.cluster.typed.ClusterSingleton
import org.apache.pekko.cluster.typed.SingletonActor
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import jakarta.annotation.PreDestroy
import java.time.Duration

@Configuration
class PekkoClusterConfig {
    private val log = LoggerFactory.getLogger(PekkoClusterConfig::class.java)
    private lateinit var system: ActorSystem<Void>

    @Bean
    fun clusterActorSystem(): ActorSystem<Void> {
        log.info("Creating Pekko Cluster ActorSystem...")
        system = ActorSystem.create(Behaviors.empty(), "spring-cluster-system")
        log.info("Pekko Cluster ActorSystem created: {}", system.name())

        // Spawn cluster listener
        system.systemActorOf(ClusterListener.create(), "cluster-listener", org.apache.pekko.actor.typed.Props.empty())
        log.info("ClusterListener spawned")

        return system
    }

    @Bean
    fun clusterScheduler(system: ActorSystem<Void>): Scheduler {
        return system.scheduler()
    }

    @Bean
    fun singletonCounter(system: ActorSystem<Void>): ActorRef<SingletonCounter.Command> {
        val singleton = ClusterSingleton.get(system)

        val singletonActor = SingletonActor.of(
            SingletonCounter.create(),
            SingletonCounter.SINGLETON_NAME
        )

        val counterRef = singleton.init(singletonActor)
        log.info("SingletonCounter initialized: {}", counterRef)
        return counterRef
    }

    @PreDestroy
    fun shutdown() {
        if (::system.isInitialized) {
            log.info("Shutting down Pekko Cluster ActorSystem...")
            system.terminate()
            system.whenTerminated.toCompletableFuture().get(Duration.ofSeconds(30).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
            log.info("Pekko Cluster ActorSystem terminated")
        }
    }
}
