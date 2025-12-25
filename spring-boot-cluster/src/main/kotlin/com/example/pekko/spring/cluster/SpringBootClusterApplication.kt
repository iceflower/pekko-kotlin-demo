package com.example.pekko.spring.cluster

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class SpringBootClusterApplication

fun main(args: Array<String>) {
    runApplication<SpringBootClusterApplication>(*args)
}
