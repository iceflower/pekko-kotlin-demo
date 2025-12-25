package com.example.pekko.http

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit

/**
 * TaskRegistry Actor 테스트 (Kotest)
 */
class TaskRegistryTest : FunSpec({

    val testKit = ActorTestKit.create()

    afterSpec {
        testKit.shutdownTestKit()
    }

    test("TaskRegistry는 초기에 샘플 데이터를 가지고 있어야 한다") {
        val registry = testKit.spawn(TaskRegistry.create())
        val probe = testKit.createTestProbe<TaskRegistry.Tasks>()

        registry.tell(TaskRegistry.GetAllTasks(probe.ref()))

        val response = probe.receiveMessage()
        response.tasks.size shouldBe 3
    }

    test("새 Task를 생성할 수 있다") {
        val registry = testKit.spawn(TaskRegistry.create())
        val probe = testKit.createTestProbe<Task>()

        registry.tell(TaskRegistry.CreateTask("새로운 작업", probe.ref()))

        val task = probe.receiveMessage()
        task.title shouldBe "새로운 작업"
        task.completed shouldBe false
    }

    test("ID로 Task를 조회할 수 있다") {
        val registry = testKit.spawn(TaskRegistry.create())
        val createProbe = testKit.createTestProbe<Task>()
        val getProbe = testKit.createTestProbe<TaskRegistry.TaskResponse>()

        // Task 생성
        registry.tell(TaskRegistry.CreateTask("조회할 작업", createProbe.ref()))
        val created = createProbe.receiveMessage()

        // Task 조회
        registry.tell(TaskRegistry.GetTask(created.id, getProbe.ref()))
        val response = getProbe.receiveMessage()

        response.shouldBeInstanceOf<TaskRegistry.TaskResponse.Found>().task.title shouldBe "조회할 작업"
    }

    test("존재하지 않는 Task 조회 시 NotFound를 반환해야 한다") {
        val registry = testKit.spawn(TaskRegistry.create())
        val probe = testKit.createTestProbe<TaskRegistry.TaskResponse>()

        registry.tell(TaskRegistry.GetTask(9999L, probe.ref()))

        val response = probe.receiveMessage()
        response.shouldBeInstanceOf<TaskRegistry.TaskResponse.NotFound>()
    }

    test("Task를 업데이트할 수 있다") {
        val registry = testKit.spawn(TaskRegistry.create())
        val createProbe = testKit.createTestProbe<Task>()
        val updateProbe = testKit.createTestProbe<TaskRegistry.TaskResponse>()

        // Task 생성
        registry.tell(TaskRegistry.CreateTask("업데이트 전", createProbe.ref()))
        val created = createProbe.receiveMessage()

        // Task 업데이트
        registry.tell(TaskRegistry.UpdateTask(created.id, "업데이트 후", true, updateProbe.ref()))
        val response = updateProbe.receiveMessage()

        val updated = response.shouldBeInstanceOf<TaskRegistry.TaskResponse.Found>().task
        updated.title shouldBe "업데이트 후"
        updated.completed shouldBe true
    }

    test("Task를 삭제할 수 있다") {
        val registry = testKit.spawn(TaskRegistry.create())
        val createProbe = testKit.createTestProbe<Task>()
        val deleteProbe = testKit.createTestProbe<TaskRegistry.TaskResponse>()

        // Task 생성
        registry.tell(TaskRegistry.CreateTask("삭제할 작업", createProbe.ref()))
        val created = createProbe.receiveMessage()

        // Task 삭제
        registry.tell(TaskRegistry.DeleteTask(created.id, deleteProbe.ref()))
        val response = deleteProbe.receiveMessage()

        response.shouldBeInstanceOf<TaskRegistry.TaskResponse.Deleted>()
    }
})
