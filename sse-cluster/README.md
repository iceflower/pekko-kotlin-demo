# SSE Cluster 모듈

Pekko Cluster와 Server-Sent Events를 결합한 분산 이벤트 스트리밍 예제입니다.

## 아키텍처

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Pekko Cluster                                │
│                                                                     │
│  ┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐ │
│  │    Node 1       │    │    Node 2       │    │    Node 3       │ │
│  │  (port 2551)    │    │  (port 2552)    │    │  (port 2553)    │ │
│  │                 │    │                 │    │                 │ │
│  │ ┌─────────────┐ │    │ ┌─────────────┐ │    │ ┌─────────────┐ │ │
│  │ │Distributed  │◄────►│ │Distributed  │◄────►│ │Distributed  │ │ │
│  │ │ EventBus    │ │    │ │ EventBus    │ │    │ │ EventBus    │ │ │
│  │ └─────────────┘ │    │ └─────────────┘ │    │ └─────────────┘ │ │
│  │       ▲         │    │       ▲         │    │       ▲         │ │
│  │       │         │    │       │         │    │       │         │ │
│  │ ┌─────────────┐ │    │ ┌─────────────┐ │    │ ┌─────────────┐ │ │
│  │ │  HTTP/SSE   │ │    │ │  HTTP/SSE   │ │    │ │  HTTP/SSE   │ │ │
│  │ │  Server     │ │    │ │  Server     │ │    │ │  Server     │ │ │
│  │ │ (port 8091) │ │    │ │ (port 8092) │ │    │ │ (port 8093) │ │ │
│  │ └─────────────┘ │    │ └─────────────┘ │    │ └─────────────┘ │ │
│  └─────────────────┘    └─────────────────┘    └─────────────────┘ │
│                                                                     │
│               Distributed PubSub (Topic)                           │
│         ◄────────────────────────────────────────────►             │
└─────────────────────────────────────────────────────────────────────┘
              ▲                    ▲                    ▲
              │ SSE                │ SSE                │ SSE
         ┌────────┐           ┌────────┐           ┌────────┐
         │Client A│           │Client B│           │Client C│
         └────────┘           └────────┘           └────────┘
```

## 주요 컴포넌트

### DistributedEventBus
- Pekko Distributed PubSub(Topic)을 사용하여 클러스터 전체에 이벤트 브로드캐스트
- 각 노드는 로컬 SSE 구독자만 직접 관리
- 이벤트 발행 시 모든 노드의 구독자에게 전달

### ClusterListener
- 클러스터 멤버 이벤트 모니터링
- 노드 가입/탈퇴/도달성 변경 로깅

### SseClusterRoutes
- Pekko HTTP를 사용한 SSE 스트리밍 엔드포인트
- REST API로 이벤트 발행, 구독자 목록, 클러스터 통계 제공

## 실행 방법

### 노드 1 (Seed Node)
```bash
./gradlew :sse-cluster:run -PCLUSTER_PORT=2551 -PHTTP_PORT=8091
```

### 노드 2
```bash
./gradlew :sse-cluster:run -PCLUSTER_PORT=2552 -PHTTP_PORT=8092
```

### 노드 3
```bash
./gradlew :sse-cluster:run -PCLUSTER_PORT=2553 -PHTTP_PORT=8093
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
./gradlew :sse-cluster:test
```

## 이벤트 발행 예시

```bash
curl -X POST http://localhost:8091/events/publish \
  -H "Content-Type: application/json" \
  -d '{"type": "notification", "data": "{\"message\": \"Hello from cluster!\"}"}'
```

## SSE 이벤트 형식

```
event: notification
id: pekko://SseClusterSystem@127.0.0.1:2551-1
data: {"data":"{\"message\":\"Hello!\"}","nodeAddress":"pekko://SseClusterSystem@127.0.0.1:2551","timestamp":1703500000000}
```

## 주요 특징

- **분산 이벤트 브로드캐스팅**: 어느 노드에서 발행한 이벤트든 모든 노드의 구독자에게 전달
- **노드 정보 포함**: 각 이벤트에 발행 노드 정보 포함
- **자동 재연결**: 클라이언트 HTML에서 SSE 연결 끊김 시 자동 재연결
- **실시간 통계**: 구독자 수, 노드 정보 실시간 조회 가능
