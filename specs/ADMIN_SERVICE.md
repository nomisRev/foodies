# Admin Service Specification

## Overview

The Admin Service provides a centralized administration interface for managing the Foodies platform.
Inspired by the eShop architecture patterns, this service enables authorized administrators to manage menu items, view user profiles, monitor system health, and access audit logs.

## Goals

1. **Centralized Management**: Single service for all administrative operations
2. **Role-Based Access**: Secure admin-only access with Keycloak integration
3. **Audit Compliance**: Full audit trail for all administrative actions
4. **Real-time Monitoring**: System health and metrics visibility
5. **Operational Efficiency**: Bulk operations and data export capabilities

## Architecture

### Service Design

```
┌─────────────────────────────────────────────────────────────────┐
│                      Admin Service (Port 8083)                   │
├─────────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐              │
│  │   Routes    │  │   Service   │  │ Repository  │              │
│  │  (HTTP)     │──│  (Logic)    │──│  (Data)     │──► PostgreSQL│
│  └─────────────┘  └─────────────┘  └─────────────┘              │
│         │                │                                       │
│         ▼                ▼                                       │
│  ┌─────────────┐  ┌─────────────┐                               │
│  │ Auth Guard  │  │ Audit Log   │                               │
│  │ (Keycloak)  │  │ (Recorder)  │                               │
│  └─────────────┘  └─────────────┘                               │
└─────────────────────────────────────────────────────────────────┘
         │                    │                    │
         ▼                    ▼                    ▼
   ┌──────────┐        ┌──────────┐        ┌──────────┐
   │ Menu DB  │        │Profile DB│        │ Admin DB │
   │ (5433)   │        │ (5432)   │        │ (5434)   │
   └──────────┘        └──────────┘        └──────────┘
```

### Integration Points

| Component | Integration Method | Purpose |
|-----------|-------------------|---------|
| Menu Service | Direct DB read + HTTP for writes | Menu item management |
| Profile Service | Direct DB read-only | User profile viewing |
| Keycloak | OAuth2/OIDC + Admin API | Authentication & user management |
| Admin Database | Direct connection | Audit logs, admin config |

## Authentication & Authorization

### Keycloak Configuration

**New Realm Roles**:
- `admin` - Full administrative access
- `menu-admin` - Menu management only
- `support` - Read-only access to users and orders

**Admin Client Configuration**:
```yaml
client_id: foodies-admin
client_secret: ${ADMIN_CLIENT_SECRET}
redirect_uri: http://localhost:8083/oauth/callback
scopes:
  - openid
  - profile
  - admin
```

### Authorization Middleware

All admin routes require:
1. Valid JWT token from Keycloak
2. `admin` or specific role in token claims
3. Token not expired
4. Matching audience claim

```kotlin
// Example route protection
route("/admin") {
    authenticate("admin-auth") {
        authorize("admin", "menu-admin") {
            menuAdminRoutes()
        }
        authorize("admin") {
            userAdminRoutes()
            auditRoutes()
        }
    }
}
```

## API Specification

### Base URL
```
http://localhost:8083/admin/api/v1
```

### Authentication
All endpoints require Bearer token authentication:
```
Authorization: Bearer <jwt_token>
```

---

### Menu Management Endpoints

#### List Menu Items
```http
GET /menu
```

**Query Parameters**:
| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| offset | Int | 0 | Pagination offset |
| limit | Int | 50 | Items per page (max 100) |
| search | String | null | Search by name/description |
| sortBy | String | "createdAt" | Sort field |
| sortOrder | String | "desc" | Sort direction (asc/desc) |

**Response** (200 OK):
```json
{
  "items": [
    {
      "id": 1,
      "name": "Margherita Pizza",
      "description": "Classic Italian pizza",
      "imageUrl": "https://example.com/pizza.jpg",
      "price": 12.99,
      "isActive": true,
      "createdAt": "2025-01-10T10:00:00Z",
      "updatedAt": "2025-01-10T10:00:00Z"
    }
  ],
  "total": 150,
  "offset": 0,
  "limit": 50
}
```

