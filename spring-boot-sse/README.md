# Spring Boot SSE 모듈

이 모듈은 Spring Boot와 Pekko Actor를 사용한 Server-Sent Events (SSE) 통합을 보여줍니다.

## 기능

- **Spring WebFlux SSE**: 리액티브 이벤트 스트리밍
- **Pekko Actor 통합**: EventPublisher Actor가 구독 관리
- **다양한 이벤트 타입**: 여러 이벤트 카테고리 지원
- **하트비트**: 30초마다 자동 Keep-alive
- **통계 엔드포인트**: 구독자 수 및 이벤트 총계 모니터링

## 아키텍처

```mermaid
flowchart TB
    subgraph SpringBoot[Spring Boot Application]
        subgraph Controller[SseController - @RestController]
            GET1["GET /api/events"]
            POST1["POST /api/publish"]
            GET2["GET /api/stats"]
        end

        subgraph ActorSystem[Pekko ActorSystem]
            subgraph EventPublisher[EventPublisher Actor]
                Commands["Commands:<br/>Subscribe, Unsubscribe<br/>Publish, GetStats<br/><br/>Timer: 30초마다 Heartbeat"]
            end
        end

        Controller --> ActorSystem
    end
```

## 실행 방법

```bash
./gradlew :spring-boot-sse:bootRun
```

서버 시작 위치:

- HTTP: http://localhost:8083/
- SSE: http://localhost:8083/api/events

## API

### SSE 엔드포인트

**GET /api/events**

서버 전송 이벤트를 수신하기 위해 연결합니다.

```bash
curl -N http://localhost:8083/api/events
```

이벤트 형식:

```
id: <uuid>
event: <event-type>
data: {"data": "<payload>", "timestamp": 1703123456789}

```

### REST API

| Method | Endpoint                        | 설명                     |
|--------|---------------------------------|------------------------|
| GET    | `/api/events`                   | SSE 이벤트 스트림            |
| POST   | `/api/publish?type=<eventType>` | 이벤트 발행 (body = data)   |
| GET    | `/api/stats`                    | 발행자 통계 조회              |

### 이벤트 발행

```bash
curl -X POST "http://localhost:8083/api/publish?type=notification" \
  -H "Content-Type: text/plain" \
  -d "Hello, SSE!"
```

### 통계 조회

```bash
curl http://localhost:8083/api/stats
```

응답:

```json
{
  "subscriberCount": 3,
  "totalEventsPublished": 42
}
```

## 이벤트 타입

| 타입             | 설명                    |
|----------------|-----------------------|
| `connected`    | 초기 연결 시 전송            |
| `notification` | 일반 알림                 |
| `alert`        | 중요 알림                 |
| `update`       | 데이터 업데이트              |
| `heartbeat`    | Keep-alive (30초마다 자동) |

## 테스트

```bash
./gradlew :spring-boot-sse:test
```

## 브라우저로 테스트

http://localhost:8083/ 에서 내장 SSE 데모 UI를 사용할 수 있습니다.

## curl로 테스트

```bash
# 이벤트 구독 (Ctrl+C까지 실행)
curl -N http://localhost:8083/api/events

# 다른 터미널에서 이벤트 발행
curl -X POST "http://localhost:8083/api/publish?type=notification" -d "Test message"
```

## 통합 포인트

### Pekko Actor와 Spring WebFlux

Reactor의 `Sinks`를 사용하여 연동:

1. Pekko Actor 콜백 → 리액티브 스트림 emission
2. SSE 구독 → Actor Subscribe 커맨드
3. SSE 연결 해제 → Actor Unsubscribe 커맨드

### 주요 클래스

- **PekkoConfig**: ActorSystem과 EventPublisher를 Spring Bean으로 생성
- **SseController**: WebFlux SSE 엔드포인트
- **EventPublisher**: pub/sub과 하트비트를 관리하는 Actor

## Pure Pekko 모듈과의 비교

| 기능       | Spring Boot          | Pure Pekko              |
|----------|----------------------|-------------------------|
| 프레임워크    | Spring WebFlux       | Pekko HTTP              |
| SSE 형식   | ServerSentEvent<T>   | Source<ServerSentEvent> |
| 리액티브     | Project Reactor      | Pekko Streams           |
| 의존성      | Spring 생태계           | 최소                      |
| 사용 사례    | 엔터프라이즈 앱             | 마이크로서비스                 |
