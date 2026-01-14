#!/usr/bin/env bash

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

echo_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

echo_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check prerequisites
check_prerequisites() {
    echo_info "Checking prerequisites..."

    if ! command -v kubectl &> /dev/null; then
        echo_error "kubectl not found. Please install kubectl."
        exit 1
    fi

    if ! command -v docker &> /dev/null; then
        echo_error "docker not found. Please install Docker."
        exit 1
    fi

    # Check if kubectl can connect to cluster
    if ! kubectl cluster-info &> /dev/null; then
        echo_error "Cannot connect to Kubernetes cluster. Is Docker Desktop Kubernetes enabled?"
        exit 1
    fi

    echo_info "Prerequisites check passed"
}

read_version() {
    if [ -f "local.version" ]; then
        cat local.version
    else
        echo "WARNING!! Falling back to local version 0"
    fi
}

increment_version() {
    local version=$1
    echo $((version + 1))
}

write_version() {
    echo $1 > local.version
}

build_project_and_publish_images() {
    echo_info "Reading and incrementing version..."
    local current_version=$(read_version)
    echo_info "Current version: $current_version"

    local new_version=$(increment_version $current_version)
    echo_info "New version: $new_version"

    write_version $new_version

    echo_info "Building project with version $new_version..."
    ./gradlew -Pversion=$new_version publishToLocalRegistry :keycloak-rabbitmq-publisher:shadowJar
    docker build -t foodies-keycloak:$new_version -f keycloak/Dockerfile .
}

# Delete existing namespace and wait for cleanup
cleanup_namespace() {
    echo_info "Cleaning up existing deployment..."

    if kubectl get namespace foodies &> /dev/null; then
        kubectl delete namespace foodies --wait=true
        echo_info "Waiting for namespace cleanup..."
        sleep 5
    else
        echo_info "No existing namespace found"
    fi
}

# Create namespace
create_namespace() {
    echo_info "Creating namespace..."
    kubectl apply -f k8s/base/namespace.yaml
}

# Apply secrets
apply_secrets() {
    echo_info "Applying secrets..."
    kubectl apply -f k8s/base/secrets/
}

# Apply configmaps
apply_configmaps() {
    echo_info "Applying configmaps..."
    kubectl apply -f k8s/base/configmaps/
}

# Deploy databases
deploy_databases() {
    echo_info "Deploying databases..."
    kubectl apply -f k8s/base/databases/

    echo_info "Waiting for databases to be ready..."
    kubectl wait --for=condition=ready pod -l app=menu-postgres -n foodies --timeout=120s || echo_warn "menu-postgres not ready yet"
    kubectl wait --for=condition=ready pod -l app=profile-postgres -n foodies --timeout=120s || echo_warn "profile-postgres not ready yet"
    kubectl wait --for=condition=ready pod -l app=order-postgres -n foodies --timeout=120s || echo_warn "order-postgres not ready yet"
    kubectl wait --for=condition=ready pod -l app=payment-postgres -n foodies --timeout=120s || echo_warn "payment-postgres not ready yet"
    kubectl wait --for=condition=ready pod -l app=redis -n foodies --timeout=120s || echo_warn "redis not ready yet"
}

# Deploy infrastructure
deploy_infrastructure() {
    echo_info "Deploying infrastructure (RabbitMQ, Keycloak)..."
    kubectl apply -f k8s/base/infrastructure/

    echo_info "Waiting for infrastructure to be ready..."
    kubectl wait --for=condition=ready pod -l app=rabbitmq -n foodies --timeout=180s || echo_warn "RabbitMQ not ready yet"
    kubectl wait --for=condition=ready pod -l app=keycloak -n foodies --timeout=180s || echo_warn "Keycloak not ready yet"
}

# Deploy services
deploy_services() {
    echo_info "Deploying application services..."
    kubectl apply -f k8s/base/services/

    echo_info "Waiting for services to be ready..."
    kubectl wait --for=condition=ready pod -l app=webapp -n foodies --timeout=180s || echo_warn "webapp not ready yet"
    kubectl wait --for=condition=ready pod -l app=menu -n foodies --timeout=180s || echo_warn "menu not ready yet"
    kubectl wait --for=condition=ready pod -l app=profile -n foodies --timeout=180s || echo_warn "profile not ready yet"
    kubectl wait --for=condition=ready pod -l app=basket -n foodies --timeout=180s || echo_warn "basket not ready yet"
    kubectl wait --for=condition=ready pod -l app=order -n foodies --timeout=180s || echo_warn "order not ready yet"
    kubectl wait --for=condition=ready pod -l app=payment -n foodies --timeout=180s || echo_warn "payment not ready yet"
}

# Deploy ingress/load balancer
deploy_ingress() {
    echo_info "Deploying ingress/load balancer..."
    kubectl apply -f k8s/base/ingress.yaml
}

# Show deployment status
show_status() {
    echo_info "Deployment complete! Checking status..."
    echo ""
    echo_info "All pods in foodies namespace:"
    kubectl get pods -n foodies
    echo ""
    echo_info "All services in foodies namespace:"
    kubectl get svc -n foodies
    echo ""
    echo_info "Ingress status:"
    kubectl get ingress -n foodies
    echo ""
    echo_info "Access the application:"
    echo "  - Using Ingress: http://foodies.local"
    echo ""
    echo_info "Make sure you have 'foodies.local' in /etc/hosts pointing to 127.0.0.1"
}

# Main deployment flow
main() {
    echo_info "Starting local Kubernetes deployment..."
    echo ""

    check_prerequisites
    build_project_and_publish_images
    cleanup_namespace
    create_namespace
    apply_secrets
    apply_configmaps
    deploy_databases
    deploy_infrastructure
    deploy_services
    deploy_ingress

    echo ""
    show_status

    echo ""
    echo_info "Deployment script completed successfully!"
}

# Run main function
main
