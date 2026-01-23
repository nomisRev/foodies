# Keycloak Operator

This directory contains the Keycloak Operator deployment configuration for managing Keycloak instances in Kubernetes.

## Overview

The Keycloak Operator simplifies Keycloak deployment and management by providing Kubernetes-native Custom Resources (CRDs) for declarative configuration of Keycloak servers and realms.

## Resources

- `namespace.yaml` - Creates the `keycloak` namespace
- `keycloak-crd.yaml` - Keycloak Custom Resource Definition
- `keycloakrealmimport-crd.yaml` - KeycloakRealmImport Custom Resource Definition  
- `operator-deployment.yaml` - Operator deployment with RBAC
- `kustomization.yaml` - Kustomize configuration

## Installation

Apply the operator and CRDs to your cluster:

```bash
kubectl apply -k k8s/base/keycloak-operator/
```

This will:
1. Create the `keycloak` namespace
2. Install the Keycloak and KeycloakRealmImport CRDs (cluster-scoped)
3. Deploy the Keycloak Operator with appropriate RBAC permissions

## Verification

Verify the operator is running correctly:

```bash
# Check operator pod status
kubectl get pods -n keycloak

# Check CRDs are installed
kubectl get crd keycloaks.k8s.keycloak.org
kubectl get crd keycloakrealmimports.k8s.keycloak.org

# Check operator logs
kubectl logs -n keycloak deployment/keycloak-operator
```

## Troubleshooting

### Operator Pod Not Starting

```bash
kubectl describe pod -n keycloak -l app=keycloak-operator
```

Common issues:
- Insufficient cluster permissions
- Image pull failures
- Resource constraints

### CRDs Not Installing

```bash
kubectl get crd | grep keycloak
```

If CRDs are missing, check cluster-admin permissions or reapply the kustomization.

### Operator Logs Show Errors

Check operator logs for reconciliation errors:

```bash
kubectl logs -n keycloak deployment/keycloak-operator --follow
```

### CR Not Being Processed

If Custom Resources are created but not reconciled:

```bash
# Check CR status
kubectl get keycloak -A -o wide
kubectl get keycloakrealmimport -A -o wide

# Check operator is watching namespaces
kubectl logs -n keycloak deployment/keycloak-operator | grep -i watch
```

### Custom Image Not Loading

If Keycloak pod is stuck in ImagePullBackOff:

```bash
# Check pod events and image
kubectl describe pod -n foodies -l app=keycloak
kubectl get pod -n foodies -l app=keycloak -o jsonpath='{.items[0].spec.containers[0].image}'
```

## Configuration

The operator deploys with the following default configuration:
- Watches all namespaces (`WATCH_NAMESPACE=""`)
- Uses Keycloak Operator version 26.5.0
- Resource limits: 512Mi memory, 500m CPU

## Next Steps

After installing the operator, apply Keycloak CRs from `k8s/base/keycloak/` or overlays.