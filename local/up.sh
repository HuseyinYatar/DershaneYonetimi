#!/usr/bin/env bash
# Brings up the whole local backend (dependencies + all services + gateway) with docker compose.
#   local/up.sh            start what is not running (images are built only if missing), wait until ready
#   local/up.sh --build    rebuild the service images first (after code changes)
#   local/up.sh --seed     then load the demo data (local/seed/seed.mjs, needs Node 22+)
#   local/up.sh --reset    wipe volumes (databases, Floci, Terraform state) first
#   local/up.sh down       stop the containers (data kept)
# floci-init runs on every start: ~10 s no-op, since Floci persists to the floci-data volume. Only after
# --reset does it create every queue again (~25 s each, 2-3 min in total).
set -euo pipefail

cd "$(dirname "$0")"
compose() { docker compose -f docker-compose.yml --profile services "$@"; }

seed=false
build=()
for arg in "$@"; do
  case "$arg" in
    down) compose stop; exit 0 ;;
    --reset) compose down -v ;;
    --build) build=(--build) ;;
    --seed) seed=true ;;
    *) echo "Unknown argument: $arg" >&2; exit 2 ;;
  esac
done

compose up -d "${build[@]}"

# name -> readiness URL (Java: management port, Python: API port)
checks=(
  "user http://localhost:8081/health/readiness"
  "attendance http://localhost:8083/health/readiness"
  "task http://localhost:8085/health/readiness"
  "messaging http://localhost:8087/health/readiness"
  "exam-analysis-api http://localhost:8001/health/ready"
  "notification-api http://localhost:8003/health/ready"
  "api-gateway http://localhost:8002/health/readiness"
)
for check in "${checks[@]}"; do
  read -r name url <<<"$check"
  printf 'Waiting for %s' "$name"
  for _ in $(seq 1 120); do
    curl -fsS -o /dev/null "$url" 2>/dev/null && { echo " ok"; continue 2; }
    printf '.'; sleep 2
  done
  echo " not ready after 4 min; see: docker compose -f local/docker-compose.yml logs $name" >&2
  exit 1
done

if $seed; then node seed/seed.mjs; fi

cat <<'EOF'

Backend is up.
  Gateway   http://localhost:8000/api/v1/...
  Keycloak  http://localhost:8180  (admin / admin)
  Mailpit   http://localhost:8025
  Frontend  cd frontend && npm run dev  ->  http://localhost:3000
EOF
