#!/bin/bash
# OryxOS Server Start Script
# Usage: ./bin/start.sh [port] [--with-manager]
#
# Starts the OryxOS server (Spring Boot) and optionally the admin manager UI.
# Port defaults to 8080 if not specified.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
PORT="${1:-8080}"
WITH_MANAGER=false

# Parse arguments
for arg in "$@"; do
    case "$arg" in
        --with-manager) WITH_MANAGER=true ;;
    esac
done

PID_DIR="$PROJECT_DIR/.pids"
LOG_DIR="$PROJECT_DIR/logs"
CONFIG_DIR="$PROJECT_DIR/config"

# Determine JAR file
VERSION="${ORYXOS_VERSION:-1.0.0-SNAPSHOT}"
JAR_FILE="$PROJECT_DIR/oryxos-boot/target/oryxos-boot-${VERSION}.jar"

# Create directories
mkdir -p "$PID_DIR" "$LOG_DIR"

echo "=========================================="
echo " OryxOS Server Start"
echo " Port: $PORT"
echo "=========================================="
echo ""

# Build if JAR not found
if [ ! -f "$JAR_FILE" ]; then
    echo "[0/2] JAR not found, building project..."
    cd "$PROJECT_DIR"
    mvn clean package -DskipTests -Drevision="$VERSION"
    echo "  ✓ Build complete"
    echo ""
fi

# Verify JAR
if [ ! -f "$JAR_FILE" ]; then
    echo "✗ JAR not found: $JAR_FILE"
    echo "  Run 'mvn clean package -DskipTests' first."
    exit 1
fi

# Build classpath with optional external config
CLASSPATH="$JAR_FILE"
if [ -d "$CONFIG_DIR" ]; then
    CLASSPATH="$CONFIG_DIR:$CLASSPATH"
fi

# Start OryxOS server
echo "[1/2] Starting OryxOS server on port $PORT..."
SERVER_PORT="$PORT" \
ORYXOS_ROOT="${ORYXOS_ROOT:-.oryxos}" \
nohup java -jar "$JAR_FILE" serve --port "$PORT" \
    > "$LOG_DIR/server.log" 2>&1 &
SERVER_PID=$!
echo "$SERVER_PID" > "$PID_DIR/server.pid"
echo "  ✓ Server started (PID: $SERVER_PID)"
echo "  → REST API: http://localhost:$PORT/api/v1/health"
echo "  → Admin UI: http://localhost:$PORT/admin/"
echo ""

# Optionally start manager (VitePress dev server)
if [ "$WITH_MANAGER" = true ] && [ -d "$PROJECT_DIR/website" ]; then
    echo "[2/2] Starting manager dev server..."
    cd "$PROJECT_DIR/website"
    if [ -f "package.json" ]; then
        npm install --silent 2>/dev/null || true
        nohup npm run dev -- --port 5173 \
            > "$LOG_DIR/manager.log" 2>&1 &
        MANAGER_PID=$!
        echo "$MANAGER_PID" > "$PID_DIR/manager.pid"
        echo "  ✓ Manager started (PID: $MANAGER_PID)"
        echo "  → Manager dev: http://localhost:5173/admin/"
    else
        echo "  ⚠ website/package.json not found, skipping"
    fi
    cd "$PROJECT_DIR"
    echo ""
fi

echo "=========================================="
echo " OryxOS is running!"
echo ""
echo " Logs:"
echo "   Server:  tail -f $LOG_DIR/server.log"
if [ "$WITH_MANAGER" = true ]; then
    echo "   Manager: tail -f $LOG_DIR/manager.log"
fi
echo ""
echo " Stop: ./bin/stop.sh"
echo "=========================================="
