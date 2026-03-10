#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

FOODIES_KIND_CLUSTER="${FOODIES_KIND_CLUSTER:-foodies}"
FOODIES_NAMESPACE="${FOODIES_NAMESPACE:-foodies}"
FOODIES_WAIT_TIMEOUT="${FOODIES_WAIT_TIMEOUT:-600s}"
FOODIES_KIND_CONFIG="${FOODIES_KIND_CONFIG:-${SCRIPT_DIR}/kind.config.yaml}"
FOODIES_OVERLAY_PATH="${FOODIES_OVERLAY_PATH:-${REPO_ROOT}/k8s/overlays/dev}"
FOODIES_INGRESS_KUSTOMIZATION="${FOODIES_INGRESS_KUSTOMIZATION:-${REPO_ROOT}/k8s/base/ingress-nginx}"
FOODIES_CERT_MANAGER_KUSTOMIZATION="${FOODIES_CERT_MANAGER_KUSTOMIZATION:-${REPO_ROOT}/k8s/base/cert-manager}"
FOODIES_RABBITMQ_OPERATOR_KUSTOMIZATION="${FOODIES_RABBITMQ_OPERATOR_KUSTOMIZATION:-${REPO_ROOT}/k8s/base/rabbitmq-operator}"
KUBECTL_CONTEXT="kind-${FOODIES_KIND_CLUSTER}"

log() {
  printf '[%s] %s\n' "$(date +'%H:%M:%S')" "$*"
}

warn() {
  printf '[%s] WARN: %s\n' "$(date +'%H:%M:%S')" "$*" >&2
}

fail() {
  printf '[%s] ERROR: %s\n' "$(date +'%H:%M:%S')" "$*" >&2
  exit 1
}

run() {
  log "$*"
  "$@"
}

require_command() {
  local command_name="$1"
  command -v "$command_name" >/dev/null 2>&1 || fail "Missing required command: ${command_name}"
}

require_path_exists() {
  local path="$1"
  [[ -e "$path" ]] || fail "Required path not found: ${path}"
}

doctor_checks() {
  require_command kind
  require_command kubectl
  require_command docker
  require_command awk
  require_command grep
  require_command sort

  require_path_exists "${REPO_ROOT}/gradlew"
  require_path_exists "${FOODIES_KIND_CONFIG}"
  require_path_exists "${FOODIES_OVERLAY_PATH}"
  require_path_exists "${FOODIES_INGRESS_KUSTOMIZATION}"
  require_path_exists "${FOODIES_CERT_MANAGER_KUSTOMIZATION}"
  require_path_exists "${FOODIES_RABBITMQ_OPERATOR_KUSTOMIZATION}"

  [[ -x "${REPO_ROOT}/gradlew" ]] || fail "${REPO_ROOT}/gradlew is not executable"

  docker info >/dev/null 2>&1 || fail "Docker daemon is not reachable"

  if ! grep -Eq '(^|[[:space:]])foodies\.local([[:space:]]|$)' /etc/hosts; then
    warn "foodies.local is not present in /etc/hosts"
  fi
}

kind_cluster_exists() {
  kind get clusters 2>/dev/null | grep -Fxq "${FOODIES_KIND_CLUSTER}"
}

assert_kind_cluster_exists() {
  kind_cluster_exists || fail "Kind cluster '${FOODIES_KIND_CLUSTER}' does not exist"
}

ensure_kind_cluster() {
  if kind_cluster_exists; then
    log "Kind cluster '${FOODIES_KIND_CLUSTER}' already exists"
  else
    run kind create cluster --name "${FOODIES_KIND_CLUSTER}" --config "${FOODIES_KIND_CONFIG}"
  fi

  kubectl config get-contexts -o name | grep -Fxq "${KUBECTL_CONTEXT}" || fail "kubectl context '${KUBECTL_CONTEXT}' was not created"
}

delete_kind_cluster_if_exists() {
  if kind_cluster_exists; then
    run kind delete cluster --name "${FOODIES_KIND_CLUSTER}"
  else
    log "Kind cluster '${FOODIES_KIND_CLUSTER}' does not exist"
  fi
}

kc() {
  kubectl --context "${KUBECTL_CONTEXT}" "$@"
}

wait_for_deployment() {
  local namespace="$1"
  local name="$2"
  run kc rollout status "deployment/${name}" -n "${namespace}" --timeout "${FOODIES_WAIT_TIMEOUT}"
}

wait_for_statefulset() {
  local namespace="$1"
  local name="$2"
  run kc rollout status "statefulset/${name}" -n "${namespace}" --timeout "${FOODIES_WAIT_TIMEOUT}"
}

wait_for_job() {
  local namespace="$1"
  local name="$2"
  run kc wait --for=condition=complete "job/${name}" -n "${namespace}" --timeout "${FOODIES_WAIT_TIMEOUT}"
}

wait_for_crd() {
  local name="$1"
  run kubectl --context "${KUBECTL_CONTEXT}" wait --for=condition=Established "crd/${name}" --timeout "${FOODIES_WAIT_TIMEOUT}"
}

install_ingress_nginx() {
  run kc apply -k "${FOODIES_INGRESS_KUSTOMIZATION}"
  wait_for_deployment ingress-nginx ingress-nginx-controller
}

install_cert_manager() {
  run kc apply -k "${FOODIES_CERT_MANAGER_KUSTOMIZATION}"
  wait_for_deployment cert-manager cert-manager
  wait_for_deployment cert-manager cert-manager-cainjector
  wait_for_deployment cert-manager cert-manager-webhook
}

