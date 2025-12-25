# Apache Pekko + Kotlin 학습 가이드

## Pekko란?

**Apache Pekko**는 Akka 2.6.x의 오픈소스 포크입니다. 2022년 Lightbend가 Akka의 라이선스를 BSL(Business Source License)로 변경하면서, Apache 재단 아래에서 커뮤니티 주도로 탄생했습니다.

### 핵심 특징

| 특징                   | 설명                    |
|----------------------|-----------------------|
| **Actor Model**      | 동시성을 메시지 패싱으로 처리하는 모델 |
| **분산 시스템**           | 클러스터링, 리모팅 지원         |
| **Reactive Streams** | 백프레셔를 지원하는 비동기 스트림    |
| **Fault Tolerance**  | Supervisor 전략으로 장애 복구 |
| **Apache 2.0 라이선스**  | 상업적 사용 무료             |

---

## Actor Model 기초

### Actor란?

Actor는 동시성 프로그래밍의 기본 단위입니다. 각 Actor는:

- **자신만의 상태**를 가짐 (외부에서 직접 접근 불가)
- **메시지**를 통해서만 통신
- **한 번에 하나의 메시지**만 처리 (Thread-safe)
- **다른 Actor를 생성**할 수 있음

```
┌─────────────┐     메시지      ┌─────────────┐
│   Actor A   │ ──────────────▶ │   Actor B   │
│  (Sender)   │                 │  (Receiver) │
└─────────────┘                 └─────────────┘
                                      │
                                      ▼
                                 ┌─────────┐
                                 │ Mailbox │ ← 메시지 큐
                                 └─────────┘
```

### Typed Actor (권장)

Pekko는 **Typed Actor**를 권장합니다. 컴파일 타임에 메시지 타입을 검증할 수 있습니다.

```kotlin
// 메시지 정의
sealed interface Command
data class Greet(val name: String, val replyTo: ActorRef<Response>) : Command

// Actor 생성
fun create(): Behavior<Command> {
    return Behaviors.receive { context, message ->
        when (message) {
            is Greet -> {
                context.log.info("Hello, ${message.name}!")
                message.replyTo.tell(Response("Hi!"))
                Behaviors.same()
            }
        }
    }
}
```

---

## 프로젝트 구조

```
pekko-kotlin-demo/
├── build.gradle.kts              # 루트 빌드 설정
├── settings.gradle.kts           # 멀티모듈 설정
├── core/                         # 기본 Actor 예제
│   └── src/main/kotlin/com/example/pekko/
│       ├── HelloWorldActor.kt    # 클래스 기반 Actor
│       ├── CounterActor.kt       # 함수형 Actor
│       ├── StreamExample.kt      # Streams 예제
│       └── AskPatternExample.kt  # 요청-응답 패턴
├── cluster/                      # 클러스터링 예제
│   └── src/main/kotlin/com/example/pekko/cluster/
│       ├── ClusterListener.kt    # 클러스터 이벤트 리스너
│       ├── SingletonCounter.kt   # 클러스터 싱글톤
│       └── ClusterMain.kt        # 진입점
├── persistence/                  # 이벤트 소싱 예제
│   └── src/main/kotlin/com/example/pekko/persistence/
│       ├── PersistentCounter.kt  # EventSourcedBehavior
│       └── PersistenceMain.kt    # 진입점
├── http/                         # REST API 예제
│   └── src/main/kotlin/com/example/pekko/http/
│       ├── TaskRegistry.kt       # Task CRUD Actor
│       ├── TaskRoutes.kt         # REST API 라우트
│       └── HttpMain.kt           # 진입점
└── grpc/                         # gRPC 예제
    ├── src/main/proto/
    │   └── greeter.proto         # 서비스 정의
    └── src/main/kotlin/com/example/pekko/grpc/
        ├── GreeterActor.kt       # 인사 처리 Actor
        ├── GreeterServiceImpl.kt # gRPC 서비스 구현
        └── GrpcMain.kt           # 진입점
```

---

## Actor 구현 스타일

### 1. 클래스 기반 (AbstractBehavior)

상태가 많거나 복잡한 로직에 적합합니다.

```kotlin
class MyActor(context: ActorContext<Command>) : AbstractBehavior<Command>(context) {

    private var count = 0  // 내부 상태

    override fun createReceive(): Receive<Command> {
        return newReceiveBuilder()
            .onMessage(Increment::class.java) {
                count++
                this
            }
            .build()
    }
}
```

### 2. 함수형 (Behaviors.receive)

간단한 Actor나 상태 변경이 명확할 때 적합합니다.

```kotlin
fun counter(count: Int): Behavior<Command> {
    return Behaviors.receive { context, message ->
        when (message) {
            is Increment -> counter(count + 1)  // 새 상태로 재귀
            is GetValue -> {
                message.replyTo.tell(Value(count))
                Behaviors.same()
            }
        }
    }
}
```

---

## 주요 패턴

### Ask 패턴 (요청-응답)

Actor에게 메시지를 보내고 응답을 `CompletionStage`로 받습니다.

```kotlin
val future: CompletionStage<Response> = AskPattern.ask(
    actorRef,
    { replyTo -> GetData(replyTo) },
    Duration.ofSeconds(3),
    system.scheduler()
)

future.thenAccept { response ->
    println("받은 응답: $response")
}
```

