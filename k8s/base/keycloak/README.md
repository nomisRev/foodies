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

Before applying these CRs, ensure Keycloak Operator is installed in the foodies namespace:

```bash
kubectl create namespace foodies || true
kubectl apply -f https://raw.githubusercontent.com/keycloak/keycloak-operator/main/kubernetes/target/kubernetes/operator/0.0.0/operator.yaml
```

See issue `bd-3l0` for operator installation details.

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

1. **Install Keycloak Operator** (See issue `bd-3l0`):
   ```bash
   kubectl create namespace foodies || true
   kubectl apply -f https://raw.githubusercontent.com/keycloak/keycloak-operator/main/kubernetes/target/kubernetes/operator/0.0.0/operator.yaml
   ```

2. **Test the configuration**: Apply the CRs and verify deployment

3. **Remove deprecated files** (optional): Delete `k8s/overlays/dev/deprecated/` directory once migration is complete

4. **Monitor operator logs**: Watch operator logs for any reconciliation errors
