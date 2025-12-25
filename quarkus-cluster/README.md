# Quarkus + Pekko Cluster 통합 예제

이 모듈은 Quarkus 프레임워크와 Apache Pekko Cluster를 함께 사용하는 방법을 보여줍니다.

## 아키텍처

```mermaid
flowchart TB
    subgraph Node1[Quarkus Node 1]
        subgraph Q1[Quarkus CDI]
            Res1[ClusterResource]
            Config1[PekkoClusterConfig]
        end

        subgraph Pekko1[Pekko Cluster]
            System1[ActorSystem]
            Listener1[ClusterListener]
            Singleton1[SingletonCounter<br/>Primary]
        end
    end

    subgraph Node2[Quarkus Node 2]
        subgraph Q2[Quarkus CDI]
            Res2[ClusterResource]
            Config2[PekkoClusterConfig]
        end

        subgraph Pekko2[Pekko Cluster]
            System2[ActorSystem]
            Listener2[ClusterListener]
            Proxy2[SingletonProxy]
        end
    end

    Client[HTTP Client] -->|REST API| Res1
    Client -->|REST API| Res2

    System1 <-->|Cluster Protocol| System2
    Singleton1 -.->|Proxy| Proxy2
    Res2 -->|via Proxy| Singleton1
```

## Quarkus vs Spring Boot 클러스터 통합 비교

| 특성 | Quarkus Cluster | Spring Boot Cluster |
|------|-----------------|---------------------|
| **시작 시간** | ~1초 (JVM), ~20ms (Native) | ~3-4초 |
| **메모리 사용** | 낮음 (특히 Native) | 보통 |
| **DI 컨테이너** | CDI (ArC) | Spring IoC |
| **Bean 등록** | `@Observes StartupEvent` | `@Bean` |
| **포트** | 8084 (HTTP), 25530 (Cluster) | 8083 (HTTP), 25520 (Cluster) |

## Cluster Singleton 패턴

Cluster Singleton은 전체 클러스터에서 **단 하나의 Actor 인스턴스**만 실행되도록 보장합니다:

- **자동 Failover**: 싱글톤이 실행 중인 노드가 다운되면 다른 노드에서 자동 재시작
- **위치 투명성**: 어떤 노드에서든 싱글톤에 메시지를 보낼 수 있음
- **일관성**: 클러스터 전체에서 상태 일관성 유지

## 주요 구성 요소

### 1. CborSerializable (`actor/CborSerializable.kt`)
- 클러스터 노드 간 메시지 직렬화를 위한 마커 인터페이스
- Jackson CBOR 직렬화 사용

### 2. ClusterListener (`actor/ClusterListener.kt`)
- 클러스터 멤버십 이벤트 수신
- 노드 Join/Leave/Unreachable 상태 로깅

### 3. SingletonCounter (`actor/SingletonCounter.kt`)
- Cluster Singleton으로 실행되는 카운터 Actor
- 증가, 감소, 조회, 리셋 기능 제공

### 4. PekkoClusterConfig (`config/PekkoClusterConfig.kt`)
- Quarkus CDI를 사용하여 ActorSystem을 Cluster 모드로 생성
- `@Observes StartupEvent`로 시작 시 초기화
- `@Observes ShutdownEvent`로 종료 시 graceful shutdown
- `@Produces`로 ActorSystem, Scheduler, SingletonCounter를 CDI Bean으로 제공

### 5. ClusterResource (`resource/ClusterResource.kt`)
- JAX-RS(REST) 엔드포인트 제공
- CDI `@Inject`로 ActorRef 주입
- 클러스터 상태 조회 및 싱글톤 카운터 조작 API

## 실행 방법

### 단일 노드 실행

```bash
./gradlew :quarkus-cluster:quarkusDev
```

서버가 시작되면 http://localhost:8084 에서 접근 가능합니다.

### 다중 노드 실행 (클러스터 테스트)

```bash
# 터미널 1: 첫 번째 노드 (seed node)
./gradlew :quarkus-cluster:quarkusDev

# 터미널 2: 두 번째 노드
PEKKO_REMOTE_PORT=25531 QUARKUS_HTTP_PORT=8085 ./gradlew :quarkus-cluster:quarkusDev

# 터미널 3: 세 번째 노드
PEKKO_REMOTE_PORT=25532 QUARKUS_HTTP_PORT=8086 ./gradlew :quarkus-cluster:quarkusDev
```

## API 엔드포인트

| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | `/api/cluster/status` | 클러스터 상태 조회 |
| GET | `/api/cluster/counter` | 카운터 값 조회 |
| POST | `/api/cluster/counter/increment` | 카운터 증가 |
| POST | `/api/cluster/counter/decrement` | 카운터 감소 |
| POST | `/api/cluster/counter/reset` | 카운터 리셋 |

## API 사용 예시

### 클러스터 상태 조회
```bash
curl http://localhost:8084/api/cluster/status
```

응답 예시:
```json
{
  "selfAddress": "pekko://quarkus-cluster-system@127.0.0.1:25530",
  "selfRoles": ["backend"],
  "selfStatus": "Up",
  "leader": "pekko://quarkus-cluster-system@127.0.0.1:25530",
  "members": [
    {
      "address": "pekko://quarkus-cluster-system@127.0.0.1:25530",
      "status": "Up",
      "roles": ["backend"]
    }
  ],
  "unreachable": []
}
```

### 카운터 조작
```bash
# 카운터 증가
curl -X POST "http://localhost:8084/api/cluster/counter/increment?delta=5"

# 카운터 조회
curl http://localhost:8084/api/cluster/counter

# 카운터 감소
curl -X POST "http://localhost:8084/api/cluster/counter/decrement?delta=2"

# 카운터 리셋
curl -X POST http://localhost:8084/api/cluster/counter/reset
```

## Split Brain Resolver (SBR)

네트워크 파티션 발생 시 클러스터 일관성을 유지하기 위한 전략:

```hocon
cluster.split-brain-resolver {
  active-strategy = "keep-majority"
  stable-after = 10s
}
```

## Quarkus와 Pekko Cluster 통합의 이점

1. **빠른 시작 시간**: Quarkus의 빌드 타임 최적화로 빠른 부팅
2. **낮은 메모리**: 클라우드 환경에 최적화된 리소스 사용
3. **CDI 통합**: 표준 CDI로 ActorRef를 쉽게 주입
4. **Dev 모드**: 코드 변경 시 자동 재시작 (Live Reload)
5. **Native 빌드**: GraalVM Native Image로 컴파일 가능
6. **생명주기 관리**: Quarkus 이벤트로 ActorSystem 생명주기 자동 관리

## 테스트

```bash
./gradlew :quarkus-cluster:test
```

## Native 빌드 (선택사항)

```bash
# GraalVM 필요
./gradlew :quarkus-cluster:build -Dquarkus.native.enabled=true
```

## 의존성

- Quarkus 3.30.5
- Apache Pekko 1.4.0
- Apache Pekko Cluster 1.4.0
- Kotlin 2.3.0
- Kotest 6.0.5

## 참고 자료

- [Quarkus 공식 문서](https://quarkus.io/guides/)
- [Pekko Cluster 문서](https://pekko.apache.org/docs/pekko/current/typed/cluster.html)
- [Pekko Cluster Singleton](https://pekko.apache.org/docs/pekko/current/typed/cluster-singleton.html)
- [Split Brain Resolver](https://pekko.apache.org/docs/pekko/current/split-brain-resolver.html)
