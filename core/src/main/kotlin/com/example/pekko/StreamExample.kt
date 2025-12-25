package com.example.pekko

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.japi.function.Function
import org.apache.pekko.stream.javadsl.Source
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

/**
 * Pekko Streams 예제
 *
 * Pekko Streams는 비동기 스트림 처리를 위한 라이브러리입니다.
 * Reactive Streams 표준을 구현하며, 백프레셔(backpressure)를 자동으로 처리합니다.
 */
object StreamExample {

    /**
     * 기본 스트림 예제
     * Source -> Flow -> Sink 파이프라인 구성
     */
    fun runBasicStream(system: ActorSystem<*>): CompletionStage<Done> {
        // 파이프라인 연결 및 실행
        println("\n=== Pekko Streams 기본 예제 ===")
        println("1~10의 제곱 중 짝수만 출력:")

        // Source -> map -> filter -> foreach 체이닝
        return Source.range(1, 10)
            .map { it * it }           // 각 숫자를 제곱
            .filter { it % 2 == 0 }    // 짝수만 필터링
            .runForeach({ value ->
                println("  스트림 결과: $value")
            }, system)
    }

    /**
     * 비동기 처리 스트림 예제
     */
    fun runAsyncStream(system: ActorSystem<*>): CompletionStage<Done> {
        println("\n=== Pekko Streams 비동기 예제 ===")
        println("비동기로 데이터 처리 중...")

        return Source.range(1, 5)
            .mapAsync<String>(3, Function { num ->
                // 최대 3개 병렬 처리
                CompletableFuture.supplyAsync {
                    Thread.sleep(100)  // 가상의 비동기 작업
                    "처리된 값: ${num * 10}"
                }
            })
            .runForeach({ result: String ->
                println("  $result")
            }, system)
    }
}
