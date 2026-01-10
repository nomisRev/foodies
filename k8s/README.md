# Kubernetes Deployment

This directory contains Kubernetes manifests for deploying the Foodies application stack.

## Requirements

- **Kubernetes cluster** (v1.24+)
  - Local: Docker Desktop with Kubernetes, Minikube, or Kind
  - Cloud: GKE, EKS, or AKS
- **kubectl** (v1.24+)
- **Docker images** built for:
  - `foodies-webapp:latest`
  - `foodies-menu:latest`
  - `foodies-profile:latest`

## Architecture

The deployment includes:
- **Namespace**: `foodies`
- **Services**: webapp (port 8080), menu (port 8082), profile (port 8081)
- **Infrastructure**: RabbitMQ, Keycloak
- **Databases**: PostgreSQL for menu and profile services
- **LoadBalancer**: Exposes webapp on port 80

## Deployment Instructions

### 1. Create Secrets

Create the required secrets with your own values:

```bash
# Postgres credentials
kubectl create secret generic postgres-credentials \
  --namespace=foodies \
  --from-literal=POSTGRES_USER=your_user \
  --from-literal=POSTGRES_PASSWORD=your_password

# RabbitMQ credentials
kubectl create secret generic rabbitmq-credentials \
  --namespace=foodies \
  --from-literal=RABBITMQ_USERNAME=your_user \
  --from-literal=RABBITMQ_PASSWORD=your_password

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

Or apply the YAML files in `k8s/secrets/` (after updating with your values).

**Note**: Default secrets are provided in the repository with development credentials. For production, update these values before deployment.

### 2. Build the custom Keycloak image (provider baked in)

The Keycloak deployment now uses a custom image with the RabbitMQ event listener already installed.

```bash
# Build the provider JAR (creates keycloak-rabbitmq-publisher--all.jar)
./gradlew :keycloak-rabbitmq-publisher:build

# Build the Keycloak image expected by the k8s manifest
docker build -t foodies-keycloak:local -f keycloak/Dockerfile .

# (Optional) Load the image into your cluster if it cannot pull from your local daemon
# Kind: kind load docker-image foodies-keycloak:local --name <your-kind-cluster>
# Minikube: minikube image load foodies-keycloak:local
```

If you need to push to a registry, tag the image (e.g., `ghcr.io/your-org/foodies-keycloak:<tag>`) and update `image:` in `k8s/infrastructure/keycloak.yaml` accordingly.

### 3. Deploy Infrastructure

```bash
# Create namespace
kubectl apply -f k8s/namespace.yaml

# Deploy configmaps
kubectl apply -f k8s/configmaps/

# Deploy databases
kubectl apply -f k8s/databases/

# Deploy infrastructure (RabbitMQ, Keycloak)
kubectl apply -f k8s/infrastructure/

# Wait for infrastructure to be ready
kubectl wait --for=condition=ready pod -l app=rabbitmq -n foodies --timeout=120s
kubectl wait --for=condition=ready pod -l app=keycloak -n foodies --timeout=120s
```

### 4. Deploy Services

```bash
# Deploy all services
kubectl apply -f k8s/services/

# Wait for services to be ready
kubectl wait --for=condition=ready pod -l app=webapp -n foodies --timeout=120s
kubectl wait --for=condition=ready pod -l app=menu -n foodies --timeout=120s
kubectl wait --for=condition=ready pod -l app=profile -n foodies --timeout=120s
```

### 5. Deploy LoadBalancer

```bash
kubectl apply -f k8s/ingress.yaml
```

### 6. Access the Application

Get the external IP of the LoadBalancer:

```bash
kubectl get svc foodies-loadbalancer -n foodies
```

Once the `EXTERNAL-IP` is assigned (may take a few minutes), access the application at `http://<EXTERNAL-IP>`.

For local clusters (Docker Desktop/Minikube), the LoadBalancer may show `<pending>`. Use port-forwarding instead:

```bash
kubectl port-forward -n foodies svc/webapp 8080:8080
```

Then access at `http://localhost:8080`.

**Note**: The application is configured to use `foodies.local` as the hostname. For proper authentication flow with Keycloak, add this to your `/etc/hosts` file:

