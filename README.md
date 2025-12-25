# Pekko Kotlin Demo

![Kotlin](https://img.shields.io/badge/Kotlin-2.3.0-7F52FF?logo=kotlin&logoColor=white)
![Pekko](https://img.shields.io/badge/Pekko-1.3.0-005596)
![JDK](https://img.shields.io/badge/JDK-25-007396?logo=openjdk&logoColor=white)
![Gradle](https://img.shields.io/badge/Gradle-8.x-02303A?logo=gradle&logoColor=white)
![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)

## 목차

- [개요](#개요)
- [기술 스택](#기술-스택)
- [프로젝트 구조](#프로젝트-구조)
- [예제 설명](#예제-설명)
- [실행 방법](#실행-방법)
- [자주 쓰는 명령어](#자주-쓰는-명령어)
- [문자 인코딩/콘솔 출력이 깨질 때(Windows 포함)](#문자-인코딩콘솔-출력이-깨질-때windows-포함)
- [Actor Model 핵심 개념](#actor-model-핵심-개념)
- [의존성](#의존성)
- [학습 자료](#학습-자료)
- [참고 링크](#참고-링크)
- [Akka에서 마이그레이션](#akka에서-마이그레이션)
- [라이선스](#라이선스)


Apache Pekko Actor Model을 Kotlin으로 학습하기 위한 데모 프로젝트입니다.

## 개요

[Apache Pekko](https://pekko.apache.org/)는 Akka 2.6.x의 오픈소스 포크로, Actor Model 기반의 동시성 프로그래밍 프레임워크입니다. 이 프로젝트는 Pekko의 주요 기능들을 Kotlin으로 구현한 예제 모음입니다.

### 주요 특징

- **Actor Model**: 메시지 기반 동시성 처리
- **Typed Actor**: 컴파일 타임 타입 안전성
- **Reactive Streams**: 백프레셔 지원 비동기 스트림
- **Apache 2.0 License**: 상업적 사용 무료

## 기술 스택

| 기술           | 버전     |
|--------------|--------|
| Kotlin       | 2.3.0  |
| Apache Pekko | 1.3.0  |
| JVM          | 25     |
| Gradle       | 8.x    |
| JUnit        | 5.10.1 |

> 참고: Gradle Java Toolchains를 사용하므로 로컬에 JDK 25가 없어도 Gradle이 자동으로 내려받아 사용합니다.

## 프로젝트 구조

```
pekko-kotlin-demo/
├── src/main/kotlin/com/example/pekko/
│   ├── Main.kt                  # 기본 진입점
│   ├── AllExamplesMain.kt       # 모든 예제 통합 실행
│   ├── MainActor.kt             # Guardian Actor (최상위)
│   ├── HelloWorldActor.kt       # 클래스 기반 Actor 예제
│   ├── HelloWorldBot.kt         # 반응형 Bot Actor
│   ├── CounterActor.kt          # 함수형 Actor 예제
│   ├── AskPatternExample.kt     # Ask 패턴 (요청-응답)
│   ├── StreamExample.kt         # Pekko Streams 예제
│   └── CborSerializable.kt      # 직렬화 마커 인터페이스
├── src/test/kotlin/com/example/pekko/
│   ├── HelloWorldActorTest.kt   # HelloWorld Actor 테스트
│   └── CounterActorTest.kt      # Counter Actor 테스트
├── src/main/resources/
│   ├── application.conf         # Pekko 설정
│   └── logback.xml              # 로깅 설정 (UTF-8 지정)
├── build.gradle.kts             # Gradle 빌드 설정
├── settings.gradle.kts          # 프로젝트 설정
├── gradle.properties            # 전역 JVM 인코딩(UTF-8) 등
└── PEKKO_GUIDE.md               # 상세 학습 가이드
```

## 예제 설명

### 1. HelloWorldActor (클래스 기반)

`AbstractBehavior`를 상속한 클래스 기반 Actor입니다. 상태가 복잡하거나 내부 변수 관리가 필요할 때 적합합니다.

```kotlin
// 메시지 정의
sealed interface Command : CborSerializable
data class Greet(val name: String, val replyTo: ActorRef<Greeted>) : Command
data class Greeted(val name: String, val from: ActorRef<Greet>) : Command
```

### 2. CounterActor (함수형)

`Behaviors.receive`를 사용한 함수형 Actor입니다. 상태 변경이 명확하고 간결한 코드가 가능합니다.

```kotlin
fun counter(count: Int): Behavior<Command> = Behaviors.receive { context, message ->
    when (message) {
        is Increment -> counter(count + 1)  // 새 상태로 전환
        is Decrement -> counter(count - 1)
        is GetValue -> {
            message.replyTo.tell(Value(count))
            Behaviors.same()
        }
    }
}
```

### 3. Ask 패턴

Actor에게 메시지를 보내고 `CompletionStage`로 응답을 받는 패턴입니다.

```kotlin
val future: CompletionStage<Value> = AskPattern.ask(
    counterActor,
    { replyTo -> GetValue(replyTo) },
    Duration.ofSeconds(3),
    system.scheduler()
)
```

### 4. Pekko Streams

백프레셔를 자동 처리하는 비동기 스트림 처리입니다.

```kotlin
Source.range(1, 10)
    .map { it * it }
    .filter { it % 2 == 0 }
    .runForeach({ println(it) }, system)
```

## 실행 방법

> OS별 명령어 차이
> - WSL/Linux/Mac: `./gradlew ...`
> - Windows CMD/PowerShell: `gradlew.bat ...` 또는 `gradlew ...`

### 빌드

```bash
./gradlew build
```

### 기본 예제 실행

```bash
./gradlew run
```

### 모든 예제 실행

```bash
./gradlew run -PmainClass=com.example.pekko.AllExamplesMainKt
```

### 테스트 실행

```bash
./gradlew test
```

- 테스트 리포트: `build/reports/tests/test/index.html`

## 자주 쓰는 명령어

```bash
# 정리 후 빌드
./gradlew clean build

# 메인 진입점 명시 실행 (기본 MainKt)
./gradlew run -PmainClass=com.example.pekko.MainKt

# 모든 예제 실행
./gradlew run -PmainClass=com.example.pekko.AllExamplesMainKt

# 의존성 트리 확인
./gradlew dependencies --configuration runtimeClasspath
```

## 문자 인코딩/콘솔 출력이 깨질 때(Windows 포함)

박스 문자(┌─┐, ╔═╗ 등)나 한글이 `???` 또는 깨져 보일 경우 다음 설정을 적용했습니다. 이미 저장소에 반영되어 있으므로 일반적으로 추가 조치가 필요 없습니다.

- `build.gradle.kts` 내 실행 JVM 인코딩 강제
  - `application { applicationDefaultJvmArgs = listOf("-Dfile.encoding=UTF-8", "-Dsun.stdout.encoding=UTF-8", "-Dsun.stderr.encoding=UTF-8") }`
- `src/main/resources/logback.xml` 에 ConsoleAppender 인코딩 지정
  - `<charset>UTF-8</charset>`
- `gradle.properties` 전역 JVM 인코딩 지정
  - `org.gradle.jvmargs=-Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8`

여전히 문제가 있으면:

1. Windows 터미널/폰트 확인: Windows Terminal + 유니코드 지원 폰트(예: Cascadia Mono, Fira Code) 사용
2. (구형 CMD 사용 시) 코드 페이지를 UTF-8로 전환: `chcp 65001`
3. 콘솔의 글꼴을 유니코드 지원 글꼴로 변경
4. IDE 터미널도 UTF-8로 설정(IntelliJ: File > Settings > Editor > File Encodings)

## Actor Model 핵심 개념

### Actor란?

Actor는 동시성 프로그래밍의 기본 단위입니다:

- 자신만의 **격리된 상태**를 가짐
- **메시지**를 통해서만 통신
- 한 번에 **하나의 메시지**만 처리 (Thread-safe)
- 다른 **Actor를 생성**할 수 있음

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

### 주요 패턴

| 패턴       | 설명              | 사용 시점                |
|----------|-----------------|----------------------|
| **Tell** | Fire-and-Forget | 응답이 필요 없을 때          |
| **Ask**  | 요청-응답           | 응답을 기다려야 할 때         |
| **Pipe** | 비동기 결과 전달       | Future 결과를 Actor에 전달 |

## 의존성

```kotlin
dependencies {
    // Pekko BOM
    implementation(platform("org.apache.pekko:pekko-bom_2.13:1.3.0"))

    // Pekko Actor Typed
    implementation("org.apache.pekko:pekko-actor-typed_2.13")

    // Pekko Streams
    implementation("org.apache.pekko:pekko-stream_2.13")

    // 로깅
    implementation("org.apache.pekko:pekko-slf4j_2.13")
    implementation("ch.qos.logback:logback-classic:1.5.21")

    // Kotlin 표준 라이브러리
    implementation(kotlin("stdlib"))

    // 테스트
    testImplementation("org.apache.pekko:pekko-actor-testkit-typed_2.13")
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
}
```

## 학습 자료

더 자세한 내용은 [PEKKO_GUIDE.md](./PEKKO_GUIDE.md)를 참고하세요.

## 참고 링크

- [Apache Pekko 공식 사이트](https://pekko.apache.org/)
- [Pekko GitHub](https://github.com/apache/pekko)
- [Pekko 문서](https://pekko.apache.org/docs/pekko/current/)
- [Kotlin 공식 문서](https://kotlinlang.org/docs/home.html)

## Akka에서 마이그레이션

Akka 프로젝트를 Pekko로 마이그레이션하려면:

| Akka                       | Pekko                          |
|----------------------------|--------------------------------|
| `akka.actor.typed`         | `org.apache.pekko.actor.typed` |
| `com.typesafe.akka:akka-*` | `org.apache.pekko:pekko-*`     |
| `akka { }` (설정)            | `pekko { }`                    |

## 라이선스

이 프로젝트는 MIT 라이선스 하에 배포됩니다.
