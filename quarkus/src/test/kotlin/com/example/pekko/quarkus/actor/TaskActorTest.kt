package com.example.pekko.quarkus.actor

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit

class TaskActorTest : FunSpec({

    val testKit = ActorTestKit.create()

    afterSpec {
        testKit.shutdownTestKit()
    }

    test("CreateTask로 새 Task를 생성할 수 있다") {
        val taskActor = testKit.spawn(TaskActor.create())
        val probe = testKit.createTestProbe<TaskResponse>()

        taskActor.tell(CreateTask("테스트 작업", "설명", probe.ref()))

        val response = probe.receiveMessage()
        response.shouldBeInstanceOf<SingleTask>()
        (response as SingleTask).task.title shouldBe "테스트 작업"
        response.task.description shouldBe "설명"
        response.task.completed shouldBe false
    }

    test("GetAllTasks로 모든 Task를 조회할 수 있다") {
        val taskActor = testKit.spawn(TaskActor.create())
        val probe = testKit.createTestProbe<TaskResponse>()

        // 초기 샘플 데이터 포함
        taskActor.tell(GetAllTasks(probe.ref()))

        val response = probe.receiveMessage()
        response.shouldBeInstanceOf<TaskList>()
        (response as TaskList).tasks.size shouldBe 1 // 샘플 데이터 1개
    }

    test("GetTask로 특정 Task를 조회할 수 있다") {
        val taskActor = testKit.spawn(TaskActor.create())
        val createProbe = testKit.createTestProbe<TaskResponse>()
        val getProbe = testKit.createTestProbe<TaskResponse>()

        // Task 생성
        taskActor.tell(CreateTask("조회용 작업", "설명", createProbe.ref()))
        val created = createProbe.receiveMessage() as SingleTask

        // Task 조회
        taskActor.tell(GetTask(created.task.id, getProbe.ref()))

        val response = getProbe.receiveMessage()
        response.shouldBeInstanceOf<SingleTask>()
        (response as SingleTask).task.id shouldBe created.task.id
    }

    test("존재하지 않는 Task 조회 시 TaskNotFound를 반환한다") {
        val taskActor = testKit.spawn(TaskActor.create())
        val probe = testKit.createTestProbe<TaskResponse>()

        taskActor.tell(GetTask("non-existent-id", probe.ref()))

        val response = probe.receiveMessage()
        response.shouldBeInstanceOf<TaskNotFound>()
        (response as TaskNotFound).id shouldBe "non-existent-id"
    }

    test("ToggleTask로 완료 상태를 토글할 수 있다") {
        val taskActor = testKit.spawn(TaskActor.create())
        val createProbe = testKit.createTestProbe<TaskResponse>()
        val toggleProbe = testKit.createTestProbe<TaskResponse>()

        // Task 생성
        taskActor.tell(CreateTask("토글 작업", "설명", createProbe.ref()))
        val created = createProbe.receiveMessage() as SingleTask
        created.task.completed shouldBe false

        // 토글
        taskActor.tell(ToggleTask(created.task.id, toggleProbe.ref()))

        val toggled = toggleProbe.receiveMessage()
        toggled.shouldBeInstanceOf<SingleTask>()
        (toggled as SingleTask).task.completed shouldBe true
    }

    test("DeleteTask로 Task를 삭제할 수 있다") {
        val taskActor = testKit.spawn(TaskActor.create())
        val createProbe = testKit.createTestProbe<TaskResponse>()
        val deleteProbe = testKit.createTestProbe<TaskResponse>()
        val getProbe = testKit.createTestProbe<TaskResponse>()

        // Task 생성
        taskActor.tell(CreateTask("삭제할 작업", "설명", createProbe.ref()))
        val created = createProbe.receiveMessage() as SingleTask

        // 삭제
        taskActor.tell(DeleteTask(created.task.id, deleteProbe.ref()))

        val deleted = deleteProbe.receiveMessage()
        deleted.shouldBeInstanceOf<TaskDeleted>()

        // 삭제 확인
        taskActor.tell(GetTask(created.task.id, getProbe.ref()))
        val notFound = getProbe.receiveMessage()
        notFound.shouldBeInstanceOf<TaskNotFound>()
    }
})
