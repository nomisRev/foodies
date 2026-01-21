# External Secret Management Migration Guide

## Overview

This guide covers migrating RabbitMQ and other credentials from plaintext Kubernetes secrets (stored in git) to external secret management solutions for production deployments.

## Current State

**Development Setup (Acceptable for dev/local)**:
- Secrets defined in `k8s/base/kustomization.yaml` as `secretGenerator` literals
- RabbitMQ passwords stored in plaintext in git
- Per-service credentials model with least-privilege access (✅ good architecture)
- Passwords version-controlled (⚠️ not production-ready)

**Security Note**: The current per-service credential model with least-privilege permissions is correct. Only the storage mechanism needs upgrading for production.

## Production Requirements

For production deployments, secrets must:
1. Never be stored in git repositories
2. Be managed by external secret management systems
3. Support automated rotation
4. Provide audit logging
5. Integrate with Kubernetes seamlessly

## Recommended Solutions

### Option 1: Kubernetes External Secrets Operator (ESO) - Recommended

**Best for**: Multi-cloud or provider-agnostic deployments

External Secrets Operator synchronizes secrets from external APIs (AWS Secrets Manager, HashiCorp Vault, GCP Secret Manager, Azure Key Vault) into Kubernetes secrets.

#### Architecture

```
┌─────────────────────────────────────────────────────┐
│  External Secret Provider                          │
│  (AWS Secrets Manager, Vault, GCP, Azure)          │
└─────────────────────────────────────────────────────┘
                         ▲
                         │ API calls
                         │
┌─────────────────────────────────────────────────────┐
│  Kubernetes Cluster                                 │
│  ┌──────────────────────────────────────────────┐  │
│  │ External Secrets Operator                    │  │
│  │ - Watches ExternalSecret CRDs                │  │
│  │ - Fetches from external provider             │  │
│  │ - Creates/updates Kubernetes Secrets         │  │
│  └──────────────────────────────────────────────┘  │
│                         │                           │
│                         ▼                           │
│  ┌──────────────────────────────────────────────┐  │
│  │ Kubernetes Secrets                           │  │
│  │ - profile-rabbitmq-credentials              │  │
│  │ - menu-rabbitmq-credentials                 │  │
│  │ - basket-rabbitmq-credentials               │  │
│  │ - order-rabbitmq-credentials                │  │
│  │ - payment-rabbitmq-credentials              │  │
│  └──────────────────────────────────────────────┘  │
│                         │                           │
│                         ▼                           │
│  ┌──────────────────────────────────────────────┐  │
│  │ Application Pods                             │  │
│  │ (consume secrets as env vars or volumes)     │  │
│  └──────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────┘
```

#### Installation

```bash
# Install External Secrets Operator via Helm
helm repo add external-secrets https://charts.external-secrets.io
helm repo update

helm install external-secrets \
  external-secrets/external-secrets \
  -n external-secrets-system \
  --create-namespace \
  --set installCRDs=true
```

#### Configuration Example (AWS Secrets Manager)

**Step 1: Create secrets in AWS Secrets Manager**

```bash
# Create secrets in AWS
aws secretsmanager create-secret \
  --name foodies/rabbitmq/profile-service \
  --secret-string '{"username":"profile_service","password":"<generated-secure-password>"}'

aws secretsmanager create-secret \
  --name foodies/rabbitmq/menu-service \
  --secret-string '{"username":"menu_service","password":"<generated-secure-password>"}'

# Repeat for all services: basket, order, payment, keycloak
```

**Step 2: Create SecretStore**

```yaml
# k8s/base/secrets/secret-store.yaml
apiVersion: external-secrets.io/v1beta1
kind: SecretStore
metadata:
  name: aws-secrets-manager
  namespace: foodies
spec:
  provider:
    aws:
      service: SecretsManager
      region: us-east-1
      auth:
        # Use IRSA (IAM Roles for Service Accounts) for secure authentication
        jwt:
          serviceAccountRef:
            name: external-secrets-sa
```

**Step 3: Create ExternalSecret resources**

```yaml
# k8s/base/secrets/profile-rabbitmq-external-secret.yaml
apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata:
  name: profile-rabbitmq-credentials
  namespace: foodies
spec:
  refreshInterval: 1h
  secretStoreRef:
    name: aws-secrets-manager
    kind: SecretStore
  target:
    name: profile-rabbitmq-credentials
    creationPolicy: Owner
  data:
    - secretKey: RABBITMQ_USERNAME
      remoteRef:
        key: foodies/rabbitmq/profile-service
        property: username
    - secretKey: RABBITMQ_PASSWORD
      remoteRef:
        key: foodies/rabbitmq/profile-service
        property: password
```

