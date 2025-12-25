# Core 모듈

## 개요

Pekko Actor의 기본 개념을 학습하기 위한 예제 모음입니다.

## 예제 목록

| 파일 | 설명 |
|------|------|
| `HelloWorldActor.kt` | 클래스 기반 Actor (AbstractBehavior) |
| `HelloWorldBot.kt` | 응답을 처리하는 Bot Actor |
| `CounterActor.kt` | 함수형 Actor (Behaviors.receive) |
| `AskPatternExample.kt` | Ask 패턴 (요청-응답) |
| `StreamExample.kt` | Pekko Streams 기본 |
| `MainActor.kt` | Guardian Actor |

## 실행

```bash
# 기본 실행
./gradlew :core:run

# 모든 예제 실행
./gradlew :core:run -PmainClass=com.example.pekko.AllExamplesMainKt
```

## 주요 개념

### 1. HelloWorldActor (클래스 기반)

```kotlin
class HelloWorldActor(
    context: ActorContext<Command>
) : AbstractBehavior<Command>(context) {

    override fun createReceive(): Receive<Command> {
        return newReceiveBuilder()
            .onMessage(Greet::class.java, this::onGreet)
            .build()
    }

    private fun onGreet(cmd: Greet): Behavior<Command> {
        context.log.info("Hello, ${cmd.name}!")
        cmd.replyTo.tell(Greeted(cmd.name, context.self))
        return this
    }
}
```

### 2. CounterActor (함수형)

```kotlin
fun counter(count: Int): Behavior<Command> {
    return Behaviors.receive { context, message ->
        when (message) {
            is Increment -> counter(count + 1)
            is GetValue -> {
                message.replyTo.tell(Value(count))
                Behaviors.same()
            }
        }
    }
}
```

### 3. Ask 패턴

```kotlin
val future = AskPattern.ask(
    actorRef,
    { replyTo -> GetValue(replyTo) },
    Duration.ofSeconds(3),
    system.scheduler()
)
```

### 4. Streams

```kotlin
Source.range(1, 10)
    .map { it * it }
    .filter { it % 2 == 0 }
    .runForeach({ println(it) }, system)
```

## 테스트

```bash
./gradlew :core:test
```

테스트 파일:
- `HelloWorldActorTest.kt`
- `CounterActorTest.kt`

## 학습 자료

상세한 개념 설명은 [PEKKO_GUIDE.md](../PEKKO_GUIDE.md)를 참고하세요.
