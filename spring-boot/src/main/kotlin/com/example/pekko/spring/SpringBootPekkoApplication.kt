package com.example.pekko.spring

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Spring Boot + Pekko Actor 통합 예제 애플리케이션
 *
 * 이 예제는 Spring Boot의 DI와 Pekko Actor를 함께 사용하는 방법을 보여줍니다.
 * - Spring Bean으로 ActorSystem 관리
 * - Spring Controller에서 Actor와 통신
 * - Ask 패턴을 활용한 요청/응답 처리
 */
@SpringBootApplication
class SpringBootPekkoApplication

fun main(args: Array<String>) {
    runApplication<SpringBootPekkoApplication>(*args)
}