**Step 4: Update kustomization.yaml**

```yaml
# k8s/base/kustomization.yaml
resources:
  # Remove secretGenerator entries
  # Add ExternalSecret resources
  - secrets/secret-store.yaml
  - secrets/profile-rabbitmq-external-secret.yaml
  - secrets/menu-rabbitmq-external-secret.yaml
  - secrets/basket-rabbitmq-external-secret.yaml
  - secrets/order-rabbitmq-external-secret.yaml
  - secrets/payment-rabbitmq-external-secret.yaml
  - secrets/keycloak-rabbitmq-external-secret.yaml
```

### Option 2: Cloud Provider Native Integration

#### AWS Secrets Manager with ASCP (AWS Secrets Store CSI Provider)

**Best for**: AWS EKS deployments

Uses Kubernetes CSI driver to mount secrets directly as volumes.

```yaml
# Example SecretProviderClass
apiVersion: secrets-store.csi.x-k8s.io/v1
kind: SecretProviderClass
metadata:
  name: foodies-rabbitmq-secrets
  namespace: foodies
spec:
  provider: aws
  parameters:
    objects: |
      - objectName: "foodies/rabbitmq/profile-service"
        objectType: "secretsmanager"
        jmesPath:
          - path: username
            objectAlias: RABBITMQ_USERNAME
          - path: password
            objectAlias: RABBITMQ_PASSWORD
```

#### GCP Secret Manager

**Best for**: GKE deployments

```yaml
# Use External Secrets Operator with GCP backend
apiVersion: external-secrets.io/v1beta1
kind: SecretStore
metadata:
  name: gcp-secret-manager
  namespace: foodies
spec:
  provider:
    gcpsm:
      projectID: "your-gcp-project"
      auth:
        workloadIdentity:
          clusterLocation: us-central1
          clusterName: foodies-cluster
          serviceAccountRef:
            name: external-secrets-sa
```

#### Azure Key Vault

**Best for**: AKS deployments

```yaml
apiVersion: external-secrets.io/v1beta1
kind: SecretStore
metadata:
  name: azure-keyvault
  namespace: foodies
spec:
  provider:
    azurekv:
      tenantId: "your-tenant-id"
      vaultUrl: "https://foodies-vault.vault.azure.net"
      authType: WorkloadIdentity
      serviceAccountRef:
        name: external-secrets-sa
```

### Option 3: HashiCorp Vault

**Best for**: On-premise or multi-cloud with strict compliance requirements

```yaml
# SecretStore for Vault
apiVersion: external-secrets.io/v1beta1
kind: SecretStore
metadata:
  name: vault-backend
  namespace: foodies
spec:
  provider:
    vault:
      server: "https://vault.example.com"
      path: "secret"
      version: "v2"
      auth:
        kubernetes:
          mountPath: "kubernetes"
          role: "foodies-app"
          serviceAccountRef:
            name: external-secrets-sa
```

## Migration Steps

### Phase 1: Setup External Secret Management (No Impact)

1. **Choose your provider** (AWS Secrets Manager, GCP Secret Manager, Azure Key Vault, or Vault)
2. **Generate new secure passwords**:
   ```bash
   # Generate cryptographically secure passwords
   for service in profile menu basket order payment keycloak; do
     password=$(openssl rand -base64 32)
     echo "${service}_service: $password"
   done
   ```

3. **Store secrets in external provider**:
   ```bash
   # Example for AWS Secrets Manager
   aws secretsmanager create-secret \
     --name foodies/rabbitmq/profile-service \
     --secret-string "{\"username\":\"profile_service\",\"password\":\"${PROFILE_PASSWORD}\"}"
   ```

4. **Install External Secrets Operator** (if using ESO):
   ```bash
   helm install external-secrets \
     external-secrets/external-secrets \
     -n external-secrets-system \
     --create-namespace
   ```

5. **Configure authentication** (IRSA for AWS, Workload Identity for GCP/Azure):
   ```bash
   # Example: Create IAM role for External Secrets Operator
   eksctl create iamserviceaccount \
     --name external-secrets-sa \
     --namespace foodies \
     --cluster foodies-cluster \
     --attach-policy-arn arn:aws:iam::aws:policy/SecretsManagerReadWrite \
     --approve
   ```

### Phase 2: Create ExternalSecret Resources