#### Get Menu Item
```http
GET /menu/{id}
```

**Response** (200 OK):
```json
{
  "id": 1,
  "name": "Margherita Pizza",
  "description": "Classic Italian pizza",
  "imageUrl": "https://example.com/pizza.jpg",
  "price": 12.99,
  "isActive": true,
  "createdAt": "2025-01-10T10:00:00Z",
  "updatedAt": "2025-01-10T10:00:00Z"
}
```

#### Create Menu Item
```http
POST /menu
```

**Request Body**:
```json
{
  "name": "Margherita Pizza",
  "description": "Classic Italian pizza with fresh mozzarella",
  "imageUrl": "https://example.com/pizza.jpg",
  "price": 12.99,
  "isActive": true
}
```

**Response** (201 Created):
```json
{
  "id": 1,
  "name": "Margherita Pizza",
  "description": "Classic Italian pizza with fresh mozzarella",
  "imageUrl": "https://example.com/pizza.jpg",
  "price": 12.99,
  "isActive": true,
  "createdAt": "2025-01-10T10:00:00Z",
  "updatedAt": "2025-01-10T10:00:00Z"
}
```

#### Update Menu Item
```http
PUT /menu/{id}
```

**Request Body** (partial update supported):
```json
{
  "price": 14.99,
  "isActive": false
}
```

**Response** (200 OK):
```json
{
  "id": 1,
  "name": "Margherita Pizza",
  "description": "Classic Italian pizza with fresh mozzarella",
  "imageUrl": "https://example.com/pizza.jpg",
  "price": 14.99,
  "isActive": false,
  "createdAt": "2025-01-10T10:00:00Z",
  "updatedAt": "2025-01-11T15:30:00Z"
}
```

#### Delete Menu Item
```http
DELETE /menu/{id}
```

**Response** (204 No Content)

#### Bulk Delete Menu Items
```http
POST /menu/bulk-delete
```

**Request Body**:
```json
{
  "ids": [1, 2, 3, 4, 5]
}
```

**Response** (200 OK):
```json
{
  "deleted": 5,
  "failed": []
}
```

#### Toggle Menu Item Availability
```http
PUT /menu/{id}/toggle-active
```

**Response** (200 OK):
```json
{
  "id": 1,
  "isActive": false
}
```

#### Export Menu Items
```http
GET /menu/export
```

**Query Parameters**:
| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| format | String | "json" | Export format (json/csv) |

**Response** (200 OK):
- Content-Type: `application/json` or `text/csv`
- Content-Disposition: `attachment; filename="menu-export-2025-01-11.json"`

#### Import Menu Items
```http
POST /menu/import
```

**Request Body** (multipart/form-data):
- `file`: CSV or JSON file
- `mode`: "create" | "upsert" | "replace"

**Response** (200 OK):
```json
{
  "created": 10,
  "updated": 5,
  "failed": 2,
  "errors": [
    {"row": 3, "error": "Invalid price format"},
    {"row": 7, "error": "Name is required"}
  ]
}
```

---

### User Management Endpoints

#### List Users
```http
GET /users
```

**Query Parameters**:
| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| offset | Int | 0 | Pagination offset |
| limit | Int | 50 | Users per page (max 100) |
| search | String | null | Search by email/name |
| status | String | null | Filter by status (active/inactive) |

**Response** (200 OK):
```json
{
  "users": [
    {
      "id": 1,
      "subject": "550e8400-e29b-41d4-a716-446655440000",
      "email": "user@example.com",
      "firstName": "John",
      "lastName": "Doe",
      "isActive": true,
      "createdAt": "2025-01-01T12:00:00Z"
    }
  ],
  "total": 1000,
  "offset": 0,
  "limit": 50
}
```

#### Get User Details
```http
GET /users/{id}
```

**Response** (200 OK):
```json
{
  "id": 1,
  "subject": "550e8400-e29b-41d4-a716-446655440000",
  "email": "user@example.com",
  "firstName": "John",
  "lastName": "Doe",
  "isActive": true,
  "createdAt": "2025-01-01T12:00:00Z",
  "lastLoginAt": "2025-01-10T08:30:00Z",
  "orderCount": 15,
  "totalSpent": 245.50
}
```

