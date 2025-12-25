package com.example.pekko.spring.websocket.cluster

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Spring Boot + Pekko Cluster WebSocket 애플리케이션.
 *
 * 실행 예시:
 * - 노드 1: ./gradlew :spring-boot-websocket-cluster:bootRun --args='--server.port=8081 --pekko.cluster.port=2551'
 * - 노드 2: ./gradlew :spring-boot-websocket-cluster:bootRun --args='--server.port=8082 --pekko.cluster.port=2552'
 */
@SpringBootApplication
class SpringBootWebSocketClusterApplication

fun main(args: Array<String>) {
    runApplication<SpringBootWebSocketClusterApplication>(*args)
}
