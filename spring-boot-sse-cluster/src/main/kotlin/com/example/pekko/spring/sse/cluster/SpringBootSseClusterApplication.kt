package com.example.pekko.spring.sse.cluster

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Spring Boot + Pekko Cluster SSE 애플리케이션.
 *
 * 실행 예시:
 * - 노드 1: ./gradlew :spring-boot-sse-cluster:bootRun --args='--server.port=8091 --pekko.cluster.port=2551'
 * - 노드 2: ./gradlew :spring-boot-sse-cluster:bootRun --args='--server.port=8092 --pekko.cluster.port=2552'
 */
@SpringBootApplication
class SpringBootSseClusterApplication

fun main(args: Array<String>) {
    runApplication<SpringBootSseClusterApplication>(*args)
}
