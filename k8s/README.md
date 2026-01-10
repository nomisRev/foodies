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
  --from-literal=KEYCLOAK_ADMIN=admin \
  --from-literal=KEYCLOAK_ADMIN_PASSWORD=your_password

# Webapp auth secret
kubectl create secret generic webapp-auth \
  --namespace=foodies \
  --from-literal=AUTH_CLIENT_SECRET=your_client_secret
```

Or apply the YAML files in `k8s/secrets/` (after updating with your values).

### 2. Deploy Infrastructure

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

### 3. Deploy Services

```bash
# Deploy all services
kubectl apply -f k8s/services/

# Wait for services to be ready
kubectl wait --for=condition=ready pod -l app=webapp -n foodies --timeout=120s
kubectl wait --for=condition=ready pod -l app=menu -n foodies --timeout=120s
kubectl wait --for=condition=ready pod -l app=profile -n foodies --timeout=120s
```

### 4. Deploy LoadBalancer

```bash
kubectl apply -f k8s/ingress.yaml
```

### 5. Access the Application

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

## Quick Deploy (All at Once)

```bash
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/secrets/
kubectl apply -f k8s/configmaps/
kubectl apply -f k8s/databases/
kubectl apply -f k8s/infrastructure/
kubectl apply -f k8s/services/
kubectl apply -f k8s/ingress.yaml
```

## Verify Deployment

```bash
# Check all resources in the namespace
kubectl get all -n foodies

# Check pod logs
kubectl logs -n foodies -l app=webapp
kubectl logs -n foodies -l app=menu
kubectl logs -n foodies -l app=profile
```

## Cleanup

```bash
kubectl delete namespace foodies
```