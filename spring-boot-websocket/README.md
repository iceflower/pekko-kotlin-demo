# Spring Boot WebSocket Module

This module demonstrates WebSocket integration with Spring Boot and Pekko Actors.

## Features

- **Spring WebSocket**: Native Spring WebSocket support
- **Pekko Actor Integration**: ChatRoom actor manages chat state
- **Real-time Chat**: Bidirectional WebSocket communication
- **JSON Messages**: Structured message protocol

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Spring Boot Application                   │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │              WebSocketConfig                          │   │
│  │              @EnableWebSocket                         │   │
│  └──────────────────────────────────────────────────────┘   │
│                            │                                 │
│                            ▼                                 │
│  ┌──────────────────────────────────────────────────────┐   │
│  │           ChatWebSocketHandler                        │   │
│  │           TextWebSocketHandler                        │   │
│  │                                                       │   │
│  │  - afterConnectionEstablished()                       │   │
│  │  - handleTextMessage()      ─────────────────────┐    │   │
│  │  - afterConnectionClosed()                       │    │   │
│  └──────────────────────────────────────────────────│────┘   │
│                                                     │        │
│                                                     ▼        │
│  ┌──────────────────────────────────────────────────────┐   │
│  │              Pekko ActorSystem                        │   │
│  │  ┌────────────────────────────────────────────────┐  │   │
│  │  │              ChatRoom Actor                     │  │   │
│  │  │                                                 │  │   │
│  │  │  Commands:                                      │  │   │
│  │  │  - Join(sessionId, username, callback)          │  │   │
│  │  │  - Leave(sessionId)                             │  │   │
│  │  │  - SendMessage(sessionId, content)              │  │   │
│  │  │  - GetUsers(replyTo)                            │  │   │
│  │  └────────────────────────────────────────────────┘  │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

## Running

```bash
./gradlew :spring-boot-websocket:bootRun
```

Server starts at:
- HTTP: http://localhost:8082/
- WebSocket: ws://localhost:8082/ws/chat

## API

### WebSocket Endpoint

**ws://localhost:8082/ws/chat**

#### Client → Server Messages

**Join Chat**
```json
{
  "type": "JOIN",
  "username": "Alice"
}
```

**Send Message**
```json
{
  "type": "MESSAGE",
  "content": "Hello, everyone!"
}
```

**Leave Chat**
```json
{
  "type": "LEAVE"
}
```

#### Server → Client Messages

**Chat Message**
```json
{
  "type": "CHAT",
  "username": "Alice",
  "content": "Hello, everyone!",
  "timestamp": 1703123456789
}
```

**System Message**
```json
{
  "type": "SYSTEM",
  "content": "Alice joined the chat",
  "timestamp": 1703123456789
}
```

## Testing

```bash
./gradlew :spring-boot-websocket:test
```

## Integration Points

### Pekko Actor Lifecycle

The `PekkoConfig` class manages:
1. ActorSystem creation with Spring lifecycle
2. ChatRoom actor spawning as Spring Bean
3. Graceful shutdown on application stop

### Spring WebSocket Integration

The `ChatWebSocketHandler` bridges:
1. Spring WebSocket sessions → Pekko actor messages
2. Actor callbacks → WebSocket text messages
3. Session lifecycle → Actor Join/Leave commands

## Comparison with Pure Pekko Module

| Feature          | Spring Boot      | Pure Pekko     |
|------------------|------------------|----------------|
| Framework        | Spring WebSocket | Pekko HTTP     |
| Configuration    | @EnableWebSocket | Routes DSL     |
| Session Handling | Spring managed   | ActorSource    |
| Dependencies     | Spring ecosystem | Minimal        |
| Use Case         | Enterprise apps  | Microservices  |
