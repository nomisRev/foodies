# Kubernetes Deployment

Kubernetes manifests for deploying the Foodies application stack using Kustomize for environment-specific configurations.

> For service-specific details, see individual [service READMEs](../README.md#service-documentation).

## Requirements

- **Kind** (v0.23+)
- **kubectl** (v1.24+)
- **Docker** (daemon must be running)
- **Bash** (macOS or Linux)
- `/etc/hosts` entry for local ingress:
  - `127.0.0.1 foodies.local`

## Architecture

The deployment follows a microservices architecture with comprehensive observability:

### Core Services
- **webapp** (port 8080): Frontend application
- **menu** (port 8082): Menu management service
- **profile** (port 8081): User profile service
- **basket** (port 8083): Shopping basket service
- **order** (port 8084): Order management service
- **payment** (port 8085): Payment processing service

### Infrastructure Components
- **Keycloak** (port 8000): Identity and Access Management with custom RabbitMQ event listener
- **RabbitMQ**: Message broker for asynchronous communication (managed by RabbitMQ Cluster Operator)
  - Uses Custom Resources (User, Queue, Exchange, Binding, Permission) for declarative configuration
  - **Binding Routing Keys**: When the `routingKey` field is omitted from Binding resources (as in `k8s/base/rabbitmq/bindings.yaml`), the RabbitMQ Cluster Operator defaults to using the destination queue name as the routing key. For example, the binding for `menu.stock-validation` queue automatically uses `menu.stock-validation` as its routing key. This default behavior aligns with common patterns where routing keys match queue names for topic/direct exchanges.
- **Redis**: In-memory data store for basket service
- **PostgreSQL**: Database instances for menu, profile, order, and payment services

### Observability Stack
- **OpenTelemetry Collector**: Centralized telemetry collection
- **Jaeger** (port 16686): Distributed tracing
- **Prometheus** (port 9090): Metrics collection and monitoring
- **NGINX Ingress**: External access routing

### Communication Patterns
- **Synchronous**: REST APIs between services
- **Asynchronous**: RabbitMQ events for order processing and profile updates
- **Authentication**: OAuth2/OIDC via Keycloak with JWT tokens

## Kind Rollout (Recommended)

Use the scripted workflow for deterministic local cluster rollout:

```bash
# Validate local prerequisites
./scripts/kind/foodies-kind.sh doctor

# Create/update cluster and deploy stack
./scripts/kind/foodies-kind.sh up

# Full reset (delete cluster, recreate, redeploy)
./scripts/kind/foodies-kind.sh reset

# Check runtime status
./scripts/kind/foodies-kind.sh status

# Delete cluster
./scripts/kind/foodies-kind.sh down
```

### What `up` does

`./scripts/kind/foodies-kind.sh up` runs the following steps in order:

1. Checks required commands (`kind`, `kubectl`, `docker`, `./gradlew`) and Docker daemon connectivity.
2. Creates (or reuses) the Kind cluster using `scripts/kind/kind.config.yaml` with ingress-ready node labels and `80/443` host mappings.
3. Installs ingress-nginx from `k8s/base/ingress-nginx`.
4. Installs cert-manager from `k8s/base/cert-manager`.
5. Installs RabbitMQ operators from `k8s/base/rabbitmq-operator` and waits for CRDs.
6. Builds all Foodies images via `./gradlew publishImageToLocalRegistry`.
7. Loads all `foodies-*` images referenced by `k8s/overlays/dev` into Kind with `kind load docker-image`.
8. Applies `k8s/overlays/dev`.
9. Waits for core deployments/statefulsets and the `keycloak-config-cli` job.
10. Prints access URLs and useful debug commands.

### Endpoints

- Webapp: [http://foodies.local](http://foodies.local)
- Keycloak: [http://foodies.local/auth](http://foodies.local/auth)
- Jaeger: [http://foodies.local/jaeger](http://foodies.local/jaeger)
- Prometheus: [http://foodies.local/prometheus](http://foodies.local/prometheus)

## Deployment Instructions

### 1. Install Cluster Controllers

Install ingress-nginx, cert-manager, and RabbitMQ operators before applying app resources:

```bash
# Ingress controller for foodies.local
kubectl apply -k k8s/base/ingress-nginx
kubectl wait --for=condition=available deployment/ingress-nginx-controller -n ingress-nginx --timeout=180s

# Cert-manager required by messaging topology operator
kubectl apply -k k8s/base/cert-manager
kubectl wait --for=condition=available deployment/cert-manager -n cert-manager --timeout=180s
kubectl wait --for=condition=available deployment/cert-manager-cainjector -n cert-manager --timeout=180s
kubectl wait --for=condition=available deployment/cert-manager-webhook -n cert-manager --timeout=180s

# RabbitMQ operators and CRDs
kubectl apply -k k8s/base/rabbitmq-operator

# Wait for the operator to be ready
kubectl wait --for=condition=available deployment/rabbitmq-cluster-operator -n rabbitmq-system --timeout=180s
kubectl wait --for=condition=available deployment/messaging-topology-operator -n rabbitmq-system --timeout=180s
```

### 2. Create Secrets (Optional for local dev)

Create the required secrets with your own values:

```bash
# Postgres credentials
kubectl create secret generic postgres-credentials \
  --namespace=foodies \
  --from-literal=POSTGRES_USER=your_user \
  --from-literal=POSTGRES_PASSWORD=your_password



# Keycloak admin credentials
kubectl create secret generic keycloak-admin \
  --namespace=foodies \
  --from-literal=KC_BOOTSTRAP_ADMIN_USERNAME=admin \
  --from-literal=KC_BOOTSTRAP_ADMIN_PASSWORD=your_password

# Webapp auth secret
kubectl create secret generic webapp-auth \
  --namespace=foodies \
  --from-literal=AUTH_CLIENT_SECRET=your_client_secret
```

**Note**: For local development, defaults are generated by Kustomize in `k8s/base/kustomization.yaml`. For production, override secrets through `k8s/overlays/prod` or external secret management.

### 3. Build and load application images

```bash
# Build all service images + custom Keycloak image
./gradlew publishImageToLocalRegistry
```

For Kind, load the locally built images into the node runtime:

```bash
kind load docker-image foodies-webapp:0.0.3 --name foodies
kind load docker-image foodies-menu:0.0.1 --name foodies
kind load docker-image foodies-profile:0.0.4 --name foodies
kind load docker-image foodies-basket:0.0.6 --name foodies
kind load docker-image foodies-order:0.0.1 --name foodies
kind load docker-image foodies-payment:0.0.3 --name foodies
kind load docker-image foodies-keycloak:0.0.5 --name foodies
```

If you push images to an external registry, update image references in `k8s/base/*/deployment.yaml` (or use a Kustomize image override).

### 4. Deploy Everything with Kustomize

The deployment now supports Kustomize for better management of resources and environment-specific overlays.

#### Local Development (Dev Overlay)

```bash
kubectl apply -k k8s/overlays/dev
```

This applies application resources in `foodies`, including databases, services, ingress routes, and observability components.

#### Base Deployment

If you want to apply the base configuration without any overlay:

```bash
kubectl apply -k k8s/base
```

### 5. Wait for resources to be ready

```bash
# Wait for infrastructure components
kubectl wait --for=condition=ready pod -l app=rabbitmq -n foodies --timeout=120s
kubectl wait --for=condition=ready pod -l app=keycloak -n foodies --timeout=120s
kubectl wait --for=condition=ready pod -l app=redis -n foodies --timeout=120s

# Wait for observability stack
kubectl wait --for=condition=ready pod -l app=otel-collector -n foodies --timeout=60s
kubectl wait --for=condition=ready pod -l app=jaeger -n foodies --timeout=60s
kubectl wait --for=condition=ready pod -l app=prometheus -n foodies --timeout=60s

# Wait for all application services
kubectl wait --for=condition=ready pod -l app=webapp -n foodies --timeout=120s
kubectl wait --for=condition=ready pod -l app=menu -n foodies --timeout=120s
kubectl wait --for=condition=ready pod -l app=profile -n foodies --timeout=120s
kubectl wait --for=condition=ready pod -l app=basket -n foodies --timeout=120s
kubectl wait --for=condition=ready pod -l app=order -n foodies --timeout=120s
kubectl wait --for=condition=ready pod -l app=payment -n foodies --timeout=120s
```

### Environment-Specific Deployment

#### Development Environment
```bash
kubectl apply -k k8s/overlays/dev
```
- Single replicas for all services
- Development credentials (admin/admin, guest/guest)
- HostPath storage for Redis (local development)
- Keycloak realm configuration job included
- `foodies.local` hostname

#### Production Environment
```bash
kubectl apply -k k8s/overlays/prod
```
- Webapp scaled to 3 replicas for high availability
- Production-grade secrets (change-me-at-runtime placeholders)
- `foodies.com` hostname
- Enhanced security configurations
- Resource optimization

### 6. Access the Application

For Kind with ingress-nginx, access services through ingress:

- [http://foodies.local](http://foodies.local)
- [http://foodies.local/auth](http://foodies.local/auth)

If ingress is not available, use port-forwarding:

```bash
kubectl port-forward -n foodies svc/webapp 8080:8080
```

**Note**: The application is configured to use `foodies.local` as the hostname. For proper authentication flow with Keycloak, add this to your `/etc/hosts` file:

```
127.0.0.1 foodies.local
```

Or update the `FOODIES_HOST` value in `k8s/base/kustomization.yaml` (or override it in an overlay) to match your environment.

### 7. Monitoring and Tracing

#### Observability Stack Access
- **Jaeger UI**: http://foodies.local/jaeger (distributed tracing)
  - **Query traces**: Select service from dropdown (e.g., `webapp`, `menu`, `order`) and click **Find Traces**
  - **Trace analysis**: Click on individual traces to see detailed service call chains
- **Prometheus UI**: http://foodies.local/prometheus (metrics)
  - **Query metrics**: Use expression browser for metrics like:
    - `http_server_requests_seconds_count` (HTTP request count)
    - `jvm_memory_used_bytes` (JVM memory usage)
    - `rabbitmq_queue_messages` (Queue depth)
  - **Service health**: Check **Status** -> **Targets** for service availability
- **RabbitMQ Management**: http://foodies.local:15672 (message broker UI)
  - Credentials: guest/guest (development)
  - Monitor queues: `profile.registration`, `basket.order-created`

#### Telemetry Flow
1. All services emit traces and metrics to **OpenTelemetry Collector** (port 4317/4318)
2. Collector routes traces to **Jaeger** for distributed tracing
3. Collector routes metrics to **Prometheus** for time-series storage
4. Services use structured logging with tracing correlation

## Configuration

### Configuration

#### ConfigMaps

The deployment uses centralized configuration through ConfigMaps:

1. **foodies-config** (generated by Kustomize):
   - `FOODIES_HOST`: Application hostname (dev: `foodies.local`, prod: `foodies.com`)
   - `PROFILE_DB_NAME`: Profile database name
   - `RABBITMQ_QUEUE`: RabbitMQ queue name for profile events
   - `BASKET_RABBITMQ_QUEUE`: RabbitMQ queue for basket events
   - `KEYCLOAK_HTTP_PORT`: Keycloak HTTP port (default: 8000)
   - `OTEL_EXPORTER_OTLP_ENDPOINT`: OpenTelemetry collector endpoint

2. **otel-collector-config**: OpenTelemetry collector configuration for telemetry routing
3. **prometheus-config**: Prometheus configuration for metrics collection
4. **keycloak-realm**: Keycloak realm configuration with OAuth2/OIDC setup

#### Service-Specific ConfigMaps
Each service has its own ConfigMap with specific configuration:
- **basket-config**: Basket service configuration including Redis and RabbitMQ settings
- **menu-config**: Menu service configuration with database connection
- **profile-config**: Profile service configuration with database connection
- **order-config**: Order service configuration with database and messaging
- **payment-config**: Payment service configuration with database connection
- **webapp-config**: Web application configuration with authentication settings

#### Secrets

Sensitive data is managed through Kubernetes Secrets:

1. **keycloak-admin**: Keycloak administrator credentials
2. **postgres-credentials**: PostgreSQL database credentials

4. **webapp-auth**: OAuth2 client secret for web application

**Development defaults are provided - update for production deployments!**

### File Structure

```
k8s/
├── base/                       # Base Kustomize configuration
│   ├── kustomization.yaml     # Base kustomization with all resources
│   ├── namespace/             # Namespace definition
│   ├── ingress/               # NGINX Ingress configuration
│   ├── ingress-nginx/         # Ingress controller installation (Kind)
│   ├── basket/                # Basket service (deployment + service + config)
│   ├── keycloak/              # Keycloak IAM with custom provider
│   ├── menu/                  # Menu service (deployment + service + database)
│   ├── order/                 # Order service (deployment + service + database)
│   ├── payment/               # Payment service (deployment + service + database)
│   ├── profile/               # Profile service (deployment + service + database)
│   ├── webapp/                # Frontend application (deployment + service)
│   ├── otel-collector/        # OpenTelemetry collector
│   ├── jaeger/                # Jaeger tracing
│   ├── prometheus/            # Prometheus monitoring
│   ├── rabbitmq/              # RabbitMQ message broker
│   └── redis/                 # Redis cache with persistence
└── overlays/                  # Environment-specific overlays
    ├── dev/                   # Development overlay (host: foodies.local + storage tweaks)
    │   └── kustomization.yaml # Development-specific settings
    └── prod/                  # Production overlay (scaled + security-hardened)
        └── kustomization.yaml # Production-specific settings

scripts/
└── kind/
    ├── foodies-kind.sh        # Rollout CLI (doctor/up/reset/status/down)
    ├── lib.sh                 # Shared checks/waits/helpers
    └── kind.config.yaml       # Kind cluster config (ingress-ready node + 80/443 mapping)
```

## Quick Deploy (All at Once)

```bash
# Recommended scripted rollout
./scripts/kind/foodies-kind.sh up
```

Manual equivalent:

```bash
kubectl apply -k k8s/base/ingress-nginx
kubectl apply -k k8s/base/cert-manager
kubectl apply -k k8s/base/rabbitmq-operator
./gradlew publishImageToLocalRegistry
kubectl apply -k k8s/overlays/dev
```

## Verify Deployment

```bash
# Check all resources in the namespace
kubectl get all -n foodies

# Check pod status
kubectl get pods -n foodies

# Check services
kubectl get svc -n foodies

# Check configmaps and secrets
kubectl get configmaps,secrets -n foodies

# Check pod logs
kubectl logs -n foodies -l app=webapp
kubectl logs -n foodies -l app=menu
kubectl logs -n foodies -l app=profile
kubectl logs -n foodies -l app=keycloak
kubectl logs -n foodies -l app=rabbitmq
```

## Troubleshooting

### Keycloak Provider Not Loading

If the Keycloak event listener provider is not working:

1. Confirm the deployment points to the custom image:
   ```bash
   kubectl get deployment keycloak -n foodies -o jsonpath='{.spec.template.spec.containers[0].image}'
   ```

2. Check Keycloak logs for `profile-webhook` initialization:
   ```bash
   kubectl logs -n foodies -l app=keycloak
   ```

3. If missing, rebuild and reload the image:
   ```bash
   ./gradlew publishImageToLocalRegistry
   kind load docker-image foodies-keycloak:0.0.5 --name foodies
   kubectl rollout restart deployment/keycloak -n foodies
   ```

### Database Connection Issues

Check database pods and connectivity:

```bash
# Check database pods
kubectl get pods -n foodies | grep postgres

# Check database logs
kubectl logs -n foodies -l app=menu-postgres
kubectl logs -n foodies -l app=profile-postgres

# Test connection from service pod
kubectl exec -n foodies -it deployment/profile -- sh
# Then inside the pod:
# nc -zv menu-postgres 5432
```

### RabbitMQ Operator Issues

If you see errors about missing CRDs (Binding, Exchange, Permission, Queue, User):

```bash
# Check if the operator is installed
kubectl get deployment -n rabbitmq-system

# If not installed, install it first
kubectl apply -k k8s/base/rabbitmq-operator
kubectl wait --for=condition=ready pod -l app.kubernetes.io/name=rabbitmq-cluster-operator -n rabbitmq-system --timeout=120s

# Then apply the rest
kubectl apply -k k8s/overlays/dev
```

### RabbitMQ Connection Issues

```bash
# Check RabbitMQ cluster status
kubectl get rabbitmqclusters -n foodies

# Check RabbitMQ status
kubectl get pods -n foodies -l app.kubernetes.io/name=rabbitmq

# Check RabbitMQ logs
kubectl logs -n foodies -l app=rabbitmq

# Access RabbitMQ management UI (port-forward)
kubectl port-forward -n foodies svc/rabbitmq 15672:15672
# Then access http://localhost:15672
```

### Authentication/Keycloak Issues

```bash
# Check Keycloak logs
kubectl logs -n foodies -l app=keycloak

# Check realm import status
kubectl logs -n foodies -l app=keycloak -c process-realm-template

# Access Keycloak admin console (port-forward)
kubectl port-forward -n foodies svc/keycloak 8000:8000
# Then access http://localhost:8000/auth
```

### Service Not Accessible

```bash
# Check ingress routing
kubectl get ingress foodies-ingress -n foodies
kubectl describe ingress foodies-ingress -n foodies

# Check webapp endpoints
kubectl get endpoints webapp -n foodies

# Port-forward to test directly
kubectl port-forward -n foodies svc/webapp 8080:8080
```

### Monitoring Stack Issues

If Jaeger or Prometheus are not showing data:

1. Check OpenTelemetry Collector logs:
   ```bash
   kubectl logs -n foodies -l app=otel-collector
   ```

2. Verify service telemetry configuration:
   ```bash
   # Check that services have OTEL environment variables
   kubectl describe deployment -n foodies webapp
   # Look for OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4317
   ```

3. Check Prometheus targets:
   - Access Prometheus UI: http://foodies.local/prometheus
   - Navigate to **Status** -> **Targets**
   - All service endpoints should be "UP"

4. Verify Jaeger traces:
   - Access Jaeger UI: http://foodies.local/jaeger
   - Select any service from dropdown and click **Find Traces**

5. Test OpenTelemetry endpoint connectivity:
   ```bash
   kubectl exec -n foodies -it deployment/webapp -- curl -s http://otel-collector:4317
   ```

## Advanced Operations

### Scaling Services

#### Manual Scaling
```bash
# Scale webapp to 3 replicas
kubectl scale deployment webapp --replicas=3 -n foodies

# Scale all backend services
kubectl scale deployment menu profile basket order payment --replicas=2 -n foodies
```

#### Horizontal Pod Autoscaling
```bash
# Create HPA for webapp (requires metrics server)
kubectl autoscale deployment webapp --cpu-percent=70 --min=1 --max=5 -n foodies

# View HPA status
kubectl get hpa -n foodies
```

### Debugging Tools

#### Port Forwarding for Local Access
```bash
# Web application
kubectl port-forward -n foodies svc/webapp 8080:8080

# Keycloak admin console
kubectl port-forward -n foodies svc/keycloak 8000:8000

# RabbitMQ management
kubectl port-forward -n foodies svc/rabbitmq 15672:15672

# Database access (for debugging)
kubectl port-forward -n foodies svc/menu-postgres 5432:5432
```

#### Service Mesh Analysis
```bash
# Test service connectivity
kubectl exec -n foodies -it deployment/webapp -- curl -s http://menu:8082/health

# Check DNS resolution
kubectl exec -n foodies -it deployment/webapp -- nslookup menu

# Network policies (if applied)
kubectl get networkpolicies -n foodies
```

### Backup and Recovery

#### Database Backups
```bash
# Backup menu database
kubectl exec -n foodies -it deployment/menu-postgres -- pg_dump -U foodies_admin foodies-menu-database > menu-backup.sql

# Restore menu database
kubectl exec -i -n foodies deployment/menu-postgres -- psql -U foodies_admin foodies-menu-database < menu-backup.sql
```

#### Persistent Volumes
```bash
# List PVCs
kubectl get pvc -n foodies

# Check PVC details
kubectl describe pvc redis-data -n foodies
```

## Production Considerations

### Security Hardening
- **Secrets Management**: See [External Secret Management Guide](../docs/EXTERNAL_SECRET_MANAGEMENT.md) for migrating to production-grade secret storage
- Update all default secrets in production overlays
- Enable NetworkPolicies for service-to-service communication
- Use PodSecurityPolicies or PSP replacement
- Enable RBAC with least privilege principle

### Resource Optimization
- Adjust resource requests/limits based on actual usage
- Enable cluster autoscaling for production workloads
- Use node affinity for database workloads
- Consider spot instances for stateless services

### High Availability
- Deploy with 3+ control plane nodes
- Use managed database services where possible
- Enable multi-zone deployment
- Configure proper backup schedules
- Implement disaster recovery procedures

## Cleanup

```bash
# Delete entire namespace and all resources
kubectl delete namespace foodies

# Or delete individual components using Kustomize
kubectl delete -k k8s/overlays/dev

# Force delete stuck resources
kubectl delete namespace foodies --force --grace-period=0
```
