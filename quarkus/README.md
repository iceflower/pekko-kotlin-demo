# Quarkus + Pekko Actor 통합 예제

이 모듈은 Quarkus 프레임워크와 Apache Pekko Actor를 함께 사용하는 방법을 보여줍니다.

## 아키텍처

```mermaid
flowchart TB
    subgraph Quarkus[Quarkus Application]
        subgraph CDI[CDI Container]
            Config[PekkoConfig<br/>@ApplicationScoped]
            Resource[TaskResource<br/>@Path]
        end

        subgraph Pekko[Pekko Actor System]
            System[ActorSystem]
            Actor[TaskActor]
        end
    end

    Client[HTTP Client] -->|REST API| Resource
    Resource -->|Ask Pattern| Actor
    Config -->|@Produces| System
    Config -->|@Produces| Actor
    System --> Actor
```

## Quarkus vs Spring Boot

| 특성 | Quarkus | Spring Boot |
|------|---------|-------------|
| **시작 시간** | ~0.5초 (JVM), ~10ms (Native) | ~2-3초 |
| **메모리 사용** | 낮음 (특히 Native) | 보통 |
| **DI 컨테이너** | CDI (ArC) | Spring IoC |
| **빌드 방식** | 빌드 타임 최적화 | 런타임 리플렉션 |
| **Native Image** | 1등급 지원 | 지원 (제한적) |

## 주요 구성 요소

### 1. PekkoConfig (`config/PekkoConfig.kt`)
- Quarkus CDI를 사용하여 `ActorSystem`을 Bean으로 등록
- `@Observes StartupEvent`로 시작 시 초기화
- `@Observes ShutdownEvent`로 종료 시 graceful shutdown
- `@Produces`로 ActorSystem과 TaskActor를 CDI Bean으로 제공

### 2. TaskActor (`actor/TaskActor.kt`)
- Task CRUD 작업을 처리하는 Actor
- Command 패턴으로 메시지 정의
- 상태를 내부 ConcurrentHashMap으로 관리

### 3. TaskResource (`resource/TaskResource.kt`)
- JAX-RS(REST) 엔드포인트 제공
- CDI `@Inject`로 ActorRef 주입
- Ask 패턴으로 Actor와 비동기 통신
- `CompletionStage`를 반환하여 non-blocking 처리

## 실행 방법

```bash
# 프로젝트 루트에서
GRADLE_USER_HOME=/c/gradle-home ./gradlew :quarkus:quarkusDev
```

서버가 시작되면 http://localhost:8082 에서 접근 가능합니다.

## API 엔드포인트

| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | `/api/tasks` | 모든 Task 조회 |
| GET | `/api/tasks/{id}` | ID로 Task 조회 |
| POST | `/api/tasks` | 새 Task 생성 |
| PATCH | `/api/tasks/{id}/toggle` | Task 완료 상태 토글 |
| DELETE | `/api/tasks/{id}` | Task 삭제 |

## API 사용 예시

### Task 생성
```bash
curl -X POST http://localhost:8082/api/tasks \
  -H "Content-Type: application/json" \
  -d '{"title": "새 작업", "description": "작업 설명"}'
```

### 모든 Task 조회
```bash
curl http://localhost:8082/api/tasks
```

### Task 완료 토글
```bash
curl -X PATCH http://localhost:8082/api/tasks/{id}/toggle
```

### Task 삭제
```bash
curl -X DELETE http://localhost:8082/api/tasks/{id}
```

## Quarkus와 Pekko 통합의 이점

1. **빠른 시작 시간**: Quarkus의 빌드 타임 최적화로 빠른 부팅
2. **낮은 메모리**: 클라우드 환경에 최적화된 리소스 사용
3. **CDI 통합**: 표준 CDI로 ActorRef를 쉽게 주입
4. **Dev 모드**: 코드 변경 시 자동 재시작 (Live Reload)
5. **Native 빌드**: GraalVM Native Image로 컴파일 가능
6. **생명주기 관리**: Quarkus 이벤트로 ActorSystem 생명주기 자동 관리

## 테스트

Kotest FunSpec 스타일로 작성된 테스트:

```bash
./gradlew :quarkus:test
```

### 테스트 예제 (Kotest)

```kotlin
class TaskActorTest : FunSpec({

    val testKit = ActorTestKit.create()

    afterSpec {
        testKit.shutdownTestKit()
    }

    test("CreateTask로 새 Task를 생성할 수 있다") {
        val taskActor = testKit.spawn(TaskActor.create())
        val probe = testKit.createTestProbe<TaskResponse>()

        taskActor.tell(CreateTask("테스트 작업", "설명", probe.ref()))

        val response = probe.receiveMessage()
        response.shouldBeInstanceOf<SingleTask>()
        (response as SingleTask).task.title shouldBe "테스트 작업"
    }
})
```

테스트 파일:
- `TaskActorTest.kt`

## 의존성

- Quarkus 3.30.5
- Apache Pekko 1.4.0
- Kotlin 2.3.0
- Kotest 6.0.5

## Native 빌드 (선택사항)

```bash
# GraalVM 필요
./gradlew :quarkus:build -Dquarkus.native.enabled=true
```

## 참고 자료

- [Quarkus 공식 문서](https://quarkus.io/guides/)
- [Quarkus Kotlin 가이드](https://quarkus.io/guides/kotlin)
- [Quarkus CDI 가이드](https://quarkus.io/guides/cdi-reference)
- [Apache Pekko 문서](https://pekko.apache.org/docs/pekko/current/)
