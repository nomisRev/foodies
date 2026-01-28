# Service-to-Service Authentication (Keycloak) Implementation

## Goal
Provide a secure, least-privilege, service-to-service authentication model by creating a dedicated Keycloak client for every service and issuing tokens using client credentials with explicit audience targeting.

## Current State (Summary)
- Realm: `foodies-keycloak`.
- Single application client: `foodies` with a shared client secret.
- Services validate tokens against the `foodies` audience.
- Keycloak config is applied via `k8s/overlays/dev/keycloak-config-job.yaml` and `keycloak/realm.json` for local/test usage.

## Target Model
### Clients
Create a **confidential client per service** (client credentials only) and keep the existing user-facing webapp client separate.

Proposed client IDs (explicit, stable):
- `webapp` (user-facing, auth code flow)
- `basket-service`
- `menu-service`
- `order-service`
- `payment-service`
- `profile-service`

### Token Use
- **User-facing flows**: `webapp` uses authorization code with PKCE and `openid` scopes.
- **Service-to-service flows**: services use **client credentials** to request tokens targeted to a specific downstream service.

### Authorization Model
- Use **client roles** on each target service client to model API permissions, e.g.:
  - `order-service:read`, `order-service:write`
  - `payment-service:charge`
- Assign those roles to **service accounts** of calling services via the Keycloak Admin API.

### Audience Rules
- Each downstream service should validate tokens for **its own audience only**.
- The token request must include a scope that adds the downstream client as an **audience** in the access token.

## Keycloak Configuration Plan

### 1) Create service clients
For each service client:
- `clientId`: `<service>-service`
- `clientType`: Confidential
- **Service Accounts Enabled**: true
- **Standard Flow**: false
- **Direct Access Grants**: false
- **Implicit Flow**: false
- **Client Credentials**: enabled

### 2) Create per-service client roles
For each service, define its API permissions as **client roles** on that service client.
Example (for `order-service`):
- `read`
- `write`
- `admin` (if needed)

### 3) Assign client roles to calling services
For each caller → callee relationship:
- Assign callee client roles to the **service account** of the caller.
- Example: `basket-service` can call `order-service` with `read` and `write`.

### 4) Add audience client scopes
Define one **optional client scope** per target service to control the `aud` claim.

Example: scope `aud-order-service`
- Type: `client scope`
- Protocol: `openid-connect`
- Mapper: `oidc-audience-mapper`
  - `included.client.audience`: `order-service`
  - `access.token.claim`: true
  - `id.token.claim`: true

### 5) Token request pattern (client credentials)
Calling service requests a token with explicit audience scope:
- Token endpoint: `${AUTH_ISSUER}/protocol/openid-connect/token`
- `grant_type=client_credentials`
- `client_id=<caller-service>`
- `client_secret=<caller-secret>`
- `scope=aud-<target-service>`

## Kustomize / Realm Updates

### Key files to update
- `k8s/overlays/dev/keycloak-config-job.yaml`
  - Create clients, client roles, client scopes, and role assignments.
- `keycloak/realm.json`
  - Keep in sync for local testcontainers usage.

### Environment variables per service
Add the following per-service settings to each service deployment:
- `AUTH_ISSUER`
- `AUTH_CLIENT_ID` (service client ID)
- `AUTH_CLIENT_SECRET` (service client secret)
- `AUTH_AUDIENCE` (target service client ID)

## Service Validation Rules
Update service validation to:
- Accept only tokens with `aud == <service-client-id>`
- Use `azp` or `client_id` claim to identify the caller
- Authorize via **client roles** from `resource_access[<service-client-id>].roles`

## Secret Handling
- **Never commit** service client secrets to git.
- Use External Secrets (see `docs/EXTERNAL_SECRET_MANAGEMENT.md`) for production.
- For dev, keep secrets in `k8s/overlays/dev` only and scope them per service.

## Rollout Steps
1. Add service clients and scopes to Keycloak config job.
2. Add per-service client secrets to Kubernetes secrets.
3. Update service deployments with `AUTH_CLIENT_ID` and `AUTH_CLIENT_SECRET`.
4. Update server shared auth validation to use per-service audience and client roles.
5. Validate using a simple caller → callee request with a token from client credentials.

## Verification Checklist
- Each service can mint a token using its own client credentials.
- Tokens include `aud` for the intended callee only.
- Requests are denied when `aud` is incorrect.
- Client roles map to permissions correctly.
- User-facing flows remain unchanged for `webapp`.
