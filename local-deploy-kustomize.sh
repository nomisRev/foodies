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

    # Check if kustomize is available (built into kubectl)
    if ! kubectl kustomize --help &> /dev/null; then
        echo_error "kubectl kustomize not available. Please update kubectl to 1.14+."
        exit 1
    fi

    echo_info "Prerequisites check passed"
}

read_version() {
    if [ -f "local.version" ]; then
        cat local.version
    else
        echo "0"
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
    local version=$1
    echo_info "Building project with version $version..."

    ./gradlew -Pversion=$version publishToLocalRegistry :keycloak-rabbitmq-publisher:shadowJar

    echo_info "Building Keycloak image..."
    docker build -t foodies-keycloak:$version -f keycloak/Dockerfile .
}

update_kustomize_versions() {
    local version=$1
    echo_info "Updating Kustomize with version $version..."

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

    echo_info "Version $version set in Kustomize configuration"
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

# Deploy everything with Kustomize
deploy_with_kustomize() {
    echo_info "Deploying with Kustomize..."

    # Validate kustomize configuration first
    echo_info "Validating Kustomize configuration..."
    kubectl kustomize k8s/overlays/dev > /tmp/foodies-manifest.yaml
    echo_info "Generated manifest size: $(wc -l < /tmp/foodies-manifest.yaml) lines"

    # Apply the configuration
    kubectl apply -k k8s/overlays/dev

    echo_info "Waiting for resources to be ready..."

    # Wait for databases
    echo_info "Waiting for databases..."
    kubectl wait --for=condition=ready pod -l app=menu-postgres -n foodies --timeout=120s || echo_warn "menu-postgres not ready yet"
    kubectl wait --for=condition=ready pod -l app=profile-postgres -n foodies --timeout=120s || echo_warn "profile-postgres not ready yet"
    kubectl wait --for=condition=ready pod -l app=order-postgres -n foodies --timeout=120s || echo_warn "order-postgres not ready yet"
    kubectl wait --for=condition=ready pod -l app=payment-postgres -n foodies --timeout=120s || echo_warn "payment-postgres not ready yet"
    kubectl wait --for=condition=ready pod -l app=redis -n foodies --timeout=120s || echo_warn "redis not ready yet"

    # Wait for infrastructure
    echo_info "Waiting for infrastructure..."
    kubectl wait --for=condition=ready pod -l app=rabbitmq -n foodies --timeout=180s || echo_warn "RabbitMQ not ready yet"
    kubectl wait --for=condition=ready pod -l app=keycloak -n foodies --timeout=180s || echo_warn "Keycloak not ready yet"

    # Wait for services
    echo_info "Waiting for application services..."
    kubectl wait --for=condition=ready pod -l app=webapp -n foodies --timeout=180s || echo_warn "webapp not ready yet"
    kubectl wait --for=condition=ready pod -l app=menu -n foodies --timeout=180s || echo_warn "menu not ready yet"
    kubectl wait --for=condition=ready pod -l app=profile -n foodies --timeout=180s || echo_warn "profile not ready yet"
    kubectl wait --for=condition=ready pod -l app=basket -n foodies --timeout=180s || echo_warn "basket not ready yet"
    kubectl wait --for=condition=ready pod -l app=order -n foodies --timeout=180s || echo_warn "order not ready yet"
    kubectl wait --for=condition=ready pod -l app=payment -n foodies --timeout=180s || echo_warn "payment not ready yet"
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
    echo_info "Deployed image versions:"
    kubectl get deployments -n foodies -o custom-columns=NAME:.metadata.name,IMAGE:.spec.template.spec.containers[0].image
    echo ""
    echo_info "Access the application:"
    echo "  - Using Ingress: http://foodies.local"
    echo ""
    echo_info "Make sure you have 'foodies.local' in /etc/hosts pointing to 127.0.0.1"
}

# Main deployment flow
main() {
    echo_info "Starting local Kubernetes deployment with Kustomize..."
    echo ""

    check_prerequisites

    # Read and increment version
    echo_info "Managing version..."
    local current_version=$(read_version)
    echo_info "Current version: $current_version"

    local new_version=$(increment_version $current_version)
    echo_info "New version: $new_version"

    write_version $new_version

    # Build images with new version
    build_project_and_publish_images $new_version

    # Update Kustomize configuration with new version
    update_kustomize_versions $new_version

    # Clean up existing deployment
    cleanup_namespace

    # Deploy everything with Kustomize
    deploy_with_kustomize

    echo ""
    show_status

    echo ""
    echo_info "Deployment script completed successfully!"
    echo_info "Deployed version: $new_version"
}

# Run main function
main
