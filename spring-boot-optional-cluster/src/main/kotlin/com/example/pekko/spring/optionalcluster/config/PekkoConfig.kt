package com.example.pekko.spring.optionalcluster.config

import com.example.pekko.spring.optionalcluster.actor.ClusterListener
import com.example.pekko.spring.optionalcluster.actor.Counter
import com.typesafe.config.ConfigFactory
import jakarta.annotation.PreDestroy
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.Props
import org.apache.pekko.actor.typed.Scheduler
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.cluster.typed.ClusterSingleton
import org.apache.pekko.cluster.typed.SingletonActor
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

/**
 * Base Pekko configuration that creates either a standalone or cluster-enabled ActorSystem
 * based on the `pekko.cluster.enabled` property.
 */
@Configuration
@EnableConfigurationProperties(PekkoProperties::class)
class PekkoConfig(
    private val properties: PekkoProperties
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private lateinit var system: ActorSystem<Void>

    @Bean
    fun actorSystem(): ActorSystem<Void> {
        val config = if (properties.cluster.enabled) {
            log.info("Creating CLUSTER-enabled Pekko ActorSystem...")
            createClusterConfig()
        } else {
            log.info("Creating STANDALONE Pekko ActorSystem...")
            createStandaloneConfig()
        }

        system = ActorSystem.create(Behaviors.empty(), "optional-cluster-system", config)
        log.info("Pekko ActorSystem created: {} (cluster mode: {})", system.name(), properties.cluster.enabled)

        return system
    }

    @Bean
    fun scheduler(system: ActorSystem<Void>): Scheduler = system.scheduler()

    private fun createStandaloneConfig() = ConfigFactory.parseString("""
        pekko {
            loglevel = "INFO"
            actor {
                # Default provider for standalone mode
                serialization-bindings {
                    "com.example.pekko.spring.optionalcluster.actor.CborSerializable" = jackson-cbor
                }
            }
        }
    """.trimIndent()).withFallback(ConfigFactory.load())

    private fun createClusterConfig(): com.typesafe.config.Config {
        val seedNodesConfig = properties.cluster.seedNodes.joinToString(",") { "\"$it\"" }

        return ConfigFactory.parseString("""
            pekko {
                loglevel = "INFO"

                actor {
                    provider = cluster

                    serialization-bindings {
                        "com.example.pekko.spring.optionalcluster.actor.CborSerializable" = jackson-cbor
                    }
                }

                remote.artery {
                    canonical {
                        hostname = "${properties.cluster.hostname}"
                        port = ${properties.cluster.port}
                    }
                }

                cluster {
                    seed-nodes = [$seedNodesConfig]
                    downing-provider-class = "org.apache.pekko.cluster.sbr.SplitBrainResolverProvider"

                    split-brain-resolver {
                        active-strategy = keep-majority
                        stable-after = 20s
                    }
                }
            }
        """.trimIndent()).withFallback(ConfigFactory.load())
    }

    @PreDestroy
    fun shutdown() {
        if (::system.isInitialized) {
            log.info("Shutting down Pekko ActorSystem...")
            system.terminate()
            system.whenTerminated.toCompletableFuture().get(
                Duration.ofSeconds(30).toMillis(),
                java.util.concurrent.TimeUnit.MILLISECONDS
            )
            log.info("Pekko ActorSystem terminated")
        }
    }
}

/**
 * Configuration for STANDALONE mode (cluster disabled).
 * Creates a regular actor for the counter.
 */
@Configuration
@ConditionalOnProperty(name = ["pekko.cluster.enabled"], havingValue = "false", matchIfMissing = true)
class StandaloneActorConfig {
    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun counterActor(system: ActorSystem<Void>): ActorRef<Counter.Command> {
        log.info("Creating standalone Counter actor")
        return system.systemActorOf(Counter.create(), Counter.ACTOR_NAME, Props.empty())
    }
}

/**
 * Configuration for CLUSTER mode (cluster enabled).
 * Creates a ClusterSingleton for the counter and spawns ClusterListener.
 */
@Configuration
@ConditionalOnProperty(name = ["pekko.cluster.enabled"], havingValue = "true")
class ClusterActorConfig {
    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun clusterListener(system: ActorSystem<Void>): ActorRef<*> {
        log.info("Creating ClusterListener")
        return system.systemActorOf(ClusterListener.create(), "cluster-listener", Props.empty())
    }

    @Bean
    fun counterActor(system: ActorSystem<Void>): ActorRef<Counter.Command> {
        log.info("Creating ClusterSingleton Counter actor")
        val singleton = ClusterSingleton.get(system)
        val singletonActor = SingletonActor.of(Counter.create(), Counter.ACTOR_NAME)
        return singleton.init(singletonActor)
    }
}