#### Update User Status
```http
PUT /users/{id}/status
```

**Request Body**:
```json
{
  "isActive": false,
  "reason": "Violation of terms of service"
}
```

**Response** (200 OK):
```json
{
  "id": 1,
  "isActive": false,
  "statusChangedAt": "2025-01-11T10:00:00Z"
}
```

#### Export User Data (GDPR)
```http
GET /users/{id}/export
```

**Response** (200 OK):
```json
{
  "profile": {
    "email": "user@example.com",
    "firstName": "John",
    "lastName": "Doe"
  },
  "orders": [],
  "exportedAt": "2025-01-11T10:00:00Z"
}
```

---

### Audit Log Endpoints

#### List Audit Logs
```http
GET /audit-logs
```

**Query Parameters**:
| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| offset | Int | 0 | Pagination offset |
| limit | Int | 50 | Logs per page (max 200) |
| action | String | null | Filter by action type |
| resource | String | null | Filter by resource type |
| adminId | String | null | Filter by admin user |
| from | DateTime | null | Start date filter |
| to | DateTime | null | End date filter |

**Response** (200 OK):
```json
{
  "logs": [
    {
      "id": 1,
      "action": "UPDATE",
      "resourceType": "MENU_ITEM",
      "resourceId": "42",
      "adminId": "admin-uuid",
      "adminEmail": "admin@example.com",
      "changesBefore": {"price": 12.99},
      "changesAfter": {"price": 14.99},
      "ipAddress": "192.168.1.1",
      "userAgent": "Mozilla/5.0...",
      "createdAt": "2025-01-11T10:00:00Z"
    }
  ],
  "total": 5000,
  "offset": 0,
  "limit": 50
}
```

#### Export Audit Logs
```http
GET /audit-logs/export
```

**Query Parameters**:
| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| format | String | "json" | Export format (json/csv) |
| from | DateTime | -30 days | Start date |
| to | DateTime | now | End date |

**Response** (200 OK):
- Content-Type: `application/json` or `text/csv`
- Content-Disposition: `attachment; filename="audit-logs-2025-01-11.json"`

---

### System Health Endpoints

#### Aggregated Health Check
```http
GET /health
```

**Response** (200 OK):
```json
{
  "status": "healthy",
  "services": {
    "admin": {"status": "healthy", "latency": 2},
    "menu": {"status": "healthy", "latency": 15},
    "profile": {"status": "healthy", "latency": 12},
    "webapp": {"status": "healthy", "latency": 8}
  },
  "databases": {
    "admin-db": {"status": "healthy", "connections": 5},
    "menu-db": {"status": "healthy", "connections": 10},
    "profile-db": {"status": "healthy", "connections": 8}
  },
  "infrastructure": {
    "rabbitmq": {"status": "healthy", "queues": 2},
    "keycloak": {"status": "healthy"}
  },
  "timestamp": "2025-01-11T10:00:00Z"
}
```

#### Service Metrics
```http
GET /metrics
```

**Response** (200 OK):
```json
{
  "menuItems": {
    "total": 150,
    "active": 142,
    "inactive": 8
  },
  "users": {
    "total": 1000,
    "active": 950,
    "registeredToday": 15
  },
  "system": {
    "uptime": "5d 12h 30m",
    "memoryUsage": "256MB",
    "cpuUsage": "12%"
  }
}
```

---

## Database Schema

### Admin Database (Port 5434)

