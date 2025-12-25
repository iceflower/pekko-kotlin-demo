package com.example.pekko.http

import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.javadsl.Receive
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Task 데이터 클래스
 */
data class Task(
    val id: Long,
    val title: String,
    val completed: Boolean = false
)

/**
 * Task 레지스트리 Actor
 * Task CRUD 작업을 처리
 */
object TaskRegistry {

    sealed interface Command
    data class GetAllTasks(val replyTo: ActorRef<Tasks>) : Command
    data class GetTask(val id: Long, val replyTo: ActorRef<TaskResponse>) : Command
    data class CreateTask(val title: String, val replyTo: ActorRef<Task>) : Command
    data class UpdateTask(val id: Long, val title: String?, val completed: Boolean?, val replyTo: ActorRef<TaskResponse>) : Command
    data class DeleteTask(val id: Long, val replyTo: ActorRef<TaskResponse>) : Command

    data class Tasks(val tasks: List<Task>)
    sealed interface TaskResponse {
        data class Found(val task: Task) : TaskResponse
        data object NotFound : TaskResponse
        data object Deleted : TaskResponse
    }

    fun create(): Behavior<Command> = Behaviors.setup { context ->
        TaskRegistryBehavior(context)
    }

    private class TaskRegistryBehavior(
        context: ActorContext<Command>
    ) : AbstractBehavior<Command>(context) {

        private val tasks = ConcurrentHashMap<Long, Task>()
        private val idCounter = AtomicLong(0)

        init {
            context.log.info("TaskRegistry 시작됨")
            // 샘플 데이터 추가
            createTask("Pekko 학습하기")
            createTask("REST API 구현하기")
            createTask("테스트 작성하기")
        }

        private fun createTask(title: String): Task {
            val id = idCounter.incrementAndGet()
            val task = Task(id, title)
            tasks[id] = task
            return task
        }

        override fun createReceive(): Receive<Command> {
            return newReceiveBuilder()
                .onMessage(GetAllTasks::class.java, this::onGetAllTasks)
                .onMessage(GetTask::class.java, this::onGetTask)
                .onMessage(CreateTask::class.java, this::onCreateTask)
                .onMessage(UpdateTask::class.java, this::onUpdateTask)
                .onMessage(DeleteTask::class.java, this::onDeleteTask)
                .build()
        }

        private fun onGetAllTasks(cmd: GetAllTasks): Behavior<Command> {
            cmd.replyTo.tell(Tasks(tasks.values.toList()))
            return this
        }

        private fun onGetTask(cmd: GetTask): Behavior<Command> {
            val task = tasks[cmd.id]
            if (task != null) {
                cmd.replyTo.tell(TaskResponse.Found(task))
            } else {
                cmd.replyTo.tell(TaskResponse.NotFound)
            }
            return this
        }

        private fun onCreateTask(cmd: CreateTask): Behavior<Command> {
            val task = createTask(cmd.title)
            context.log.info("Task 생성됨: $task")
            cmd.replyTo.tell(task)
            return this
        }

        private fun onUpdateTask(cmd: UpdateTask): Behavior<Command> {
            val existing = tasks[cmd.id]
            if (existing != null) {
                val updated = existing.copy(
                    title = cmd.title ?: existing.title,
                    completed = cmd.completed ?: existing.completed
                )
                tasks[cmd.id] = updated
                context.log.info("Task 업데이트됨: $updated")
                cmd.replyTo.tell(TaskResponse.Found(updated))
            } else {
                cmd.replyTo.tell(TaskResponse.NotFound)
            }
            return this
        }

        private fun onDeleteTask(cmd: DeleteTask): Behavior<Command> {
            val removed = tasks.remove(cmd.id)
            if (removed != null) {
                context.log.info("Task 삭제됨: $removed")
                cmd.replyTo.tell(TaskResponse.Deleted)
            } else {
                cmd.replyTo.tell(TaskResponse.NotFound)
            }
            return this
        }
    }
}