```
127.0.0.1 foodies.local
```

Or update the `FOODIES_HOST` value in [k8s/configmaps/foodies-config.yaml](fleet-file://mmglq7uf96d8i197ro8d/Users/simonvergauwen/Developer/foodies/k8s/configmaps/foodies-config.yaml?type=file&root=%252F) to match your environment.

## Configuration

### ConfigMaps

The deployment uses two ConfigMaps:

1. **foodies-config** ([k8s/configmaps/foodies-config.yaml](fleet-file://mmglq7uf96d8i197ro8d/Users/simonvergauwen/Developer/foodies/k8s/configmaps/foodies-config.yaml?type=file&root=%252F))
   - `FOODIES_HOST`: Application hostname (default: `foodies.local`)
   - `PROFILE_DB_NAME`: Profile database name
   - `MENU_DB_NAME`: Menu database name
   - `RABBITMQ_QUEUE`: RabbitMQ queue name for events
   - `KEYCLOAK_HTTP_PORT`: Keycloak HTTP port (default: 8000)

2. **keycloak-realm** ([k8s/configmaps/keycloak-realm.yaml](fleet-file://mmglq7uf96d8i197ro8d/Users/simonvergauwen/Developer/foodies/k8s/configmaps/keycloak-realm.yaml?type=file&root=%252F))
   - Contains the Keycloak realm configuration with:
     - Realm name: `foodies-keycloak`
     - Client configuration for OAuth2/OIDC
     - Event listeners and enabled event types
     - Default test user credentials

### File Structure

```
k8s/
├── namespace.yaml              # Namespace definition
├── secrets/                    # Secret configurations
│   ├── keycloak-admin.yaml    # Keycloak admin credentials
│   ├── postgres-credentials.yaml  # PostgreSQL credentials
│   ├── rabbitmq-credentials.yaml  # RabbitMQ credentials
│   └── webapp-auth.yaml       # OAuth client secret
├── configmaps/                 # ConfigMap definitions
│   ├── foodies-config.yaml    # Application configuration
│   └── keycloak-realm.yaml    # Keycloak realm import
├── databases/                  # Database deployments
│   ├── menu-postgres.yaml     # Menu service database
│   └── profile-postgres.yaml  # Profile service database
├── infrastructure/             # Infrastructure services
│   ├── keycloak.yaml          # Keycloak deployment
│   └── rabbitmq.yaml          # RabbitMQ deployment
├── services/                   # Application services
│   ├── menu.yaml              # Menu service
│   ├── profile.yaml           # Profile service
│   └── webapp.yaml            # Webapp service
└── ingress.yaml               # LoadBalancer configuration
```

## Quick Deploy (All at Once)

```bash
# Apply all manifests
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/secrets/
kubectl apply -f k8s/configmaps/
kubectl apply -f k8s/databases/
kubectl apply -f k8s/infrastructure/
kubectl apply -f k8s/services/
kubectl apply -f k8s/ingress.yaml
```

**Remember**: build and load the custom Keycloak image (`foodies-keycloak:local`) before applying manifests.

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
   ./gradlew :keycloak-rabbitmq-publisher:build
   docker build -t foodies-keycloak:local -f keycloak/Dockerfile .
   # Load into the cluster (kind/minikube) or push to your registry if required
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

### RabbitMQ Connection Issues

```bash
# Check RabbitMQ status
kubectl get pods -n foodies -l app=rabbitmq

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
# Check LoadBalancer status
kubectl get svc foodies-loadbalancer -n foodies

# Check webapp endpoints
kubectl get endpoints webapp -n foodies

# Port-forward to test directly
kubectl port-forward -n foodies svc/webapp 8080:8080
```

## Cleanup

```bash
# Delete entire namespace and all resources
kubectl delete namespace foodies

# Or delete individual components
kubectl delete -f k8s/ingress.yaml
kubectl delete -f k8s/services/
kubectl delete -f k8s/infrastructure/
kubectl delete -f k8s/databases/
kubectl delete -f k8s/configmaps/
kubectl delete -f k8s/secrets/
kubectl delete -f k8s/namespace.yaml
```
