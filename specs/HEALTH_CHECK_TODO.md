# Health Check Implementation TODO

This document breaks down the implementation of the Health Check Specification into small, actionable tasks.

## Phase 1: Dependencies ✅ COMPLETED

### Task 1.1: Add Cohort Dependencies to server-shared ✅
- [x] Open `server-shared/build.gradle.kts`
- [x] Add `api("com.sksamuel.cohort:cohort-ktor:2.8.2")` to dependencies block
- [x] Add `api("com.sksamuel.cohort:cohort-api:2.8.2")` to dependencies block
- [x] Add `api("com.sksamuel.cohort:cohort-hikari:2.8.2")` to dependencies block
- [x] Sync Gradle project

### Task 1.2: Add RabbitMQ Health Check Dependency to profile ✅
- [x] Open `profile/build.gradle.kts`
- [x] Add `implementation("com.sksamuel.cohort:cohort-rabbit:2.8.2")` to dependencies block
- [x] Sync Gradle project

## Phase 2: Shared Health Check Utilities ✅ COMPLETED

### Task 2.1: Create Health.kt in server-shared ✅
- [x] Create file `server-shared/src/main/kotlin/io/ktor/foodies/server/Health.kt`
- [x] Add package declaration and imports
- [x] Implement `defaultLivenessChecks()` function with ThreadDeadlockHealthCheck
- [x] Implement `databaseHealthCheckRegistry()` function with HikariConnectionsHealthCheck
- [x] Add KDoc comments to all functions

### Task 2.2: Create RabbitHealthCheck.kt in profile ✅
- [x] Create file `profile/src/main/kotlin/io/ktor/foodies/server/RabbitHealthCheck.kt`
- [x] Add package declaration and imports
- [x] Implement `RabbitConnectionHealthCheck` class implementing `HealthCheck` interface
- [x] Override `name` property with value "rabbitmq-connection"
- [x] Override `check()` suspend function to verify `connection.isOpen`
- [x] Return `HealthCheckResult.healthy()` when open, `HealthCheckResult.unhealthy()` when closed
- [x] Add KDoc comments

## Phase 3: Menu Service Health Checks ✅ COMPLETED

### Task 3.1: Update MenuModule to expose HikariDataSource ✅
- [x] Open `menu/src/main/kotlin/io/ktor/foodies/menu/MenuModule.kt`
- [x] Add `dataSource: HikariDataSource` property to MenuModule data class
- [x] Update `module()` function to pass dataSource to MenuModule constructor

### Task 3.2: Install Cohort in Menu Service ✅
- [x] Open `menu/src/main/kotlin/io/ktor/foodies/menu/App.kt`
- [x] Add import for `com.sksamuel.cohort.Cohort`
- [x] Add import for `com.sksamuel.cohort.HealthCheckRegistry`
- [x] Add import for `com.sksamuel.cohort.hikari.HikariConnectionsHealthCheck`
- [x] Add import for `io.ktor.foodies.server.defaultLivenessChecks`

### Task 3.3: Configure Menu Service Health Endpoints ✅
- [x] In `app()` function, install Cohort plugin
- [x] Set `verboseHealthCheckResponse = true`
- [x] Add startup probe: `healthcheck("/healthz/startup", HealthCheckRegistry(Dispatchers.Default))`
- [x] Add liveness probe: `healthcheck("/healthz/liveness", defaultLivenessChecks())`
- [x] Add readiness probe with database check using `HikariConnectionsHealthCheck`

### Task 3.4: Remove Old Menu Health Check Route ✅
- [x] Search for old `healthz()` route function in menu service
- [x] Remove the route registration from routing block
- [x] Delete the old `healthz()` function if it exists

## Phase 4: Profile Service Health Checks ✅ COMPLETED

### Task 4.1: Update ProfileModule to expose dependencies ✅
- [x] Open `profile/src/main/kotlin/io/ktor/foodies/server/ProfileModule.kt`
- [x] Add `dataSource: HikariDataSource` property to ProfileModule
- [x] Add `connection: Connection` property to ProfileModule
- [x] Update `module()` function to pass both dataSource and connection to ProfileModule constructor

### Task 4.2: Install Cohort in Profile Service ✅
- [x] Open `profile/src/main/kotlin/io/ktor/foodies/server/ProfileApp.kt`
- [x] Add import for `com.sksamuel.cohort.Cohort`
- [x] Add import for `com.sksamuel.cohort.HealthCheckRegistry`
- [x] Add import for `com.sksamuel.cohort.hikari.HikariConnectionsHealthCheck`
- [x] Add import for `io.ktor.foodies.server.defaultLivenessChecks`

