# Foodies

A microservices-based food ordering application built with Kotlin and Ktor. The system uses Keycloak for authentication,
RabbitMQ for event streaming, PostgreSQL for data persistence, and Redis for caching.

## Architecture Overview

```mermaid
%%{init: {"theme":"base","themeVariables":{"background":"#F7F5FF","primaryColor":"#7F52FF","primaryTextColor":"#0B1020","primaryBorderColor":"#4E2BBE","secondaryColor":"#0095D5","tertiaryColor":"#F97B00","lineColor":"#1B3A57","edgeLabelBackground":"#F7F5FF","clusterBkg":"#FFF4E6","clusterBorder":"#F6B36A","fontFamily":"JetBrains Mono, Fira Code, Menlo, monospace","fontSize":"14px"},"flowchart":{"nodeSpacing":60,"rankSpacing":70,"curve":"basis"}}}%%
flowchart TB
    browser["Web Browser"]
    webapp["webapp<br/>Port 8080<br/>HTMX UI + OAuth2"]
    keycloak["Keycloak<br/>Port 8000<br/>Identity & Access"]
    rabbit["RabbitMQ<br/>Event Streaming & Messaging"]

    ms_label["Microservices"]:::label

    subgraph services[" "]
        direction LR
        profile["profile<br/>8081<br/>Event-Driven<br/>PostgreSQL"]
        menu["menu<br/>8082<br/>REST API<br/>PostgreSQL"]
        basket["basket<br/>8083<br/>REST API<br/>Redis"]
        order["order<br/>8084<br/>REST API<br/>PostgreSQL"]
        payment["payment<br/>8085<br/>REST API<br/>PostgreSQL"]
    end

    browser --> webapp
    webapp --> ms_label
    ms_label --> profile
    ms_label --> menu
    ms_label --> basket
    ms_label --> order
    ms_label --> payment

    profile --> rabbit
    menu --> rabbit
    basket --> rabbit
    order --> rabbit
    payment --> rabbit

    webapp -->|OAuth2| keycloak
    keycloak -->|events| rabbit

    class browser entry
    class webapp web
    class profile,menu,basket,order,payment service
    class rabbit,keycloak infra
    class ms_label label

    classDef entry fill:#F7F5FF,stroke:#0095D5,stroke-width:1px,color:#0B1020,rx:6,ry:6
    classDef web fill:#E7F4FF,stroke:#0095D5,stroke-width:2px,color:#0B1020,rx:8,ry:8
    classDef service fill:#F1E9FF,stroke:#7F52FF,stroke-width:1.5px,color:#1B0B3A,rx:6,ry:6
    classDef infra fill:#FFF1DD,stroke:#F97B00,stroke-width:1.5px,color:#5C2A00,rx:6,ry:6
    classDef label fill:transparent,stroke:transparent,color:#1B3A57,font-size:13px,font-weight:700
    style services fill:#FFF4E6,stroke:#F6B36A,stroke-width:1px,rx:8,ry:8
```

## Modules

```
foodies/
├── webapp/                      # Main web application
├── menu/                        # Menu microservice
├── basket/                      # Basket microservice
├── profile/                     # Profile microservice
├── order/                       # Order microservice (in development)
├── payment/                     # Payment microservice (in development)
├── server-shared/               # Shared server utilities
├── server-shared-test/          # Shared test helpers
├── events/                      # Event definitions
│   ├── events-common/           # Shared event types
│   ├── events-user/             # User events
│   ├── events-order/            # Order events
│   ├── events-payment/          # Payment events
│   └── events-menu/             # Menu events
├── keycloak-rabbitmq-publisher/ # Custom Keycloak provider for RabbitMQ
├── rabbitmq-ext/                # RabbitMQ extensions
├── e2e/                         # End-to-end tests
├── k8s/                         # Kubernetes manifests
├── docs/                        # Documentation
```

## Technology Stack

| Category       | Technology                               |
|----------------|------------------------------------------|
| Language       | Kotlin (JDK 21)                          |
| Framework      | Ktor 3.3.3                               |
| Build          | Gradle with Kotlin DSL, Version Catalogs |
| Database       | PostgreSQL 18, Redis 8                   |
| ORM            | Exposed v1                               |
| Migrations     | Flyway                                   |
| Messaging      | RabbitMQ 4.2                             |
| Authentication | Keycloak 26.5 (OAuth2/OIDC)              |
| Frontend       | HTMX 1.9.12, kotlinx.html                |
| Testing        | TestBalloon, Testcontainers, Playwright  |
| Observability  | OpenTelemetry, Prometheus                |
| Deployment     | Docker Compose, Kubernetes (Kustomize)   |