install_rabbitmq_operators() {
  run kc apply -k "${FOODIES_RABBITMQ_OPERATOR_KUSTOMIZATION}"
  wait_for_deployment rabbitmq-system rabbitmq-cluster-operator
  wait_for_deployment rabbitmq-system messaging-topology-operator

  wait_for_crd rabbitmqclusters.rabbitmq.com
  wait_for_crd users.rabbitmq.com
  wait_for_crd permissions.rabbitmq.com
  wait_for_crd queues.rabbitmq.com
  wait_for_crd exchanges.rabbitmq.com
  wait_for_crd bindings.rabbitmq.com
}

build_all_foodies_images() {
  run "${REPO_ROOT}/gradlew" publishImageToLocalRegistry
}

collect_foodies_images() {
  kubectl kustomize "${FOODIES_OVERLAY_PATH}" |
    awk '/^[[:space:]]*image:[[:space:]]+foodies-/ { print $2 }' |
    sort -u
}

load_foodies_images_into_kind() {
  mapfile -t images < <(collect_foodies_images)

  [[ "${#images[@]}" -gt 0 ]] || fail "No foodies images found in ${FOODIES_OVERLAY_PATH}"

  for image in "${images[@]}"; do
    run kind load docker-image "${image}" --name "${FOODIES_KIND_CLUSTER}"
  done
}

deploy_foodies_overlay() {
  run kc apply -k "${FOODIES_OVERLAY_PATH}"
}

wait_for_rabbitmq() {
  if kc rollout status statefulset/rabbitmq-server -n "${FOODIES_NAMESPACE}" --timeout "${FOODIES_WAIT_TIMEOUT}" >/dev/null 2>&1; then
    log "RabbitMQ statefulset is ready"
    return 0
  fi

  run kc wait --for=condition=ready pod -l app.kubernetes.io/name=rabbitmq -n "${FOODIES_NAMESPACE}" --timeout "${FOODIES_WAIT_TIMEOUT}"
}

wait_for_foodies_stack() {
  wait_for_statefulset "${FOODIES_NAMESPACE}" keycloak-postgres
  wait_for_statefulset "${FOODIES_NAMESPACE}" menu-postgres
  wait_for_statefulset "${FOODIES_NAMESPACE}" profile-postgres
  wait_for_statefulset "${FOODIES_NAMESPACE}" order-postgres
  wait_for_statefulset "${FOODIES_NAMESPACE}" payment-postgres
  wait_for_rabbitmq

  wait_for_deployment "${FOODIES_NAMESPACE}" redis
  wait_for_deployment "${FOODIES_NAMESPACE}" keycloak
  wait_for_deployment "${FOODIES_NAMESPACE}" otel-collector
  wait_for_deployment "${FOODIES_NAMESPACE}" jaeger
  wait_for_deployment "${FOODIES_NAMESPACE}" prometheus
  wait_for_deployment "${FOODIES_NAMESPACE}" webapp
  wait_for_deployment "${FOODIES_NAMESPACE}" menu
  wait_for_deployment "${FOODIES_NAMESPACE}" profile
  wait_for_deployment "${FOODIES_NAMESPACE}" basket
  wait_for_deployment "${FOODIES_NAMESPACE}" order
  wait_for_deployment "${FOODIES_NAMESPACE}" payment

  if kc get job keycloak-config-cli -n "${FOODIES_NAMESPACE}" >/dev/null 2>&1; then
    wait_for_job "${FOODIES_NAMESPACE}" keycloak-config-cli
  else
    warn "keycloak-config-cli job not found; it may have already been garbage-collected"
  fi
}

print_access_summary() {
  cat <<EOF_SUMMARY

Foodies stack is ready.
- Webapp: http://foodies.local
- Keycloak: http://foodies.local/auth
- Jaeger: http://foodies.local/jaeger
- Prometheus: http://foodies.local/prometheus

Useful commands:
- kubectl --context ${KUBECTL_CONTEXT} get pods -n ${FOODIES_NAMESPACE}
- kubectl --context ${KUBECTL_CONTEXT} get ingress -n ${FOODIES_NAMESPACE}
- kubectl --context ${KUBECTL_CONTEXT} logs -n ${FOODIES_NAMESPACE} -l app=webapp --tail=100
EOF_SUMMARY
}

print_failure_diagnostics() {
  warn "Rollout failed. Collecting diagnostics"
  kubectl --context "${KUBECTL_CONTEXT}" get pods -A || true
  kubectl --context "${KUBECTL_CONTEXT}" get events -n "${FOODIES_NAMESPACE}" --sort-by=.metadata.creationTimestamp | tail -n 40 || true
  kubectl --context "${KUBECTL_CONTEXT}" get ingress -n "${FOODIES_NAMESPACE}" || true
}

print_status() {
  assert_kind_cluster_exists

  run kubectl --context "${KUBECTL_CONTEXT}" get nodes
  run kubectl --context "${KUBECTL_CONTEXT}" get deployments -n ingress-nginx
  run kubectl --context "${KUBECTL_CONTEXT}" get deployments -n cert-manager
  run kubectl --context "${KUBECTL_CONTEXT}" get deployments -n rabbitmq-system
  run kubectl --context "${KUBECTL_CONTEXT}" get pods -n "${FOODIES_NAMESPACE}"
  run kubectl --context "${KUBECTL_CONTEXT}" get svc -n "${FOODIES_NAMESPACE}"
  run kubectl --context "${KUBECTL_CONTEXT}" get ingress -n "${FOODIES_NAMESPACE}"
}
