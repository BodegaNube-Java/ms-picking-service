#!/usr/bin/env bash
# Administración del clúster Docker Swarm de BodegaNube.
#
# Uso:
#   ./scripts/swarm.sh init <ip-advertise>        Inicializa el manager (solo una vez)
#   ./scripts/swarm.sh token worker|manager        Muestra el comando para unir un nodo
#   ./scripts/swarm.sh deploy <imagen:tag>         Despliega/actualiza el stack
#   ./scripts/swarm.sh scale <n>                   Ajusta las réplicas de ms-picking
#   ./scripts/swarm.sh status                      Muestra nodos, servicios y tareas
#   ./scripts/swarm.sh rollback                    Vuelve a la versión anterior del servicio

set -euo pipefail

STACK="bodeganube"
SERVICE="${STACK}_ms-picking"
COMPOSE_FILE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/docker-compose.yml"

usage() {
  sed -n '2,11p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
  exit 1
}

require_manager() {
  local state
  state="$(docker info --format '{{.Swarm.LocalNodeState}} {{.Swarm.ControlAvailable}}')"
  if [[ "${state}" != "active true" ]]; then
    echo "Error: este nodo no es un manager activo de Swarm (estado: ${state})." >&2
    exit 1
  fi
}

cmd_init() {
  local advertise="${1:?Falta la IP de advertise, ej: ./scripts/swarm.sh init 10.0.1.10}"
  docker swarm init --advertise-addr "${advertise}"
  echo "Manager inicializado. Siguiente paso: ./scripts/swarm.sh token worker"
}

cmd_token() {
  local role="${1:?Indica worker o manager}"
  require_manager
  case "${role}" in
    worker|manager) docker swarm join-token "${role}" ;;
    *) echo "Rol inválido: ${role} (usa worker o manager)" >&2; exit 1 ;;
  esac
}

cmd_deploy() {
  local image="${1:?Falta la imagen, ej: ghcr.io/bodeganube-java/ms-picking-service:1.0.0}"
  require_manager
  PICKING_IMAGE="${image}" docker stack deploy \
    --with-registry-auth \
    --prune \
    -c "${COMPOSE_FILE}" "${STACK}"
  echo "Stack ${STACK} desplegado con ${image}"
}

cmd_scale() {
  local n="${1:?Indica el número de réplicas}"
  if ! [[ "${n}" =~ ^[0-9]+$ ]] || (( n < 1 )); then
    echo "El número de réplicas debe ser un entero >= 1" >&2
    exit 1
  fi
  require_manager
  docker service scale "${SERVICE}=${n}"
  docker service ps "${SERVICE}" --filter "desired-state=running"
}

cmd_rollback() {
  require_manager
  docker service rollback "${SERVICE}"
}

cmd_status() {
  require_manager
  echo "== Nodos";     docker node ls
  echo "== Servicios"; docker stack services "${STACK}"
  echo "== Tareas";    docker stack ps "${STACK}"
}

main() {
  local sub="${1:-}"
  shift || true
  case "${sub}" in
    init)     cmd_init "$@" ;;
    token)    cmd_token "$@" ;;
    deploy)   cmd_deploy "$@" ;;
    scale)    cmd_scale "$@" ;;
    rollback) cmd_rollback ;;
    status)   cmd_status ;;
    *)        usage ;;
  esac
}

main "$@"