#### Audit Logs Table
```sql
CREATE TABLE audit_logs (
    id BIGSERIAL PRIMARY KEY,
    action VARCHAR(50) NOT NULL,           -- CREATE, UPDATE, DELETE, EXPORT, etc.
    resource_type VARCHAR(50) NOT NULL,    -- MENU_ITEM, USER, CONFIG, etc.
    resource_id VARCHAR(255),              -- ID of affected resource
    admin_id VARCHAR(255) NOT NULL,        -- Keycloak subject of admin
    admin_email VARCHAR(255) NOT NULL,     -- Email for readability
    changes_before JSONB,                  -- State before change
    changes_after JSONB,                   -- State after change
    ip_address VARCHAR(45),                -- IPv4 or IPv6
    user_agent TEXT,                       -- Browser/client info
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_logs_action ON audit_logs(action);
CREATE INDEX idx_audit_logs_resource_type ON audit_logs(resource_type);
CREATE INDEX idx_audit_logs_admin_id ON audit_logs(admin_id);
CREATE INDEX idx_audit_logs_created_at ON audit_logs(created_at);
```

#### User Status Table
```sql
CREATE TABLE user_status (
    id BIGSERIAL PRIMARY KEY,
    profile_id BIGINT NOT NULL,            -- References profile.profiles.id
    subject VARCHAR(255) NOT NULL UNIQUE,  -- Keycloak subject for lookup
    is_active BOOLEAN NOT NULL DEFAULT true,
    status_reason TEXT,                    -- Reason for status change
    last_login_at TIMESTAMPTZ,
    status_changed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_user_status_subject ON user_status(subject);
CREATE INDEX idx_user_status_is_active ON user_status(is_active);
```

#### Admin Configuration Table
```sql
CREATE TABLE admin_config (
    id BIGSERIAL PRIMARY KEY,
    key VARCHAR(255) NOT NULL UNIQUE,
    value JSONB NOT NULL,
    description TEXT,
    updated_by VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

### Menu Database Extension

Add `is_active` column to existing `menu_items` table:

```sql
-- Migration: V2__add_menu_item_active_status.sql
ALTER TABLE menu_items
ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT true;

CREATE INDEX idx_menu_items_is_active ON menu_items(is_active);
```

## Data Models

### Kotlin Data Classes

```kotlin
// Request/Response Models
@Serializable
data class AdminMenuItemResponse(
    val id: Long,
    val name: String,
    val description: String,
    val imageUrl: String,
    val price: BigDecimal,
    val isActive: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant
)

@Serializable
data class AdminCreateMenuItemRequest(
    val name: String,
    val description: String,
    val imageUrl: String,
    val price: BigDecimal,
    val isActive: Boolean = true
)

@Serializable
data class AdminUpdateMenuItemRequest(
    val name: String? = null,
    val description: String? = null,
    val imageUrl: String? = null,
    val price: BigDecimal? = null,
    val isActive: Boolean? = null
)

@Serializable
data class BulkDeleteRequest(
    val ids: List<Long>
)

@Serializable
data class BulkDeleteResponse(
    val deleted: Int,
    val failed: List<Long>
)

@Serializable
data class UserResponse(
    val id: Long,
    val subject: String,
    val email: String,
    val firstName: String,
    val lastName: String,
    val isActive: Boolean,
    val createdAt: Instant,
    val lastLoginAt: Instant? = null
)

@Serializable
data class UpdateUserStatusRequest(
    val isActive: Boolean,
    val reason: String? = null
)

@Serializable
data class AuditLogResponse(
    val id: Long,
    val action: String,
    val resourceType: String,
    val resourceId: String?,
    val adminId: String,
    val adminEmail: String,
    val changesBefore: JsonObject?,
    val changesAfter: JsonObject?,
    val ipAddress: String?,
    val userAgent: String?,
    val createdAt: Instant
)

@Serializable
data class HealthResponse(
    val status: String,
    val services: Map<String, ServiceHealth>,
    val databases: Map<String, DatabaseHealth>,
    val infrastructure: Map<String, ServiceHealth>,
    val timestamp: Instant
)

@Serializable
data class ServiceHealth(
    val status: String,
    val latency: Long? = null
)

