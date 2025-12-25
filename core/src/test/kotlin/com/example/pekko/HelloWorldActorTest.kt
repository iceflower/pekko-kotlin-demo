package com.example.pekko

import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * HelloWorld Actor 테스트
 *
 * Pekko TestKit을 사용한 Actor 단위 테스트 예제입니다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HelloWorldActorTest {

    private val testKit = ActorTestKit.create()

    @AfterAll
    fun cleanup() {
        testKit.shutdownTestKit()
    }

    @Test
    fun `HelloWorld Actor가 인사에 응답해야 한다`() {
        // Given: HelloWorld Actor 생성
        val helloWorld = testKit.spawn(HelloWorldActor.create())

        // And: 응답을 받을 테스트 프로브 생성
        val probe = testKit.createTestProbe<HelloWorldActor.Greeted>()

        // When: 인사 메시지 전송
        helloWorld.tell(HelloWorldActor.Greet("테스터", probe.ref()))

        // Then: 응답 확인
        val response = probe.receiveMessage()
        assertEquals("테스터", response.name)
    }

    @Test
    fun `HelloWorld Actor가 여러 번 인사해도 정상 동작해야 한다`() {
        val helloWorld = testKit.spawn(HelloWorldActor.create())
        val probe = testKit.createTestProbe<HelloWorldActor.Greeted>()

        // 여러 번 인사하고 각각 응답 확인
        for (i in 0..2) {
            helloWorld.tell(HelloWorldActor.Greet("사용자$i", probe.ref()))
            val response = probe.receiveMessage()
            assertEquals("사용자$i", response.name)
        }
    }
}
