# Kustomize Migration for Dev & Prod Ready K8s Setup

## Problem Statement

Currently, the k8s deployment has these issues:
1. All deployments use `:latest` image tags
2. Version management system builds versioned images (e.g., `foodies-keycloak:7`) but manifests don't reference them
3. No distinction between dev and prod configurations
4. Staleness prevention relies on full namespace deletion
5. No proper image versioning strategy for deployments

## Solution: Kustomize with Base + Overlays

### Proposed Directory Structure

```
k8s/
├── base/                          # Base configuration (shared between dev/prod)
│   ├── kustomization.yaml         # Base kustomization
│   ├── namespace.yaml
│   ├── configmaps/
│   │   ├── foodies-config.yaml
│   │   └── keycloak-realm.yaml
│   ├── secrets/
│   │   ├── keycloak-admin.yaml
│   │   ├── postgres-credentials.yaml
│   │   ├── rabbitmq-credentials.yaml
│   │   └── webapp-auth.yaml
│   ├── databases/
│   │   ├── menu-postgres.yaml
│   │   ├── order-postgres.yaml
│   │   ├── payment-postgres.yaml
│   │   ├── profile-postgres.yaml
│   │   └── redis.yaml
│   ├── infrastructure/
│   │   ├── keycloak.yaml
│   │   └── rabbitmq.yaml
│   ├── services/
│   │   ├── basket.yaml
│   │   ├── menu.yaml
│   │   ├── order.yaml
│   │   ├── payment.yaml
│   │   ├── profile.yaml
│   │   └── webapp.yaml
│   └── ingress.yaml
│
├── overlays/
│   ├── dev/
│   │   ├── kustomization.yaml     # Dev-specific config
│   │   └── patches/
│   │       ├── imagePullPolicy-never.yaml  # Set imagePullPolicy: Never for dev
│   │       └── resource-limits.yaml        # Lower resource limits for dev
│   │
│   └── prod/
│       ├── kustomization.yaml     # Prod-specific config
│       ├── patches/
│       │   ├── imagePullPolicy-always.yaml  # Set imagePullPolicy: Always
│       │   ├── replicas.yaml                # Higher replica counts
│       │   └── resource-limits.yaml         # Production resource limits
│       └── secrets/                         # Production secrets (gitignored)
│           └── ...
```

## Implementation Plan

### Phase 1: Create Base Configuration

