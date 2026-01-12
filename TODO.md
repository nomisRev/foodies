# Payment Service Implementation TODO

## Phase 1: Project Setup

### Task 1.1: Register :payment module
- [x] Add `include(":payment")` to `settings.gradle.kts`
- [x] Create `payment/build.gradle.kts` with dependencies from specification

### Task 1.2: Define domain models and configuration
- [x] Create `payment/src/main/kotlin/io/ktor/foodies/payment/Domain.kt` with models from specification
- [x] Create `payment/src/main/kotlin/io/ktor/foodies/payment/Config.kt` and `payment/src/main/resources/application.yaml`

### Task 1.3: Database Schema and Repository
- [ ] Create database migration `payment/src/main/resources/db/migration/V1__create_payment_table.sql`
- [ ] Create `payment/src/main/kotlin/io/ktor/foodies/payment/Repository.kt` with `PaymentRepository` interface and implementation
