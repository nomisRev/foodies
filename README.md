# foodies

## Modules

- `webapp`: Ktor server serving the web UI and handling authentication with Keycloak.
- `profile`: Ktor service for user profile data and webhooks.
- `keycloak-webhook`: Custom Keycloak event listener provider that forwards registration events to the profile service.
- `server-shared` and `server-shared-test`: Shared server utilities and test helpers.

## Running Keycloak with the profile registration webhook

1) Build the provider jar so Keycloak can load it:

```bash
./gradlew :keycloak-webhook:build
```

2) Start Keycloak and RabbitMQ (from the `webapp` module directory) with the included `docker-compose.yml`:

```bash
cd webapp
docker compose up keycloak rabbitmq
```

Environment variables (with defaults) used by the Keycloak container for RabbitMQ delivery:

- `RABBITMQ_HOST` (default `rabbitmq`)
- `RABBITMQ_PORT` (default `5672`)
- `RABBITMQ_USERNAME` (default `guest`)
- `RABBITMQ_PASSWORD` (default `guest`)
- `RABBITMQ_QUEUE` (default `profile.registration`)

The realm import in `webapp/keycloak/realm.json` enables the `profile-webhook` listener for `REGISTER` events.

## Running the profile service

The profile service listens on port `8081` by default (see `profile/src/main/resources/application.yaml`) and consumes Keycloak registration events from RabbitMQ.
Update the `rabbit` section in `application.yaml` (or the corresponding environment variables) so it can reach your broker. A RabbitMQ container is included in `profile/docker-compose.yml`:

```bash
cd profile
docker compose up rabbitmq
```

Start the service with:

```bash
./gradlew :profile:run
```

## Running tests

Use the Gradle Wrapper from the project root:

```bash
./gradlew check
```