package com.example.pekko.spring.websocket.cluster

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

    @Value("\${pekko.cluster.seed-nodes:pekko://SpringWebSocketCluster@127.0.0.1:2551,pekko://SpringWebSocketCluster@127.0.0.1:2552}")
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
                        "com.example.pekko.spring.websocket.cluster.CborSerializable" = jackson-cbor
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
            "SpringWebSocketCluster",
            config
        )
        actorSystem = system
        return system
    }

    private fun rootBehavior(): Behavior<Nothing> = Behaviors.setup { context ->
        // 분산 PubSub Topic 생성
        val topic = context.spawn(
            Topic.create(DistributedChatRoom.ChatEvent::class.java, DistributedChatRoom.TOPIC_NAME),
            "chat-topic"
        )

        // 분산 채팅방 생성
        context.spawn(DistributedChatRoom.create(topic), "chat-room")

        Behaviors.empty()
    }

    @Bean
    fun chatRoom(actorSystem: ActorSystem<Nothing>): ActorRef<DistributedChatRoom.Command> {
        return org.apache.pekko.actor.typed.ActorRefResolver.get(actorSystem)
            .resolveActorRef("pekko://SpringWebSocketCluster/user/chat-room")
    }

    @PreDestroy
    fun shutdown() {
        actorSystem?.terminate()
    }
}
