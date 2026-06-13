#!/usr/bin/env sh

set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
COMPOSE_FILE="$ROOT_DIR/docker-compose.local-infra.yml"
ACTION="${1:-up}"

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker bulunamadi. Docker Desktop'i kurup calistirin." >&2
  exit 1
fi

if ! docker compose version >/dev/null 2>&1; then
  echo "'docker compose' kullanilamiyor. Docker Compose eklentisini kurun." >&2
  exit 1
fi

compose() {
  docker compose --project-directory "$ROOT_DIR" -f "$COMPOSE_FILE" "$@"
}

case "$ACTION" in
  up|start)
    compose up -d
    echo ""
    echo "Local Docker altyapisi baslatildi:"
    echo "  PostgreSQL:     localhost:5433 (appdb)"
    echo "  Keycloak:       http://localhost:8081"
    echo "  Keycloak admin: http://localhost:8081/admin"
    echo ""
    echo "Backend ve frontend Docker'da baslatilmadi."
    echo "  Backend:  cd backend && SPRING_PROFILES_ACTIVE=local mvn spring-boot:run"
    echo "  Frontend: cd frontend && npm start"
    echo ""
    echo "Loglar icin: ./local.sh logs"
    ;;
  down|stop)
    compose down
    ;;
  restart)
    compose down
    compose up -d
    ;;
  logs)
    shift || true
    compose logs -f --tail=100 "$@"
    ;;
  status|ps)
    compose ps
    ;;
  *)
    echo "Kullanim: ./local.sh {up|down|restart|logs|status}" >&2
    exit 1
    ;;
esac
