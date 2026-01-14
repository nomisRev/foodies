# Foodies

A microservices-based food ordering application built with Kotlin and Ktor. The system uses Keycloak for authentication, RabbitMQ for event streaming, PostgreSQL for data persistence, and Redis for caching.

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              Web Browser                                     │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         webapp (Port 8080)                                   │
│                    HTMX UI + OAuth2 Authentication                           │
└─────────────────────────────────────────────────────────────────────────────┘
           │                    │                    │
           ▼                    ▼                    ▼
┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│  menu (8082)    │  │  basket (8083)  │  │  profile (8081) │
│  REST API       │  │  REST API       │  │  Event-Driven   │
│  PostgreSQL     │  │  Redis + JWT    │  │  PostgreSQL     │
└─────────────────┘  └─────────────────┘  └─────────────────┘
                              │                    ▲
                              │                    │
                              ▼                    │
┌─────────────────────────────────────────────────────────────────────────────┐
│                            RabbitMQ                                          │
│                     Event Streaming & Messaging                              │
└─────────────────────────────────────────────────────────────────────────────┘
                                    ▲
                                    │
┌─────────────────────────────────────────────────────────────────────────────┐
│                         Keycloak (Port 8000)                                 │
│                    Identity & Access Management                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Services

### Implemented

| Service | Port | Description | Database |
|---------|------|-------------|----------|
| **webapp** | 8080 | Main web application with HTMX UI and OAuth2/OIDC authentication | - |
| **menu** | 8082 | Menu item management with full CRUD REST API | PostgreSQL |
| **basket** | 8083 | Shopping basket service with JWT authentication | Redis |
| **profile** | 8081 | Event-driven user profile service (consumes Keycloak events) | PostgreSQL |

### Planned (Specifications Available)

| Service | Port | Description |
|---------|------|-------------|
| **order** | 8084 | Order lifecycle management with saga pattern |
| **payment** | 8085 | Event-driven payment processing |
| **admin** | 8086 | Centralized administration interface |

## Modules

```
foodies/
├── webapp/                      # Main web application
├── menu/                        # Menu microservice
├── basket/                      # Basket microservice
├── profile/                     # Profile microservice
├── server-shared/               # Shared server utilities
├── server-shared-test/          # Shared test helpers
├── keycloak-events/             # Keycloak event models
├── keycloak-rabbitmq-publisher/ # Custom Keycloak provider for RabbitMQ
├── k8s/                         # Kubernetes manifests
├── docs/                        # Documentation
└── specs/                       # Service specifications
```

## Technology Stack

| Category | Technology |
|----------|------------|
| Language | Kotlin (JDK 21) |
| Framework | Ktor 3.3.3 |
| Build | Gradle with Kotlin DSL |
| Database | PostgreSQL 18, Redis 7 |
| ORM | Exposed v1 |
| Migrations | Flyway |
| Messaging | RabbitMQ 4.2 |
| Authentication | Keycloak 26.5 (OAuth2/OIDC) |
| Frontend | HTMX 1.9.12, kotlinx.html |
| Testing | TestBalloon, Testcontainers |
| Deployment | Docker Compose, Kubernetes |

## Quick Start

### Prerequisites

- JDK 21
- Docker and Docker Compose

### 1. Build the Keycloak Provider

```bash
./gradlew :keycloak-rabbitmq-publisher:build
```

### 2. Start Infrastructure

```bash
docker compose up -d
```

This starts:
- PostgreSQL (profile database on 5432, menu database on 5433)
- Redis (6379)
- RabbitMQ (5672, management UI on 15672)
- Keycloak (8000)

### 3. Run Services

In separate terminals:

```bash
./gradlew :profile:run    # Port 8081
./gradlew :menu:run       # Port 8082
./gradlew :basket:run     # Port 8083
./gradlew :webapp:run     # Port 8080
```

### 4. Access the Application

- **Web App**: http://localhost:8080
- **Keycloak Admin**: http://localhost:8000 (admin/admin)
- **RabbitMQ Management**: http://localhost:15672 (guest/guest)

