# WebApp

Ktor app serving the HTMX front-end. It authenticates users via OpenID Connect (Keycloak by default) and fetches menu data
from the menu service over HTTP.

## Configuration

Loaded from `config` in `src/main/resources/application.yaml` (env overrides shown in parentheses):

- `HOST` / `PORT`: bind address and port (default `0.0.0.0:8080`)
- `AUTH_ISSUER`, `AUTH_CLIENT_ID`, `AUTH_CLIENT_SECRET`: OpenID Connect discovery and OAuth client credentials
- `MENU_BASE_URL`: base URL for the menu service (default `http://localhost:8082`)

## Routes and behavior

- `GET /`: renders the landing page with HTMX infinite scroll for menu cards
- `GET /menu`: HTMX endpoint returning menu fragments (`offset`/`limit` query params forwarded to the menu service)
- `GET /login` / `GET /oauth/callback`: OAuth login flow, stores ID token in a secure cookie session
- `GET /logout`: clears the session and redirects to the provider's end-session endpoint
- `GET /healthz`: health probe
- Static assets served from `/static`

## Running locally

```bash
# Keycloak/issuer and the menu service must be reachable with the values above
./gradlew :webapp:run
```
