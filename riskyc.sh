#!/usr/bin/env bash
#
# Manages the riskyc-frontend and riskyc-backend Docker deployments.
#
# Expects the layout shown by `ls ~/riskyc`:
#   ~/riskyc/riskyc-backend/   (this script lives here, or is copied to ~/riskyc/)
#   ~/riskyc/riskyc-frontend/
#
# Usage:
#   ./riskyc.sh start [all|frontend|backend]        start containers (build image if missing)
#   ./riskyc.sh stop [all|frontend|backend]          stop containers, keep them + their data
#   ./riskyc.sh full-restart [all|frontend|backend]  stop, remove containers, rebuild images,
#                                                     recreate fresh — Postgres/MinIO DATA VOLUMES
#                                                     are preserved (only containers are recreated)
#   ./riskyc.sh restart <service...>                 restart specific container(s) in place,
#                                                     no rebuild — e.g. `restart backend`,
#                                                     `restart postgres minio`, `restart frontend`
#   ./riskyc.sh status                               show container status for both stacks
#   ./riskyc.sh logs <service> [-f]                  tail logs for one container
#   ./riskyc.sh wipe-data                            DESTRUCTIVE — also deletes the Postgres +
#                                                     MinIO volumes (asks for confirmation)
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# This script is meant to live at either ~/riskyc/riskyc-backend/riskyc.sh
# or ~/riskyc/riskyc.sh — resolve both repo paths relative to wherever it
# actually is, so it works from either location without editing anything.
if [ -f "$SCRIPT_DIR/docker-compose.yml" ]; then
  BACKEND_DIR="$SCRIPT_DIR"
  FRONTEND_DIR="$SCRIPT_DIR/../riskyc-frontend"
else
  BACKEND_DIR="$SCRIPT_DIR/riskyc-backend"
  FRONTEND_DIR="$SCRIPT_DIR/riskyc-frontend"
fi

BACKEND_SERVICES=(postgres minio backend)
FRONTEND_SERVICES=(frontend)

log()  { echo -e "\033[1;36m[riskyc]\033[0m $*"; }
warn() { echo -e "\033[1;33m[riskyc]\033[0m $*"; }
err()  { echo -e "\033[1;31m[riskyc]\033[0m $*" >&2; }

check_dirs() {
  [ -d "$BACKEND_DIR" ] || { err "Backend dir not found: $BACKEND_DIR"; exit 1; }
  [ -d "$FRONTEND_DIR" ] || { err "Frontend dir not found: $FRONTEND_DIR"; exit 1; }
  [ -f "$BACKEND_DIR/.env" ] || warn "No .env in $BACKEND_DIR — using defaults from .env.example. Run: cp $BACKEND_DIR/.env.example $BACKEND_DIR/.env"
  [ -f "$FRONTEND_DIR/.env" ] || warn "No .env in $FRONTEND_DIR — using defaults from .env.example. Run: cp $FRONTEND_DIR/.env.example $FRONTEND_DIR/.env"
}

compose_backend()  { (cd "$BACKEND_DIR"  && docker compose "$@"); }
compose_frontend() { (cd "$FRONTEND_DIR" && docker compose "$@"); }

# Which stack a given service name belongs to.
dir_for_service() {
  local svc="$1"
  for s in "${BACKEND_SERVICES[@]}"; do [ "$s" = "$svc" ] && { echo "$BACKEND_DIR"; return; }; done
  for s in "${FRONTEND_SERVICES[@]}"; do [ "$s" = "$svc" ] && { echo "$FRONTEND_DIR"; return; }; done
  err "Unknown service '$svc'. Known services: ${BACKEND_SERVICES[*]} ${FRONTEND_SERVICES[*]}"
  exit 1
}

cmd_start() {
  local target="${1:-all}"
  check_dirs
  case "$target" in
    all)
      log "Starting backend (postgres, minio, backend)..."
      compose_backend up -d
      log "Starting frontend..."
      compose_frontend up -d
      ;;
    backend)  log "Starting backend..."; compose_backend up -d ;;
    frontend) log "Starting frontend..."; compose_frontend up -d ;;
    *) err "Usage: $0 start [all|frontend|backend]"; exit 1 ;;
  esac
  log "Done. Check with: $0 status"
}