### Test User

- Username: `food_lover`
- Password: `password`

## Running Tests

```bash
./gradlew check
```

## API Endpoints

### Menu Service (Port 8082)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/menu` | List menu items (paginated: `offset`, `limit` max 50) |
| GET | `/menu/{id}` | Get single menu item |
| POST | `/menu` | Create menu item |
| PUT | `/menu/{id}` | Update menu item (partial updates supported) |
| DELETE | `/menu/{id}` | Delete menu item |
| GET | `/healthz` | Health check |

### Basket Service (Port 8083)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/basket` | Get current user's basket |
| POST | `/basket/items` | Add item to basket |
| PUT | `/basket/items/{itemId}` | Update item quantity |
| DELETE | `/basket/items/{itemId}` | Remove item from basket |
| DELETE | `/basket` | Clear basket |
| GET | `/healthz` | Health check |

### Profile Service (Port 8081)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/healthz` | Health check |

*Profile data is managed via Keycloak events (Registration, UpdateProfile, Delete)*

### WebApp (Port 8080)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/` | Home page with menu feed |
| GET | `/login` | OAuth2 login redirect |
| GET | `/oauth/callback` | OAuth2 callback handler |
| GET | `/logout` | Logout and redirect to Keycloak |
| GET | `/menu` | Paginated menu items (HTMX fragments) |
| GET | `/healthz` | Health check |

## Configuration

Services are configured via environment variables with sensible defaults. See each service's `application.yaml` for details:

- `webapp/src/main/resources/application.yaml`
- `menu/src/main/resources/application.yaml`
- `basket/src/main/resources/application.yaml`
- `profile/src/main/resources/application.yaml`

### Key Environment Variables

| Variable | Service | Description |
|----------|---------|-------------|
| `HOST` | All | Server host (default: 0.0.0.0) |
| `PORT` | All | Server port |
| `DB_URL` | menu, profile | PostgreSQL connection string |
| `DB_USERNAME` | menu, profile | Database username |
| `DB_PASSWORD` | menu, profile | Database password |
| `REDIS_HOST` | webapp, basket | Redis host |
| `REDIS_PORT` | webapp, basket | Redis port |
| `REDIS_PASSWORD` | webapp, basket | Redis password |
| `SESSION_TTL_SECONDS` | webapp | Session TTL in seconds |
| `RABBITMQ_HOST` | profile, basket | RabbitMQ host |
| `RABBITMQ_PORT` | profile, basket | RabbitMQ port |
| `AUTH_ISSUER` | webapp, basket | Keycloak issuer URL |
| `AUTH_CLIENT_ID` | webapp | OAuth client ID |
| `AUTH_CLIENT_SECRET` | webapp | OAuth client secret |
| `MENU_BASE_URL` | webapp, basket | Menu service URL |

## Kubernetes Deployment

See [k8s/README.md](k8s/README.md) for detailed deployment instructions.

```bash
# Apply development overlay
kubectl apply -k k8s/overlays/dev
```

## Documentation

- [Project Setup Guide](docs/PROJECT_SETUP.md) - Ktor server setup best practices

### Service Documentation

- [WebApp](webapp/README.MD)
- [Menu](menu/README.MD)
- [Basket](basket/README.md)
- [Profile](profile/README.MD)

## Architecture Patterns

### Manual Dependency Injection
Dependencies are wired explicitly in `Module` classes without a DI framework, promoting clear understanding of application structure.

### Layered Architecture
```
Routes (HTTP) → Service (Business Logic) → Repository (Data Access) → Database
```

### Event-Driven Communication
Services communicate via RabbitMQ events with idempotent operations and manual ack/nack for reliable processing.

### Validation DSL
```kotlin
validate {
    name.validate(String::isNotBlank) { "name must not be blank" }
    price.validate({ it > BigDecimal.ZERO }) { "price must be positive" }
}
```

## License

This project is for educational purposes.
