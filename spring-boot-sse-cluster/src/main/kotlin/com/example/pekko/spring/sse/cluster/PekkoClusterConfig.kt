package com.example.pekko.spring.sse.cluster

import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.pubsub.Topic
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import jakarta.annotation.PreDestroy

/**
 * Pekko 클러스터 설정.
 */
@Configuration
class PekkoClusterConfig {

    @Value("\${pekko.cluster.port:2551}")
    private var clusterPort: Int = 2551

    @Value("\${pekko.cluster.seed-nodes:pekko://SpringSseCluster@127.0.0.1:2551,pekko://SpringSseCluster@127.0.0.1:2552}")
    private lateinit var seedNodes: String

    private var actorSystem: ActorSystem<Nothing>? = null

    @Bean
    fun actorSystem(): ActorSystem<Nothing> {
        val config = ConfigFactory.parseString(
            """
            pekko {
                loglevel = "INFO"

                actor {
                    provider = "cluster"

                    serialization-bindings {
                        "com.example.pekko.spring.sse.cluster.CborSerializable" = jackson-cbor
                    }
                }

                remote.artery {
                    canonical {
                        hostname = "127.0.0.1"
                        port = $clusterPort
                    }
                }

                cluster {
                    seed-nodes = [${seedNodes.split(",").joinToString(",") { "\"$it\"" }}]
                    downing-provider-class = "org.apache.pekko.cluster.sbr.SplitBrainResolverProvider"
                    min-nr-of-members = 1
                }
            }
            """.trimIndent()
        ).withFallback(ConfigFactory.load())

        val system = ActorSystem.create(
            rootBehavior(),
            "SpringSseCluster",
            config
        )
        actorSystem = system
        return system
    }

    private fun rootBehavior(): Behavior<Nothing> = Behaviors.setup { context ->
        // 분산 PubSub Topic 생성
        val topic = context.spawn(
            Topic.create(DistributedEventBus.ClusterEvent::class.java, DistributedEventBus.TOPIC_NAME),
            "sse-topic"
        )

        // 분산 이벤트 버스 생성
        context.spawn(DistributedEventBus.create(topic), "event-bus")

        Behaviors.empty()
    }

    @Bean
    fun eventBus(actorSystem: ActorSystem<Nothing>): ActorRef<DistributedEventBus.Command> {
        return org.apache.pekko.actor.typed.ActorRefResolver.get(actorSystem)
            .resolveActorRef("pekko://SpringSseCluster/user/event-bus")
    }

    @PreDestroy
    fun shutdown() {
        actorSystem?.terminate()
    }
}