1. **Move existing k8s files to base/**
   ```bash
   mkdir -p k8s/base
   mv k8s/namespace.yaml k8s/base/
   mv k8s/ingress.yaml k8s/base/
   mv k8s/configmaps k8s/base/
   mv k8s/secrets k8s/base/
   mv k8s/databases k8s/base/
   mv k8s/infrastructure k8s/base/
   mv k8s/services k8s/base/
   ```

2. **Create base/kustomization.yaml**
   ```yaml
   apiVersion: kustomize.config.k8s.io/v1beta1
   kind: Kustomization

   namespace: foodies

   resources:
     - namespace.yaml
     - configmaps/foodies-config.yaml
     - configmaps/keycloak-realm.yaml
     - secrets/keycloak-admin.yaml
     - secrets/postgres-credentials.yaml
     - secrets/rabbitmq-credentials.yaml
     - secrets/webapp-auth.yaml
     - databases/menu-postgres.yaml
     - databases/order-postgres.yaml
     - databases/payment-postgres.yaml
     - databases/profile-postgres.yaml
     - databases/redis.yaml
     - infrastructure/keycloak.yaml
     - infrastructure/rabbitmq.yaml
     - services/basket.yaml
     - services/menu.yaml
     - services/order.yaml
     - services/payment.yaml
     - services/profile.yaml
     - services/webapp.yaml
     - ingress.yaml
   ```

### Phase 2: Create Dev Overlay

1. **Create overlays/dev/kustomization.yaml**
   ```yaml
   apiVersion: kustomize.config.k8s.io/v1beta1
   kind: Kustomization

   bases:
     - ../../base

   # Dynamic image versioning
   images:
     - name: foodies-keycloak
       newName: foodies-keycloak
       newTag: "7"  # Will be updated by local-deploy.sh
     - name: foodies-webapp
       newName: foodies-webapp
       newTag: "7"
     - name: foodies-menu
       newName: foodies-menu
       newTag: "7"
     - name: foodies-profile
       newName: foodies-profile
       newTag: "7"
     - name: foodies-basket
       newName: foodies-basket
       newTag: "7"
     - name: foodies-order
       newName: foodies-order
       newTag: "7"
     - name: foodies-payment
       newName: foodies-payment
       newTag: "7"

   # Patches for dev environment
   patches:
     - path: patches/imagePullPolicy-never.yaml
   ```

2. **Create overlays/dev/patches/imagePullPolicy-never.yaml**
   ```yaml
   apiVersion: apps/v1
   kind: Deployment
   metadata:
     name: keycloak
     namespace: foodies
   spec:
     template:
       spec:
         containers:
           - name: keycloak
             imagePullPolicy: Never
   ---
   apiVersion: apps/v1
   kind: Deployment
   metadata:
     name: webapp
     namespace: foodies
   spec:
     template:
       spec:
         containers:
           - name: webapp
             imagePullPolicy: Never
   ---
   apiVersion: apps/v1
   kind: Deployment
   metadata:
     name: menu
     namespace: foodies
   spec:
     template:
       spec:
         containers:
           - name: menu
             imagePullPolicy: Never
   ---
   apiVersion: apps/v1
   kind: Deployment
   metadata:
     name: profile
     namespace: foodies
   spec:
     template:
       spec:
         containers:
           - name: profile
             imagePullPolicy: Never
   ---
   apiVersion: apps/v1
   kind: Deployment
   metadata:
     name: basket
     namespace: foodies
   spec:
     template:
       spec:
         containers:
           - name: basket
             imagePullPolicy: Never
   ---
   apiVersion: apps/v1
   kind: Deployment
   metadata:
     name: order
     namespace: foodies
   spec:
     template:
       spec:
         containers:
           - name: order
             imagePullPolicy: Never
   ---
   apiVersion: apps/v1
   kind: Deployment
   metadata:
     name: payment
     namespace: foodies
   spec:
     template:
       spec:
         containers:
           - name: payment
             imagePullPolicy: Never
   ```

### Phase 3: Create Prod Overlay

1. **Create overlays/prod/kustomization.yaml**
   ```yaml
   apiVersion: kustomize.config.k8s.io/v1beta1
   kind: Kustomization

   bases:
     - ../../base

   # Production images from registry
   images:
     - name: foodies-keycloak
       newName: your-registry.io/foodies-keycloak
       newTag: PROD_VERSION
     - name: foodies-webapp
       newName: your-registry.io/foodies-webapp
       newTag: PROD_VERSION
     - name: foodies-menu
       newName: your-registry.io/foodies-menu
       newTag: PROD_VERSION
     - name: foodies-profile
       newName: your-registry.io/foodies-profile
       newTag: PROD_VERSION
     - name: foodies-basket
       newName: your-registry.io/foodies-basket
       newTag: PROD_VERSION
     - name: foodies-order
       newName: your-registry.io/foodies-order
       newTag: PROD_VERSION
     - name: foodies-payment
       newName: your-registry.io/foodies-payment
       newTag: PROD_VERSION

   patches:
     - path: patches/imagePullPolicy-always.yaml
     - path: patches/replicas.yaml
     - path: patches/resource-limits.yaml
   ```

### Phase 4: Update local-deploy.sh

**Key Changes:**

1. **Add function to update kustomize versions**
   ```bash
   update_kustomize_versions() {
       local version=$1
       echo_info "Updating kustomize with version $version..."

       cd k8s/overlays/dev
       kustomize edit set image \
           foodies-keycloak=foodies-keycloak:$version \
           foodies-webapp=foodies-webapp:$version \
           foodies-menu=foodies-menu:$version \
           foodies-profile=foodies-profile:$version \
           foodies-basket=foodies-basket:$version \
           foodies-order=foodies-order:$version \
           foodies-payment=foodies-payment:$version
       cd ../../..
   }
   ```

2. **Replace individual deploy functions with kustomize**
   ```bash
   deploy_with_kustomize() {
       echo_info "Deploying with Kustomize..."
       kubectl apply -k k8s/overlays/dev

       echo_info "Waiting for all pods to be ready..."
       kubectl wait --for=condition=ready pod -l app=menu-postgres -n foodies --timeout=120s || echo_warn "menu-postgres not ready yet"
       kubectl wait --for=condition=ready pod -l app=profile-postgres -n foodies --timeout=120s || echo_warn "profile-postgres not ready yet"
       kubectl wait --for=condition=ready pod -l app=order-postgres -n foodies --timeout=120s || echo_warn "order-postgres not ready yet"
       kubectl wait --for=condition=ready pod -l app=payment-postgres -n foodies --timeout=120s || echo_warn "payment-postgres not ready yet"
       kubectl wait --for=condition=ready pod -l app=redis -n foodies --timeout=120s || echo_warn "redis not ready yet"
       kubectl wait --for=condition=ready pod -l app=rabbitmq -n foodies --timeout=180s || echo_warn "RabbitMQ not ready yet"
       kubectl wait --for=condition=ready pod -l app=keycloak -n foodies --timeout=180s || echo_warn "Keycloak not ready yet"
       kubectl wait --for=condition=ready pod -l app=webapp -n foodies --timeout=180s || echo_warn "webapp not ready yet"
       kubectl wait --for=condition=ready pod -l app=menu -n foodies --timeout=180s || echo_warn "menu not ready yet"
       kubectl wait --for=condition=ready pod -l app=profile -n foodies --timeout=180s || echo_warn "profile not ready yet"
       kubectl wait --for=condition=ready pod -l app=basket -n foodies --timeout=180s || echo_warn "basket not ready yet"
       kubectl wait --for=condition=ready pod -l app=order -n foodies --timeout=180s || echo_warn "order not ready yet"
       kubectl wait --for=condition=ready pod -l app=payment -n foodies --timeout=180s || echo_warn "payment not ready yet"
   }
   ```

3. **Update main function**
   ```bash
   main() {
       echo_info "Starting local Kubernetes deployment..."
       echo ""

       check_prerequisites

       # Read and increment version
       current_version=$(read_version)
       new_version=$(increment_version $current_version)
       write_version $new_version

       # Build images with version
       echo_info "Building project with version $new_version..."
       ./gradlew -Pversion=$new_version publishToLocalRegistry :keycloak-rabbitmq-publisher:shadowJar
       docker build -t foodies-keycloak:$new_version -f keycloak/Dockerfile .

       # Update kustomize with new version
       update_kustomize_versions $new_version

       # Clean up and deploy
       cleanup_namespace

       # Deploy everything with kustomize
       deploy_with_kustomize

       echo ""
       show_status

       echo ""
       echo_info "Deployment script completed successfully!"
   }
   ```

## Benefits

### For Development
- ✅ **Guaranteed fresh images**: Each build gets a unique version tag
- ✅ **No staleness**: K8s sees new version as different image
- ✅ **No registry needed**: `imagePullPolicy: Never` uses local images only
- ✅ **Clean manifests**: Base YAML files stay unchanged
- ✅ **Fast iteration**: Version auto-increments on each deploy
- ✅ **Maintainable**: Version updates happen in one place (kustomization.yaml)

### For Production
- ✅ **Separate configuration**: Different resource limits, replicas, secrets
- ✅ **Registry integration**: Pull from container registry
- ✅ **Always latest**: `imagePullPolicy: Always` ensures updates
- ✅ **Version tracking**: Clear version history
- ✅ **Easy rollback**: `kubectl rollout undo` or deploy older version

## Migration Steps Checklist

- [ ] Create base directory: `mkdir -p k8s/base`
- [ ] Move existing YAML files to base/
- [ ] Create base/kustomization.yaml
- [ ] Create overlays/dev/ directory structure
- [ ] Create overlays/dev/kustomization.yaml
- [ ] Create overlays/dev/patches/imagePullPolicy-never.yaml
- [ ] Update local-deploy.sh with kustomize functions
- [ ] Test kustomize generation: `kubectl kustomize k8s/overlays/dev`
- [ ] Test deployment: `./local-deploy.sh`
- [ ] Verify image versions: `kubectl get deployment -n foodies -o wide`
- [ ] Update .gitignore if needed
- [ ] Create overlays/prod/ when ready for production

## Testing Plan

```bash
# 1. Test that kustomize generates correct YAML
kubectl kustomize k8s/overlays/dev > /tmp/dev-manifest.yaml
cat /tmp/dev-manifest.yaml | grep "image:" | head -10
# Should show versioned images like foodies-keycloak:8

# 2. Test version update command
cd k8s/overlays/dev
kustomize edit set image foodies-keycloak=foodies-keycloak:999
cd ../../..
kubectl kustomize k8s/overlays/dev | grep "foodies-keycloak:999"

# 3. Test full deployment
./local-deploy.sh

# 4. Verify versions are applied
kubectl get deployment -n foodies -o wide
kubectl describe pod -n foodies -l app=keycloak | grep "Image:"

# 5. Verify imagePullPolicy is set correctly
kubectl get deployment keycloak -n foodies -o yaml | grep imagePullPolicy
# Should show: imagePullPolicy: Never
```

## Additional Improvements

1. **Add .gitignore entries**
   ```
   # Keep kustomization.yaml changes local (optional)
   # k8s/overlays/dev/kustomization.yaml
   ```

2. **Add validation script** (`scripts/validate-kustomize.sh`)
   ```bash
   #!/usr/bin/env bash
   set -e

   echo "Validating dev kustomization..."
   kubectl kustomize k8s/overlays/dev > /dev/null
   echo "✅ Dev kustomization is valid"

   if [ -d "k8s/overlays/prod" ]; then
       echo "Validating prod kustomization..."
       kubectl kustomize k8s/overlays/prod > /dev/null
       echo "✅ Prod kustomization is valid"
   fi
   ```

3. **Document in CLAUDE.md**
   ```markdown
   - Use `./local-deploy.sh` to deploy the k8s cluster
   - The deployment uses Kustomize with versioned images
   - Dev overlay uses `imagePullPolicy: Never` for local images
   - Each deployment auto-increments version in `local.version`
   ```

## Why Kustomize Over Alternatives?

**vs. Helm:**
- ✅ Simpler for our use case (no need for full templating)
- ✅ Built into kubectl (no extra tools)
- ✅ Declarative patches instead of templating logic
- ✅ Easier to review changes (plain YAML diffs)

**vs. Manual kubectl set image:**
- ✅ Version-controlled configuration
- ✅ Reproducible deployments
- ✅ Can review changes before applying
- ✅ Supports more than just image updates (patches, etc.)

**vs. Plain YAML with sed/envsubst:**
- ✅ Type-safe transformations
- ✅ No risk of corrupting YAML syntax
- ✅ Better error messages
- ✅ Standardized tool with community support

## References

- [Kustomize Documentation](https://kustomize.io/)
- [Kubernetes Kustomize Guide](https://kubernetes.io/docs/tasks/manage-kubernetes-objects/kustomization/)
- [Kustomize Image Transformer](https://kubectl.docs.kubernetes.io/references/kustomize/kustomization/images/)
- [Strategic Merge Patch](https://kubectl.docs.kubernetes.io/references/kustomize/glossary/#patchstrategicmerge)
