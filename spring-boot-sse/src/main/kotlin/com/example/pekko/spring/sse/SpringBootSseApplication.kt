package com.example.pekko.spring.sse

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Spring Boot SSE application with Pekko Actor integration.
 *
 * Demonstrates Server-Sent Events using Spring WebFlux with
 * Pekko Actors for event publishing and subscription management.
 */
@SpringBootApplication
class SpringBootSseApplication

fun main(args: Array<String>) {
    runApplication<SpringBootSseApplication>(*args)
}
