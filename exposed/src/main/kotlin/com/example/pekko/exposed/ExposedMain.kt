package com.example.pekko.exposed

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.javadsl.AskPattern
import java.time.Duration
import java.util.concurrent.CompletionStage

/**
 * Main entry point for Pekko + Exposed demo.
 * Demonstrates CRUD operations using Pekko Actors with JetBrains Exposed ORM.
 */
fun main() {
    // Initialize database
    val database = DatabaseConfig.init()
    println("Database initialized with H2 in-memory database")

    // Create repository
    val repository = TaskRepository(database)

    // Create actor system
    val system: ActorSystem<TaskActor.Command> = ActorSystem.create(
        TaskActor.create(repository),
        "exposed-demo"
    )

    val timeout = Duration.ofSeconds(5)

    try {
        println("\n=== Pekko + Exposed Demo ===\n")

        // Create tasks
        println("Creating tasks...")
        val task1 = askAndGet(system, timeout) { replyTo ->
            TaskActor.CreateTask("Learn Pekko", "Study Pekko actor model", replyTo)
        }
        println("Created: $task1")

        val task2 = askAndGet(system, timeout) { replyTo ->
            TaskActor.CreateTask("Learn Exposed", "Study JetBrains Exposed ORM", replyTo)
        }
        println("Created: $task2")

        val task3 = askAndGet(system, timeout) { replyTo ->
            TaskActor.CreateTask("Build demo app", null, replyTo)
        }
        println("Created: $task3")

        // Get all tasks
        println("\n--- All Tasks ---")
        val allTasks = askListAndGet(system, timeout) { replyTo ->
            TaskActor.GetAllTasks(replyTo)
        }
        allTasks.tasks.forEach { println("  - ${it.id}: ${it.title} [${if (it.completed) "X" else " "}]") }

        // Toggle first task
        println("\n--- Toggle Task 1 ---")
        val toggled = askAndGet(system, timeout) { replyTo ->
            TaskActor.ToggleTask(1L, replyTo)
        }
        println("Toggled: $toggled")

        // Update second task
        println("\n--- Update Task 2 ---")
        val updated = askAndGet(system, timeout) { replyTo ->
            TaskActor.UpdateTask(2L, "Master Exposed", "Become an Exposed ORM expert", null, replyTo)
        }
        println("Updated: $updated")

        // Get stats
        println("\n--- Statistics ---")
        val stats = askStatsAndGet(system, timeout) { replyTo ->
            TaskActor.GetStats(replyTo)
        }
        println("Total tasks: ${stats.total}, Completed: ${stats.completed}")

        // Get single task
        println("\n--- Get Task by ID ---")
        val found = askAndGet(system, timeout) { replyTo ->
            TaskActor.GetTask(2L, replyTo)
        }
        println("Found: $found")

        // Try to get non-existent task
        println("\n--- Get Non-existent Task ---")
        val notFound = askAndGet(system, timeout) { replyTo ->
            TaskActor.GetTask(999L, replyTo)
        }
        println("Result: $notFound")

        // Delete task
        println("\n--- Delete Task 3 ---")
        val deleted = askDeleteAndGet(system, timeout) { replyTo ->
            TaskActor.DeleteTask(3L, replyTo)
        }
        println("Deleted: ${deleted.deleted}")

        // Final list
        println("\n--- Final Task List ---")
        val finalTasks = askListAndGet(system, timeout) { replyTo ->
            TaskActor.GetAllTasks(replyTo)
        }
        finalTasks.tasks.forEach { task ->
            val status = if (task.completed) "[X]" else "[ ]"
            println("  $status ${task.id}: ${task.title}")
            task.description?.let { println("       $it") }
        }

        println("\n=== Demo Complete ===")

    } finally {
        // Shutdown
        system.terminate()
        system.whenTerminated.toCompletableFuture().get()
        DatabaseConfig.shutdown()
        println("System shutdown complete")
    }
}

// Helper functions for ask pattern
private fun askAndGet(
    system: ActorSystem<TaskActor.Command>,
    timeout: Duration,
    messageFactory: (org.apache.pekko.actor.typed.ActorRef<TaskActor.TaskResponse>) -> TaskActor.Command
): TaskActor.TaskResponse {
    val future: CompletionStage<TaskActor.TaskResponse> = AskPattern.ask(
        system,
        { replyTo -> messageFactory(replyTo) },
        timeout,
        system.scheduler()
    )
    return future.toCompletableFuture().get()
}

private fun askListAndGet(
    system: ActorSystem<TaskActor.Command>,
    timeout: Duration,
    messageFactory: (org.apache.pekko.actor.typed.ActorRef<TaskActor.TaskListResponse>) -> TaskActor.Command
): TaskActor.TaskListResponse {
    val future: CompletionStage<TaskActor.TaskListResponse> = AskPattern.ask(
        system,
        { replyTo -> messageFactory(replyTo) },
        timeout,
        system.scheduler()
    )
    return future.toCompletableFuture().get()
}

private fun askDeleteAndGet(
    system: ActorSystem<TaskActor.Command>,
    timeout: Duration,
    messageFactory: (org.apache.pekko.actor.typed.ActorRef<TaskActor.DeleteResponse>) -> TaskActor.Command
): TaskActor.DeleteResponse {
    val future: CompletionStage<TaskActor.DeleteResponse> = AskPattern.ask(
        system,
        { replyTo -> messageFactory(replyTo) },
        timeout,
        system.scheduler()
    )
    return future.toCompletableFuture().get()
}

private fun askStatsAndGet(
    system: ActorSystem<TaskActor.Command>,
    timeout: Duration,
    messageFactory: (org.apache.pekko.actor.typed.ActorRef<TaskActor.StatsResponse>) -> TaskActor.Command
): TaskActor.StatsResponse {
    val future: CompletionStage<TaskActor.StatsResponse> = AskPattern.ask(
        system,
        { replyTo -> messageFactory(replyTo) },
        timeout,
        system.scheduler()
    )
    return future.toCompletableFuture().get()
}
