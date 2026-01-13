# TODO: Distributed Session Storage

## Phase 1: Add Redis Session Storage Implementation
- [x] Create `SessionStorage` interface
- [x] Implement `RedisSessionStorage` with Lettuce
- [x] Implement `InMemorySessionStorage` for testing
- [x] Add configuration classes

## Phase 2: Update WebApp Configuration
- [x] Add Redis configuration to `Config.kt`
- [x] Add Redis connection management to `Security.kt`
- [x] Update `Sessions` plugin to use `RedisSessionStorage`
- [x] Add Redis health check to readiness probe

## Phase 3: Update Dependencies
- [x] Add Lettuce to `webapp/build.gradle.kts`
- [x] Add Cohort Lettuce for health checks
- [x] Add TestContainers Redis for tests

## Phase 4: Update Kubernetes Configuration
- [ ] Add Redis environment variables to `webapp.yaml`
- [ ] Update readiness probe to include Redis check
- [ ] Document configuration options

## Phase 5: Testing
- [ ] Write unit tests for `RedisSessionStorage`
- [ ] Write integration tests with TestContainers
- [ ] Test session persistence across pod restarts
- [ ] Test concurrent session access from multiple replicas
- [ ] Load test with multiple WebApp replicas

## Phase 6: Deployment
- [ ] Deploy updated WebApp image
- [ ] Verify Redis connectivity
- [ ] Monitor session creation/retrieval metrics
- [ ] Test login flow with multiple replicas