## Quick Start

### Prerequisites

- JDK 21
- kubectl (1.14+ for kustomize)

### 1. Build and publish images

See [k8s/README.md](k8s/README.md) for detailed deployment instructions.

```bash
./gradlew publishImageToLocalRegistry
kubectl apply -k k8s/overlays/dev
```

#### Access the Application

- Username: `food_lover@gmail.com`
- Password: `password`

This setup assumes '127.0.0.1 foodies.local' in /etc/hosts

- **Web App**: http://foodies.local
- **Keycloak Admin**: http://foodies.local/auth (admin/admin)

## Documentation

- [Project Setup Guide](docs/PROJECT_SETUP.md) - Ktor server setup best practices
- [Security Guidelines](docs/SECURITY.md) - Security best practices and guidelines
- [Kubernetes Deployment](k8s/README.md) - Detailed Kubernetes deployment instructions

### Service Documentation

- [WebApp](webapp/README.MD)
- [Menu](menu/README.MD)
- [Basket](basket/README.md)
- [Profile](profile/README.MD)
- [Order](order/README.md)
- [Payment](payment/README.md)

### Event Documentation

- [User Events](events/events-user/README.MD)

## Architecture Patterns

All services follow consistent architectural patterns documented below. Service-specific READMEs focus on their unique API, configuration, and domain logic.

### Manual Dependency Injection

Dependencies are wired explicitly in `Module` classes (e.g., `MenuModule`, `BasketModule`) without a DI framework, promoting clear understanding of application structure and explicit dependency graphs.

### Layered Architecture

```
Routes (HTTP) → Service (Business Logic) → Repository (Data Access) → Database
```

Each layer has clear responsibilities:
- **Routes**: HTTP endpoint definitions, request/response handling, authentication
- **Service**: Business logic, validation, orchestration
- **Repository**: Data access, database operations
- **Database**: PostgreSQL (persistent data) or Redis (cache/sessions)

### Event-Driven Communication

Services communicate via RabbitMQ events:
- **Exchange**: `foodies` (topic exchange)
- **Idempotent Operations**: Events are designed to be safely reprocessed
- **Manual Ack/Nack**: Explicit acknowledgment for reliable processing
- **Event Types**: Defined in `events/` modules (`events-user`, `events-order`, `events-payment`, `events-menu`)

### Configuration Pattern

All services use a consistent configuration approach:
- Configuration loaded from `src/main/resources/application.yaml`
- Environment variable overrides for all settings
- Common variables: `HOST`, `PORT`, `DB_URL`, `RABBITMQ_HOST`, `OTEL_EXPORTER_OTLP_ENDPOINT`

### Health Check Endpoints

All services expose standard health check endpoints via [Cohort](https://github.com/sksamuel/cohort):
- `GET /healthz/startup`: Service initialization status
- `GET /healthz/liveness`: Service running status
- `GET /healthz/readiness`: Ready to handle requests (includes dependency checks)

### Validation DSL

Input validation uses a consistent DSL pattern:
```kotlin
validate {
    name.validate(String::isNotBlank) { "name must not be blank" }
    price.validate({ it > BigDecimal.ZERO }) { "price must be positive" }
}
```

## Development Guidelines

This project follows specific development patterns and conventions. See [AGENTS.md](AGENTS.md) for detailed guidelines.

### Code Style
- **Structured Logging**: Tracing-based logging, never log secrets
- **Type Safety**: Proper domain modeling, avoid exceptions for control flow

### Testing
- **No Mocks**: Use TestContainers for real integration testing
- **TestBalloon**: Test framework with fixture management (see [server-shared-test](server-shared-test/README.md))
- **Comprehensive Coverage**: Unit tests for business logic, integration tests for full flows

### Build Commands

```bash
# Build any module
./gradlew :<module>:build

# Run tests for a module
./gradlew :<module>:jvmTest

# Run specific test suite
./gradlew :<module>:jvmTest --tests "*ServiceSpec*"

# Build Docker image for local registry
./gradlew :<module>:publishImageToLocalRegistry

# Build all images
./gradlew publishImageToLocalRegistry
```

## License

This project is for educational purposes.
