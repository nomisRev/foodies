# Kubernetes Deployment Status

## Completed Work

### 1. Docker Images
- Successfully rebuilt and published all service images using Gradle
- Images published to local registry with version tags
- All service images: webapp, menu, profile, basket, order, payment, keycloak

### 2. Kubernetes Configuration Updates
- **RabbitMQ Migration**: Migrated from StatefulSet to RabbitMQ Cluster Operator
  - Added RabbitMQ Operator deployment in `rabbitmq-system` namespace
  - Created operator namespace and kustomization structure
  - Configured RabbitMQ cluster with proper storage class for dev environment

- **Secret References Fixed**: Updated all service deployments to use operator-generated RabbitMQ credentials
  - basket, menu, order, payment, profile, keycloak deployments updated
  - Changed from `*-rabbitmq-credentials` to `*-service-user-credentials`
  - Updated secret keys from custom names to `username` and `password`

- **Namespace Separation**: Properly separated operator and application namespaces
  - RabbitMQ operator: `rabbitmq-system` namespace
  - Application services: `foodies` namespace
  - Kustomize overlays updated to deploy both correctly

- **Storage Configuration**: Added dev overlay patch for RabbitMQ to use `hostpath` storage class

### 3. Deployment Status
Successfully deployed infrastructure components:
- ✅ Keycloak (Identity Management) - Running
- ✅ RabbitMQ Cluster - Running
- ✅ Redis - Running
- ✅ PostgreSQL databases (menu, profile, order, payment, keycloak) - Running
- ✅ Jaeger (Tracing) - Running
- ✅ Prometheus (Metrics) - Running
- ✅ OpenTelemetry Collector - Running
- ✅ RabbitMQ Cluster Operator - Running (rabbitmq-system namespace)
- ✅ Messaging Topology Operator - Running (rabbitmq-system namespace)

## Known Issues

### Issue bd-1e6: Fix RabbitMQ Topology Operator authentication
**Status**: Open
**Type**: Bug
**Priority**: High

The RabbitMQ Topology Operator is failing to create users with `401 Unauthorized` errors when connecting to the RabbitMQ management API. This is preventing services from starting properly.

**Symptoms**:
- Service pods (basket, menu, order, payment, profile) are crash-looping
- RabbitMQ User resources show status: `FailedCreateOrUpdate`
- Error message: "API responded with a 401 Unauthorized"

**Investigation Needed**:
1. Check how the topology operator authenticates to the RabbitMQ cluster
2. Verify the operator has correct credentials for the management API
3. Ensure the RabbitMQ cluster's default user credentials are accessible to the operator
4. May need to configure operator with proper service account or secret reference

## Single-Command Deployment

The deployment structure now supports single-command deployment for both environments:

```bash
# Development
kubectl apply -k k8s/overlays/dev

# Production
kubectl apply -k k8s/overlays/prod
```

This will deploy:
1. RabbitMQ Operator (rabbitmq-system namespace)
2. Cert-Manager (cert-manager namespace)
3. All application services (foodies namespace)

## Next Steps

1. **Immediate**: Resolve RabbitMQ Topology Operator authentication (bd-1e6)
2. Verify all services start successfully after RabbitMQ users are created
3. Test end-to-end application flow through the webapp
4. Verify observability stack (Jaeger traces, Prometheus metrics)
5. Test authentication flow through Keycloak

## Configuration Changes Committed

All Kubernetes configuration changes have been committed and pushed to the repository (commit 3ce167c):
- k8s/base/*/deployment.yaml files updated with correct secret references
- k8s/base/rabbitmq-operator/ structure created
- k8s/overlays/dev/kustomization.yaml updated with operator reference
- k8s/overlays/prod/kustomization.yaml updated with operator reference

## Access Information

Once services are fully operational:
- **Application**: http://foodies.local (requires /etc/hosts entry)
- **Jaeger UI**: http://foodies.local/jaeger
- **Prometheus UI**: http://foodies.local/prometheus
- **RabbitMQ Management**: http://foodies.local:15672 (guest/guest for dev)
- **Keycloak Admin**: http://foodies.local/auth (admin/admin for dev)
