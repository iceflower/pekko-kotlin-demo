package com.example.pekko.spring.actor

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit

/**
 * TaskActor 테스트 (Kotest BDD Style)
 *
 * Spring Boot 모듈의 TaskActor 단위 테스트
 */
class TaskActorTest : DescribeSpec({

    val testKit = ActorTestKit.create()

    afterSpec {
        testKit.shutdownTestKit()
    }

    describe("TaskActor") {
        context("초기 상태에서") {
            it("샘플 Task를 가지고 있어야 한다") {
                val actor = testKit.spawn(TaskActor.create())
                val probe = testKit.createTestProbe<TaskResponse>()

                actor.tell(GetAllTasks(probe.ref()))

                val response = probe.receiveMessage()
                response.shouldBeInstanceOf<TaskList>()
                (response as TaskList).tasks.size shouldBe 1
            }
        }

        context("CreateTask 메시지를 받으면") {
            it("새 Task를 생성할 수 있다") {
                val actor = testKit.spawn(TaskActor.create())
                val probe = testKit.createTestProbe<TaskResponse>()

                actor.tell(CreateTask("새 작업", "작업 설명", probe.ref()))

                val response = probe.receiveMessage()
                response.shouldBeInstanceOf<SingleTask>()
                val task = (response as SingleTask).task
                task.title shouldBe "새 작업"
                task.description shouldBe "작업 설명"
                task.completed shouldBe false
            }
        }

        context("GetTask 메시지를 받으면") {
            it("ID로 Task를 조회할 수 있다") {
                val actor = testKit.spawn(TaskActor.create())
                val createProbe = testKit.createTestProbe<TaskResponse>()
                val getProbe = testKit.createTestProbe<TaskResponse>()

                // Task 생성
                actor.tell(CreateTask("조회할 작업", "설명", createProbe.ref()))
                val createResponse = createProbe.receiveMessage() as SingleTask

                // Task 조회
                actor.tell(GetTask(createResponse.task.id, getProbe.ref()))
                val getResponse = getProbe.receiveMessage()

                getResponse.shouldBeInstanceOf<SingleTask>()
                (getResponse as SingleTask).task.title shouldBe "조회할 작업"
            }

            it("존재하지 않는 Task 조회 시 TaskNotFound를 반환해야 한다") {
                val actor = testKit.spawn(TaskActor.create())
                val probe = testKit.createTestProbe<TaskResponse>()

                actor.tell(GetTask("non-existent-id", probe.ref()))

                val response = probe.receiveMessage()
                response.shouldBeInstanceOf<TaskNotFound>()
            }
        }

        context("ToggleTask 메시지를 받으면") {
            it("Task 완료 상태를 토글할 수 있다") {
                val actor = testKit.spawn(TaskActor.create())
                val createProbe = testKit.createTestProbe<TaskResponse>()
                val toggleProbe = testKit.createTestProbe<TaskResponse>()

                // Task 생성 (초기 completed = false)
                actor.tell(CreateTask("토글할 작업", "설명", createProbe.ref()))
                val created = (createProbe.receiveMessage() as SingleTask).task
                created.completed shouldBe false

                // 토글 (false -> true)
                actor.tell(ToggleTask(created.id, toggleProbe.ref()))
                val toggled = toggleProbe.receiveMessage()
                toggled.shouldBeInstanceOf<SingleTask>()
                (toggled as SingleTask).task.completed shouldBe true

                // 다시 토글 (true -> false)
                actor.tell(ToggleTask(created.id, toggleProbe.ref()))
                val toggledAgain = toggleProbe.receiveMessage()
                (toggledAgain as SingleTask).task.completed shouldBe false
            }
        }

        context("DeleteTask 메시지를 받으면") {
            it("Task를 삭제할 수 있다") {
                val actor = testKit.spawn(TaskActor.create())
                val createProbe = testKit.createTestProbe<TaskResponse>()
                val deleteProbe = testKit.createTestProbe<TaskResponse>()
                val getProbe = testKit.createTestProbe<TaskResponse>()

                // Task 생성
                actor.tell(CreateTask("삭제할 작업", "설명", createProbe.ref()))
                val created = (createProbe.receiveMessage() as SingleTask).task

                // Task 삭제
                actor.tell(DeleteTask(created.id, deleteProbe.ref()))
                val deleteResponse = deleteProbe.receiveMessage()
                deleteResponse.shouldBeInstanceOf<TaskDeleted>()

                // 삭제된 Task 조회 시도
                actor.tell(GetTask(created.id, getProbe.ref()))
                val getResponse = getProbe.receiveMessage()
                getResponse.shouldBeInstanceOf<TaskNotFound>()
            }
        }
    }
})
