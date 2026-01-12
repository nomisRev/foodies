# Payment Service Implementation TODO

## Phase 1: Project Setup

### Task 1.1: Register :payment module
- [x] Add `include(":payment")` to `settings.gradle.kts`
- [x] Create `payment/build.gradle.kts` with dependencies from specification

### Task 1.2: Define domain models and configuration
- [x] Create `payment/src/main/kotlin/io/ktor/foodies/payment/Domain.kt` with models from specification
- [x] Create `payment/src/main/kotlin/io/ktor/foodies/payment/Config.kt` and `payment/src/main/resources/application.yaml`

### Task 1.3: Database Schema and Repository
- [x] Create database migration `payment/src/main/resources/db/migration/V1__create_payment_table.sql`
- [x] Create `payment/src/main/kotlin/io/ktor/foodies/payment/Repository.kt` with `PaymentRepository` interface and implementation

### Task 1.4: External Payment Gateway
- [x] Implement `PaymentGateway` interface and `SimulatedPaymentGateway`

## Phase 2: Service Layer and Events

### Task 2.1: Implement Payment Service
- [x] Create `PaymentService` interface and implementation in `payment/src/main/kotlin/io/ktor/foodies/payment/Service.kt`
- [x] Implement idempotency logic and gateway integration

### Task 2.2: Implement Events & RabbitMQ Integration
- [x] Create `payment/src/main/kotlin/io/ktor/foodies/payment/events/Events.kt` with event definitions
- [ ] Implement `EventPublisher` and `RabbitMQEventPublisher`
- [ ] Implement `OrderStockConfirmedEventHandler`
- [ ] Implement `RabbitMQEventConsumer`

## Phase 3: API and Application Wiring

### Task 3.1: Implement API and DI
- [ ] Create `payment/src/main/kotlin/io/ktor/foodies/payment/Routes.kt` with health and admin routes
