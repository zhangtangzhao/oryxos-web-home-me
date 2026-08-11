#!/bin/bash
# OryxOS Package Script
# Usage: ./scripts/package.sh [version]
#
# Steps:
# 1. Clean and compile all Maven modules
# 2. Package fat JAR
# 3. Build VitePress website
# 4. Prepare release archive (excluding target/ artifacts from source)

set -euo pipefail

VERSION="${1:-1.0.0-SNAPSHOT}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
RELEASE_DIR="$PROJECT_DIR/release"
JAR_FILE="$PROJECT_DIR/oryxos-boot/target/oryxos-boot-${VERSION}.jar"

echo "=========================================="
echo " OryxOS Package Script"
echo " Version: $VERSION"
echo "=========================================="
echo ""

# Step 1: Build Java project
echo "[1/4] Building Java project..."
cd "$PROJECT_DIR"
mvn clean package -DskipTests -Drevision="$VERSION"
echo "  ✓ Maven build completed"
echo ""

# Step 2: Verify fat JAR
echo "[2/4] Verifying fat JAR..."
if [ -f "$JAR_FILE" ]; then
    JAR_SIZE=$(du -h "$JAR_FILE" | cut -f1)
    echo "  ✓ JAR created: $JAR_FILE ($JAR_SIZE)"
else
    echo "  ✗ JAR not found: $JAR_FILE"
    echo "  Looking for JAR files in target/:"
    find "$PROJECT_DIR" -name "*.jar" -path "*/target/*" 2>/dev/null || echo "  (no JAR files found)"
    exit 1
fi
echo ""

# Step 3: Build website
echo "[3/4] Building VitePress website..."
if [ -d "$PROJECT_DIR/website" ]; then
    cd "$PROJECT_DIR/website"
    if [ -f "package.json" ]; then
        npm install --silent
        npm run docs:build
        echo "  ✓ Website built"
    else
        echo "  ⚠ package.json not found, skipping website build"
    fi
else
    echo "  ⚠ website/ directory not found, skipping"
fi
cd "$PROJECT_DIR"
echo ""

# Step 4: Prepare release archive
echo "[4/4] Preparing release archive..."
mkdir -p "$RELEASE_DIR"

# Copy JAR
cp "$JAR_FILE" "$RELEASE_DIR/"

# Copy website dist (if exists)
if [ -d "$PROJECT_DIR/website/.vitepress/dist" ]; then
    cp -r "$PROJECT_DIR/website/.vitepress/dist" "$RELEASE_DIR/website"
fi

# Copy docs
cp -r "$PROJECT_DIR/docs" "$RELEASE_DIR/docs"

# Copy README and LICENSE
cp "$PROJECT_DIR/README.md" "$RELEASE_DIR/"
if [ -f "$PROJECT_DIR/LICENSE" ]; then
    cp "$PROJECT_DIR/LICENSE" "$RELEASE_DIR/"
fi

echo "  ✓ Release prepared in: $RELEASE_DIR"
echo ""

# Upload to remote (customize this section for your deployment target)
# Example: SCP to remote server
# REMOTE_HOST="${DEPLOY_HOST:-}"
# REMOTE_PATH="${DEPLOY_PATH:-/opt/oryxos}"
# if [ -n "$REMOTE_HOST" ]; then
#     echo "[Upload] Uploading to $REMOTE_HOST:$REMOTE_PATH ..."
#     # rsync -avz --exclude='target/' "$RELEASE_DIR/" "$REMOTE_HOST:$REMOTE_PATH/"
#     # scp "$JAR_FILE" "$REMOTE_HOST:$REMOTE_PATH/"
#     echo "  (Upload skipped — set DEPLOY_HOST and DEPLOY_PATH to enable)"
# fi

echo "=========================================="
echo " Package complete!"
echo " JAR: $RELEASE_DIR/$(basename "$JAR_FILE")"
echo " Website: $RELEASE_DIR/website/"
echo ""
echo " Run: java -jar $RELEASE_DIR/$(basename "$JAR_FILE") --version"
echo "=========================================="
