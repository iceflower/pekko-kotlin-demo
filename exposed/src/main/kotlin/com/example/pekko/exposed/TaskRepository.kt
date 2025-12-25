package com.example.pekko.exposed

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

/**
 * Task data class.
 */
data class Task(
    val id: Long,
    val title: String,
    val description: String?,
    val completed: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant?
)

/**
 * Repository for Task CRUD operations using Exposed.
 */
class TaskRepository(private val database: Database) {

    /**
     * Create a new task.
     */
    fun create(title: String, description: String? = null): Task = transaction(database) {
        val now = Clock.System.now()
        val id = Tasks.insertAndGetId {
            it[Tasks.title] = title
            it[Tasks.description] = description
            it[Tasks.completed] = false
            it[Tasks.createdAt] = now
            it[Tasks.updatedAt] = null
        }

        Task(
            id = id.value,
            title = title,
            description = description,
            completed = false,
            createdAt = now,
            updatedAt = null
        )
    }

    /**
     * Find a task by ID.
     */
    fun findById(id: Long): Task? = transaction(database) {
        Tasks.selectAll().where { Tasks.id eq id }
            .map { toTask(it) }
            .singleOrNull()
    }

    /**
     * Get all tasks.
     */
    fun findAll(): List<Task> = transaction(database) {
        Tasks.selectAll()
            .orderBy(Tasks.createdAt, SortOrder.DESC)
            .map { toTask(it) }
    }

    /**
     * Update a task.
     */
    fun update(id: Long, title: String? = null, description: String? = null, completed: Boolean? = null): Task? =
        transaction(database) {
            val updated = Tasks.update({ Tasks.id eq id }) {
                title?.let { value -> it[Tasks.title] = value }
                description?.let { value -> it[Tasks.description] = value }
                completed?.let { value -> it[Tasks.completed] = value }
                it[Tasks.updatedAt] = Clock.System.now()
            }

            if (updated > 0) findById(id) else null
        }

    /**
     * Toggle task completion status.
     */
    fun toggleCompleted(id: Long): Task? = transaction(database) {
        val task = findById(id) ?: return@transaction null
        update(id, completed = !task.completed)
    }

    /**
     * Delete a task.
     */
    fun delete(id: Long): Boolean = transaction(database) {
        Tasks.deleteWhere { Tasks.id eq id } > 0
    }

    /**
     * Count all tasks.
     */
    fun count(): Long = transaction(database) {
        Tasks.selectAll().count()
    }

    /**
     * Count completed tasks.
     */
    fun countCompleted(): Long = transaction(database) {
        Tasks.selectAll().where { Tasks.completed eq true }.count()
    }

    private fun toTask(row: ResultRow): Task = Task(
        id = row[Tasks.id].value,
        title = row[Tasks.title],
        description = row[Tasks.description],
        completed = row[Tasks.completed],
        createdAt = row[Tasks.createdAt],
        updatedAt = row[Tasks.updatedAt]
    )
}
