# WebApp

Ktor-based web application serving an HTMX front-end for the Foodies platform. Provides user authentication via OpenID Connect (Keycloak), dynamic menu browsing with infinite scroll, and a fully functional shopping cart system.

> For common architectural patterns, testing approach, and build commands, see the [main README](../README.md#architecture-patterns).

## Service-Specific Stack

- **HTMX** - Dynamic frontend interactions without heavy JavaScript
- **OpenID Connect** - Secure authentication with Keycloak
- **Redis** - Session storage with TTL

## Project Structure

```
src/main/kotlin/io/ktor/foodies/server/
├── WebApp.kt                 # Main application entry point
├── WebAppModule.kt          # Dependency injection and service configuration
├── Config.kt                # Configuration data classes
├── security/                # Authentication and session management
│   ├── Security.kt          # OpenID Connect integration
│   ├── UserSessionScope.kt  # User session utilities
│   └── RedisSessionStorage.kt # Redis-based session storage
├── htmx/                    # HTMX-specific routes and components
│   ├── Home.kt              # Landing page with infinite scroll
│   ├── Syntax.kt            # HTMX response utilities
│   ├── menu/                # Menu browsing functionality
│   │   ├── MenuRoutes.kt    # Menu API endpoints
│   │   └── MenuService.kt   # Menu service client
│   ├── basket/              # Shopping cart domain
│   │   ├── Domain.kt        # Basket data models
│   │   └── BasketService.kt # Basket service client
│   └── cart/                # Cart UI and routes
│       └── CartRoutes.kt    # Cart management endpoints
└── telemetry/               # OpenTelemetry configuration
    └── Monitoring.kt        # Tracing setup
```

## Configuration

Configuration is loaded from `src/main/resources/application.yaml` with environment variable overrides:

### Server Configuration
- `HOST` / `PORT`: Bind address and port (default `0.0.0.0:8080`)

### Authentication (OpenID Connect)
- `AUTH_ISSUER`: OpenID Connect issuer URL (default `http://localhost:8000/realms/foodies-keycloak`)
- `AUTH_CLIENT_ID`: OAuth client identifier (default `foodies`)
- `AUTH_CLIENT_SECRET`: OAuth client secret (default `foodies_client_secret`)

### Service Endpoints
- `MENU_BASE_URL`: Menu service base URL (default `http://localhost:8082`)
- `BASKET_BASE_URL`: Basket service base URL (default `http://localhost:8083`)

### Session Storage (Redis)
- `REDIS_HOST`: Redis server host (default `localhost`)
- `REDIS_PORT`: Redis server port (default `6379`)
- `REDIS_PASSWORD`: Redis authentication password (default empty)
- `SESSION_TTL_SECONDS`: Session time-to-live (default `3600`)

### Telemetry
- `OTEL_EXPORTER_OTLP_ENDPOINT`: OpenTelemetry collector endpoint (default `http://localhost:4317`)

## API Routes

### Public Routes
- `GET /`: Landing page with infinite scroll menu
- `GET /menu`: HTMX endpoint for paginated menu items
- `GET /login`: Initiates OAuth login flow
- `GET /oauth/callback`: OAuth callback handler
- `GET /logout`: Clears session and redirects to provider logout

### Protected Routes (Requires Authentication)
- `GET /cart`: Shopping cart page
- `GET /cart/badge`: Cart item count badge (HTMX fragment)
- `POST /cart/items`: Add item to cart
- `PUT /cart/items/{itemId}`: Update cart item quantity
- `DELETE /cart/items/{itemId}`: Remove item from cart
- `DELETE /cart`: Clear entire cart

### Health Checks
- `GET /healthz/startup`: Startup health probe
- `GET /healthz/liveness`: Liveness health probe
- `GET /healthz/readiness`: Readiness health probe (includes downstream service checks)

### Static Assets
- `/static/*`: Static CSS and JavaScript files

## Features

### Menu Browsing
- Infinite scroll loading of menu items
- Real-time search and filtering
- Responsive grid layout with item cards
- Image support for menu items
- Pricing and descriptions

### Shopping Cart
- Add/remove items with quantity controls
- Real-time cart updates via HTMX
- Cart badge showing item count
- Order summary with pricing calculations
- Session-based cart persistence

### Authentication
- OpenID Connect integration
- Secure session management with Redis
- Automatic token refresh
- Logout with provider redirection

## Development

### Prerequisites

Ensure the following services are running:
- **Redis** (localhost:6379)
- **Keycloak** at AUTH_ISSUER URL
- **Menu Service** at MENU_BASE_URL
- **Basket Service** at BASKET_BASE_URL

### Testing

Test suites include:
- **HealthCheckSpec**: Health endpoint tests
- **Session tests**: Redis session storage tests

## Browser Compatibility

- Modern browsers supporting ES6+
- HTMX 1.9.12+ (loaded from CDN)