@Serializable
data class DatabaseHealth(
    val status: String,
    val connections: Int? = null
)
```

### Exposed ORM Tables

```kotlin
object AuditLogs : LongIdTable("audit_logs") {
    val action = varchar("action", 50)
    val resourceType = varchar("resource_type", 50)
    val resourceId = varchar("resource_id", 255).nullable()
    val adminId = varchar("admin_id", 255)
    val adminEmail = varchar("admin_email", 255)
    val changesBefore = jsonb<JsonObject>("changes_before").nullable()
    val changesAfter = jsonb<JsonObject>("changes_after").nullable()
    val ipAddress = varchar("ip_address", 45).nullable()
    val userAgent = text("user_agent").nullable()
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)
}

object UserStatus : LongIdTable("user_status") {
    val profileId = long("profile_id")
    val subject = varchar("subject", 255).uniqueIndex()
    val isActive = bool("is_active").default(true)
    val statusReason = text("status_reason").nullable()
    val lastLoginAt = timestampWithTimeZone("last_login_at").nullable()
    val statusChangedAt = timestampWithTimeZone("status_changed_at").nullable()
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)
    val updatedAt = timestampWithTimeZone("updated_at").defaultExpression(CurrentTimestampWithTimeZone)
}

object AdminConfig : LongIdTable("admin_config") {
    val key = varchar("key", 255).uniqueIndex()
    val value = jsonb<JsonObject>("value")
    val description = text("description").nullable()
    val updatedBy = varchar("updated_by", 255).nullable()
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)
    val updatedAt = timestampWithTimeZone("updated_at").defaultExpression(CurrentTimestampWithTimeZone)
}
```

## Configuration

### Application Configuration

```yaml
# admin/src/main/resources/application.yaml
ktor:
  deployment:
    host: ${HOST:0.0.0.0}
    port: ${PORT:8083}
  application:
    modules:
      - io.ktor.foodies.admin.AdminAppKt.module

