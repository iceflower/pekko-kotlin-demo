package com.example.pekko.exposed

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * HikariCP 커넥션 풀을 사용한 데이터베이스 설정.
 */
object DatabaseConfig {

    private var dataSource: HikariDataSource? = null
    private var database: Database? = null

    /**
     * 데이터베이스 커넥션 풀 초기화.
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

        // 테이블 생성
        transaction(database!!) {
            SchemaUtils.create(Users, Tasks, TaskAssignments)
        }

        return database!!
    }

    /**
     * 현재 데이터베이스 인스턴스 반환.
     */
    fun getDatabase(): Database? = database

    /**
     * 커넥션 풀 종료.
     */
    fun shutdown() {
        dataSource?.close()
        dataSource = null
        database = null
    }
}