1. **Create SecretStore**:
   ```bash
   kubectl apply -f k8s/base/secrets/secret-store.yaml
   ```

2. **Create ExternalSecret resources for each service**:
   ```bash
   kubectl apply -f k8s/base/secrets/profile-rabbitmq-external-secret.yaml
   kubectl apply -f k8s/base/secrets/menu-rabbitmq-external-secret.yaml
   # ... etc for all services
   ```

3. **Verify secrets are synchronized**:
   ```bash
   kubectl get externalsecrets -n foodies
   kubectl get secrets -n foodies | grep rabbitmq
   ```

### Phase 3: Update RabbitMQ Definitions

1. **Update RabbitMQ definitions ConfigMap** with new passwords:
   ```bash
   # Generate password hashes
   docker run -it --rm rabbitmq:4.2-alpine rabbitmqctl hash_password "new-password"
   ```

2. **Update k8s/base/rabbitmq/definitions-configmap.yaml** with new password hashes

3. **Apply updated ConfigMap**:
   ```bash
   kubectl apply -f k8s/base/rabbitmq/definitions-configmap.yaml
   kubectl rollout restart statefulset/rabbitmq -n foodies
   ```

### Phase 4: Migrate Services One by One (Zero Downtime)

1. **Update kustomization.yaml** to use ExternalSecrets instead of secretGenerator
2. **Deploy to a test environment first**
3. **Roll out to production service by service**:
   ```bash
   # Example: Update profile service
   kubectl rollout restart deployment/profile -n foodies
   kubectl rollout status deployment/profile -n foodies

   # Verify connectivity
   kubectl logs -n foodies -l app=profile --tail=50 | grep -i rabbitmq
   ```

4. **Monitor for authentication errors**:
   ```bash
   # Check service logs
   kubectl logs -n foodies -l app=profile --tail=100

   # Check RabbitMQ connections
   kubectl exec -n foodies rabbitmq-0 -- rabbitmqctl list_connections
   ```

### Phase 5: Remove Plaintext Secrets from Git

1. **Remove secretGenerator entries** from `k8s/base/kustomization.yaml`
2. **Remove passwords from** `k8s/base/rabbitmq/definitions-configmap.yaml` (use password_hash only)
3. **Commit changes**:
   ```bash
   git add k8s/base/kustomization.yaml k8s/base/rabbitmq/definitions-configmap.yaml
   git commit -m "Migrate to external secret management"
   git push
   ```

### Phase 6: Clean Git History (Optional but Recommended)

**Warning**: This rewrites git history. Coordinate with team and ensure all changes are committed.

```bash
# Install git-filter-repo
pip install git-filter-repo

# Backup repository
git clone --mirror <repo-url> foodies-backup.git

# Remove secrets from history
git filter-repo --path k8s/base/kustomization.yaml --invert-paths --force
git filter-repo --path k8s/base/rabbitmq/definitions-configmap.yaml --invert-paths --force

# Force push (requires coordination)
git push origin --force --all
git push origin --force --tags
```

**Alternative**: Use BFG Repo-Cleaner:
```bash
# Download BFG
wget https://repo1.maven.org/maven2/com/madgag/bfg/1.14.0/bfg-1.14.0.jar

# Remove passwords
java -jar bfg-1.14.0.jar --replace-text passwords.txt

# passwords.txt content:
# VZv5gEI5hcbizdn72Mqw0xaRLlzvwyVeNrVvPz0F+QM=
# LySiUU4zTOfNtSonzFY5nWlVhJfNPc2AL/JMucsrt5U=
# ... (all passwords to remove)
```

## Secret Rotation Procedure

### Manual Rotation

1. **Generate new password**:
   ```bash
   NEW_PASSWORD=$(openssl rand -base64 32)
   ```

2. **Update external secret provider**:
   ```bash
   # AWS example
   aws secretsmanager update-secret \
     --secret-id foodies/rabbitmq/profile-service \
     --secret-string "{\"username\":\"profile_service\",\"password\":\"${NEW_PASSWORD}\"}"
   ```

3. **Update RabbitMQ user password**:
   ```bash
   kubectl exec -n foodies rabbitmq-0 -- \
     rabbitmqctl change_password profile_service "${NEW_PASSWORD}"
   ```

4. **Force secret refresh** (ESO automatically refreshes based on refreshInterval):
   ```bash
   # Or manually annotate to force immediate refresh
   kubectl annotate externalsecret profile-rabbitmq-credentials \
     -n foodies \
     force-sync=$(date +%s) \
     --overwrite
   ```

