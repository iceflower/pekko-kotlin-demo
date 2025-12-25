package com.example.pekko.quarkus.actor

import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.javadsl.Receive
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Task 데이터 클래스
 */
data class Task(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String,
    val completed: Boolean = false,
    val createdAt: LocalDateTime = LocalDateTime.now()
)

/**
 * TaskActor가 처리하는 명령(Command) 정의
 */
sealed interface TaskCommand

/**
 * 모든 Task 목록 조회
 */
data class GetAllTasks(val replyTo: ActorRef<TaskResponse>) : TaskCommand

/**
 * ID로 Task 조회
 */
data class GetTask(val id: String, val replyTo: ActorRef<TaskResponse>) : TaskCommand

/**
 * 새 Task 생성
 */
data class CreateTask(
    val title: String,
    val description: String,
    val replyTo: ActorRef<TaskResponse>
) : TaskCommand

/**
 * Task 완료 상태 토글
 */
data class ToggleTask(val id: String, val replyTo: ActorRef<TaskResponse>) : TaskCommand

/**
 * Task 삭제
 */
data class DeleteTask(val id: String, val replyTo: ActorRef<TaskResponse>) : TaskCommand

/**
 * TaskActor 응답 타입
 */
sealed interface TaskResponse

data class SingleTask(val task: Task) : TaskResponse
data class TaskList(val tasks: List<Task>) : TaskResponse
data class TaskDeleted(val id: String) : TaskResponse
data class TaskNotFound(val id: String) : TaskResponse
data class TaskError(val message: String) : TaskResponse

/**
 * Task 관리를 담당하는 Actor
 *
 * Quarkus REST Resource에서 Ask 패턴을 통해 이 Actor와 통신합니다.
 */
class TaskActor private constructor(
    context: ActorContext<TaskCommand>
) : AbstractBehavior<TaskCommand>(context) {

    private val tasks = ConcurrentHashMap<String, Task>()

    companion object {
        fun create(): Behavior<TaskCommand> {
            return Behaviors.setup { context -> TaskActor(context) }
        }
    }

    init {
        context.log.info("TaskActor started")
        // 샘플 데이터 추가
        val sampleTask = Task(
            title = "Pekko 학습하기",
            description = "Quarkus와 Pekko Actor 통합 예제 완성"
        )
        tasks[sampleTask.id] = sampleTask
    }

    override fun createReceive(): Receive<TaskCommand> {
        return newReceiveBuilder()
            .onMessage(GetAllTasks::class.java, this::onGetAllTasks)
            .onMessage(GetTask::class.java, this::onGetTask)
            .onMessage(CreateTask::class.java, this::onCreateTask)
            .onMessage(ToggleTask::class.java, this::onToggleTask)
            .onMessage(DeleteTask::class.java, this::onDeleteTask)
            .build()
    }

    private fun onGetAllTasks(command: GetAllTasks): Behavior<TaskCommand> {
        context.log.info("Getting all tasks, count: {}", tasks.size)
        command.replyTo.tell(TaskList(tasks.values.toList()))
        return this
    }

    private fun onGetTask(command: GetTask): Behavior<TaskCommand> {
        val task = tasks[command.id]
        if (task != null) {
            command.replyTo.tell(SingleTask(task))
        } else {
            command.replyTo.tell(TaskNotFound(command.id))
        }
        return this
    }

    private fun onCreateTask(command: CreateTask): Behavior<TaskCommand> {
        val task = Task(
            title = command.title,
            description = command.description
        )
        tasks[task.id] = task
        context.log.info("Created task: {} - {}", task.id, task.title)
        command.replyTo.tell(SingleTask(task))
        return this
    }

    private fun onToggleTask(command: ToggleTask): Behavior<TaskCommand> {
        val task = tasks[command.id]
        if (task != null) {
            val updated = task.copy(completed = !task.completed)
            tasks[command.id] = updated
            context.log.info("Toggled task: {} - completed: {}", task.id, updated.completed)
            command.replyTo.tell(SingleTask(updated))
        } else {
            command.replyTo.tell(TaskNotFound(command.id))
        }
        return this
    }

    private fun onDeleteTask(command: DeleteTask): Behavior<TaskCommand> {
        val removed = tasks.remove(command.id)
        if (removed != null) {
            context.log.info("Deleted task: {}", command.id)
            command.replyTo.tell(TaskDeleted(command.id))
        } else {
            command.replyTo.tell(TaskNotFound(command.id))
        }
        return this
    }
}
