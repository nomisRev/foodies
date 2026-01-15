#!/usr/bin/env bash

set -e

echo "==> Building all Docker images..."
./gradlew publishImageToLocalRegistry

echo "==> Applying Kubernetes manifests..."
kubectl apply -k k8s/overlays/dev

echo "==> Restarting all deployments (forces fresh image pull)..."
kubectl rollout restart deployment -n foodies

echo "==> Waiting for rollout to complete..."
kubectl rollout status deployment -n foodies --timeout=120s

echo ""
echo "==> Deployment complete!"
echo "==> Access at: http://foodies.local"
echo ""
kubectl get pods -n foodies
