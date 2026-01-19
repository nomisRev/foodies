# Keycloak RabbitMQ Publisher

A Keycloak event listener that publishes user lifecycle events to a RabbitMQ queue.

> For common build commands and testing approach, see the [main README](../README.md#architecture-patterns).

## Overview

This module provides a lightweight, custom Keycloak event listener that captures user registration, profile updates, and account deletion events. It transforms these events into a standardized `UserEvent` format and publishes them to a RabbitMQ queue for downstream processing.

## Features

- **Event Filtering**: Only processes relevant user events (REGISTER, UPDATE_PROFILE, DELETE_ACCOUNT)
- **Structured Events**: Converts Keycloak events to typed `UserEvent` objects with JSON serialization
- **RabbitMQ Integration**: Durable queue publishing with configurable connection settings
- **Error Handling**: Comprehensive error handling with structured logging
- **Production Ready**: Minimal dependencies (~100 lines of core logic) with full control over behavior

## Architecture

```
Keycloak Event → ProfileWebhookEventListener → UserEvent → RabbitMQ Queue
```

### Core Components

- **ProfileWebhookEventListenerFactory**: Factory class that creates the event listener and manages RabbitMQ connections
- **ProfileWebhookEventListener**: Main event listener that processes Keycloak events
- **toUserEvent()**: Extension function that converts Keycloak events to UserEvent types
- **RabbitConfig**: Configuration data class for RabbitMQ connection settings

## Supported Events

| Keycloak Event | UserEvent Type | Required Fields |
|----------------|----------------|-----------------|
| `REGISTER` | `UserEvent.Registration` | userId, email, firstName, lastName |
| `UPDATE_PROFILE` | `UserEvent.UpdateProfile` | userId, email, firstName, lastName |
| `DELETE_ACCOUNT` | `UserEvent.Delete` | userId |

## Configuration

The listener is configured through environment variables:

| Variable | Default | Description |
|----------|---------|-------------|
| `RABBITMQ_HOST` | `localhost` | RabbitMQ server host |
| `RABBITMQ_PORT` | `5672` | RabbitMQ server port |
| `RABBITMQ_USERNAME` | `guest` | RabbitMQ username |
| `RABBITMQ_PASSWORD` | `guest` | RabbitMQ password |
| `RABBITMQ_QUEUE` | `profile.registration` | Target queue name |

## Installation

1. Build the JAR with all dependencies:
   ```bash
   ./gradlew :keycloak-rabbitmq-publisher:shadowJar
   ```

2. Deploy the JAR to Keycloak's `providers/` directory

3. Register the listener in Keycloak configuration or admin console using the ID: `profile-webhook`

4. Configure environment variables for RabbitMQ connection

## Testing

Test suites use TestContainers with real RabbitMQ:

- Event conversion and validation
- RabbitMQ message publishing
- Error handling scenarios
- Edge cases and null safety

## Event Format

Events are published as JSON with the following structure:

### Registration Event
```json
{
  "type": "Registration",
  "subject": "user-uuid",
  "email": "user@example.com",
  "firstName": "John",
  "lastName": "Doe"
}
```

### Profile Update Event
```json
{
  "type": "UpdateProfile",
  "subject": "user-uuid",
  "email": "updated@example.com",
  "firstName": "Jane",
  "lastName": "Smith"
}
```

### Delete Event
```json
{
  "type": "Delete",
  "subject": "user-uuid"
}
```

## Error Handling

- **Missing Fields**: Throws `IllegalStateException` with descriptive messages for required field validation
- **RabbitMQ Failures**: Logs errors without failing the Keycloak event processing
- **Connection Issues**: Graceful connection handling with lazy initialization
- **Resource Cleanup**: Proper channel and connection cleanup on shutdown

## Monitoring

The listener uses structured logging via JBoss Log Manager:
- Event processing errors are logged with user context
- RabbitMQ connection failures are logged with stack traces
- Resource cleanup issues are logged as warnings

## Rationale

While there are community solutions for Keycloak event publishing:
- [keycloak-webhook](https://github.com/vymalo/keycloak-webhook)
- [keycloak-events](https://github.com/p2-inc/keycloak-events)

This custom implementation was chosen because:
- Only requires ~100 lines of code for core functionality
- Full control over event filtering and transformation
- Minimal dependencies and deployment complexity
- Tailored specifically for user lifecycle events
- Easy integration with existing infrastructure
