#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
test_name="gymfort-auth-browser-$$"
log="$(mktemp)"
backend_pid=""
cleanup() {
  if [[ -n "$backend_pid" ]]; then kill -- "-$backend_pid" 2>/dev/null || true; fi
  docker rm -f "$test_name-postgres" "$test_name-redis" >/dev/null 2>&1 || true
  rm -f "$log"
}
trap cleanup EXIT

docker run --rm -d --name "$test_name-postgres" -p 127.0.0.1::5432 \
  -e POSTGRES_DB=gymos -e POSTGRES_USER=gymos -e POSTGRES_PASSWORD=gymos postgres:18-alpine >/dev/null
docker run --rm -d --name "$test_name-redis" -p 127.0.0.1::6379 redis:8-alpine >/dev/null

postgres_binding="$(docker port "$test_name-postgres" 5432/tcp)"
redis_binding="$(docker port "$test_name-redis" 6379/tcp)"
export GYMOS_DB_URL="jdbc:postgresql://127.0.0.1:${postgres_binding##*:}/gymos"
export GYMOS_REDIS_HOST=127.0.0.1
export GYMOS_REDIS_PORT="${redis_binding##*:}"
PLAYWRIGHT_AUTH_PORT="$(node -e 'const socket = require("node:net").createServer(); socket.listen(0, "localhost", () => { console.log(socket.address().port); socket.close(); });')"
export PLAYWRIGHT_AUTH_PORT
export GYMOS_CORS_ALLOWED_ORIGINS="http://localhost:$PLAYWRIGHT_AUTH_PORT"
export GYMOS_SEED_ENABLED=true
export GYMOS_ACCESS_TOKEN_SECONDS=12
export GYMOS_DEMO_OWNER_EMAIL=auth-browser@example.test
export GYMOS_DEMO_OWNER_PASSWORD='AuthBrowserFixture!2026'
export SPRING_PROFILES_ACTIVE=local
export PORT=0

for attempt in {1..30}; do
  if docker exec "$test_name-postgres" pg_isready -h 127.0.0.1 -U gymos -d gymos >/dev/null; then break; fi
  sleep 1
done
(cd backend && chmod +x gradlew && exec setsid ./gradlew bootRun --no-daemon --console=plain) >"$log" 2>&1 &
backend_pid=$!
for attempt in {1..180}; do
  backend_port="$(sed -n 's/.*Tomcat started on port \([0-9][0-9]*\) .*/\1/p' "$log" | tail -n 1)"
  if [[ -n "$backend_port" ]] && curl --fail --silent "http://localhost:$backend_port/api/v1/system/info" >/dev/null; then
    export NEXT_PUBLIC_API_BASE_URL="http://localhost:$backend_port/api/v1"
    cd frontend
    npx playwright test --config=playwright.auth.config.ts
    exit
  fi
  if ! kill -0 "$backend_pid" 2>/dev/null; then break; fi
  sleep 2
done
tail -n 80 "$log"
exit 1