### Task 4.3: Configure Profile Service Health Endpoints ✅
- [x] In `app()` function, install Cohort plugin
- [x] Set `verboseHealthCheckResponse = true`
- [x] Add startup probe: `healthcheck("/healthz/startup", HealthCheckRegistry(Dispatchers.Default))`
- [x] Add liveness probe: `healthcheck("/healthz/liveness", defaultLivenessChecks())`
- [x] Add readiness probe with database check using `HikariConnectionsHealthCheck`
- [x] Add readiness probe with RabbitMQ check using `RabbitConnectionHealthCheck`

### Task 4.4: Remove Old Profile Health Check Route ✅
- [x] Search for old `healthz()` route function in profile service
- [x] Remove the route registration from routing block
- [x] Delete the old `healthz()` function if it exists

## Phase 5: Webapp Service Health Checks ✅ COMPLETED

### Task 5.1: Install Cohort in Webapp ✅
- [x] Open `webapp/src/main/kotlin/io/ktor/foodies/server/WebApp.kt`
- [x] Add import for `com.sksamuel.cohort.Cohort`
- [x] Add import for `com.sksamuel.cohort.HealthCheckRegistry`
- [x] Add custom HTTP health check implementation for menu service
- [x] Add import for `io.ktor.foodies.server.defaultLivenessChecks`

### Task 5.2: Configure Webapp Health Endpoints ✅
- [x] In `app()` function, install Cohort plugin
- [x] Set `verboseHealthCheckResponse = true`
- [x] Add startup probe: `healthcheck("/healthz/startup", HealthCheckRegistry(Dispatchers.Default))`
- [x] Add liveness probe: `healthcheck("/healthz/liveness", defaultLivenessChecks())`
- [x] Add readiness probe with menu service check using custom HTTP health check

### Task 5.3: Remove Old Webapp Health Check Route ✅
- [x] Search for old `healthz()` route function in webapp
- [x] Remove the route registration from routing block
- [x] Delete the old `healthz()` function if it exists

## Phase 6: Kubernetes Configuration Updates

### Task 6.1: Update Menu Service K8s Deployment
- [ ] Open `k8s/services/menu.yaml`
- [ ] Add `startupProbe` configuration with path `/healthz/startup`, port 8082
- [ ] Set startup probe timing: periodSeconds=2, timeoutSeconds=3, failureThreshold=30
- [ ] Add `livenessProbe` configuration with path `/healthz/liveness`, port 8082
- [ ] Set liveness probe timing: periodSeconds=10, timeoutSeconds=3, failureThreshold=3
- [ ] Add `readinessProbe` configuration with path `/healthz/readiness`, port 8082
- [ ] Set readiness probe timing: periodSeconds=5, timeoutSeconds=5, failureThreshold=3

### Task 6.2: Update Profile Service K8s Deployment
- [ ] Open `k8s/services/profile.yaml`
- [ ] Add `startupProbe` configuration with path `/healthz/startup`, port 8081
- [ ] Set startup probe timing: periodSeconds=2, timeoutSeconds=3, failureThreshold=30
- [ ] Add `livenessProbe` configuration with path `/healthz/liveness`, port 8081
- [ ] Set liveness probe timing: periodSeconds=10, timeoutSeconds=3, failureThreshold=3
- [ ] Add `readinessProbe` configuration with path `/healthz/readiness`, port 8081
- [ ] Set readiness probe timing: periodSeconds=5, timeoutSeconds=5, failureThreshold=3

### Task 6.3: Update Webapp K8s Deployment
- [ ] Open `k8s/services/webapp.yaml`
- [ ] Add `startupProbe` configuration with path `/healthz/startup`, port 8080
- [ ] Set startup probe timing: periodSeconds=2, timeoutSeconds=3, failureThreshold=30
- [ ] Add `livenessProbe` configuration with path `/healthz/liveness`, port 8080
- [ ] Set liveness probe timing: periodSeconds=10, timeoutSeconds=3, failureThreshold=3
- [ ] Add `readinessProbe` configuration with path `/healthz/readiness`, port 8080
- [ ] Set readiness probe timing: periodSeconds=5, timeoutSeconds=5, failureThreshold=3

## Phase 7: Testing

### Task 7.1: Write Unit Tests for Health Utilities
- [ ] Create test file `server-shared/src/test/kotlin/io/ktor/foodies/server/HealthTest.kt`
- [ ] Write test for `defaultLivenessChecks()` returns registry with ThreadDeadlockHealthCheck
- [ ] Write test for `databaseHealthCheckRegistry()` with mock HikariDataSource
- [ ] Write test for `httpServiceHealthCheck()` configuration

### Task 7.2: Write Integration Tests for Menu Service Health
- [ ] Create test file `menu/src/test/kotlin/io/ktor/foodies/menu/HealthCheckSpec.kt`
- [ ] Write test that `/healthz/startup` returns 200 OK
- [ ] Write test that `/healthz/liveness` returns 200 OK with ThreadDeadlockHealthCheck status
- [ ] Write test that `/healthz/readiness` returns 200 OK when database is healthy
- [ ] Write test that `/healthz/readiness` returns 503 when database is unavailable

