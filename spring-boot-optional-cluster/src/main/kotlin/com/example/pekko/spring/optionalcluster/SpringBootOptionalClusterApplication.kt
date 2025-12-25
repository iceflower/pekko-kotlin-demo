package com.example.pekko.spring.optionalcluster

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class SpringBootOptionalClusterApplication

fun main(args: Array<String>) {
    runApplication<SpringBootOptionalClusterApplication>(*args)
}
