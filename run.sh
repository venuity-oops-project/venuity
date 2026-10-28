#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAVA_DIR="$SCRIPT_DIR/java"
ENV_FILE="$JAVA_DIR/.env"
EXAMPLE_FILE="$JAVA_DIR/.env.example"
JAR_PATH="$JAVA_DIR/venuity-server/target/venuity.jar"

echo "[Venuity] Checking environment..."

# 1. Ensure .env exists
if [ ! -f "$ENV_FILE" ]; then
    echo "[Venuity] Creating $ENV_FILE from .env.example with local defaults..."
    cp "$EXAMPLE_FILE" "$ENV_FILE"
    RANDOM_SECRET=$(head -c 32 /dev/urandom | base64)
    sed -i.bak \
        -e 's/replace-with-strong-root-password/local-root-only-9f3a/g' \
        -e 's/replace-with-strong-app-password/local-app-only-7b2c/g' \
        -e "s|replace-with-at-least-32-random-bytes|$RANDOM_SECRET|g" \
        -e 's/replace-with-a-strong-admin-password/admin/g' \
        "$ENV_FILE" && rm -f "$ENV_FILE.bak"
fi

# 2. Export environment variables
set -a
# shellcheck disable=SC1090
. "$ENV_FILE"
set +a

# 3. Check Java 25
if ! command -v java >/dev/null 2>&1; then
    echo "Error: java command not found on PATH. Java 25 is required." >&2
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | head -n 1)
if [[ ! "$JAVA_VERSION" =~ version\ \"25 ]]; then
    echo "Error: Java 25 is required. Detected: $JAVA_VERSION" >&2
    exit 1
fi

# 4. Check Docker / MySQL
if command -v docker >/dev/null 2>&1; then
    echo "[Venuity] Starting / checking MySQL database container..."
    (cd "$JAVA_DIR" && docker compose up -d --wait mysql) || true
else
    echo "Warning: docker command not found. Assuming MySQL is running locally on port 3306."
fi

# 5. Build if needed
if [ ! -f "$JAR_PATH" ]; then
    echo "[Venuity] Packaging single JAR (venuity.jar)..."
    (cd "$JAVA_DIR" && ./mvnw -B -ntp -pl venuity-server -am -DskipTests package)
fi

cp -f "$JAR_PATH" "$SCRIPT_DIR/venuity.jar" 2>/dev/null || true

PORT="${SERVER_PORT:-8080}"
echo ""
echo "================================================================="
echo "   VENUITY - PlayArea Manager (Java 25 Single-JAR Application)   "
echo "================================================================="
echo "  -> Application URL:   http://localhost:$PORT"
echo "  -> Swagger / OpenAPI: http://localhost:$PORT/swagger-ui.html"
echo "  -> Health Endpoint:   http://localhost:$PORT/api/health"
echo "  -> Default Admin:     admin / admin"
echo "  -> Single JAR File:   $JAR_PATH"
echo "================================================================="
echo "Press Ctrl+C to stop the application."
echo ""

exec java -jar "$JAR_PATH"

