# SSE Module (Pure Pekko HTTP)

This module demonstrates Server-Sent Events (SSE) implementation using pure Pekko HTTP and Actors.

## Features

- **Event Streaming**: Real-time server-to-client event streaming
- **Actor-based Pub/Sub**: EventPublisher actor manages subscriptions
- **Multiple Event Types**: Support for different event categories
- **Heartbeat**: Keep-alive mechanism for long-lived connections

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    Pekko HTTP Server                    │
├─────────────────────────────────────────────────────────┤
│  SSE Endpoint (/events)                                 │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐     │
│  │  Client 1   │  │  Client 2   │  │  Client 3   │     │
│  │  (SSE sub)  │  │  (SSE sub)  │  │  (SSE sub)  │     │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘     │
│         │                │                │             │
│         └────────────────┼────────────────┘             │
│                          ▼                              │
│                  ┌───────────────┐                      │
│                  │ EventPublisher│◄──── POST /api/publish
│                  │    Actor      │                      │
│                  └───────────────┘                      │
└─────────────────────────────────────────────────────────┘
```

## Running

```bash
./gradlew :sse:run
```

Server starts at:
- HTTP: http://localhost:8081/
- SSE: http://localhost:8081/events

## API

### SSE Endpoint

**GET /events**

Connect to receive server-sent events.

```bash
curl -N http://localhost:8081/events
```

Event format:
```
id: <uuid>
event: <event-type>
data: <payload>

```

### REST API

| Method | Endpoint                        | Description                    |
|--------|---------------------------------|--------------------------------|
| POST   | `/api/publish?type=<eventType>` | Publish an event (body = data) |
| GET    | `/api/stats`                    | Get publisher statistics       |

### Publish Event

```bash
curl -X POST "http://localhost:8081/api/publish?type=notification" \
  -H "Content-Type: text/plain" \
  -d "Hello, SSE!"
```

## Event Types

| Type           | Description                     |
|----------------|---------------------------------|
| `notification` | General notifications           |
| `alert`        | Important alerts                |
| `update`       | Data updates                    |
| `heartbeat`    | Keep-alive (automatic every 30s) |

## Testing with Browser

Open http://localhost:8081/ in your browser to use the built-in SSE demo UI.

## Testing with curl

```bash
# Subscribe to events (runs until Ctrl+C)
curl -N http://localhost:8081/events

# In another terminal, publish events
curl -X POST "http://localhost:8081/api/publish?type=notification" -d "Test message"
```

## SSE vs WebSocket

| Feature      | SSE                  | WebSocket              |
|--------------|----------------------|------------------------|
| Direction    | Server → Client only | Bidirectional          |
| Protocol     | HTTP                 | WS (upgrade from HTTP) |
| Reconnection | Automatic            | Manual                 |
| Use Case     | Notifications, feeds | Chat, games            |
