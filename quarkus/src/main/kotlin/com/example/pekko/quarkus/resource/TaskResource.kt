package com.example.pekko.quarkus.resource

import com.example.pekko.quarkus.actor.*
import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Scheduler
import org.apache.pekko.actor.typed.javadsl.AskPattern
import java.time.Duration
import java.util.concurrent.CompletionStage

/**
 * Task 생성 요청 DTO
 */
data class CreateTaskRequest(
    val title: String = "",
    val description: String = ""
)

/**
 * Task REST API Resource
 *
 * Quarkus REST (RESTEasy Reactive)를 사용하여
 * Pekko Actor와 Ask 패턴으로 통신합니다.
 */
@Path("/api/tasks")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class TaskResource {

    @Inject
    lateinit var scheduler: Scheduler

    @Inject
    @Named("taskActor")
    lateinit var taskActor: ActorRef<TaskCommand>

    private val timeout: Duration = Duration.ofSeconds(5)

    /**
     * 모든 Task 조회
     */
    @GET
    fun getAllTasks(): CompletionStage<Response> {
        return AskPattern.ask(
            taskActor,
            { replyTo: ActorRef<TaskResponse> -> GetAllTasks(replyTo) },
            timeout,
            scheduler
        ).thenApply { response ->
            when (response) {
                is TaskList -> Response.ok(response.tasks).build()
                else -> Response.serverError().build()
            }
        }
    }

    /**
     * ID로 Task 조회
     */
    @GET
    @Path("/{id}")
    fun getTask(@PathParam("id") id: String): CompletionStage<Response> {
        return AskPattern.ask(
            taskActor,
            { replyTo: ActorRef<TaskResponse> -> GetTask(id, replyTo) },
            timeout,
            scheduler
        ).thenApply { response ->
            when (response) {
                is SingleTask -> Response.ok(response.task).build()
                is TaskNotFound -> Response.status(Response.Status.NOT_FOUND)
                    .entity(mapOf("error" to "Task not found: ${response.id}"))
                    .build()
                else -> Response.serverError().build()
            }
        }
    }

    /**
     * 새 Task 생성
     */
    @POST
    fun createTask(request: CreateTaskRequest): CompletionStage<Response> {
        return AskPattern.ask(
            taskActor,
            { replyTo: ActorRef<TaskResponse> ->
                CreateTask(request.title, request.description, replyTo)
            },
            timeout,
            scheduler
        ).thenApply { response ->
            when (response) {
                is SingleTask -> Response.status(Response.Status.CREATED)
                    .entity(response.task)
                    .build()
                else -> Response.serverError().build()
            }
        }
    }

    /**
     * Task 완료 상태 토글
     */
    @PATCH
    @Path("/{id}/toggle")
    fun toggleTask(@PathParam("id") id: String): CompletionStage<Response> {
        return AskPattern.ask(
            taskActor,
            { replyTo: ActorRef<TaskResponse> -> ToggleTask(id, replyTo) },
            timeout,
            scheduler
        ).thenApply { response ->
            when (response) {
                is SingleTask -> Response.ok(response.task).build()
                is TaskNotFound -> Response.status(Response.Status.NOT_FOUND)
                    .entity(mapOf("error" to "Task not found: ${response.id}"))
                    .build()
                else -> Response.serverError().build()
            }
        }
    }

    /**
     * Task 삭제
     */
    @DELETE
    @Path("/{id}")
    fun deleteTask(@PathParam("id") id: String): CompletionStage<Response> {
        return AskPattern.ask(
            taskActor,
            { replyTo: ActorRef<TaskResponse> -> DeleteTask(id, replyTo) },
            timeout,
            scheduler
        ).thenApply { response ->
            when (response) {
                is TaskDeleted -> Response.noContent().build()
                is TaskNotFound -> Response.status(Response.Status.NOT_FOUND)
                    .entity(mapOf("error" to "Task not found: ${response.id}"))
                    .build()
                else -> Response.serverError().build()
            }
        }
    }
}
