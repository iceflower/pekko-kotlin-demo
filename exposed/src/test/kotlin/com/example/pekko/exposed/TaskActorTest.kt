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
        // Initialize fresh database for each test with unique name
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

        // Create a task first
        actor.tell(TaskActor.CreateTask("Find Me", null, createProbe.ref))
        val created = createProbe.receiveMessage() as TaskActor.TaskFound

        // Get the task
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

        // Create multiple tasks
        actor.tell(TaskActor.CreateTask("Task 1", null, createProbe.ref))
        createProbe.receiveMessage()
        actor.tell(TaskActor.CreateTask("Task 2", null, createProbe.ref))
        createProbe.receiveMessage()
        actor.tell(TaskActor.CreateTask("Task 3", null, createProbe.ref))
        createProbe.receiveMessage()

        // Get all
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

        // Create a task
        actor.tell(TaskActor.CreateTask("Original", "Original desc", createProbe.ref))
        val created = createProbe.receiveMessage() as TaskActor.TaskFound

        // Update it
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

        // Create a task (default: not completed)
        actor.tell(TaskActor.CreateTask("Toggle Me", null, createProbe.ref))
        val created = createProbe.receiveMessage() as TaskActor.TaskFound
        assertEquals(false, created.task.completed)

        // Toggle to completed
        actor.tell(TaskActor.ToggleTask(created.task.id, toggleProbe.ref))
        val toggled1 = toggleProbe.receiveMessage() as TaskActor.TaskFound
        assertEquals(true, toggled1.task.completed)

        // Toggle back to not completed
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

        // Create a task
        actor.tell(TaskActor.CreateTask("Delete Me", null, createProbe.ref))
        val created = createProbe.receiveMessage() as TaskActor.TaskFound

        // Delete it
        actor.tell(TaskActor.DeleteTask(created.task.id, deleteProbe.ref))
        val deleteResponse = deleteProbe.receiveMessage()
        assertTrue(deleteResponse.deleted)

        // Verify it's gone
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

        // Create tasks
        actor.tell(TaskActor.CreateTask("Task 1", null, createProbe.ref))
        val task1 = (createProbe.receiveMessage() as TaskActor.TaskFound).task
        actor.tell(TaskActor.CreateTask("Task 2", null, createProbe.ref))
        createProbe.receiveMessage()
        actor.tell(TaskActor.CreateTask("Task 3", null, createProbe.ref))
        createProbe.receiveMessage()

        // Complete one task
        actor.tell(TaskActor.ToggleTask(task1.id, toggleProbe.ref))
        toggleProbe.receiveMessage()

        // Get stats
        actor.tell(TaskActor.GetStats(statsProbe.ref))
        val stats = statsProbe.receiveMessage()

        assertEquals(3L, stats.total)
        assertEquals(1L, stats.completed)
    }
}
