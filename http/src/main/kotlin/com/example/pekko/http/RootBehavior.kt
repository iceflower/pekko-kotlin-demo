package com.example.pekko.http

import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.http.javadsl.Http

/**
 * HTTP 서버의 루트 액터
 * TaskRegistry 액터를 생성하고 HTTP 서버를 시작
 */
object RootBehavior {

    sealed interface Command

    fun create(host: String, port: Int): Behavior<Command> = Behaviors.setup { context ->
        context.log.info("RootBehavior 시작됨")

        // TaskRegistry 액터 생성
        val taskRegistry = context.spawn(TaskRegistry.create(), "TaskRegistry")
        context.log.info("TaskRegistry 액터 생성됨: {}", taskRegistry.path())

        // REST API 라우트 생성
        val routes = TaskRoutes(context.system, taskRegistry)

        // HTTP 서버 시작
        Http.get(context.system)
            .newServerAt(host, port)
            .bind(routes.routes())
            .whenComplete { binding, error ->
                if (error != null) {
                    context.log.error("서버 시작 실패", error)
                    context.system.terminate()
                } else {
                    val address = binding.localAddress()
                    context.log.info("서버 시작됨: http://{}:{}/", address.hostString, address.port)
                    println("""

                    서버가 시작되었습니다!

                    테스트 방법:
                      curl http://localhost:$port/api/tasks
                      curl http://localhost:$port/api/tasks/1
                      curl -X POST -H "Content-Type: application/json" -d '{"title":"새 작업"}' http://localhost:$port/api/tasks
                      curl -X PUT -H "Content-Type: application/json" -d '{"completed":true}' http://localhost:$port/api/tasks/1
                      curl -X DELETE http://localhost:$port/api/tasks/1

                    종료: Ctrl+C
                    """.trimIndent())
                }
            }

        Behaviors.receive { _, _ ->
            Behaviors.same()
        }
    }
}
