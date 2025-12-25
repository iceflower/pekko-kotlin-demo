package com.example.pekko.persistence

import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.persistence.typed.PersistenceId
import org.apache.pekko.persistence.typed.javadsl.CommandHandler
import org.apache.pekko.persistence.typed.javadsl.EventHandler
import org.apache.pekko.persistence.typed.javadsl.EventSourcedBehavior

/**
 * 이벤트 소싱 기반 영구 카운터
 *
 * - Command: 외부에서 받는 명령
 * - Event: 상태 변경을 나타내는 이벤트 (저널에 저장됨)
 * - State: 현재 상태 (이벤트들을 리플레이하여 복원)
 */
object PersistentCounter {

    // === Commands ===
    sealed interface Command : CborSerializable
    data object Increment : Command
    data object Decrement : Command
    data class GetValue(val replyTo: ActorRef<State>) : Command

    // === Events ===
    sealed interface Event : CborSerializable
    data object Incremented : Event
    data object Decremented : Event

    // === State ===
    data class State(val value: Int = 0) : CborSerializable {
        fun increment(): State = copy(value = value + 1)
        fun decrement(): State = copy(value = value - 1)
    }

    fun create(id: String): Behavior<Command> {
        return Behaviors.setup { context ->
            PersistentCounterBehavior(context, PersistenceId.ofUniqueId(id))
        }
    }

    private class PersistentCounterBehavior(
        private val context: ActorContext<Command>,
        persistenceId: PersistenceId
    ) : EventSourcedBehavior<Command, Event, State>(persistenceId) {

        init {
            context.log.info("PersistentCounter[$persistenceId] 시작됨")
        }

        override fun emptyState(): State = State()

        override fun commandHandler(): CommandHandler<Command, Event, State> {
            return newCommandHandlerBuilder()
                .forAnyState()
                .onCommand(Increment::class.java) { _, _ ->
                    context.log.info("Increment 명령 수신")
                    Effect().persist(Incremented)
                        .thenRun { state: State ->
                            context.log.info("카운터 증가됨: ${state.value}")
                        }
                }
                .onCommand(Decrement::class.java) { _, _ ->
                    context.log.info("Decrement 명령 수신")
                    Effect().persist(Decremented)
                        .thenRun { state: State ->
                            context.log.info("카운터 감소됨: ${state.value}")
                        }
                }
                .onCommand(GetValue::class.java) { state, cmd ->
                    context.log.info("현재 값 조회: ${state.value}")
                    cmd.replyTo.tell(state)
                    Effect().none()
                }
                .build()
        }

        override fun eventHandler(): EventHandler<State, Event> {
            return newEventHandlerBuilder()
                .forAnyState()
                .onEvent(Incremented::class.java) { state, _ ->
                    state.increment()
                }
                .onEvent(Decremented::class.java) { state, _ ->
                    state.decrement()
                }
                .build()
        }

        // 10개 이벤트마다 스냅샷 저장
        override fun shouldSnapshot(state: State, event: Event, sequenceNr: Long): Boolean {
            return sequenceNr % 10 == 0L
        }
    }
}
