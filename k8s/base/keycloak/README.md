# Keycloak Operator Migration

This directory contains the Keycloak configuration using the Keycloak Operator.

## Migration Summary

The Keycloak deployment has been refactored from using:
- **Before**: Deployment resource + init script job (`keycloak-config-job.yaml`) using `kcadm.sh` for configuration
- **After**: Keycloak Operator CRDs (`Keycloak` CR for server, `KeycloakRealmImport` CR for realm/identity)

## Benefits

- **Declarative configuration**: All configuration is in YAML files managed by Git
- **Operator reconciliation**: The operator maintains desired state automatically
- **Simplified setup**: No more init scripts waiting for Keycloak to be ready
- **Native Kubernetes**: Uses standard CRDs instead of custom jobs
- **Better observability**: Status is available via CRD status fields

## Files

- `keycloak-cr.yaml` - Keycloak Custom Resource for server configuration (k8s.keycloak.org/v2alpha1)
- `keycloak-realm-import-cr.yaml` - KeycloakRealmImport Custom Resource for realm and identity configuration
- `deployment.yaml` - **DEPRECATED** - Original Deployment file kept for reference
- `database.yaml` - PostgreSQL database deployment

## Deprecated Files

The following files have been moved to `k8s/overlays/dev/deprecated/`:
- `keycloak-config-job.yaml` - Init script job using kcadm.sh
- `keycloak-realm-import-patch.yaml` - Realm import patch

## Keycloak Operator Resources

The Keycloak Operator uses two main Custom Resources:

### Keycloak CR (Server Configuration)

Configures the Keycloak server including:
- HTTP/HTTPS settings
- Hostname configuration
- Database connection
- Health and metrics endpoints
- Feature flags
- Environment variables (in dev overlay via `keycloak-env-patch.yaml`):
  - `FOODIES_HOST`, `KC_HOSTNAME`, `KC_HOSTNAME_ADMIN` - Hostname configuration
  - `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD`, `RABBITMQ_QUEUE` - RabbitMQ integration
  - `KC_BOOTSTRAP_ADMIN_USERNAME`, `KC_BOOTSTRAP_ADMIN_PASSWORD` - Admin credentials

### KeycloakRealmImport CR (Identity Configuration)

Configures the realm and identity settings:
- Realm creation (`foodies-keycloak`)
- Client configuration (`foodies` client)
- Client scopes (`foodies-audience`)
- Roles (`user` role)
- Users (`food3_lover` user)

## Operator Installation

Before applying these CRs, ensure Keycloak Operator is installed. The operator CRDs and deployment are available in `k8s/base/keycloak-operator/`:

```bash
# Apply the operator and CRDs
kubectl apply -k k8s/base/keycloak-operator/
```

See issue `bd-1p4` for operator installation details.

## Applying the Configuration

```bash
# Apply the base configuration
kubectl apply -k k8s/base/keycloak/

# Or apply the dev overlay
kubectl apply -k k8s/overlays/dev/
```

## Monitoring

Check the status of Keycloak resources:

```bash
kubectl get keycloak keycloak -n foodies
kubectl get keycloakrealmimport foodies-realm -n foodies
kubectl describe keycloak keycloak -n foodies
```

## Verification

After applying, verify:
1. Keycloak Pod is running: `kubectl get pods -n foodies -l app=keycloak`
2. Realm exists: Check Keycloak Admin Console at http://foodies.local/auth
3. User can login: `food_lover@gmail.com` / `password`

## Next Steps / Remaining Work

1. **Integrate Keycloak Operator into main kustomization** (See issue `bd-384`):
    Add keycloak-operator to base kustomization so CRDs and operator are deployed automatically.

2. **Apply Keycloak CRs** (See issue `bd-1sv`):
    ```bash
    kubectl apply -k k8s/base/keycloak/
    # or for dev:
    kubectl apply -k k8s/overlays/dev/
    ```

3. **Test the configuration** (See issue `bd-359`):
    - Verify Keycloak Pod is running: `kubectl get pods -n foodies -l app=keycloak`
    - Verify realm exists: Check Keycloak Admin Console at http://foodies.local/auth
    - Verify user can login: `food_lover@gmail.com` / `password`
    - Verify RabbitMQ event listener is working

4. **Monitor operator logs**: Watch operator logs for any reconciliation errors

5. **Remove deprecated files** (See issue `bd-1qz`):
    Delete `k8s/overlays/dev/deprecated/` directory and `k8s/base/keycloak/deployment.yaml` once migration is validated

6. **Create production overlay** (See issue `bd-3e6`):
    Create production-specific configuration with proper security, resources, and HA settings