### Tell 패턴 (Fire-and-Forget)

응답을 기다리지 않고 메시지만 전송합니다.

```kotlin
actorRef.tell(MyMessage("data"))
```

### Pipe 패턴

비동기 작업 결과를 Actor에게 전달합니다.

```kotlin
context.pipeToSelf(asyncFuture) { result, error ->
    if (error != null) OperationFailed(error)
    else OperationSuccess(result)
}
```

---

## Pekko Streams

백프레셔를 자동 처리하는 비동기 스트림 라이브러리입니다.

### 기본 개념

```
Source ──▶ Flow ──▶ Flow ──▶ Sink
(생산자)    (변환)    (변환)   (소비자)
```

### 예제

```kotlin
Source.range(1, 100)
    .map { it * 2 }           // 변환
    .filter { it > 50 }       // 필터링
    .mapAsync(4) { async(it) } // 병렬 비동기 처리
    .runWith(Sink.foreach { println(it) }, system)
```

---

## 설정 (application.conf)

```hocon
pekko {
  loglevel = "INFO"

  actor {
    # 기본 디스패처 설정
    default-dispatcher {
      throughput = 10
    }

    # 직렬화 설정 (클러스터링 시 필요)
    serialization-bindings {
      "com.example.CborSerializable" = jackson-cbor
    }
  }
}
```

---

## 테스트 (TestKit)

```kotlin
class MyActorTest {
    private val testKit = ActorTestKit.create()

    @AfterAll
    fun cleanup() = testKit.shutdownTestKit()

    @Test
    fun `Actor가 메시지에 응답해야 한다`() {
        val actor = testKit.spawn(MyActor.create())
        val probe = testKit.createTestProbe<Response>()

        actor.tell(GetData(probe.ref()))

        val response = probe.receiveMessage()
        assertEquals("expected", response.data)
    }
}
```

---

## 실행 명령어

```bash
# 전체 빌드
./gradlew build

# 모듈별 실행
./gradlew :core:run                    # 기본 Actor 예제
./gradlew :cluster:run                 # 클러스터 예제
./gradlew :persistence:run             # 이벤트 소싱 예제
./gradlew :http:run                    # REST API 서버 (localhost:8080)
./gradlew :grpc:run                    # gRPC 서버 (localhost:50051)

# 테스트
./gradlew test
```

### HTTP API 테스트

```bash
# Task 목록 조회
curl http://localhost:8080/api/tasks

# Task 생성
curl -X POST -H "Content-Type: application/json" \
  -d '{"title":"새 작업"}' http://localhost:8080/api/tasks

# Task 업데이트
curl -X PUT -H "Content-Type: application/json" \
  -d '{"completed":true}' http://localhost:8080/api/tasks/1

# Task 삭제
curl -X DELETE http://localhost:8080/api/tasks/1
```

### gRPC 테스트 (grpcurl 필요)

```bash
# SayHello 호출
grpcurl -plaintext -d '{"name":"Pekko"}' \
  localhost:50051 com.example.pekko.grpc.GreeterService/SayHello

# SayHelloStream 호출
grpcurl -plaintext -d '{"name":"Pekko"}' \
  localhost:50051 com.example.pekko.grpc.GreeterService/SayHelloStream
```

---

## Akka에서 Pekko로 마이그레이션

| Akka                        | Pekko                          |
|-----------------------------|--------------------------------|
| `akka.actor.typed`          | `org.apache.pekko.actor.typed` |
| `com.typesafe.akka:akka-*`  | `org.apache.pekko:pekko-*`     |
| `akka { }` (설정)             | `pekko { }`                    |

패키지명과 설정 키만 변경하면 대부분 호환됩니다.

---

## 참고 자료

- [Apache Pekko 공식 사이트](https://pekko.apache.org/)
- [Pekko GitHub](https://github.com/apache/pekko)
- [Pekko 문서](https://pekko.apache.org/docs/pekko/current/)
- [Maven Central - Pekko](https://mvnrepository.com/artifact/org.apache.pekko)

---

## 구현된 모듈

### 1. 클러스터링 (`cluster/`)
`pekko-cluster-typed`로 구현된 분산 Actor 시스템:
- **ClusterListener**: 클러스터 이벤트(멤버 가입/탈퇴) 모니터링
- **SingletonCounter**: 클러스터 전체에서 단일 인스턴스 보장

### 2. Persistence (`persistence/`)
`pekko-persistence-typed`로 구현된 이벤트 소싱:
- **PersistentCounter**: 상태를 이벤트로 저장하고 재시작 시 복구
- LevelDB 저널 사용

### 3. HTTP (`http/`)
`pekko-http`로 구현된 REST API 서버:
- Task CRUD API (GET, POST, PUT, DELETE)
- Jackson을 사용한 JSON 직렬화
- Actor 기반 상태 관리

### 4. gRPC (`grpc/`)
`grpc-java`와 Pekko Actor를 결합한 gRPC 서비스:
- **SayHello**: Unary RPC
- **SayHelloStream**: Server Streaming RPC
- protobuf-gradle-plugin으로 스텁 생성

---

## 추가 학습 자료

- **Cluster Sharding**: 대용량 Actor를 여러 노드에 분산
- **Distributed Data**: CRDTs를 사용한 분산 상태 관리
- **Alpakka**: 외부 시스템(Kafka, DB 등) 연동 커넥터
