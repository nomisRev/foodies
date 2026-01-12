# menu

CRUD service for menu items backed by Postgres. Runs Flyway migrations on startup and exposes a JSON REST API.

## Configuration

Values come from `config` in `src/main/resources/application.yaml` (env overrides shown in parentheses):

- `HOST` / `PORT`: bind address and port (default `0.0.0.0:8082`)
- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`: Postgres connection (defaults to `jdbc:postgresql://localhost:5433/foodies-menu-database`, `foodies_admin` / `foodies_password`)

## API

All routes live under `/menu` and respond with JSON:

- `GET /menu?offset&limit`: paginated list (defaults: offset `0`, limit `20`, max `50`)
- `GET /menu/{id}`: fetch a single item, `404` when missing
- `POST /menu`: create an item, expects `name`, `description`, `imageUrl`, `price` (> 0)
- `PUT /menu/{id}`: update any subset of fields
- `DELETE /menu/{id}`: delete by id, returns `204` when successful
- `GET /healthz`: basic health probe

## Running locally

```bash
# ensure Postgres is available (see defaults above)
./gradlew :menu:run
```