5. **Restart service** to pick up new credentials:
   ```bash
   kubectl rollout restart deployment/profile -n foodies
   ```

### Automated Rotation (Advanced)

Use AWS Secrets Manager rotation or Vault's dynamic secrets:

```yaml
# AWS Secrets Manager automatic rotation
aws secretsmanager rotate-secret \
  --secret-id foodies/rabbitmq/profile-service \
  --rotation-lambda-arn arn:aws:lambda:region:account:function:SecretsManagerRotation \
  --rotation-rules AutomaticallyAfterDays=30
```

## Monitoring and Alerting

### Set up alerts for:

1. **Secret sync failures**:
   ```yaml
   # Prometheus alert rule
   - alert: ExternalSecretSyncFailure
     expr: external_secrets_sync_calls_error{namespace="foodies"} > 0
     for: 5m
     annotations:
       summary: "External secret sync failed in foodies namespace"
   ```

2. **RabbitMQ authentication failures**:
   ```bash
   kubectl logs -n foodies -l app=rabbitmq | grep -i "authentication failed"
   ```

3. **Secret age** (for rotation compliance):
   ```yaml
   # Alert if secret hasn't been rotated in 90 days
   - alert: SecretRotationOverdue
     expr: (time() - aws_secretsmanager_secret_last_changed_date) > 7776000
     annotations:
       summary: "Secret {{ $labels.secret_name }} needs rotation"
   ```

## Rollback Plan

If issues occur during migration:

1. **Revert to secretGenerator** in kustomization.yaml
2. **Apply old configuration**:
   ```bash
   git revert <migration-commit>
   kubectl apply -k k8s/overlays/prod
   ```
3. **Restart affected services**:
   ```bash
   kubectl rollout restart deployment/profile -n foodies
   ```

## Development vs Production

### Development (Local/Dev)
- **Continue using** secretGenerator for local development
- Secrets in git are acceptable for dev environments
- Simplifies onboarding and local testing

### Production
- **Must use** external secret management
- Never commit secrets to git
- Enable audit logging
- Implement rotation policies

### Overlay Pattern

```
k8s/
├── base/
│   └── kustomization.yaml          # Uses secretGenerator (dev-friendly)
├── overlays/
    ├── dev/
    │   └── kustomization.yaml      # Uses base secretGenerator
    └── prod/
        ├── kustomization.yaml      # Uses ExternalSecrets
        └── secrets/
            ├── secret-store.yaml
            └── external-secrets.yaml
```

## Security Best Practices

1. **Principle of Least Privilege**: Each service has only required permissions (already implemented)
2. **Secret Rotation**: Rotate secrets every 30-90 days
3. **Audit Logging**: Enable audit logs in secret provider
4. **Encryption at Rest**: Ensure secrets are encrypted in provider
5. **Encryption in Transit**: Use TLS for all secret provider communication
6. **Access Control**: Limit who can read/modify secrets
7. **No Secrets in Logs**: Never log secrets (use structured logging)
8. **No Secrets in Code**: All secrets from external sources

## Cost Considerations

### AWS Secrets Manager
- $0.40 per secret per month
- $0.05 per 10,000 API calls
- Estimated cost for 7 secrets: ~$3/month

### GCP Secret Manager
- $0.06 per secret version per month
- $0.03 per 10,000 access operations
- Estimated cost: ~$0.50/month

### Azure Key Vault
- $0.03 per 10,000 operations
- Secrets stored at no additional cost
- Estimated cost: ~$0.10/month

### HashiCorp Vault
- Self-hosted: Infrastructure costs only
- Cloud (HCP Vault): Starts at $0.03/hour (~$22/month)

## References

- [External Secrets Operator Documentation](https://external-secrets.io/)
- [AWS Secrets Manager Best Practices](https://docs.aws.amazon.com/secretsmanager/latest/userguide/best-practices.html)
- [Kubernetes Secrets Management](https://kubernetes.io/docs/concepts/configuration/secret/)
- [RabbitMQ Access Control](https://www.rabbitmq.com/access-control.html)
- [OWASP Secrets Management Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Secrets_Management_Cheat_Sheet.html)

## Next Steps

1. Choose external secret provider based on cloud platform
2. Set up provider-specific authentication (IRSA, Workload Identity)
3. Create secrets in external provider
4. Install and configure External Secrets Operator
5. Test in dev/staging environment
6. Gradually roll out to production
7. Remove plaintext secrets from git
8. Document secret rotation procedures for operations team
9. Set up monitoring and alerting
10. Schedule regular secret rotation
