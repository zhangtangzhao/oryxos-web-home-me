#!/bin/bash
# OryxOS Server Stop Script
# Usage: ./bin/stop.sh
#
# Stops all running OryxOS processes (server and optional manager).

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
PID_DIR="$PROJECT_DIR/.pids"

echo "=========================================="
echo " OryxOS Server Stop"
echo "=========================================="
echo ""

STOPPED_ANY=false

# Stop server
if [ -f "$PID_DIR/server.pid" ]; then
    SERVER_PID=$(cat "$PID_DIR/server.pid")
    if kill -0 "$SERVER_PID" 2>/dev/null; then
        echo "[1/2] Stopping server (PID: $SERVER_PID)..."
        kill "$SERVER_PID" 2>/dev/null || true
        # Wait for graceful shutdown
        for i in $(seq 1 10); do
            if ! kill -0 "$SERVER_PID" 2>/dev/null; then
                break
            fi
            sleep 1
        done
        # Force kill if still running
        if kill -0 "$SERVER_PID" 2>/dev/null; then
            kill -9 "$SERVER_PID" 2>/dev/null || true
        fi
        echo "  ✓ Server stopped"
    else
        echo "[1/2] Server (PID: $SERVER_PID) not running"
    fi
    rm -f "$PID_DIR/server.pid"
    STOPPED_ANY=true
else
    echo "[1/2] No server PID file found"
fi

# Stop manager
if [ -f "$PID_DIR/manager.pid" ]; then
    MANAGER_PID=$(cat "$PID_DIR/manager.pid")
    if kill -0 "$MANAGER_PID" 2>/dev/null; then
        echo "[2/2] Stopping manager (PID: $MANAGER_PID)..."
        kill "$MANAGER_PID" 2>/dev/null || true
        sleep 1
        if kill -0 "$MANAGER_PID" 2>/dev/null; then
            kill -9 "$MANAGER_PID" 2>/dev/null || true
        fi
        echo "  ✓ Manager stopped"
    else
        echo "[2/2] Manager (PID: $MANAGER_PID) not running"
    fi
    rm -f "$PID_DIR/manager.pid"
    STOPPED_ANY=true
else
    echo "[2/2] No manager PID file found"
fi

echo ""

if [ "$STOPPED_ANY" = true ]; then
    echo "=========================================="
    echo " OryxOS stopped."
    echo "=========================================="
else
    echo "=========================================="
    echo " No running processes found."
    echo "=========================================="
fi
