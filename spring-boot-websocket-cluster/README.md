# Spring Boot WebSocket Cluster 모듈

Spring Boot와 Pekko Cluster를 결합한 분산 WebSocket 채팅 예제입니다.

## 아키텍처

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Pekko Cluster                                │
│                                                                     │
│  ┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐ │
│  │  Spring Boot 1  │    │  Spring Boot 2  │    │  Spring Boot 3  │ │
│  │   (port 8081)   │    │   (port 8082)   │    │   (port 8083)   │ │
│  │                 │    │                 │    │                 │ │
│  │ ┌─────────────┐ │    │ ┌─────────────┐ │    │ ┌─────────────┐ │ │
│  │ │DistributedChat│◄───►│DistributedChat│◄───►│DistributedChat│ │ │
│  │ │   Room      │ │    │ │   Room      │ │    │ │   Room      │ │ │
│  │ └─────────────┘ │    │ └─────────────┘ │    │ └─────────────┘ │ │
│  │       ▲         │    │       ▲         │    │       ▲         │ │
│  │       │         │    │       │         │    │       │         │ │
│  │ ┌─────────────┐ │    │ ┌─────────────┐ │    │ ┌─────────────┐ │ │
│  │ │  WebSocket  │ │    │ │  WebSocket  │ │    │ │  WebSocket  │ │ │
│  │ │  Handler    │ │    │ │  Handler    │ │    │ │  Handler    │ │ │
│  │ └─────────────┘ │    │ └─────────────┘ │    │ └─────────────┘ │ │
│  └─────────────────┘    └─────────────────┘    └─────────────────┘ │
│                                                                     │
│               Distributed PubSub (Topic)                           │
│         ◄────────────────────────────────────────────►             │
└─────────────────────────────────────────────────────────────────────┘
```

## 주요 컴포넌트

### DistributedChatRoom
- Pekko Distributed PubSub(Topic)을 사용한 클러스터 간 메시지 공유
- 각 Spring Boot 인스턴스에서 로컬 WebSocket 세션 관리

### ChatWebSocketHandler
- Spring WebSocket 핸들러
- 로컬 세션과 Pekko Actor 간 연동

### PekkoClusterConfig
- Spring Bean으로 Pekko ActorSystem 설정
- 클러스터 포트 및 seed-nodes 설정

## 실행 방법

### 노드 1 (Seed Node)
```bash
./gradlew :spring-boot-websocket-cluster:bootRun \
  --args='--server.port=8081 --pekko.cluster.port=2551'
```

### 노드 2
```bash
./gradlew :spring-boot-websocket-cluster:bootRun \
  --args='--server.port=8082 --pekko.cluster.port=2552'
```

### 노드 3
```bash
./gradlew :spring-boot-websocket-cluster:bootRun \
  --args='--server.port=8083 --pekko.cluster.port=2553'
```

## API 엔드포인트

- `ws://localhost:{port}/ws/chat?username={name}` - WebSocket 채팅
- `GET /api/users` - 접속 사용자 목록
- `GET /api/cluster-info` - 클러스터 노드 정보
- `GET /` - 채팅 클라이언트 HTML 페이지

## 테스트

```bash
./gradlew :spring-boot-websocket-cluster:test
```

## 설정

`application.properties`:
```properties
server.port=8081
pekko.cluster.port=2551
pekko.cluster.seed-nodes=pekko://SpringWebSocketCluster@127.0.0.1:2551,pekko://SpringWebSocketCluster@127.0.0.1:2552
```

## 메시지 형식

### 채팅 메시지 (수신)
```json
{
  "type": "chat",
  "username": "alice",
  "message": "안녕하세요!",
  "nodeAddress": "pekko://SpringWebSocketCluster@127.0.0.1:2551",
  "timestamp": 1703500000000
}
```

### 메시지 전송
```json
{
  "type": "message",
  "content": "안녕하세요!"
}
```
