package com.example.pekko.spring.controller

import com.example.pekko.spring.actor.*
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.javadsl.AskPattern
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.Duration
import java.util.concurrent.CompletionStage

/**
 * Task API 요청/응답 DTO
 */
data class CreateTaskRequest(
    val title: String,
    val description: String
)

data class TaskDto(
    val id: String,
    val title: String,
    val description: String,
    val completed: Boolean,
    val createdAt: String
)

data class MessageResponse(val message: String)

/**
 * Task 관리 REST Controller
 *
 * Spring MVC Controller에서 Pekko Actor와 통신하는 예제입니다.
 * Ask 패턴을 사용하여 Actor에게 메시지를 보내고 응답을 기다립니다.
 */
@RestController
@RequestMapping("/api/tasks")
class TaskController(
    private val taskActor: ActorRef<TaskCommand>,
    private val actorSystem: ActorSystem<Void>
) {
    private val timeout = Duration.ofSeconds(5)

    /**
     * 모든 Task 목록 조회
     * GET /api/tasks
     */
    @GetMapping
    fun getAllTasks(): CompletionStage<ResponseEntity<List<TaskDto>>> {
        return AskPattern.ask(
            taskActor,
            { replyTo: ActorRef<TaskResponse> -> GetAllTasks(replyTo) },
            timeout,
            actorSystem.scheduler()
        ).thenApply { response ->
            when (response) {
                is TaskList -> ResponseEntity.ok(response.tasks.map { it.toDto() })
                else -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(emptyList())
            }
        }
    }

    /**
     * ID로 Task 조회
     * GET /api/tasks/{id}
     */
    @GetMapping("/{id}")
    fun getTask(@PathVariable id: String): CompletionStage<ResponseEntity<Any>> {
        return AskPattern.ask(
            taskActor,
            { replyTo: ActorRef<TaskResponse> -> GetTask(id, replyTo) },
            timeout,
            actorSystem.scheduler()
        ).thenApply { response ->
            when (response) {
                is SingleTask -> ResponseEntity.ok(response.task.toDto() as Any)
                is TaskNotFound -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(MessageResponse("Task not found: ${response.id}") as Any)
                else -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(MessageResponse("Unexpected error") as Any)
            }
        }
    }

    /**
     * 새 Task 생성
     * POST /api/tasks
     */
    @PostMapping
    fun createTask(@RequestBody request: CreateTaskRequest): CompletionStage<ResponseEntity<Any>> {
        return AskPattern.ask(
            taskActor,
            { replyTo: ActorRef<TaskResponse> ->
                CreateTask(request.title, request.description, replyTo)
            },
            timeout,
            actorSystem.scheduler()
        ).thenApply { response ->
            when (response) {
                is SingleTask -> ResponseEntity.status(HttpStatus.CREATED)
                    .body(response.task.toDto() as Any)
                else -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(MessageResponse("Failed to create task") as Any)
            }
        }
    }

    /**
     * Task 완료 상태 토글
     * PATCH /api/tasks/{id}/toggle
     */
    @PatchMapping("/{id}/toggle")
    fun toggleTask(@PathVariable id: String): CompletionStage<ResponseEntity<Any>> {
        return AskPattern.ask(
            taskActor,
            { replyTo: ActorRef<TaskResponse> -> ToggleTask(id, replyTo) },
            timeout,
            actorSystem.scheduler()
        ).thenApply { response ->
            when (response) {
                is SingleTask -> ResponseEntity.ok(response.task.toDto() as Any)
                is TaskNotFound -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(MessageResponse("Task not found: ${response.id}") as Any)
                else -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(MessageResponse("Failed to toggle task") as Any)
            }
        }
    }

    /**
     * Task 삭제
     * DELETE /api/tasks/{id}
     */
    @DeleteMapping("/{id}")
    fun deleteTask(@PathVariable id: String): CompletionStage<ResponseEntity<Any>> {
        return AskPattern.ask(
            taskActor,
            { replyTo: ActorRef<TaskResponse> -> DeleteTask(id, replyTo) },
            timeout,
            actorSystem.scheduler()
        ).thenApply { response ->
            when (response) {
                is TaskDeleted -> ResponseEntity.ok(
                    MessageResponse("Task deleted: ${response.id}") as Any
                )
                is TaskNotFound -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(MessageResponse("Task not found: ${response.id}") as Any)
                else -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(MessageResponse("Failed to delete task") as Any)
            }
        }
    }

    private fun Task.toDto() = TaskDto(
        id = id,
        title = title,
        description = description,
        completed = completed,
        createdAt = createdAt.toString()
    )
}
