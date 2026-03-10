#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/lib.sh"

usage() {
  cat <<EOF_USAGE
Usage: ./scripts/kind/foodies-kind.sh <command>

Commands:
  doctor   Validate required dependencies and local environment
  up       Create/update Kind cluster and deploy Foodies stack
  reset    Recreate Kind cluster and deploy Foodies stack
  status   Show Kind and Foodies deployment status
  down     Delete the Kind cluster
EOF_USAGE
}

run_rollout() {
  trap print_failure_diagnostics ERR

  ensure_kind_cluster
  install_ingress_nginx
  install_cert_manager
  install_rabbitmq_operators
  build_all_foodies_images
  load_foodies_images_into_kind
  deploy_foodies_overlay
  wait_for_foodies_stack
  print_access_summary

  trap - ERR
}

command_doctor() {
  doctor_checks
  log "Doctor checks passed"
}

command_up() {
  doctor_checks
  run_rollout
}

command_reset() {
  doctor_checks
  delete_kind_cluster_if_exists
  run_rollout
}

command_status() {
  require_command kind
  require_command kubectl
  print_status
}

command_down() {
  require_command kind
  delete_kind_cluster_if_exists
}

main() {
  local command="${1:-}"

  case "${command}" in
    doctor)
      command_doctor
      ;;
    up)
      command_up
      ;;
    reset)
      command_reset
      ;;
    status)
      command_status
      ;;
    down)
      command_down
      ;;
    -h|--help|help|"")
      usage
      ;;
    *)
      usage
      fail "Unknown command: ${command}"
      ;;
  esac
}

main "$@"
