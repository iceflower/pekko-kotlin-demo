package com.example.pekko.exposed

import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/**
 * Task table definition using Exposed DSL.
 */
object Tasks : LongIdTable("tasks") {
    val title = varchar("title", 255)
    val description = text("description").nullable()
    val completed = bool("completed").default(false)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at").nullable()
}

/**
 * User table definition.
 */
object Users : LongIdTable("users") {
    val name = varchar("name", 100)
    val email = varchar("email", 255).uniqueIndex()
    val createdAt = timestamp("created_at")
}

/**
 * Task assignment (many-to-many relationship).
 */
object TaskAssignments : LongIdTable("task_assignments") {
    val task = reference("task_id", Tasks)
    val user = reference("user_id", Users)
    val assignedAt = timestamp("assigned_at")
}
