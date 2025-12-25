# Spring Boot SSE Cluster 모듈

Spring Boot와 Pekko Cluster를 결합한 분산 Server-Sent Events 예제입니다.

## 아키텍처

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Pekko Cluster                                │
│                                                                     │
│  ┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐ │
│  │  Spring Boot 1  │    │  Spring Boot 2  │    │  Spring Boot 3  │ │
│  │   (port 8091)   │    │   (port 8092)   │    │   (port 8093)   │ │
│  │                 │    │                 │    │                 │ │
│  │ ┌─────────────┐ │    │ ┌─────────────┐ │    │ ┌─────────────┐ │ │
│  │ │Distributed  │◄────►│ │Distributed  │◄────►│ │Distributed  │ │ │
│  │ │ EventBus    │ │    │ │ EventBus    │ │    │ │ EventBus    │ │ │
│  │ └─────────────┘ │    │ └─────────────┘ │    │ └─────────────┘ │ │
│  │       ▲         │    │       ▲         │    │       ▲         │ │
│  │       │         │    │       │         │    │       │         │ │
│  │ ┌─────────────┐ │    │ ┌─────────────┐ │    │ ┌─────────────┐ │ │
│  │ │    SSE      │ │    │ │    SSE      │ │    │ │    SSE      │ │ │
│  │ │ Controller  │ │    │ │ Controller  │ │    │ │ Controller  │ │ │
│  │ └─────────────┘ │    │ └─────────────┘ │    │ └─────────────┘ │ │
│  └─────────────────┘    └─────────────────┘    └─────────────────┘ │
│                                                                     │
│               Distributed PubSub (Topic)                           │
│         ◄────────────────────────────────────────────►             │
└─────────────────────────────────────────────────────────────────────┘
```

## 주요 컴포넌트

### DistributedEventBus
- Pekko Distributed PubSub(Topic)을 사용한 클러스터 간 이벤트 공유
- 각 Spring Boot 인스턴스에서 로컬 SSE 구독자 관리

### SseController
- Spring MVC의 SseEmitter를 사용한 SSE 스트리밍
- REST API로 이벤트 발행, 구독자 목록, 통계 조회

### PekkoClusterConfig
- Spring Bean으로 Pekko ActorSystem 설정
- 클러스터 포트 및 seed-nodes 설정

## 실행 방법

### 노드 1 (Seed Node)
```bash
./gradlew :spring-boot-sse-cluster:bootRun \
  --args='--server.port=8091 --pekko.cluster.port=2551'
```

### 노드 2
```bash
./gradlew :spring-boot-sse-cluster:bootRun \
  --args='--server.port=8092 --pekko.cluster.port=2552'
```

### 노드 3
```bash
./gradlew :spring-boot-sse-cluster:bootRun \
  --args='--server.port=8093 --pekko.cluster.port=2553'
```

## API 엔드포인트

- `GET /events/stream` - SSE 이벤트 스트림 구독
- `POST /events/publish` - 이벤트 발행
- `GET /api/subscribers` - 구독자 목록
- `GET /api/stats` - 클러스터 통계
- `GET /api/cluster-info` - 클러스터 노드 정보
- `GET /` - SSE 클라이언트 HTML 페이지

## 테스트

```bash
./gradlew :spring-boot-sse-cluster:test
```

## 설정

`application.properties`:
```properties
server.port=8091
pekko.cluster.port=2551
pekko.cluster.seed-nodes=pekko://SpringSseCluster@127.0.0.1:2551,pekko://SpringSseCluster@127.0.0.1:2552
```

## 이벤트 발행 예시

```bash
curl -X POST http://localhost:8091/events/publish \
  -H "Content-Type: application/json" \
  -d '{"type": "notification", "data": "{\"message\": \"Hello from Spring Boot!\"}"}'
```

## SSE 이벤트 형식

```
event: notification
id: pekko://SpringSseCluster@127.0.0.1:2551-1
data: {"data":"{\"message\":\"Hello!\"}","nodeAddress":"pekko://SpringSseCluster@127.0.0.1:2551","timestamp":1703500000000}
```
