package com.example.pekko.exposed

import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TaskActorTest {

    companion object {
        private lateinit var testKit: ActorTestKit
        private var testCounter = 0

        @BeforeAll
        @JvmStatic
        fun setupAll() {
            testKit = ActorTestKit.create()
        }

        @AfterAll
        @JvmStatic
        fun teardownAll() {
            testKit.shutdownTestKit()
        }
    }

    @BeforeEach
    fun setup() {
        // 각 테스트마다 고유한 이름으로 새 데이터베이스 초기화
        testCounter++
        DatabaseConfig.init(
            url = "jdbc:h2:mem:pekko_test_$testCounter;DB_CLOSE_DELAY=-1;"
        )
    }

    @AfterEach
    fun teardown() {
        DatabaseConfig.shutdown()
    }

    @Test
    fun `should create a task`() {
        val repository = TaskRepository(DatabaseConfig.getDatabase()!!)
        val actor = testKit.spawn(TaskActor.create(repository))
        val probe: TestProbe<TaskActor.TaskResponse> = testKit.createTestProbe()

        actor.tell(TaskActor.CreateTask("Test Task", "Test Description", probe.ref))

        val response = probe.receiveMessage()
        assertTrue(response is TaskActor.TaskFound)
        val task = (response as TaskActor.TaskFound).task
        assertEquals("Test Task", task.title)
        assertEquals("Test Description", task.description)
        assertEquals(false, task.completed)
    }

    @Test
    fun `should get task by id`() {
        val repository = TaskRepository(DatabaseConfig.getDatabase()!!)
        val actor = testKit.spawn(TaskActor.create(repository))
        val createProbe: TestProbe<TaskActor.TaskResponse> = testKit.createTestProbe()
        val getProbe: TestProbe<TaskActor.TaskResponse> = testKit.createTestProbe()

        // 먼저 Task 생성
        actor.tell(TaskActor.CreateTask("Find Me", null, createProbe.ref))
        val created = createProbe.receiveMessage() as TaskActor.TaskFound

        // Task 조회
        actor.tell(TaskActor.GetTask(created.task.id, getProbe.ref))
        val response = getProbe.receiveMessage()

        assertTrue(response is TaskActor.TaskFound)
        assertEquals("Find Me", (response as TaskActor.TaskFound).task.title)
    }

    @Test
    fun `should return TaskNotFound for non-existent task`() {
        val repository = TaskRepository(DatabaseConfig.getDatabase()!!)
        val actor = testKit.spawn(TaskActor.create(repository))
        val probe: TestProbe<TaskActor.TaskResponse> = testKit.createTestProbe()

        actor.tell(TaskActor.GetTask(999L, probe.ref))

        val response = probe.receiveMessage()
        assertTrue(response is TaskActor.TaskNotFound)
    }

    @Test
    fun `should get all tasks`() {
        val repository = TaskRepository(DatabaseConfig.getDatabase()!!)
        val actor = testKit.spawn(TaskActor.create(repository))
        val createProbe: TestProbe<TaskActor.TaskResponse> = testKit.createTestProbe()
        val listProbe: TestProbe<TaskActor.TaskListResponse> = testKit.createTestProbe()

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

        assertEquals(3, response.tasks.size)
    }

    @Test
    fun `should update task`() {
        val repository = TaskRepository(DatabaseConfig.getDatabase()!!)
        val actor = testKit.spawn(TaskActor.create(repository))
        val createProbe: TestProbe<TaskActor.TaskResponse> = testKit.createTestProbe()
        val updateProbe: TestProbe<TaskActor.TaskResponse> = testKit.createTestProbe()

        // Task 생성
        actor.tell(TaskActor.CreateTask("Original", "Original desc", createProbe.ref))
        val created = createProbe.receiveMessage() as TaskActor.TaskFound

        // 업데이트
        actor.tell(TaskActor.UpdateTask(created.task.id, "Updated", "Updated desc", true, updateProbe.ref))
        val response = updateProbe.receiveMessage()

        assertTrue(response is TaskActor.TaskFound)
        val updated = (response as TaskActor.TaskFound).task
        assertEquals("Updated", updated.title)
        assertEquals("Updated desc", updated.description)
        assertEquals(true, updated.completed)
    }

    @Test
    fun `should toggle task completion`() {
        val repository = TaskRepository(DatabaseConfig.getDatabase()!!)
        val actor = testKit.spawn(TaskActor.create(repository))
        val createProbe: TestProbe<TaskActor.TaskResponse> = testKit.createTestProbe()
        val toggleProbe: TestProbe<TaskActor.TaskResponse> = testKit.createTestProbe()

        // Task 생성 (기본값: 미완료)
        actor.tell(TaskActor.CreateTask("Toggle Me", null, createProbe.ref))
        val created = createProbe.receiveMessage() as TaskActor.TaskFound
        assertEquals(false, created.task.completed)

        // 완료로 토글
        actor.tell(TaskActor.ToggleTask(created.task.id, toggleProbe.ref))
        val toggled1 = toggleProbe.receiveMessage() as TaskActor.TaskFound
        assertEquals(true, toggled1.task.completed)

        // 다시 미완료로 토글
        actor.tell(TaskActor.ToggleTask(created.task.id, toggleProbe.ref))
        val toggled2 = toggleProbe.receiveMessage() as TaskActor.TaskFound
        assertEquals(false, toggled2.task.completed)
    }

    @Test
    fun `should delete task`() {
        val repository = TaskRepository(DatabaseConfig.getDatabase()!!)
        val actor = testKit.spawn(TaskActor.create(repository))
        val createProbe: TestProbe<TaskActor.TaskResponse> = testKit.createTestProbe()
        val deleteProbe: TestProbe<TaskActor.DeleteResponse> = testKit.createTestProbe()
        val getProbe: TestProbe<TaskActor.TaskResponse> = testKit.createTestProbe()

        // Task 생성
        actor.tell(TaskActor.CreateTask("Delete Me", null, createProbe.ref))
        val created = createProbe.receiveMessage() as TaskActor.TaskFound

        // 삭제
        actor.tell(TaskActor.DeleteTask(created.task.id, deleteProbe.ref))
        val deleteResponse = deleteProbe.receiveMessage()
        assertTrue(deleteResponse.deleted)

        // 삭제되었는지 확인
        actor.tell(TaskActor.GetTask(created.task.id, getProbe.ref))
        val getResponse = getProbe.receiveMessage()
        assertTrue(getResponse is TaskActor.TaskNotFound)
    }

    @Test
    fun `should get statistics`() {
        val repository = TaskRepository(DatabaseConfig.getDatabase()!!)
        val actor = testKit.spawn(TaskActor.create(repository))
        val createProbe: TestProbe<TaskActor.TaskResponse> = testKit.createTestProbe()
        val toggleProbe: TestProbe<TaskActor.TaskResponse> = testKit.createTestProbe()
        val statsProbe: TestProbe<TaskActor.StatsResponse> = testKit.createTestProbe()

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

        assertEquals(3L, stats.total)
        assertEquals(1L, stats.completed)
    }
}
