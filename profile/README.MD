# profile

Processes user lifecycle events from Keycloak and keeps profile data in Postgres. On start it applies Flyway migrations
and begins consuming RabbitMQ messages.

## Runtime dependencies

- Postgres (defaults: `jdbc:postgresql://localhost:5432/foodies-profile-database`, username `foodies_admin`, password `foodies_password`)
- RabbitMQ queue for user events (defaults to host `localhost`, port `5672`, queue `profile.registration`)

## Configuration

All values are loaded from `config` in `src/main/resources/application.yaml` and can be overridden via env vars:

- `HOST` / `PORT`: server bind address and port (default `0.0.0.0:8081`)
- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`: Postgres connection
- `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD`, `RABBITMQ_QUEUE`: queue connectivity

## Behavior

- Consumers: processes `UserEvent.Registration`, `UserEvent.UpdateProfile`, and `UserEvent.Delete` messages, acking or
  nacking per message. Retries with a simple delay loop on failures.
- Persistence: `profiles` table with unique `subject`; operations are idempotent where possible (`insertOrIgnore`).
- HTTP: `GET /healthz` for readiness checks.

## Running locally

```bash
# ensure Postgres and RabbitMQ are reachable (see env defaults above)
./gradlew :profile:run
```
