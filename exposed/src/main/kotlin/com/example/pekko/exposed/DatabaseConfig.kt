package com.example.pekko.exposed

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * Database configuration with HikariCP connection pool.
 */
object DatabaseConfig {

    private var dataSource: HikariDataSource? = null
    private var database: Database? = null

    /**
     * Initialize the database connection pool.
     */
    fun init(
        url: String = "jdbc:h2:mem:pekko_demo;DB_CLOSE_DELAY=-1;",
        driver: String = "org.h2.Driver",
        user: String = "sa",
        password: String = ""
    ): Database {
        val config = HikariConfig().apply {
            jdbcUrl = url
            driverClassName = driver
            username = user
            this.password = password
            maximumPoolSize = 10
            minimumIdle = 2
            idleTimeout = 30000
            connectionTimeout = 20000
            maxLifetime = 1800000
        }

        dataSource = HikariDataSource(config)

        database = Database.connect(dataSource!!)

        // Create tables
        transaction(database!!) {
            SchemaUtils.create(Users, Tasks, TaskAssignments)
        }

        return database!!
    }

    /**
     * Get the current database instance.
     */
    fun getDatabase(): Database? = database

    /**
     * Shutdown the connection pool.
     */
    fun shutdown() {
        dataSource?.close()
        dataSource = null
        database = null
    }
}
