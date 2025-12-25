package com.example.pekko.http

import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.javadsl.AskPattern
import org.apache.pekko.http.javadsl.marshallers.jackson.Jackson
import org.apache.pekko.http.javadsl.model.StatusCodes
import org.apache.pekko.http.javadsl.server.AllDirectives
import org.apache.pekko.http.javadsl.server.PathMatchers.longSegment
import org.apache.pekko.http.javadsl.server.PathMatchers.segment
import org.apache.pekko.http.javadsl.server.Route
import java.time.Duration

/**
 * Task REST API 라우트
 *
 * GET    /api/tasks      - 모든 Task 조회
 * GET    /api/tasks/{id} - 특정 Task 조회
 * POST   /api/tasks      - Task 생성
 * PUT    /api/tasks/{id} - Task 업데이트
 * DELETE /api/tasks/{id} - Task 삭제
 */
class TaskRoutes(
    private val system: ActorSystem<*>,
    private val taskRegistry: ActorRef<TaskRegistry.Command>
) : AllDirectives() {

    private val timeout = Duration.ofSeconds(3)

    // JSON 요청 DTO
    data class CreateTaskRequest(val title: String = "")
    data class UpdateTaskRequest(val title: String? = null, val completed: Boolean? = null)

    fun routes(): Route = pathPrefix("api") {
        concat(
            taskRoutes()
        )
    }

    private fun taskRoutes(): Route = pathPrefix("tasks") {
        concat(
            // GET /api/tasks
            pathEnd {
                get {
                    val future = AskPattern.ask(
                        taskRegistry,
                        { replyTo: ActorRef<TaskRegistry.Tasks> -> TaskRegistry.GetAllTasks(replyTo) },
                        timeout,
                        system.scheduler()
                    )
                    onSuccess(future) { tasks ->
                        complete(StatusCodes.OK, tasks.tasks, Jackson.marshaller())
                    }
                }
            },
            // POST /api/tasks
            pathEnd {
                post {
                    entity(Jackson.unmarshaller(CreateTaskRequest::class.java)) { request ->
                        val future = AskPattern.ask(
                            taskRegistry,
                            { replyTo: ActorRef<Task> -> TaskRegistry.CreateTask(request.title, replyTo) },
                            timeout,
                            system.scheduler()
                        )
                        onSuccess(future) { task ->
                            complete(StatusCodes.CREATED, task, Jackson.marshaller())
                        }
                    }
                }
            },
            // GET /api/tasks/{id}
            path(longSegment()) { id ->
                get {
                    val future = AskPattern.ask(
                        taskRegistry,
                        { replyTo: ActorRef<TaskRegistry.TaskResponse> -> TaskRegistry.GetTask(id, replyTo) },
                        timeout,
                        system.scheduler()
                    )
                    onSuccess(future) { response ->
                        when (response) {
                            is TaskRegistry.TaskResponse.Found ->
                                complete(StatusCodes.OK, response.task, Jackson.marshaller())
                            is TaskRegistry.TaskResponse.NotFound ->
                                complete(StatusCodes.NOT_FOUND, mapOf("error" to "Task not found"), Jackson.marshaller())
                            else ->
                                complete(StatusCodes.INTERNAL_SERVER_ERROR)
                        }
                    }
                }
            },
            // PUT /api/tasks/{id}
            path(longSegment()) { id ->
                put {
                    entity(Jackson.unmarshaller(UpdateTaskRequest::class.java)) { request ->
                        val future = AskPattern.ask(
                            taskRegistry,
                            { replyTo: ActorRef<TaskRegistry.TaskResponse> ->
                                TaskRegistry.UpdateTask(id, request.title, request.completed, replyTo)
                            },
                            timeout,
                            system.scheduler()
                        )
                        onSuccess(future) { response ->
                            when (response) {
                                is TaskRegistry.TaskResponse.Found ->
                                    complete(StatusCodes.OK, response.task, Jackson.marshaller())
                                is TaskRegistry.TaskResponse.NotFound ->
                                    complete(StatusCodes.NOT_FOUND, mapOf("error" to "Task not found"), Jackson.marshaller())
                                else ->
                                    complete(StatusCodes.INTERNAL_SERVER_ERROR)
                            }
                        }
                    }
                }
            },
            // DELETE /api/tasks/{id}
            path(longSegment()) { id ->
                delete {
                    val future = AskPattern.ask(
                        taskRegistry,
                        { replyTo: ActorRef<TaskRegistry.TaskResponse> -> TaskRegistry.DeleteTask(id, replyTo) },
                        timeout,
                        system.scheduler()
                    )
                    onSuccess(future) { response ->
                        when (response) {
                            is TaskRegistry.TaskResponse.Deleted ->
                                complete(StatusCodes.NO_CONTENT)
                            is TaskRegistry.TaskResponse.NotFound ->
                                complete(StatusCodes.NOT_FOUND, mapOf("error" to "Task not found"), Jackson.marshaller())
                            else ->
                                complete(StatusCodes.INTERNAL_SERVER_ERROR)
                        }
                    }
                }
            }
        )
    }
}
