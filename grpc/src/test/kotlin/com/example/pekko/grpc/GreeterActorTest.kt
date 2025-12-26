package com.example.pekko.grpc

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit

/**
 * GreeterActor 테스트 (Kotest BDD Style)
 */
class GreeterActorTest : DescribeSpec({

    val testKit = ActorTestKit.create()

    afterSpec {
        testKit.shutdownTestKit()
    }

    describe("GreeterActor") {
        context("Greet 메시지를 받으면") {
            it("인사 메시지를 생성해야 한다") {
                val greeter = testKit.spawn(GreeterActor.create())
                val probe = testKit.createTestProbe<GreeterActor.Greeting>()

                greeter.tell(GreeterActor.Greet("홍길동", probe.ref()))

                val response = probe.receiveMessage()
                response.message shouldContain "홍길동"
                response.message shouldContain "안녕하세요"
            }

            it("인사 횟수를 카운트해야 한다") {
                val greeter = testKit.spawn(GreeterActor.create())
                val probe = testKit.createTestProbe<GreeterActor.Greeting>()

                greeter.tell(GreeterActor.Greet("사용자1", probe.ref()))
                val first = probe.receiveMessage()

                greeter.tell(GreeterActor.Greet("사용자2", probe.ref()))
                val second = probe.receiveMessage()

                first.message shouldContain "#1"
                second.message shouldContain "#2"
            }

            it("타임스탬프가 포함되어야 한다") {
                val greeter = testKit.spawn(GreeterActor.create())
                val probe = testKit.createTestProbe<GreeterActor.Greeting>()

                val beforeTime = System.currentTimeMillis()
                greeter.tell(GreeterActor.Greet("테스터", probe.ref()))
                val response = probe.receiveMessage()
                val afterTime = System.currentTimeMillis()

                (response.timestamp >= beforeTime) shouldBe true
                (response.timestamp <= afterTime) shouldBe true
            }
        }

        context("GreetMultiple 메시지를 받으면") {
            it("다중 인사를 생성해야 한다") {
                val greeter = testKit.spawn(GreeterActor.create())
                val probe = testKit.createTestProbe<GreeterActor.MultipleGreetings>()

                greeter.tell(GreeterActor.GreetMultiple("테스터", 3, probe.ref()))

                val response = probe.receiveMessage()
                response.greetings.size shouldBe 3
                response.greetings[0].message shouldContain "[1/3]"
                response.greetings[1].message shouldContain "[2/3]"
                response.greetings[2].message shouldContain "[3/3]"
            }
        }
    }
})
