#!/usr/bin/env bash
# make eval: starts the backend in mock AI profile on a throwaway database, runs the whole eval suite, stops the backend.
# Set EVAL_BASE_URL to run against an already running backend instead (nothing is started then).
# Extra arguments are passed to the runner, e.g. `bash evals/run_mock.sh --thresholds my.yaml`.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
if [ -z "${PYTHON:-}" ]; then
  for candidate in python3 python; do
    if "$candidate" -c "import sys; sys.exit(0 if sys.version_info >= (3, 11) else 1)" >/dev/null 2>&1; then PYTHON="$candidate"; break; fi
  done
fi
PYTHON="${PYTHON:?Python 3.11+ is required}"

if [ -n "${EVAL_BASE_URL:-}" ]; then
  exec "$PYTHON" -m evals run --base-url "$EVAL_BASE_URL" "$@"
fi

PORT="${EVAL_PORT:-8081}"
DB_NAME="${EVAL_DB:-flowpanel_eval}"
DB_HOST_URL="${EVAL_DATABASE_URL:-}"

cd "$ROOT"
if [ -z "$DB_HOST_URL" ]; then
  docker compose up -d --wait postgres >/dev/null
  docker compose exec -T postgres psql -U flowpanel -d postgres -qc "DROP DATABASE IF EXISTS $DB_NAME WITH (FORCE)" >/dev/null
  docker compose exec -T postgres psql -U flowpanel -d postgres -qc "CREATE DATABASE $DB_NAME" >/dev/null
  DB_HOST_URL="jdbc:postgresql://localhost:5433/$DB_NAME"
fi

JAR="$ROOT/backend/target/flowpanel-backend.jar"
if [ ! -f "$JAR" ] || [ -n "$(find "$ROOT/backend/src" -newer "$JAR" -type f -print -quit)" ]; then
  (cd backend && ./mvnw -B -ntp -q package -DskipTests)
fi
mkdir -p "$ROOT/.run"
cp "$JAR" "$ROOT/.run/eval-backend.jar"

AI_PROFILE=mock DATABASE_URL="$DB_HOST_URL" PORT="$PORT" JWT_SECRET="eval-only-jwt-secret-0123456789-abcdefghijkl" \
  java -XX:TieredStopAtLevel=1 -jar "$ROOT/.run/eval-backend.jar" --spring.profiles.active=ci \
  > "$ROOT/.run/eval-backend.log" 2>&1 &
BACKEND_PID=$!
trap 'kill $BACKEND_PID 2>/dev/null || true' EXIT

for _ in $(seq 1 120); do
  if curl -sf "http://localhost:$PORT/actuator/health" >/dev/null; then break; fi
  if ! kill -0 $BACKEND_PID 2>/dev/null; then echo "backend failed to start:"; tail -40 "$ROOT/.run/eval-backend.log"; exit 2; fi
  sleep 1
done

set +e
"$PYTHON" -m evals run --base-url "http://localhost:$PORT" "$@"
STATUS=$?
set -e
exit $STATUS
