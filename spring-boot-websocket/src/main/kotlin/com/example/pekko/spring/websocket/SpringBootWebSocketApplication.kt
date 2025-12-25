package com.example.pekko.spring.websocket

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class SpringBootWebSocketApplication

fun main(args: Array<String>) {
    runApplication<SpringBootWebSocketApplication>(*args)
}