cmd_stop() {
  local target="${1:-all}"
  case "$target" in
    all)
      log "Stopping frontend..."
      compose_frontend stop
      log "Stopping backend..."
      compose_backend stop
      ;;
    backend)  log "Stopping backend..."; compose_backend stop ;;
    frontend) log "Stopping frontend..."; compose_frontend stop ;;
    *) err "Usage: $0 stop [all|frontend|backend]"; exit 1 ;;
  esac
  log "Stopped. Containers and data are preserved — use '$0 start' to bring them back."
}

cmd_full_restart() {
  local target="${1:-all}"
  check_dirs
  restart_backend() {
    log "Backend: stopping and removing containers (data volumes preserved)..."
    compose_backend down --remove-orphans
    log "Backend: rebuilding image..."
    compose_backend build
    log "Backend: recreating containers..."
    compose_backend up -d
  }
  restart_frontend() {
    log "Frontend: stopping and removing container..."
    compose_frontend down --remove-orphans
    log "Frontend: rebuilding image..."
    compose_frontend build
    log "Frontend: recreating container..."
    compose_frontend up -d
  }
  case "$target" in
    all)      restart_backend; restart_frontend ;;
    backend)  restart_backend ;;
    frontend) restart_frontend ;;
    *) err "Usage: $0 full-restart [all|frontend|backend]"; exit 1 ;;
  esac
  log "Full restart complete. Check with: $0 status"
}

cmd_restart() {
  if [ "$#" -eq 0 ]; then
    err "Usage: $0 restart <service...>  (e.g. $0 restart backend, or $0 restart postgres minio)"
    exit 1
  fi
  for svc in "$@"; do
    local dir; dir="$(dir_for_service "$svc")"
    log "Restarting '$svc'..."
    (cd "$dir" && docker compose restart "$svc")
  done
  log "Done."
}

cmd_status() {
  echo "── Backend ──────────────────────────────"
  compose_backend ps
  echo
  echo "── Frontend ─────────────────────────────"
  compose_frontend ps
}

cmd_logs() {
  local svc="${1:-}"
  [ -z "$svc" ] && { err "Usage: $0 logs <service> [-f]"; exit 1; }
  shift || true
  local dir; dir="$(dir_for_service "$svc")"
  (cd "$dir" && docker compose logs --tail=200 "$@" "$svc")
}

cmd_wipe_data() {
  warn "This permanently deletes the Postgres database AND all MinIO-stored images/videos."
  read -r -p "Type 'DELETE' to confirm: " confirm
  if [ "$confirm" != "DELETE" ]; then
    log "Aborted — nothing was deleted."
    exit 0
  fi
  log "Stopping and wiping backend stack (containers + volumes)..."
  compose_backend down -v --remove-orphans
  log "Data wiped. Run '$0 start' to recreate everything from scratch."
}

case "${1:-}" in
  start)         shift; cmd_start "$@" ;;
  stop)          shift; cmd_stop "$@" ;;
  full-restart)  shift; cmd_full_restart "$@" ;;
  restart)       shift; cmd_restart "$@" ;;
  status)        cmd_status ;;
  logs)          shift; cmd_logs "$@" ;;
  wipe-data)     cmd_wipe_data ;;
  *)
    cat <<EOF
Usage: $0 <command> [args]

  start [all|frontend|backend]        Start containers (default: all)
  stop [all|frontend|backend]         Stop containers, keep data (default: all)
  full-restart [all|frontend|backend] Full teardown + rebuild + recreate (data volumes kept)
  restart <service...>                Restart specific container(s) in place, no rebuild
                                       services: ${BACKEND_SERVICES[*]} ${FRONTEND_SERVICES[*]}
  status                              Show container status
  logs <service> [-f]                 Tail logs for one container
  wipe-data                           DESTRUCTIVE — also deletes Postgres + MinIO data
EOF
    exit 1
    ;;
esac