admin:
  database:
    url: ${ADMIN_DB_URL:jdbc:postgresql://localhost:5434/foodies_admin}
    username: ${ADMIN_DB_USERNAME:foodies}
    password: ${ADMIN_DB_PASSWORD:foodies}
    poolSize: ${ADMIN_DB_POOL_SIZE:10}

  menu:
    database:
      url: ${MENU_DB_URL:jdbc:postgresql://localhost:5433/foodies_menu}
      username: ${MENU_DB_USERNAME:foodies}
      password: ${MENU_DB_PASSWORD:foodies}
      poolSize: ${MENU_DB_POOL_SIZE:5}

  profile:
    database:
      url: ${PROFILE_DB_URL:jdbc:postgresql://localhost:5432/foodies_profile}
      username: ${PROFILE_DB_USERNAME:foodies}
      password: ${PROFILE_DB_PASSWORD:foodies}
      poolSize: ${PROFILE_DB_POOL_SIZE:5}

  auth:
    issuer: ${AUTH_ISSUER:http://localhost:8000/realms/foodies-keycloak}
    clientId: ${AUTH_CLIENT_ID:foodies-admin}
    clientSecret: ${AUTH_CLIENT_SECRET:admin_client_secret}
    requiredRoles:
      - admin

  services:
    menu:
      baseUrl: ${MENU_SERVICE_URL:http://localhost:8082}
      timeout: ${MENU_SERVICE_TIMEOUT:30000}
    profile:
      baseUrl: ${PROFILE_SERVICE_URL:http://localhost:8081}
      timeout: ${PROFILE_SERVICE_TIMEOUT:30000}
    webapp:
      baseUrl: ${WEBAPP_SERVICE_URL:http://localhost:8080}
      timeout: ${WEBAPP_SERVICE_TIMEOUT:30000}
```

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| HOST | 0.0.0.0 | Server bind address |
| PORT | 8083 | Server port |
| ADMIN_DB_URL | jdbc:postgresql://localhost:5434/foodies_admin | Admin database URL |
| ADMIN_DB_USERNAME | foodies | Admin database username |
| ADMIN_DB_PASSWORD | foodies | Admin database password |
| MENU_DB_URL | jdbc:postgresql://localhost:5433/foodies_menu | Menu database URL (read) |
| PROFILE_DB_URL | jdbc:postgresql://localhost:5432/foodies_profile | Profile database URL (read) |
| AUTH_ISSUER | http://localhost:8000/realms/foodies-keycloak | Keycloak issuer URL |
| AUTH_CLIENT_ID | foodies-admin | Admin OAuth client ID |
| AUTH_CLIENT_SECRET | admin_client_secret | Admin OAuth client secret |

## Project Structure

```
admin/
├── build.gradle.kts
├── src/
│   ├── main/
│   │   ├── kotlin/
│   │   │   └── io/ktor/foodies/admin/
│   │   │       ├── AdminApp.kt              # Application entry point
│   │   │       ├── Config.kt                # Configuration loading
│   │   │       ├── Module.kt                # Dependency wiring
│   │   │       ├── auth/
│   │   │       │   ├── AdminAuth.kt         # Authentication setup
│   │   │       │   ├── AuthorizationPlugin.kt # Role-based authorization
│   │   │       │   └── AdminPrincipal.kt    # Admin user principal
│   │   │       ├── menu/
│   │   │       │   ├── MenuAdminRoutes.kt   # Menu management routes
│   │   │       │   ├── MenuAdminService.kt  # Menu business logic
│   │   │       │   └── MenuAdminRepository.kt # Menu data access
│   │   │       ├── users/
│   │   │       │   ├── UserAdminRoutes.kt   # User management routes
│   │   │       │   ├── UserAdminService.kt  # User business logic
│   │   │       │   └── UserAdminRepository.kt # User data access
│   │   │       ├── audit/
│   │   │       │   ├── AuditRoutes.kt       # Audit log routes
│   │   │       │   ├── AuditService.kt      # Audit business logic
│   │   │       │   ├── AuditRepository.kt   # Audit data access
│   │   │       │   └── AuditInterceptor.kt  # Automatic audit logging
│   │   │       ├── health/
│   │   │       │   ├── HealthRoutes.kt      # Health check routes
│   │   │       │   └── HealthService.kt     # Health aggregation
│   │   │       └── export/
│   │   │           ├── ExportService.kt     # CSV/JSON export
│   │   │           └── ImportService.kt     # CSV/JSON import
│   │   └── resources/
│   │       ├── application.yaml
│   │       └── db/migration/
│   │           ├── V1__create_audit_logs_table.sql
│   │           ├── V2__create_user_status_table.sql
│   │           └── V3__create_admin_config_table.sql
│   └── test/
│       └── kotlin/
│           └── io/ktor/foodies/admin/
│               ├── menu/
│               │   ├── MenuAdminRoutesSpec.kt
│               │   └── MenuAdminServiceSpec.kt
│               ├── users/
│               │   └── UserAdminServiceSpec.kt
│               └── audit/
│                   └── AuditServiceSpec.kt
└── README.md
```

## Implementation Guidelines

### Layered Architecture

Follow the existing Foodies pattern:

```
Routes (HTTP handling)
    ↓
Service (Business logic, validation)
    ↓
Repository (Data access, transactions)
    ↓
Database (PostgreSQL via Exposed)
```

### Manual Dependency Injection

Wire dependencies explicitly in `Module.kt`:

```kotlin
class AdminModule(config: AdminConfig) {
    // Repositories
    val auditRepository = AuditRepository(adminDataSource)
    val menuAdminRepository = MenuAdminRepository(menuDataSource)
    val userAdminRepository = UserAdminRepository(profileDataSource, adminDataSource)

    // Services
    val auditService = AuditService(auditRepository)
    val menuAdminService = MenuAdminService(menuAdminRepository, auditService)
    val userAdminService = UserAdminService(userAdminRepository, auditService)
    val healthService = HealthService(config.services)

    // Cleanup on shutdown
    fun close() {
        adminDataSource.close()
        menuDataSource.close()
        profileDataSource.close()
    }
}
```

### Validation Pattern

Use the existing `validate { }` DSL:

```kotlin
fun validateCreateMenuItem(request: AdminCreateMenuItemRequest): List<String> = validate {
    request.name.validate(String::isNotBlank) { "name must not be blank" }
    request.description.validate(String::isNotBlank) { "description must not be blank" }
    request.imageUrl.validate(String::isNotBlank) { "imageUrl must not be blank" }
    request.price.validate({ it > BigDecimal.ZERO }) { "price must be greater than 0" }
}
```

### Audit Logging

Automatically log all write operations:

```kotlin
class AuditInterceptor(private val auditService: AuditService) {
    suspend fun <T> withAudit(
        action: String,
        resourceType: String,
        resourceId: String?,
        admin: AdminPrincipal,
        before: Any? = null,
        block: suspend () -> T
    ): T {
        val result = block()
        auditService.log(
            AuditEntry(
                action = action,
                resourceType = resourceType,
                resourceId = resourceId,
                adminId = admin.subject,
                adminEmail = admin.email,
                changesBefore = before?.toJsonObject(),
                changesAfter = result?.toJsonObject(),
                ipAddress = admin.ipAddress,
                userAgent = admin.userAgent
            )
        )
        return result
    }
}
```

### Error Handling

Use consistent error responses:

```kotlin
@Serializable
data class ErrorResponse(
    val error: String,
    val message: String,
    val details: List<String>? = null,
    val timestamp: Instant = Clock.System.now()
)

// HTTP status codes:
// 400 - Validation errors
// 401 - Not authenticated
// 403 - Not authorized (missing role)
// 404 - Resource not found
// 409 - Conflict (e.g., duplicate)
// 500 - Internal server error
```

## Testing Strategy

### Unit Tests
- Service validation logic
- Data transformation
- Business rules

### Integration Tests
- Repository operations with Testcontainers
- Authentication/authorization middleware
- API contract tests

### Test Fixtures

```kotlin
class AdminTestFixture : TestFixture {
    val adminDb = PostgreSQLContainer("postgres:15")
    val menuDb = PostgreSQLContainer("postgres:15")
    val profileDb = PostgreSQLContainer("postgres:15")

    val testAdmin = AdminPrincipal(
        subject = "test-admin-uuid",
        email = "admin@test.com",
        roles = setOf("admin")
    )
}
```

## Deployment

### Docker Compose Addition

```yaml
# webapp/docker-compose.yml (addition)
admin:
  build:
    context: ../admin
    dockerfile: Dockerfile
  ports:
    - "8083:8083"
  environment:
    - PORT=8083
    - ADMIN_DB_URL=jdbc:postgresql://admin-database:5432/foodies_admin
    - MENU_DB_URL=jdbc:postgresql://menu-database:5432/foodies_menu
    - PROFILE_DB_URL=jdbc:postgresql://profile-database:5432/foodies_profile
    - AUTH_ISSUER=http://keycloak:8080/realms/foodies-keycloak
  depends_on:
    - admin-database
    - menu-database
    - profile-database
    - keycloak

admin-database:
  image: postgres:15
  environment:
    - POSTGRES_DB=foodies_admin
    - POSTGRES_USER=foodies
    - POSTGRES_PASSWORD=foodies
  ports:
    - "5434:5432"
  volumes:
    - admin-data:/var/lib/postgresql/data

volumes:
  admin-data:
```

### Kubernetes Manifests

Create manifests in `k8s/services/admin/`:
- `deployment.yaml`
- `service.yaml`
- `configmap.yaml`

## Security Considerations

1. **Authentication**: All endpoints require valid JWT from Keycloak
2. **Authorization**: Role-based access control (RBAC) enforced at route level
3. **Audit Trail**: All write operations logged with admin identity
4. **Data Access**: Read-only connections to menu/profile databases
5. **Rate Limiting**: Consider adding rate limiting for export operations
6. **Input Validation**: All inputs validated before processing
7. **CORS**: Restrict to admin dashboard origin only

## Future Enhancements

1. **Admin Dashboard UI**: HTMX-based admin interface (separate module)
2. **Order Management**: When order service is implemented
3. **Analytics Dashboard**: Menu performance, user metrics
4. **Notification System**: Alert admins on critical events
5. **Scheduled Tasks**: Automated reports, data cleanup
6. **Multi-tenancy**: Support for multiple restaurant branches
