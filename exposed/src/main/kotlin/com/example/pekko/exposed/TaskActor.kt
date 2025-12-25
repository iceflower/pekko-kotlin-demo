package com.example.pekko.exposed

import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.javadsl.Receive
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

/**
 * Exposed를 통해 데이터베이스 작업을 처리하는 Task Actor.
 * 블로킹 데이터베이스 작업을 위한 전용 스레드 풀을 사용합니다.
 */
class TaskActor private constructor(
    context: ActorContext<Command>,
    private val repository: TaskRepository
) : AbstractBehavior<TaskActor.Command>(context) {

    // 블로킹 데이터베이스 작업을 위한 전용 실행기
    private val dbExecutor = Executors.newFixedThreadPool(4)

    sealed interface Command

    // 커맨드
    data class CreateTask(
        val title: String,
        val description: String?,
        val replyTo: ActorRef<TaskResponse>
    ) : Command

    data class GetTask(val id: Long, val replyTo: ActorRef<TaskResponse>) : Command
    data class GetAllTasks(val replyTo: ActorRef<TaskListResponse>) : Command
    data class UpdateTask(
        val id: Long,
        val title: String?,
        val description: String?,
        val completed: Boolean?,
        val replyTo: ActorRef<TaskResponse>
    ) : Command

    data class ToggleTask(val id: Long, val replyTo: ActorRef<TaskResponse>) : Command
    data class DeleteTask(val id: Long, val replyTo: ActorRef<DeleteResponse>) : Command
    data class GetStats(val replyTo: ActorRef<StatsResponse>) : Command

    // 비동기 DB 결과를 위한 내부 메시지
    private data class DbResult<T>(val result: T, val replyTo: ActorRef<in T>) : Command
    private data class DbError(val error: Throwable, val replyTo: ActorRef<*>) : Command

    // 응답
    sealed interface TaskResponse
    data class TaskFound(val task: Task) : TaskResponse
    data object TaskNotFound : TaskResponse
    data class TaskError(val message: String) : TaskResponse

    data class TaskListResponse(val tasks: List<Task>)
    data class DeleteResponse(val deleted: Boolean)
    data class StatsResponse(val total: Long, val completed: Long)

    override fun createReceive(): Receive<Command> = newReceiveBuilder()
        .onMessage(CreateTask::class.java, this::onCreateTask)
        .onMessage(GetTask::class.java, this::onGetTask)
        .onMessage(GetAllTasks::class.java, this::onGetAllTasks)
        .onMessage(UpdateTask::class.java, this::onUpdateTask)
        .onMessage(ToggleTask::class.java, this::onToggleTask)
        .onMessage(DeleteTask::class.java, this::onDeleteTask)
        .onMessage(GetStats::class.java, this::onGetStats)
        .onMessage(DbResult::class.java) { onDbResult(it) }
        .onMessage(DbError::class.java, this::onDbError)
        .build()

    private fun onCreateTask(cmd: CreateTask): Behavior<Command> {
        runAsync(cmd.replyTo) {
            val task = repository.create(cmd.title, cmd.description)
            TaskFound(task)
        }
        return this
    }

    private fun onGetTask(cmd: GetTask): Behavior<Command> {
        runAsync(cmd.replyTo) {
            repository.findById(cmd.id)?.let { TaskFound(it) } ?: TaskNotFound
        }
        return this
    }

    private fun onGetAllTasks(cmd: GetAllTasks): Behavior<Command> {
        runAsync(cmd.replyTo) {
            TaskListResponse(repository.findAll())
        }
        return this
    }

    private fun onUpdateTask(cmd: UpdateTask): Behavior<Command> {
        runAsync(cmd.replyTo) {
            repository.update(cmd.id, cmd.title, cmd.description, cmd.completed)
                ?.let { TaskFound(it) } ?: TaskNotFound
        }
        return this
    }

    private fun onToggleTask(cmd: ToggleTask): Behavior<Command> {
        runAsync(cmd.replyTo) {
            repository.toggleCompleted(cmd.id)?.let { TaskFound(it) } ?: TaskNotFound
        }
        return this
    }

    private fun onDeleteTask(cmd: DeleteTask): Behavior<Command> {
        runAsync(cmd.replyTo) {
            DeleteResponse(repository.delete(cmd.id))
        }
        return this
    }

    private fun onGetStats(cmd: GetStats): Behavior<Command> {
        runAsync(cmd.replyTo) {
            StatsResponse(repository.count(), repository.countCompleted())
        }
        return this
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> onDbResult(result: DbResult<T>): Behavior<Command> {
        (result.replyTo as ActorRef<T>).tell(result.result)
        return this
    }

    private fun onDbError(error: DbError): Behavior<Command> {
        context.log.error("데이터베이스 오류", error.error)
        // 해당되는 경우 에러 응답 전송
        when (val replyTo = error.replyTo) {
            is ActorRef<*> -> {
                @Suppress("UNCHECKED_CAST")
                (replyTo as? ActorRef<TaskResponse>)?.tell(TaskError(error.error.message ?: "Unknown error"))
            }
        }
        return this
    }

    private fun <T> runAsync(replyTo: ActorRef<T>, block: () -> T) {
        val self = context.self
        CompletableFuture.supplyAsync(block, dbExecutor)
            .whenComplete { result, error ->
                if (error != null) {
                    self.tell(DbError(error, replyTo))
                } else {
                    self.tell(DbResult(result, replyTo))
                }
            }
    }

    companion object {
        fun create(repository: TaskRepository): Behavior<Command> = Behaviors.setup { context ->
            context.log.info("TaskActor 시작됨")
            TaskActor(context, repository)
        }
    }
}
