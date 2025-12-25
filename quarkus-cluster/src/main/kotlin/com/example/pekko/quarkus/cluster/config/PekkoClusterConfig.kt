package com.example.pekko.quarkus.cluster.config

import com.example.pekko.quarkus.cluster.actor.ClusterListener
import com.example.pekko.quarkus.cluster.actor.SingletonCounter
import io.quarkus.runtime.ShutdownEvent
import io.quarkus.runtime.StartupEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import jakarta.enterprise.inject.Produces
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.Scheduler
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.cluster.typed.ClusterSingleton
import org.apache.pekko.cluster.typed.SingletonActor
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.TimeUnit

@ApplicationScoped
class PekkoClusterConfig {
    private val log = LoggerFactory.getLogger(PekkoClusterConfig::class.java)

    private lateinit var system: ActorSystem<Void>
    private lateinit var singletonCounterRef: ActorRef<SingletonCounter.Command>

    fun onStart(@Observes ev: StartupEvent) {
        log.info("Starting Pekko Cluster ActorSystem...")
        system = ActorSystem.create(Behaviors.empty(), "quarkus-cluster-system")
        log.info("Pekko Cluster ActorSystem created: {}", system.name())

        // Spawn cluster listener
        system.systemActorOf(ClusterListener.create(), "cluster-listener", org.apache.pekko.actor.typed.Props.empty())
        log.info("ClusterListener spawned")

        // Initialize singleton counter
        val singleton = ClusterSingleton.get(system)
        val singletonActor = SingletonActor.of(
            SingletonCounter.create(),
            SingletonCounter.SINGLETON_NAME
        )
        singletonCounterRef = singleton.init(singletonActor)
        log.info("SingletonCounter initialized: {}", singletonCounterRef)
    }

    fun onStop(@Observes ev: ShutdownEvent) {
        if (::system.isInitialized) {
            log.info("Shutting down Pekko Cluster ActorSystem...")
            system.terminate()
            system.whenTerminated.toCompletableFuture().get(30, TimeUnit.SECONDS)
            log.info("Pekko Cluster ActorSystem terminated")
        }
    }

    @Produces
    @ApplicationScoped
    fun actorSystem(): ActorSystem<Void> = system

    @Produces
    @ApplicationScoped
    fun scheduler(): Scheduler = system.scheduler()

    @Produces
    @ApplicationScoped
    fun singletonCounter(): ActorRef<SingletonCounter.Command> = singletonCounterRef
}
