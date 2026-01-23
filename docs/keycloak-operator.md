# Keycloak Operator Deployment Guide

This guide covers deploying and managing Keycloak using the Keycloak Operator in the Foodies platform.

## Overview

The Keycloak Operator provides Kubernetes-native management of Keycloak instances through Custom Resources (CRDs). It replaces manual deployments and init scripts with declarative configuration.

## Prerequisites

- Kubernetes cluster with cluster-admin access
- kubectl configured to access the cluster
- `foodies.local` configured in `/etc/hosts` pointing to cluster ingress

## Installation Steps

### 1. Install Keycloak Operator

Apply the base stack (operator, CRDs, and supporting namespaces):

```bash
kubectl apply -k k8s/base/
```

This step installs the operator as part of the base deployment. If you only need to reinstall the operator or CRDs, you can still run `kubectl apply -k k8s/base/keycloak-operator/` separately.

Wait for the operator to be ready:

```bash
kubectl wait --for=condition=available --timeout=300s deployment/keycloak-operator -n keycloak
```

### 2. Verify Installation

```bash
# Check operator pod
kubectl get pods -n keycloak

# Check CRDs
kubectl get crd keycloaks.k8s.keycloak.org keycloakrealmimports.k8s.keycloak.org

# Check operator logs
kubectl logs -n keycloak deployment/keycloak-operator
```

### 3. Apply Keycloak Configuration

For development:

```bash
kubectl apply -k k8s/overlays/dev/
```

For production:

```bash
kubectl apply -k k8s/overlays/prod/
```

> The overlays build on the base kustomization, so these commands also install the Keycloak operator and CRDs along with the Keycloak CRs.

## Configuration Details

### Keycloak CR (keycloak-cr.yaml)

Configures the Keycloak server with:
- HTTP settings and hostname
- Database connection (PostgreSQL)
- Health and metrics endpoints
- Feature flags and extensions
- Resource limits and requests

### KeycloakRealmImport CR (keycloak-realm-import-cr.yaml)

Configures realm and identity:
- `foodies-keycloak` realm
- `foodies` client with OIDC settings
- `foodies-audience` client scope
- `user` role
- Default user `food3_lover`

## Verification Commands

### Check Resource Status

```bash
# Keycloak CR status
kubectl get keycloak keycloak -n foodies
kubectl describe keycloak keycloak -n foodies

# Realm import status
kubectl get keycloakrealmimport foodies-realm -n foodies
kubectl describe keycloakrealmimport foodies-realm -n foodies
```

### Check Pod Status

```bash
kubectl get pods -n foodies -l app=keycloak
kubectl logs -n foodies -l app=keycloak --follow
```

### Access Keycloak

- Admin Console: http://foodies.local/auth
- Login: `food_lover@gmail.com` / `password`

## Troubleshooting

### Common Issues

#### 1. Operator Not Starting

**Symptoms**: Operator pod in CrashLoopBackOff

**Check**:
```bash
kubectl describe pod -n keycloak -l app=keycloak-operator
kubectl logs -n keycloak deployment/keycloak-operator
```

**Solutions**:
- Verify cluster permissions
- Check image pull policy and registry access
- Ensure sufficient resources

#### 2. Keycloak Pod Not Starting

**Symptoms**: Keycloak pod not running or crashing

**Check**:
```bash
kubectl describe pod -n foodies -l app=keycloak
kubectl logs -n foodies -l app=keycloak
```

**Common causes**:
- Database connection issues
- Invalid configuration in CR
- Resource constraints

#### 3. Realm Import Failing

**Symptoms**: KeycloakRealmImport shows errors

**Check**:
```bash
kubectl describe keycloakrealmimport foodies-realm -n foodies
```

**Solutions**:
- Verify Keycloak is running and accessible
- Check realm import YAML syntax
- Review operator logs for import errors

#### 4. CR Not Being Processed

**Symptoms**: Custom Resources created but not reconciled by operator

**Check**:
```bash
# Check CR status
kubectl get keycloak -n foodies -o wide
kubectl get keycloakrealmimport -n foodies -o wide

# Check operator is watching the namespace
kubectl logs -n keycloak deployment/keycloak-operator | grep -i watch
```

**Solutions**:
- Verify operator has permissions for the target namespace
- Check `WATCH_NAMESPACE` environment variable in operator deployment
- Ensure CRs are properly formatted with valid API version
- Restart operator pod if needed

#### 5. Custom Image Not Loading

**Symptoms**: Keycloak pod stuck in ImagePullBackOff or using wrong image

**Check**:
```bash
# Check what image is being used
kubectl get pod -n foodies -l app=keycloak -o jsonpath='{.items[0].spec.containers[0].image}'

# Check pod events
kubectl describe pod -n foodies -l app=keycloak
```

**Solutions**:
- Verify custom image exists in registry
- Check image pull secrets in the namespace
- Ensure proper image name format in Keycloak CR `spec.image` field
- For local development, use `imagePullPolicy: Never` or `IfNotPresent`

#### 6. Authentication Not Working

**Symptoms**: Cannot login or access protected resources

**Check**:
- Verify realm exists in Keycloak Admin Console
- Check client configuration
- Verify user credentials and roles
- Check OAuth flow logs

### Logs to Monitor

```bash
# Operator logs
kubectl logs -n keycloak deployment/keycloak-operator --follow

# Keycloak logs
kubectl logs -n foodies -l app=keycloak --follow

# Database logs
kubectl logs -n foodies -l app=keycloak-postgres --follow
```

## Rollback Procedure

If issues occur with the operator deployment:

### Rollback to Manual Deployment

1. Delete Keycloak CRs:
   ```bash
   kubectl delete keycloak keycloak -n foodies
   kubectl delete keycloakrealmimport foodies-realm -n foodies
   ```

2. Remove operator (optional):
   ```bash
   kubectl delete -k k8s/base/keycloak-operator/
   ```

3. Revert to manual deployment (if available):
   ```bash
   # Apply old deployment.yaml and init scripts
   kubectl apply -f k8s/base/keycloak/deployment.yaml
   kubectl apply -f k8s/overlays/dev/deprecated/keycloak-config-job.yaml
   ```

## Monitoring and Observability

### Health Checks

```bash
# Keycloak health endpoints
curl http://foodies.local/auth/health/ready
curl http://foodies.local/auth/health/live
```

### Metrics

Keycloak exposes metrics on port 9000. Configure Prometheus ServiceMonitor for scraping.

### Events

```bash
kubectl get events -n foodies --field-selector involvedObject.name=keycloak-0
kubectl get events -n keycloak --field-selector involvedObject.name=keycloak-operator
```

## Best Practices

1. **Resource Management**: Set appropriate CPU/memory limits
2. **Backup**: Regular database backups
3. **Security**: Use secrets for credentials, enable HTTPS in production
4. **Monitoring**: Set up alerts for Keycloak and operator health
5. **Updates**: Test operator updates in staging before production

## Related Issues

- `bd-61q`: Install and validate Keycloak Operator in dev environment
- `bd-359`: Test complete Keycloak Operator migration
- `bd-3e6`: Create production overlay for Keycloak Operator