### Task 7.3: Write Integration Tests for Profile Service Health
- [ ] Create test file `profile/src/test/kotlin/io/ktor/foodies/server/HealthCheckSpec.kt`
- [ ] Write test that `/healthz/startup` returns 200 OK
- [ ] Write test that `/healthz/liveness` returns 200 OK with ThreadDeadlockHealthCheck status
- [ ] Write test that `/healthz/readiness` returns 200 OK when database and RabbitMQ are healthy
- [ ] Write test that `/healthz/readiness` returns 503 when database is unavailable
- [ ] Write test that `/healthz/readiness` returns 503 when RabbitMQ is unavailable

### Task 7.4: Write Integration Tests for Webapp Health
- [ ] Create test file `webapp/src/test/kotlin/io/ktor/foodies/server/HealthCheckSpec.kt`
- [ ] Write test that `/healthz/startup` returns 200 OK
- [ ] Write test that `/healthz/liveness` returns 200 OK with ThreadDeadlockHealthCheck status
- [ ] Write test that `/healthz/readiness` returns 200 OK when menu service is healthy
- [ ] Write test that `/healthz/readiness` returns 503 when menu service is unavailable

### Task 7.5: Manual Testing in Local Environment
- [ ] Start all services locally (menu, profile, webapp)
- [ ] Test `curl http://localhost:8082/healthz/startup` returns 200
- [ ] Test `curl http://localhost:8082/healthz/liveness` returns 200 with verbose JSON
- [ ] Test `curl http://localhost:8082/healthz/readiness` returns 200 with database status
- [ ] Repeat above tests for profile (port 8081) and webapp (port 8080)
- [ ] Stop PostgreSQL container and verify readiness endpoints return 503
- [ ] Restart PostgreSQL and verify readiness endpoints return 200 again

### Task 7.6: Kubernetes Probe Testing
- [ ] Build and tag Docker images for all three services
- [ ] Deploy to local Kubernetes cluster (minikube/kind/docker-desktop)
- [ ] Verify pods reach "Ready" state within 60 seconds
- [ ] Check pod events: `kubectl describe pod -n foodies -l app=menu`
- [ ] Verify no CrashLoopBackOff during startup
- [ ] Scale down PostgreSQL to test readiness probe behavior
- [ ] Verify pods are removed from service endpoints but NOT restarted
- [ ] Restore PostgreSQL and verify pods return to service

## Phase 8: Documentation

### Task 8.1: Update Service READMEs
- [ ] Update `menu/README.MD` with new health check endpoints documentation
- [ ] Update `profile/README.MD` with new health check endpoints documentation
- [ ] Update `webapp/README.MD` with new health check endpoints documentation
- [ ] Document the three-probe strategy (startup, liveness, readiness)

### Task 8.2: Update Main README
- [ ] Update main `README.md` to reference new health check endpoints
- [ ] Add section explaining health check architecture
- [ ] Document probe behavior and timing configuration

### Task 8.3: Update K8s Documentation
- [ ] Update `k8s/README.md` with health check probe configuration
- [ ] Document troubleshooting steps for probe failures
- [ ] Add examples of checking probe status with kubectl

## Phase 9: Validation and Cleanup

### Task 9.1: Code Review Checklist
- [ ] Verify all three services have startup, liveness, and readiness probes
- [ ] Confirm liveness probes do NOT check external dependencies
- [ ] Confirm readiness probes DO check all required dependencies
- [ ] Verify all old `healthz()` route functions are removed
- [ ] Check that verbose health responses are enabled in all services

### Task 9.2: End-to-End Validation
- [ ] Deploy complete stack to Kubernetes
- [ ] Verify all pods become ready
- [ ] Simulate database failure and verify behavior (pods stay running, removed from LB)
- [ ] Simulate RabbitMQ failure for profile service and verify behavior
- [ ] Verify rolling updates work correctly with new probe configuration
- [ ] Load test to ensure probes don't impact performance

### Task 9.3: Performance Validation
- [ ] Monitor probe request frequency matches configuration (startup: 2s, liveness: 10s, readiness: 5s)
- [ ] Verify probe timeouts are respected
- [ ] Check that health check registries cache results appropriately
- [ ] Ensure no excessive resource consumption from health checks

## Notes

- Complete tasks in order by phase for cleanest implementation
- Test after each phase before moving to the next
- Pay special attention to Phase 6 (K8s config) - incorrect probe configuration can cause cascading failures
- Remember: Liveness probes should NEVER check external dependencies
- All database and RabbitMQ checks belong in readiness probes only
