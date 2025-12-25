package com.example.pekko.exposed

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.javadsl.AskPattern
import java.time.Duration
import java.util.concurrent.CompletionStage

/**
 * Pekko + Exposed 데모 진입점.
 * Pekko Actor와 JetBrains Exposed ORM을 사용한 CRUD 작업을 시연합니다.
 */
fun main() {
    // 데이터베이스 초기화
    val database = DatabaseConfig.init()
    println("Database initialized with H2 in-memory database")

    // 레포지토리 생성
    val repository = TaskRepository(database)

    // Actor 시스템 생성
    val system: ActorSystem<TaskActor.Command> = ActorSystem.create(
        TaskActor.create(repository),
        "exposed-demo"
    )

    val timeout = Duration.ofSeconds(5)

    try {
        println("\n=== Pekko + Exposed Demo ===\n")

        // Task 생성
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

        // 모든 Task 조회
        println("\n--- All Tasks ---")
        val allTasks = askListAndGet(system, timeout) { replyTo ->
            TaskActor.GetAllTasks(replyTo)
        }
        allTasks.tasks.forEach { println("  - ${it.id}: ${it.title} [${if (it.completed) "X" else " "}]") }

        // 첫 번째 Task 토글
        println("\n--- Toggle Task 1 ---")
        val toggled = askAndGet(system, timeout) { replyTo ->
            TaskActor.ToggleTask(1L, replyTo)
        }
        println("Toggled: $toggled")

        // 두 번째 Task 업데이트
        println("\n--- Update Task 2 ---")
        val updated = askAndGet(system, timeout) { replyTo ->
            TaskActor.UpdateTask(2L, "Master Exposed", "Become an Exposed ORM expert", null, replyTo)
        }
        println("Updated: $updated")

        // 통계 조회
        println("\n--- Statistics ---")
        val stats = askStatsAndGet(system, timeout) { replyTo ->
            TaskActor.GetStats(replyTo)
        }
        println("Total tasks: ${stats.total}, Completed: ${stats.completed}")

        // 단일 Task 조회
        println("\n--- Get Task by ID ---")
        val found = askAndGet(system, timeout) { replyTo ->
            TaskActor.GetTask(2L, replyTo)
        }
        println("Found: $found")

        // 존재하지 않는 Task 조회 시도
        println("\n--- Get Non-existent Task ---")
        val notFound = askAndGet(system, timeout) { replyTo ->
            TaskActor.GetTask(999L, replyTo)
        }
        println("Result: $notFound")

        // Task 삭제
        println("\n--- Delete Task 3 ---")
        val deleted = askDeleteAndGet(system, timeout) { replyTo ->
            TaskActor.DeleteTask(3L, replyTo)
        }
        println("Deleted: ${deleted.deleted}")

        // 최종 목록
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
        // 종료
        system.terminate()
        system.whenTerminated.toCompletableFuture().get()
        DatabaseConfig.shutdown()
        println("System shutdown complete")
    }
}

// Ask 패턴을 위한 헬퍼 함수
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
