# TODO: Implement OpenTelemetry Collector with Jaeger and Prometheus

## OpenTelemetry Collector Infrastructure

### 1. Create OpenTelemetry Collector Configuration
- [x] Create `k8s/base/otel-collector/` directory structure
- [x] Create ConfigMap with collector configuration (`collector-config.yaml`)
  - Configure receivers (OTLP gRPC on port 4317, OTLP HTTP on port 4318)
  - Configure processors (batch, memory_limiter)
  - Configure exporters (Jaeger, Prometheus)
  - Set up pipelines for traces and metrics

### 2. Deploy OpenTelemetry Collector
- [x] Create `k8s/base/otel-collector/deployment.yaml`
  - [x] Use official OpenTelemetry Collector image
  - [x] Configure resource limits and requests
  - [x] Mount ConfigMap as volume
  - [x] Expose ports 4317 (gRPC), 4318 (HTTP), 8888 (metrics), 8889 (Prometheus)
- [x] Create `k8s/base/otel-collector/service.yaml`
  - [x] Expose OTLP gRPC port 4317
  - [x] Expose OTLP HTTP port 4318
  - [x] Expose Prometheus metrics port 8889
- [x] Create `k8s/base/otel-collector/kustomization.yaml`

## Jaeger Deployment

### 3. Deploy Jaeger All-in-One
- [x] Create `k8s/base/jaeger/` directory structure
- [x] Create `k8s/base/jaeger/deployment.yaml`
  - [x] Use Jaeger all-in-one image
  - [x] Configure memory storage backend
  - [x] Expose necessary ports (16686 UI, 14250 gRPC)
- [x] Create `k8s/base/jaeger/service.yaml`
  - [x] Expose Jaeger UI port 16686
  - [x] Expose Jaeger collector gRPC port 14250
- [x] Create `k8s/base/jaeger/kustomization.yaml`
- [x] Add Jaeger UI to Ingress configuration for external access

## Prometheus Deployment

### 4. Deploy Prometheus
- [x] Create `k8s/base/prometheus/` directory structure
- [ ] Create `k8s/base/prometheus/configmap.yaml`
  - Configure scrape configs for OpenTelemetry Collector
  - Configure scrape configs for application metrics
  - Set retention and storage settings
- [ ] Create `k8s/base/prometheus/deployment.yaml`
  - Use official Prometheus image
  - Mount ConfigMap as configuration
  - Configure persistent volume for data
- [ ] Create `k8s/base/prometheus/service.yaml`
  - Expose Prometheus UI port 9090
- [x] Create `k8s/base/prometheus/kustomization.yaml`
- [ ] Add Prometheus UI to Ingress configuration for external access

## Integration

### 5. Update Kubernetes Configuration
- [x] Update `k8s/base/kustomization.yaml`
  - [x] Add otel-collector, jaeger, and prometheus to resources
  - [x] Verify OTEL_EXPORTER_OTLP_ENDPOINT points to `http://otel-collector:4317`

### 6. Verify Service Configuration
- [x] Ensure all services (basket, menu, order, payment, profile, webapp) have `OTEL_EXPORTER_OTLP_ENDPOINT` environment variable configured
- [x] Verify OpenTelemetry dependencies are included in Gradle version catalog

## Testing and Validation

### 7. Deploy and Test
- [ ] Run `./gradlew localDeployK8s` to deploy the cluster
- [ ] Verify all pods are running: `kubectl get pods -n foodies`
- [ ] Check OpenTelemetry Collector logs for successful startup
- [ ] Check Jaeger logs for successful startup
- [ ] Check Prometheus logs for successful startup
- [ ] Access Jaeger UI at `http://foodies.local/jaeger` to verify traces
- [ ] Access Prometheus UI at `http://foodies.local/prometheus` to verify metrics
- [ ] Generate traffic to services and verify traces appear in Jaeger
- [ ] Verify metrics are being scraped by Prometheus

## Documentation

### 8. Update Documentation
- [x] Document how to access Jaeger UI
- [x] Document how to access Prometheus UI
- [ ] Document how to query traces and metrics
- [ ] Add troubleshooting section for common issues
EOF