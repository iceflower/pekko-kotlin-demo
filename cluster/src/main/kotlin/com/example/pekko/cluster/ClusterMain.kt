package com.example.pekko.cluster

import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.cluster.typed.ClusterSingleton
import org.apache.pekko.cluster.typed.SingletonActor
import java.time.Duration

/**
 * Pekko 클러스터 데모
 *
 * 실행 방법:
 *   노드 1 (시드): ./gradlew :cluster:run
 *   노드 2:       ./gradlew :cluster:run --args="2552"
 *   노드 3:       ./gradlew :cluster:run --args="2553"
 */
fun main(args: Array<String>) {
    val port = args.firstOrNull()?.toIntOrNull() ?: 2551

    println("""
        ╔═══════════════════════════════════════════╗
        ║   Apache Pekko Cluster Demo               ║
        ║   분산 Actor 시스템 예제                   ║
        ╚═══════════════════════════════════════════╝

        포트: $port
        시드 노드: 2551, 2552
    """.trimIndent())

    val system = ActorSystem.create(
        RootBehavior.create(),
        "ClusterSystem",
        createConfig(port)
    )

    // 시스템 종료 대기
    Runtime.getRuntime().addShutdownHook(Thread {
        println("\n시스템을 종료합니다...")
        system.terminate()
    })
}

private fun createConfig(port: Int) = ConfigFactory
    .parseString("pekko.remote.artery.canonical.port=$port")
    .withFallback(ConfigFactory.load())

/**
 * 루트 Actor - 클러스터 리스너와 싱글톤 카운터를 생성
 */
object RootBehavior {

    sealed interface Command
    data object Tick : Command

    fun create(): Behavior<Command> = Behaviors.setup { context ->
        // 클러스터 이벤트 리스너 생성
        context.spawn(ClusterListener.create(), "clusterListener")

        // 클러스터 싱글톤으로 카운터 생성
        val singletonManager = ClusterSingleton.get(context.system)
        val counterProxy = singletonManager.init(
            SingletonActor.of(SingletonCounter.create(), "globalCounter")
        )

        context.log.info("클러스터 노드 초기화 완료")

        // 주기적으로 카운터 증가 (데모용)
        Behaviors.withTimers { timers ->
            timers.startTimerWithFixedDelay(
                "tick",
                Tick,
                Duration.ofSeconds(5),
                Duration.ofSeconds(10)
            )

            Behaviors.receive { _, msg ->
                when (msg) {
                    is Tick -> {
                        counterProxy.tell(SingletonCounter.Increment)
                        Behaviors.same()
                    }
                }
            }
        }
    }
}
