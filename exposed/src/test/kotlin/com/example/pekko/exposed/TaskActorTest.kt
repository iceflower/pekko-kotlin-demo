package com.example.pekko.exposed

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit

/**
 * TaskActor 테스트 (Kotest BDD Style)
 */
class TaskActorTest : DescribeSpec({

    val testKit = ActorTestKit.create()
    var testCounter = 0

    beforeEach {
        // 각 테스트마다 고유한 이름으로 새 데이터베이스 초기화
        testCounter++
        DatabaseConfig.init(
            url = "jdbc:h2:mem:pekko_test_$testCounter;DB_CLOSE_DELAY=-1;"
        )
    }

    afterEach {
        DatabaseConfig.shutdown()
    }

    afterSpec {
        testKit.shutdownTestKit()
    }

    describe("TaskActor") {
        context("CreateTask 메시지를 받으면") {
            it("Task를 생성해야 한다") {
                val repository = TaskRepository(DatabaseConfig.getDatabase()!!)
                val actor = testKit.spawn(TaskActor.create(repository))
                val probe = testKit.createTestProbe<TaskActor.TaskResponse>()

                actor.tell(TaskActor.CreateTask("Test Task", "Test Description", probe.ref))

                val response = probe.receiveMessage()
                response.shouldBeInstanceOf<TaskActor.TaskFound>()
                val task = (response as TaskActor.TaskFound).task
                task.title shouldBe "Test Task"
                task.description shouldBe "Test Description"
                task.completed shouldBe false
            }
        }

        context("GetTask 메시지를 받으면") {
            it("ID로 Task를 조회할 수 있다") {
                val repository = TaskRepository(DatabaseConfig.getDatabase()!!)
                val actor = testKit.spawn(TaskActor.create(repository))
                val createProbe = testKit.createTestProbe<TaskActor.TaskResponse>()
                val getProbe = testKit.createTestProbe<TaskActor.TaskResponse>()

                // 먼저 Task 생성
                actor.tell(TaskActor.CreateTask("Find Me", null, createProbe.ref))
                val created = createProbe.receiveMessage() as TaskActor.TaskFound

                // Task 조회
                actor.tell(TaskActor.GetTask(created.task.id, getProbe.ref))
                val response = getProbe.receiveMessage()

                response.shouldBeInstanceOf<TaskActor.TaskFound>()
                (response as TaskActor.TaskFound).task.title shouldBe "Find Me"
            }

            it("존재하지 않는 Task 조회 시 TaskNotFound를 반환해야 한다") {
                val repository = TaskRepository(DatabaseConfig.getDatabase()!!)
                val actor = testKit.spawn(TaskActor.create(repository))
                val probe = testKit.createTestProbe<TaskActor.TaskResponse>()

                actor.tell(TaskActor.GetTask(999L, probe.ref))

                val response = probe.receiveMessage()
                response.shouldBeInstanceOf<TaskActor.TaskNotFound>()
            }
        }

        context("GetAllTasks 메시지를 받으면") {
            it("모든 Task를 조회할 수 있다") {
                val repository = TaskRepository(DatabaseConfig.getDatabase()!!)
                val actor = testKit.spawn(TaskActor.create(repository))
                val createProbe = testKit.createTestProbe<TaskActor.TaskResponse>()
                val listProbe = testKit.createTestProbe<TaskActor.TaskListResponse>()

                // 여러 Task 생성
                actor.tell(TaskActor.CreateTask("Task 1", null, createProbe.ref))
                createProbe.receiveMessage()
                actor.tell(TaskActor.CreateTask("Task 2", null, createProbe.ref))
                createProbe.receiveMessage()
                actor.tell(TaskActor.CreateTask("Task 3", null, createProbe.ref))
                createProbe.receiveMessage()

                // 전체 조회
                actor.tell(TaskActor.GetAllTasks(listProbe.ref))
                val response = listProbe.receiveMessage()

                response.tasks.size shouldBe 3
            }
        }

        context("UpdateTask 메시지를 받으면") {
            it("Task를 업데이트할 수 있다") {
                val repository = TaskRepository(DatabaseConfig.getDatabase()!!)
                val actor = testKit.spawn(TaskActor.create(repository))
                val createProbe = testKit.createTestProbe<TaskActor.TaskResponse>()
                val updateProbe = testKit.createTestProbe<TaskActor.TaskResponse>()

                // Task 생성
                actor.tell(TaskActor.CreateTask("Original", "Original desc", createProbe.ref))
                val created = createProbe.receiveMessage() as TaskActor.TaskFound

                // 업데이트
                actor.tell(TaskActor.UpdateTask(created.task.id, "Updated", "Updated desc", true, updateProbe.ref))
                val response = updateProbe.receiveMessage()

                response.shouldBeInstanceOf<TaskActor.TaskFound>()
                val updated = (response as TaskActor.TaskFound).task
                updated.title shouldBe "Updated"
                updated.description shouldBe "Updated desc"
                updated.completed shouldBe true
            }
        }

        context("ToggleTask 메시지를 받으면") {
            it("Task 완료 상태를 토글할 수 있다") {
                val repository = TaskRepository(DatabaseConfig.getDatabase()!!)
                val actor = testKit.spawn(TaskActor.create(repository))
                val createProbe = testKit.createTestProbe<TaskActor.TaskResponse>()
                val toggleProbe = testKit.createTestProbe<TaskActor.TaskResponse>()

                // Task 생성 (기본값: 미완료)
                actor.tell(TaskActor.CreateTask("Toggle Me", null, createProbe.ref))
                val created = createProbe.receiveMessage() as TaskActor.TaskFound
                created.task.completed shouldBe false

                // 완료로 토글
                actor.tell(TaskActor.ToggleTask(created.task.id, toggleProbe.ref))
                val toggled1 = toggleProbe.receiveMessage() as TaskActor.TaskFound
                toggled1.task.completed shouldBe true

                // 다시 미완료로 토글
                actor.tell(TaskActor.ToggleTask(created.task.id, toggleProbe.ref))
                val toggled2 = toggleProbe.receiveMessage() as TaskActor.TaskFound
                toggled2.task.completed shouldBe false
            }
        }

        context("DeleteTask 메시지를 받으면") {
            it("Task를 삭제할 수 있다") {
                val repository = TaskRepository(DatabaseConfig.getDatabase()!!)
                val actor = testKit.spawn(TaskActor.create(repository))
                val createProbe = testKit.createTestProbe<TaskActor.TaskResponse>()
                val deleteProbe = testKit.createTestProbe<TaskActor.DeleteResponse>()
                val getProbe = testKit.createTestProbe<TaskActor.TaskResponse>()

                // Task 생성
                actor.tell(TaskActor.CreateTask("Delete Me", null, createProbe.ref))
                val created = createProbe.receiveMessage() as TaskActor.TaskFound

                // 삭제
                actor.tell(TaskActor.DeleteTask(created.task.id, deleteProbe.ref))
                val deleteResponse = deleteProbe.receiveMessage()
                deleteResponse.deleted shouldBe true

                // 삭제되었는지 확인
                actor.tell(TaskActor.GetTask(created.task.id, getProbe.ref))
                val getResponse = getProbe.receiveMessage()
                getResponse.shouldBeInstanceOf<TaskActor.TaskNotFound>()
            }
        }

        context("GetStats 메시지를 받으면") {
            it("통계를 조회할 수 있다") {
                val repository = TaskRepository(DatabaseConfig.getDatabase()!!)
                val actor = testKit.spawn(TaskActor.create(repository))
                val createProbe = testKit.createTestProbe<TaskActor.TaskResponse>()
                val toggleProbe = testKit.createTestProbe<TaskActor.TaskResponse>()
                val statsProbe = testKit.createTestProbe<TaskActor.StatsResponse>()

                // Task 생성
                actor.tell(TaskActor.CreateTask("Task 1", null, createProbe.ref))
                val task1 = (createProbe.receiveMessage() as TaskActor.TaskFound).task
                actor.tell(TaskActor.CreateTask("Task 2", null, createProbe.ref))
                createProbe.receiveMessage()
                actor.tell(TaskActor.CreateTask("Task 3", null, createProbe.ref))
                createProbe.receiveMessage()

                // 하나의 Task 완료
                actor.tell(TaskActor.ToggleTask(task1.id, toggleProbe.ref))
                toggleProbe.receiveMessage()

                // 통계 조회
                actor.tell(TaskActor.GetStats(statsProbe.ref))
                val stats = statsProbe.receiveMessage()

                stats.total shouldBe 3L
                stats.completed shouldBe 1L
            }
        }
    }
})
