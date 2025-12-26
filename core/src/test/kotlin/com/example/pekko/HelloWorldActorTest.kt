package com.example.pekko

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit

/**
 * HelloWorld Actor 테스트 (Kotest BDD Style)
 *
 * Pekko TestKit을 사용한 Actor 단위 테스트 예제입니다.
 */
class HelloWorldActorTest : DescribeSpec({

    val testKit = ActorTestKit.create()

    afterSpec {
        testKit.shutdownTestKit()
    }

    describe("HelloWorldActor") {
        context("Greet 메시지를 받으면") {
            it("인사에 응답해야 한다") {
                // Given: HelloWorld Actor 생성
                val helloWorld = testKit.spawn(HelloWorldActor.create())

                // And: 응답을 받을 테스트 프로브 생성
                val probe = testKit.createTestProbe<HelloWorldActor.Greeted>()

                // When: 인사 메시지 전송
                helloWorld.tell(HelloWorldActor.Greet("테스터", probe.ref()))

                // Then: 응답 확인
                val response = probe.receiveMessage()
                response.name shouldBe "테스터"
            }

            it("여러 번 인사해도 정상 동작해야 한다") {
                val helloWorld = testKit.spawn(HelloWorldActor.create())
                val probe = testKit.createTestProbe<HelloWorldActor.Greeted>()

                // 여러 번 인사하고 각각 응답 확인
                for (i in 0..2) {
                    helloWorld.tell(HelloWorldActor.Greet("사용자$i", probe.ref()))
                    val response = probe.receiveMessage()
                    response.name shouldBe "사용자$i"
                }
            }
        }
    }
})
